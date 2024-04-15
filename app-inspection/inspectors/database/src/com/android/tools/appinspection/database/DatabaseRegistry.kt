/*
 * Copyright 2020 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.tools.appinspection.database

import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteDatabase.OPEN_READWRITE
import android.util.ArraySet
import android.util.Log
import androidx.annotation.GuardedBy
import androidx.annotation.VisibleForTesting

private const val NOT_TRACKED = -1

/**
 * The class keeps track of databases under inspection, and can keep database connections open if
 * such option is enabled.
 *
 * Signals expected to be provided to the class:
 * * [.notifyDatabaseOpened] - should be called when the inspection code detects a database open
 *   operation.
 * * [.notifyAllDatabaseReferencesReleased] - should be called when the inspection code detects that
 *   the last database connection reference has been released (effectively a connection closed
 *   event).
 * * [.notifyKeepOpenToggle] - should be called when the inspection code detects a request to change
 *   the keep-database-connection-open setting (enabled|disabled).
 *
 * Callbacks exposed by the class:
 * * Detected a database that is now open, and previously was either closed or not tracked.
 * * Detected a database that is now closed, and previously was reported as open.
 *
 * @param onOpenedCallback called when tracking state changes (notTracked|closed)->open
 * @param onClosedCallback called when tracking state changes open->closed
 */
internal class DatabaseRegistry(
  private val onOpenedCallback: OnDatabaseOpenedCallback,
  private val onClosedCallback: OnDatabaseClosedCallback,
  private val testMode: Boolean = false,
) {
  // True if keep-database-connection-open functionality is enabled.
  private var keepDatabasesOpen = false
  private var forceOpen = false

  private val lock = Any()

  // Starting from '1' to distinguish from '0' which could stand for an unset parameter.
  @GuardedBy("lock") private var nextId = 1

  @GuardedBy("lock") private var isForceOpenInProgress = false

  // TODO: decide if use weak-references to database objects
  /**
   * Database connection id -> a list of database references pointing to the same database. The
   * collection is meant to only contain open connections (eventually consistent after all callbacks
   * queued behind [lock] are processed).
   */
  @GuardedBy("lock") private val databases = mutableMapOf<Int, MutableSet<SQLiteDatabase>>()

  // Database connection id -> extra database reference used to facilitate the
  // keep-database-connection-open functionality.
  @VisibleForTesting
  @GuardedBy("lock")
  internal val keepOpenReferences = mutableMapOf<Int, KeepOpenReference>()

  // Database path -> database connection id - allowing to report a consistent id for all
  // references pointing to the same path.
  @GuardedBy("lock") private val pathToId = mutableMapOf<String, Int>()

  @GuardedBy("lock") private val forcedOpen = mutableSetOf<SQLiteDatabase>()

  /**
   * Should be called when the inspection code detects a database being open operation.
   *
   * Note that the method should be called before any code has a chance to close the database, so
   * e.g. in an [androidx.inspection.ArtTooling.ExitHook.onExit] before the return value is
   * released. Thread-safe.
   */
  fun notifyDatabaseOpened(database: SQLiteDatabase) {
    handleDatabaseSignal(database)
  }

  fun notifyReleaseReference(database: SQLiteDatabase) {
    synchronized(lock) {
      /**
       * Prevent all other methods from releasing a reference if a [KeepOpenReference] is present
       *
       * The below will always succeed as [keepOpenReferences] only contains active references:
       * - we only insert active references into [keepOpenReferences]
       * - [KeepOpenReference.releaseAllReferences] is the only place where we allow references to
       *   be released
       * - [KeepOpenReference.releaseAllReferences] is private and can only be called from this
       *   class; and before it is called, it must be removed from [keepOpenReferences]
       */
      findKeepOpenReference(database)?.acquireReference()
    }
  }

  /**
   * Should be called when the inspection code detects that the last database connection reference
   * has been released (effectively a connection closed event). Thread-safe.
   */
  fun notifyAllDatabaseReferencesReleased(database: SQLiteDatabase) {
    handleDatabaseSignal(database)
  }

  /**
   * Should be called when the inspection code detects a request to change the
   * keep-database-connection-open setting (enabled|disabled). Thread-safe.
   */
  fun notifyKeepOpenToggle(setEnabled: Boolean) {
    synchronized(lock) {
      if (keepDatabasesOpen == setEnabled || forceOpen) {
        // forceOpen implies keepDatabasesOpen so ignore toggle commands. They will be disabled in
        // the UI anyway.
        return // no change
      }
      keepDatabasesOpen = setEnabled
      if (setEnabled) {
        // Changed from allowClose -> keepOpen
        databases.keys.forEach(::secureKeepOpenReference)
      } else {
        val openReferences = keepOpenReferences.values.toList()
        keepOpenReferences.clear()
        openReferences.forEach { it.releaseAllReferences() }
      }
    }
  }

  /**
   * Should be called at the start of inspection to pre-populate the list of databases with ones on
   * disk.
   */
  fun notifyOnDiskDatabase(path: String) {
    Log.v(HIDDEN_TAG, "notifyOnDiskDatabase: $path")
    synchronized(lock) {
      if (pathToId.containsKey(path)) {
        Log.v(HIDDEN_TAG, "Database is already open: $path")
        return
      }
      if (forceOpen) {
        Log.v(HIDDEN_TAG, "Force opening: $path")
        // We just need to open the database. Our hook will call notifyDatabaseOpened()
        isForceOpenInProgress = true
        try {
          val db = SQLiteDatabase.openDatabase(path, null, OPEN_READWRITE)
          if (testMode) {
            // During tests, ART Tooling hooks are not activated so this, so we need to trigger it
            // manually.
            notifyDatabaseOpened(db)
          }
        } finally {
          isForceOpenInProgress = false
        }
      } else {
        Log.v(HIDDEN_TAG, "Registering as offline: $path")
        val id = nextId++
        pathToId[path] = id
        onClosedCallback.onDatabaseClosed(id, path)
      }
    }
  }

  fun isForcedConnection(database: SQLiteDatabase) =
    synchronized(lock) { forcedOpen.contains(database) }

  /** Thread-safe */
  private fun handleDatabaseSignal(database: SQLiteDatabase) {
    var notifyOpenedId: Int? = null
    var notifyClosedId: Int? = null
    var isForced = isForceOpenInProgress

    synchronized(lock) {
      var id = getIdForDatabase(database)
      // TODO: revisit the text below since now we're synchronized on the same lock (lock)
      //  as releaseReference() calls -- which most likely allows for simplifying invariants
      // Guaranteed up to date:
      // - either called in a secure context (e.g. before the newly created connection is
      // returned from the creation; or with an already acquiredReference on it),
      // - or called after the last reference was released which cannot be undone.
      val isOpen = database.isOpen
      if (isForceOpenInProgress && isOpen) {
        forcedOpen.add(database)
      }

      if (id == NOT_TRACKED) { // handling a transition: not tracked -> tracked
        id = nextId++
        registerReference(id, database)
        if (isOpen) {
          notifyOpenedId = id
        } else {
          notifyClosedId = id
        }
      } else if (isOpen) {
        // handling a transition: tracked(closed) -> tracked(open)
        // There are two scenarios here:
        // - hasReferences is up-to-date and there is an open reference already, so we
        // don't need to announce a new one
        // - hasReferences is stale, and references in it are queued up to be
        // announced as closing, in this case the outside world thinks that the
        // connection is open (close ones not processed yet), so we don't need to
        // announce anything; later, when processing the queued up closed events nothing
        // will be announced as the currently processed database will keep at least one open
        // connection.
        val references = getReferences(id)
        if (references.isEmpty() || references.hasForcedConnections()) {
          notifyOpenedId = id
          // TODO(aalbert): Maybe actually close the forced connection. This would require either:
          //   1. Reopen a forced connection when all connections are closed
          //   2. Implicitly enabling keep-open when is-forced.
        }

        registerReference(id, database)
      } else {
        // handling a transition: tracked(open) -> tracked(closed)
        // There are two scenarios here:
        // - hasReferences is up-to-date, and we can use it
        // - hasReferences is stale, and references in it are queued up to be
        // announced as closed; in this case there is no harm not announcing a closed
        // event now as the subsequent calls will do it if appropriate
        val hasReferencesPre = hasReferences(id)
        unregisterReference(id, database)
        val referencesPost = getReferences(id)
        if (hasReferencesPre) {
          if (referencesPost.isEmpty()) {
            notifyClosedId = id
          } else if (referencesPost.hasOnlyForcedConnections()) {
            notifyOpenedId = id
            isForced = true
          }
        }
      }

      if (!isForced) {
        // This connection will not yet be registered as forced, so we don't want to use it as a
        // `keep-open` reference
        secureKeepOpenReference(id)
      }

      // notify of changes if any. We could have a `close` event followed by an `open` when
      // switching from forcedOpen to native connection.
      if (notifyClosedId != null) {
        onClosedCallback.onDatabaseClosed(notifyClosedId!!, database.pathForDatabase())
      }
      if (notifyOpenedId != null) {
        onOpenedCallback.onDatabaseOpened(notifyOpenedId!!, database.pathForDatabase(), isForced)
      }
      logDatabaseStatus(database.path)
    }
  }

  @GuardedBy("lock")
  private fun Set<SQLiteDatabase>.hasForcedConnections() = any { forcedOpen.contains(it) }

  @GuardedBy("lock")
  private fun Set<SQLiteDatabase>.hasOnlyForcedConnections() = all { forcedOpen.contains(it) }

  /**
   * Returns a currently active database reference if one is available. Null otherwise. Consumer of
   * this method must release the reference when done using it. Thread-safe
   */
  fun getConnection(databaseId: Int): SQLiteDatabase? {
    synchronized(lock) {
      return getConnectionImpl(databaseId)
    }
  }

  fun enableForceOpen() {
    forceOpen = true
  }

  @VisibleForTesting fun getDatabases(id: Int): Set<SQLiteDatabase> = databases.getValue(id)

  fun dispose() {
    // TODO(161081452): release database locks and keep-open references
    synchronized(lock) {
      for (database in forcedOpen) {
        database.close()
      }
    }
  }

  @GuardedBy("lock")
  private fun getConnectionImpl(databaseId: Int): SQLiteDatabase? {
    return getOpenDatabases(databaseId).findBestConnection()
  }

  @GuardedBy("lock")
  private fun registerReference(id: Int, database: SQLiteDatabase) {
    val references =
      databases.getOrPut(id) {
        ArraySet<SQLiteDatabase>(1).also { references ->
          databases[id] = references
          if (!database.isInMemoryDatabase()) {
            pathToId[database.pathForDatabase()] = id
          }
        }
      }
    // databases only tracks open instances
    if (database.isOpen) {
      references.add(database)
    }
  }

  @GuardedBy("lock")
  private fun unregisterReference(id: Int, database: SQLiteDatabase) {
    databases[id]?.remove(database)
  }

  @GuardedBy("lock")
  private fun secureKeepOpenReference(id: Int) {
    val shouldKeepOpen = keepDatabasesOpen || forceOpen
    if (!(shouldKeepOpen)) {
      return
    }

    val best =
      getOpenDatabases(id).filter { !isForcedConnection(it) }.findBestConnection() ?: return
    val kept = keepOpenReferences[id]
    if (best != kept?.database) {
      keepOpenReferences[id] = KeepOpenReference(best)
      kept?.releaseAllReferences()
    }
  }

  @GuardedBy("lock")
  private fun getIdForDatabase(database: SQLiteDatabase): Int {
    val databasePath = database.pathForDatabase()

    val previousId = pathToId[databasePath]
    if (previousId != null) {
      return previousId
    }

    if (database.isInMemoryDatabase()) {
      return databases.entries
        .find { (_, databasesForKey) -> databasesForKey.contains(database) }
        ?.key ?: NOT_TRACKED
    }

    return NOT_TRACKED
  }

  @GuardedBy("lock")
  private fun hasReferences(databaseId: Int) = getReferences(databaseId).isNotEmpty()

  @GuardedBy("lock")
  private fun getReferences(databaseId: Int) = databases[databaseId] ?: emptySet()

  internal fun interface OnDatabaseOpenedCallback {
    fun onDatabaseOpened(databaseId: Int, path: String, isForced: Boolean)
  }

  internal fun interface OnDatabaseClosedCallback {
    fun onDatabaseClosed(databaseId: Int, path: String)
  }

  @VisibleForTesting
  internal inner class KeepOpenReference(val database: SQLiteDatabase) {
    private val lock = Any()

    @GuardedBy("lock") private var acquiredReferenceCount = 0

    fun acquireReference() {
      synchronized(lock) {
        if (database.tryAcquireReference()) {
          acquiredReferenceCount++
        }
      }
    }

    /**
     * This should only be called after removing the object from
     * [DatabaseRegistry.keepOpenReferences]. Otherwise, the object will get in its own way or
     * releasing its references.
     */
    fun releaseAllReferences() {
      synchronized(lock) {
        while (acquiredReferenceCount > 0) {
          database.releaseReference()
          acquiredReferenceCount--
        }
        if (testMode && !database.isOpen) {
          // Simulate hook call if operation resulted in database getting actually closed
          notifyAllDatabaseReferencesReleased(database)
        }
      }
    }
  }

  private fun logDatabaseStatus(path: String) {
    if (!Log.isLoggable(HIDDEN_TAG, Log.VERBOSE)) {
      return
    }
    synchronized(lock) {
      val id = pathToId[path] ?: return
      Log.v(HIDDEN_TAG, "Database: $path (id=$id):")
      val statusList = databases[id]?.map { it.getStatus() } ?: return
      Log.v(HIDDEN_TAG, "  instances: [${statusList.joinToString { it }}]")
    }
  }

  @GuardedBy("lock")
  private fun SQLiteDatabase.getStatus(): String {
    val id = pathToId[path] ?: -1
    val suffix = if (keepOpenReferences[id]?.database == this) "*" else ""
    return when {
      isReadOnly -> "ReadOnly"
      isForcedConnection(this) -> "Forced"
      else -> "ReadWrite"
    } + suffix
  }

  fun getIdForPath(path: String): Int? {
    synchronized(lock) {
      return pathToId[path]
    }
  }

  private class DbScore(val db: SQLiteDatabase, val score: Int)

  @GuardedBy("lock")
  private fun getOpenDatabases(id: Int) = databases[id]?.filter { it.isOpen } ?: emptySet()

  private fun Collection<SQLiteDatabase>.findBestConnection(): SQLiteDatabase? {
    // Assign a score to each candidate.
    // - Read-only instance has the lowest score.
    // - Non forced read-write instance has the max score.
    val scores = map {
      val score =
        when {
          it.isReadOnly -> 0
          isForcedConnection(it) -> 1
          else -> return@map DbScore(it, 2)
        }
      DbScore(it, score)
    }
    if (Log.isLoggable(HIDDEN_TAG, Log.VERBOSE)) {
      Log.v(HIDDEN_TAG, "scores: ${scores.joinToString { "${it.db.hashCode()} -> ${it.score}" }}")
    }
    return scores.maxByOrNull { it.score }?.db
  }

  private fun findKeepOpenReference(database: SQLiteDatabase): KeepOpenReference? {
    return keepOpenReferences.values.find { it.database == database }
  }
}
