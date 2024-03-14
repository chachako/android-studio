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
import android.util.ArraySet
import androidx.annotation.GuardedBy

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
  private val onOpenedCallback: Callback,
  private val onClosedCallback: Callback,
) {
  // True if keep-database-connection-open functionality is enabled.
  private var keepDatabasesOpen = false

  private val lock = Any()

  // Starting from '1' to distinguish from '0' which could stand for an unset parameter.
  @GuardedBy("lock") private var mNextId = 1

  // TODO: decide if use weak-references to database objects
  /**
   * Database connection id -> a list of database references pointing to the same database. The
   * collection is meant to only contain open connections (eventually consistent after all callbacks
   * queued behind [lock] are processed).
   */
  @GuardedBy("lock") private val databases = mutableMapOf<Int, MutableSet<SQLiteDatabase>>()

  // Database connection id -> extra database reference used to facilitate the
  // keep-database-connection-open functionality.
  @GuardedBy("lock") private val keepOpenReferences = mutableMapOf<Int, KeepOpenReference>()

  // Database path -> database connection id - allowing to report a consistent id for all
  // references pointing to the same path.
  @GuardedBy("lock") private val pathToId = mutableMapOf<String, Int>()

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
       */
      keepOpenReferences.values
        .filter { it.database == database }
        .forEach {
          /**
           * The below will always succeed as [keepOpenReferences] only contains active references:
           * - we only insert active references into [keepOpenReferences]
           * - [KeepOpenReference.releaseAllReferences] is the only place where we allow references
           *   to be released
           * - [KeepOpenReference.releaseAllReferences] is private and can only be called from this
           *   class; and before it is called, it must be removed from [keepOpenReferences]
           */
          it.acquireReference()
        }
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
      if (keepDatabasesOpen == setEnabled) {
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
    synchronized(lock) {
      if (!pathToId.containsKey(path)) {
        val id = mNextId++
        pathToId[path] = id
        onClosedCallback.onPostEvent(id, path)
      }
    }
  }

  /** Thread-safe */
  private fun handleDatabaseSignal(database: SQLiteDatabase) {
    var notifyOpenedId: Int? = null
    var notifyClosedId: Int? = null

    synchronized(lock) {
      var id = getIdForDatabase(database)
      // TODO: revisit the text below since now we're synchronized on the same lock (lock)
      //  as releaseReference() calls -- which most likely allows for simplifying invariants
      // Guaranteed up to date:
      // - either called in a secure context (e.g. before the newly created connection is
      // returned from the creation; or with an already acquiredReference on it),
      // - or called after the last reference was released which cannot be undone.
      val isOpen = database.isOpen

      if (id == NOT_TRACKED) { // handling a transition: not tracked -> tracked
        id = mNextId++
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
        if (!hasReferences(id)) {
          notifyOpenedId = id
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
        val hasReferencesPost = hasReferences(id)
        if (hasReferencesPre && !hasReferencesPost) {
          notifyClosedId = id
        }
      }

      secureKeepOpenReference(id)

      // notify of changes if any
      if (notifyOpenedId != null) {
        onOpenedCallback.onPostEvent(notifyOpenedId!!, database.pathForDatabase())
      } else if (notifyClosedId != null) {
        onClosedCallback.onPostEvent(notifyClosedId!!, database.pathForDatabase())
      }
    }
  }

  /**
   * Returns a currently active database reference if one is available. Null otherwise. Consumer of
   * this method must release the reference when done using it. Thread-safe
   */
  fun getConnection(databaseId: Int): SQLiteDatabase? {
    synchronized(lock) {
      return getConnectionImpl(databaseId)
    }
  }

  @GuardedBy("lock")
  private fun getConnectionImpl(databaseId: Int): SQLiteDatabase? {
    val keepOpenReference = keepOpenReferences[databaseId]
    if (keepOpenReference != null) {
      return keepOpenReference.database
    }

    val references = databases[databaseId] ?: return null

    // tries to find an open reference preferring write-enabled over read-only
    var readOnlyReference: SQLiteDatabase? = null
    references
      .filter { it.isOpen }
      .forEach {
        if (!it.isReadOnly) {
          return it
        } // write-enabled was found: return it
        readOnlyReference = it
      }
    return readOnlyReference // or null if we did not find an open reference
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
    if (!keepDatabasesOpen || keepOpenReferences.containsKey(id)) {
      // Keep-open is disabled, or we already have a keep-open-reference for that id.
      return
    }

    // Try secure a keep-open reference
    val reference = getConnectionImpl(id)
    if (reference != null) {
      keepOpenReferences[id] = KeepOpenReference(reference)
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
  private fun hasReferences(databaseId: Int): Boolean {
    val references = databases[databaseId] ?: return false
    return references.isNotEmpty()
  }

  internal fun interface Callback {
    fun onPostEvent(databaseId: Int, path: String)
  }

  private class KeepOpenReference(val database: SQLiteDatabase) {
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
      }
    }
  }
}
