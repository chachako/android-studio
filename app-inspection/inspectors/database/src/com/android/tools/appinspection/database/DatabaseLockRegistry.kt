/*
 * Copyright 2021 The Android Open Source Project
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
import android.os.CancellationSignal
import androidx.annotation.GuardedBy
import androidx.annotation.VisibleForTesting
import com.android.tools.appinspection.database.SqliteInspector.DatabaseConnection
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit.MILLISECONDS

/** Handles database locking and associated bookkeeping. Thread-safe. */
internal class DatabaseLockRegistry {
  private val guard = Any() // used for synchronization within the class

  @GuardedBy("guard") private val lockIdToLockMap = mutableMapOf<Int, Lock>()

  @GuardedBy("guard") private val databaseIdToLockMap = mutableMapOf<Int, Lock>()

  @GuardedBy("guard") private var nextLockId = 1

  // A dedicated thread required as database transactions are tied to a thread. In order to
  // release a lock, we need to use the same thread as the one we used to establish the lock.
  // Thread names need to start with 'Studio:' as per some framework limitations.
  private val executor =
    Executors.newSingleThreadExecutor { r ->
      // limit = 15 characters
      Thread(r, "Studio:Sql:Lock").apply { isDaemon = true }
    }

  /**
   * Locks a database identified by the provided database id. If a lock on the database is already
   * in place, an existing lock will be issued. Locks keep count of simultaneous requests, so that
   * the database is only unlocked once all callers release their issued locks.
   */
  fun acquireLock(databaseId: Int, database: SQLiteDatabase): Int {
    synchronized(guard) {
      val lock =
        databaseIdToLockMap.getOrPut(databaseId) {
          Lock(nextLockId++, databaseId, database).also {
            it.lockDatabase()
            lockIdToLockMap[it.lockId] = it
          }
        }
      lock.count++
      return lock.lockId
    }
  }

  /**
   * Releases a lock on a database identified by the provided lock id. If the same lock has been
   * provided multiple times (for lock requests on an already locked database), the lock needs to be
   * released by all previous requestors for the database to get unlocked.
   */
  fun releaseLock(lockId: Int) {
    synchronized(guard) {
      val lock =
        lockIdToLockMap[lockId] ?: throw IllegalArgumentException("No lock with id: $lockId")
      if (--lock.count == 0) {
        try {
          lock.unlockDatabase()
        } catch (e: Exception) {
          lock.count++ // correct the count
          throw e
        }
        lockIdToLockMap.remove(lock.lockId)
        databaseIdToLockMap.remove(lock.databaseId)
      }
    }
  }

  /**
   * @return `null` if the database is not locked; the database and the executor that locked the
   *   database otherwise
   */
  fun getConnection(databaseId: Int): DatabaseConnection? {
    synchronized(guard) {
      val lock = databaseIdToLockMap[databaseId] ?: return null
      return DatabaseConnection(lock.database, executor)
    }
  }

  /**
   * Starts a database transaction and acquires an extra database reference to keep the database
   * open while the lock is in place.
   *
   * TODO(aalbert): Use coroutines
   */
  private fun Lock.lockDatabase() {
    // keeps the database open while a lock is in place; released when the lock is released
    var keepOpenReferenceAcquired = false

    val cancellationSignal = CancellationSignal()
    var future: Future<*>? = null

    // TODO(aalbert): Split this try-catch to 2 parts. One for `acquireReference` and one for
    //  `Future.get`. This way, we don't need the `keepOpenReferenceAcquired`
    try {
      database.acquireReference()
      keepOpenReferenceAcquired = true

      // Submitting a Runnable, so we can set a timeout.
      future =
        executor.submit {
          // starts a transaction
          database
            .rawQuery("BEGIN IMMEDIATE;", arrayOfNulls(0), cancellationSignal)
            .count // forces the cursor to execute the query
        }
      future.get(TIMEOUT_MS, MILLISECONDS)
    } catch (e: Exception) {
      if (keepOpenReferenceAcquired) database.releaseReference()
      cancellationSignal.cancel()
      future?.cancel(true)
      throw e
    }
  }

  /**
   * Ends the database transaction and releases the extra database reference that kept the database
   * open while the lock was in place.
   *
   * TODO(aalbert): Use coroutines
   */
  private fun Lock.unlockDatabase() {
    val cancellationSignal = CancellationSignal()
    var future: Future<*>? = null

    // TODO(aalbert): Split this try-catch to 2 parts. One for `acquireReference` and one for
    //  `Future.get`. This way, we don't need the `keepOpenReferenceAcquired`
    try {
      // Submitting a Runnable, so we can set a timeout.
      future =
        SqliteInspectionExecutors.submit(executor) { // ends the transaction
          database
            .rawQuery("ROLLBACK;", arrayOfNulls(0), cancellationSignal)
            .count // forces the cursor to execute the query
          database.releaseReference()
        }
      future.get(TIMEOUT_MS, MILLISECONDS)
    } catch (e: Exception) {
      cancellationSignal.cancel()
      future?.cancel(true)
      throw e
    }
  }

  private class Lock(val lockId: Int, val databaseId: Int, val database: SQLiteDatabase) {
    var count: Int = 0 // number of simultaneous locks secured on the database
  }

  companion object {
    // TODO(aalbert): Refactor to allow injecting a timeout
    @VisibleForTesting var TIMEOUT_MS: Long = 5000
  }
}
