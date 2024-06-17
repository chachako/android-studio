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
import android.os.Build
import android.os.SystemClock.sleep
import androidx.sqlite.inspection.SqliteInspectorProtocol.AcquireDatabaseLockCommand
import androidx.sqlite.inspection.SqliteInspectorProtocol.Command
import androidx.sqlite.inspection.SqliteInspectorProtocol.ErrorContent.ErrorCode.ERROR_ISSUE_WITH_LOCKING_DATABASE
import androidx.sqlite.inspection.SqliteInspectorProtocol.ErrorContent.ErrorCode.ERROR_NO_OPEN_DATABASE_WITH_REQUESTED_ID
import androidx.sqlite.inspection.SqliteInspectorProtocol.ReleaseDatabaseLockCommand
import androidx.sqlite.inspection.SqliteInspectorProtocol.Response.OneOfCase.ACQUIRE_DATABASE_LOCK
import androidx.sqlite.inspection.SqliteInspectorProtocol.Response.OneOfCase.ERROR_OCCURRED
import androidx.sqlite.inspection.SqliteInspectorProtocol.Response.OneOfCase.RELEASE_DATABASE_LOCK
import com.android.tools.appinspection.common.testing.LogPrinterRule
import com.android.tools.appinspection.database.testing.Column
import com.android.tools.appinspection.database.testing.Database
import com.android.tools.appinspection.database.testing.MessageFactory
import com.android.tools.appinspection.database.testing.MessageFactory.createTrackDatabasesCommand
import com.android.tools.appinspection.database.testing.SqliteInspectorTestEnvironment
import com.android.tools.appinspection.database.testing.Table
import com.android.tools.appinspection.database.testing.displayName
import com.android.tools.appinspection.database.testing.issueQuery
import com.google.common.truth.Truth.assertThat
import java.util.concurrent.Callable
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors.newSingleThreadExecutor
import java.util.concurrent.TimeUnit.MILLISECONDS
import java.util.concurrent.TimeUnit.SECONDS
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.regex.Pattern.CASE_INSENSITIVE
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.SQLiteMode
import org.robolectric.junit.rules.CloseGuardRule

@RunWith(RobolectricTestRunner::class)
@Config(
  manifest = Config.NONE,
  minSdk = Build.VERSION_CODES.O,
  maxSdk = Build.VERSION_CODES.UPSIDE_DOWN_CAKE,
)
@SQLiteMode(SQLiteMode.Mode.NATIVE)
class DatabaseLockingTest {
  private val testEnvironment = SqliteInspectorTestEnvironment()

  @get:Rule
  val rule: RuleChain =
    RuleChain.outerRule(CloseGuardRule()).around(testEnvironment).around(LogPrinterRule())

  private val database = Database("db1", Table("t1", listOf(Column("c1", "int"))))
  private val table = database.tables.single()
  private val column = table.columns.single()

  private val value1 = 1337
  private val value2 = 2600

  // simulates an application thread (i.e. not 'Instr: androidx.test.runner.AndroidJUnitRunner')
  private val applicationThread = newSingleThreadExecutor()

  @Test fun test_singleLock() = test_locking(simultaneousLocks = 1)

  @Test fun test_simultaneousLocks() = test_locking(simultaneousLocks = 3)

  private fun test_locking(simultaneousLocks: Int) = runBlocking {
    // create a non-empty database
    val instance = testEnvironment.openDatabase(database, writeAheadLoggingEnabled = true)
    val databaseId = testEnvironment.inspectDatabase(instance)
    insertValue(instance, value1)
    assertThat(getValueSum(instance)).isEqualTo(value1)

    // lock the database
    val lockIds =
      (1..simultaneousLocks).map {
        testEnvironment.sendCommand(acquireLockCommand(databaseId)).let { response ->
          assertThat(response.oneOfCase).isEqualTo(ACQUIRE_DATABASE_LOCK)
          response.acquireDatabaseLock.lockId
        }
      }
    assertThat(lockIds.toSet().single()) // expecting simultaneous locks to have the same id
      .isGreaterThan(0) // zero could mean unset

    // try performing an insert operation while the database is locked
    val insertJob =
      launch(applicationThread.asCoroutineDispatcher()) { insertValue(instance, value2) }
        .also {
          it.start()
          it.ensureActive()
        }
    assertThat(getValueSum(instance)).isEqualTo(value1) // no change because database is locked

    // release the lock
    lockIds.forEach { lockId ->
      insertJob.ensureActive()
      testEnvironment.sendCommand(releaseLockCommand(lockId)).let { response ->
        assertThat(response.oneOfCase).isEqualTo(RELEASE_DATABASE_LOCK)
      }
    }
    insertJob.join()

    // ensure the previously blocked insert operation succeeded
    assertThat(getValueSum(instance)).isEqualTo(value1 + value2) // insert succeeded
  }

  @Test
  fun test_lockIdsUniquePerDb() = runBlocking {
    val dbs = listOf("db1", "db2", "db3").map { testEnvironment.openDatabase(Database(it)) }
    val dbIds = testEnvironment.inspectDatabases(dbs)
    val lockIds =
      dbIds.map { testEnvironment.sendCommand(acquireLockCommand(it)).acquireDatabaseLock.lockId }
    assertThat(lockIds.toSet()).hasSize(dbIds.size)
    assertThat(lockIds.minOrNull() ?: 0).isGreaterThan(0)

    lockIds.forEach { testEnvironment.sendCommand(releaseLockCommand(it)) }
  }

  @Test
  fun test_timeoutError() =
    withLockingTimeoutOverride(50L) {
      runBlocking {
        // create a non-empty database
        val instance = testEnvironment.openDatabase(database, writeAheadLoggingEnabled = true)
        val databaseId = testEnvironment.inspectDatabase(instance)
        instance.beginTransaction() // guarantees a timeout
        try {
          // try to lock the database (expecting a failure)
          testEnvironment.sendCommand(acquireLockCommand(databaseId)).let { response ->
            // verify expected error response
            assertThat(response.oneOfCase).isEqualTo(ERROR_OCCURRED)
            assertThat(response.errorOccurred.content.message)
              .matches(".*trying to lock .*database .*TimeoutException.*".toPattern())
            assertThat(response.errorOccurred.content.errorCode)
              .isEqualTo(ERROR_ISSUE_WITH_LOCKING_DATABASE)
          }
        } finally {
          instance.endTransaction()
        }
      }
    }

  @Test
  fun test_databaseAlreadyClosed() = runBlocking {
    testEnvironment.sendCommand(createTrackDatabasesCommand())
    val instance = testEnvironment.openDatabase(database, writeAheadLoggingEnabled = true)
    val databaseId = testEnvironment.awaitDatabaseOpenedEvent(instance.displayName).databaseId
    testEnvironment.closeDatabase(instance)

    // try to lock the database (expecting a failure)
    testEnvironment.sendCommand(acquireLockCommand(databaseId)).let { response ->
      // verify expected error response
      assertThat(response.oneOfCase).isEqualTo(ERROR_OCCURRED)
      assertThat(response.errorOccurred.content.message)
        .matches(".*database .*already .*closed.*".toPattern())
      assertThat(response.errorOccurred.content.errorCode)
        .isEqualTo(ERROR_NO_OPEN_DATABASE_WITH_REQUESTED_ID)
    }
  }

  @Test
  fun test_invalidLockId() = runBlocking {
    // try to lock the database (expecting a failure)
    val invalidLockId = 2255
    testEnvironment.sendCommand(releaseLockCommand(invalidLockId)).let { response ->
      // verify expected error response
      assertThat(response.oneOfCase).isEqualTo(ERROR_OCCURRED)
      assertThat(response.errorOccurred.content.message)
        .matches(
          ".*trying to unlock .*database .*no lock with id.* $invalidLockId.*"
            .toPattern(CASE_INSENSITIVE)
        )
      assertThat(response.errorOccurred.content.errorCode)
        .isEqualTo(ERROR_ISSUE_WITH_LOCKING_DATABASE)
    }
  }

  @Test
  fun test_keepDatabaseOpenWhileLocked() = runBlocking {
    // create a non-empty database
    val instance = testEnvironment.openDatabase(database, writeAheadLoggingEnabled = true)
    val databaseId = testEnvironment.inspectDatabase(instance)

    // lock the database
    val lockId =
      testEnvironment.sendCommand(acquireLockCommand(databaseId)).acquireDatabaseLock.lockId
    assertThat(lockId).isGreaterThan(0)

    // close the database instance (unsuccessfully)
    instance.close()
    assertThat(instance.isOpen).isTrue()

    // release lock
    assertThat(testEnvironment.sendCommand(releaseLockCommand(lockId)).oneOfCase)
      .isEqualTo(RELEASE_DATABASE_LOCK)

    // successful close as db closed notification received
    assertThat(instance.isOpen).isFalse()
    testEnvironment.assertNoQueuedEvents()
  }

  @Test
  fun test_parallelRequests_firstTimesOut() =
    withLockingTimeoutOverride(500) {
      runBlocking {
        // create and inspect two databases
        val (db1, db2) =
          listOf("db1", "db2").map { testEnvironment.openDatabase(Database(it, table)) }
        val (id1, id2) = testEnvironment.inspectDatabases(db1, db2)

        // lock the first database (app thread)
        applicationThread.submit { db1.beginTransaction() }.get(2, SECONDS)

        // request lock on both databases
        val latch = CountDownLatch(1)
        val (lockTask1, lockTask2) =
          listOf(id1, id2).map { dbId ->
            newSingleThreadExecutor()
              .submit(
                Callable {
                  if (dbId == id1) latch.countDown() else latch.await() // db1 first
                  runBlocking { testEnvironment.sendCommand(acquireLockCommand(dbId)) }
                }
              )
          }

        // verify db1 timeout, db2 success
        val (result1, result2) = listOf(lockTask1, lockTask2).map { it.get(2, SECONDS) }
        assertThat(result1.oneOfCase).isEqualTo(ERROR_OCCURRED)
        assertThat(result2.oneOfCase).isEqualTo(ACQUIRE_DATABASE_LOCK)

        // unlock the first database (app thread) and retry locking
        applicationThread.submit { db1.endTransaction() }.get(2, SECONDS)
        val result3 = testEnvironment.sendCommand(acquireLockCommand(id1))
        result3.let { response -> assertThat(response.oneOfCase).isEqualTo(ACQUIRE_DATABASE_LOCK) }
        testEnvironment.sendCommand(releaseLockCommand(result2.acquireDatabaseLock.lockId))
        testEnvironment.sendCommand(releaseLockCommand(result3.acquireDatabaseLock.lockId))
      }
    }

  @Test
  fun test_lockingPreventsOpen(): Unit = runBlocking {
    val db = testEnvironment.openDatabase(Database("db", table))
    val id = testEnvironment.inspectDatabase(db)
    val latch = CountDownLatch(1)

    // Lock database
    val lockId = testEnvironment.sendCommand(acquireLockCommand(id)).acquireDatabaseLock.lockId
    launch(Dispatchers.IO) {
      // Simulate opening a database while it's locked
      testEnvironment.triggerOnOpenedEntry(db.path)
      latch.countDown()
    }

    // Should not be able top open the database while it's locked
    val completed = latch.await(500, MILLISECONDS)
    assertThat(completed).named("'Open' should not succeed because db is locked").isFalse()

    // Unlock database and assert that `open` has completed
    testEnvironment.sendCommand(releaseLockCommand(lockId))
    latch.await(2, SECONDS)
  }

  @Test
  fun test_appResumesAfterLockReleased(): Unit = runBlocking {
    // create database
    val db = testEnvironment.openDatabase(database)
    val id = testEnvironment.inspectDatabase(db)

    // start a job inserting values at app thread
    val insertCount = AtomicInteger(0)
    var latch = CountDownLatch(2) // allows for a few insert operations to succeed
    val active = AtomicBoolean(true)
    val insertTask =
      applicationThread.submit {
        while (active.get()) {
          db.execSQL("insert into ${table.name} values (1)")
          insertCount.incrementAndGet()
          latch.countDown()
          sleep(10)
        }
      }
    assertThat(latch.await(2, SECONDS)).isTrue()

    // lock the database
    val lockId = testEnvironment.sendCommand(acquireLockCommand(id)).acquireDatabaseLock.lockId
    assertThat(lockId).isGreaterThan(0) // check if locking succeeded

    // check if the lock is blocking the insert task
    val countWhenLocked = insertCount.get()
    sleep(100) // allows for a few insert operations to happen (if the lock doesn't work)
    assertThat(insertCount.get()).isEqualTo(countWhenLocked)

    // release the lock
    testEnvironment.sendCommand(releaseLockCommand(lockId)).also {
      assertThat(it.oneOfCase).isEqualTo(RELEASE_DATABASE_LOCK)
    }

    // check if the insert task resumed after unlocking
    latch = CountDownLatch(2) // allows for a few insert operations to happen
    assertThat(latch.await(2, SECONDS)).isTrue()
    assertThat(insertCount.get()).isGreaterThan(countWhenLocked)
    active.set(false)
    insertTask.get(2, SECONDS)
  }

  @Test
  fun test_endToEnd_inspector_lock_query_unlock_walOn() =
    test_endToEnd_inspector_lock_query_unlock(writeAheadLoggingEnabled = true)

  @Test
  fun test_endToEnd_inspector_lock_query_unlock_walOff() =
    test_endToEnd_inspector_lock_query_unlock(writeAheadLoggingEnabled = false)

  private fun test_endToEnd_inspector_lock_query_unlock(writeAheadLoggingEnabled: Boolean) =
    runBlocking {
      // create database
      val db = testEnvironment.openDatabase(database, writeAheadLoggingEnabled)
      insertValue(db, value1)
      assertThat(getValueSum(db)).isEqualTo(value1)

      // inspect database
      val dbId = testEnvironment.inspectDatabase(db)

      // establish a lock
      val lockId = testEnvironment.sendCommand(acquireLockCommand(dbId)).acquireDatabaseLock.lockId
      assertThat(lockId).isGreaterThan(0) // check that locking succeeded

      // try to insert a value on app thread
      val appInsertDone = AtomicBoolean(false)
      val appInsertTask =
        applicationThread.submit {
          insertValue(db, value2)
          appInsertDone.set(true)
        }
      assertThat(appInsertDone.get()).isFalse()

      // query schema
      testEnvironment.sendCommand(MessageFactory.createGetSchemaCommand(dbId)).let {
        assertThat(it.getSchema.tablesList.map { t -> t.name }).isEqualTo(listOf(table.name))
      }

      // repeat lock (testing simultaneous locks)
      testEnvironment.sendCommand(acquireLockCommand(dbId)).let {
        assertThat(it.acquireDatabaseLock.lockId).isEqualTo(lockId) // the same lock id expected
      }

      // query table
      testEnvironment.issueQuery(dbId, "select sum(${column.name}) from ${table.name}").let {
        assertThat(appInsertDone.get()).isFalse()
        assertThat(it.rowsList.single().valuesList.single().longValue.toInt()).isEqualTo(value1)
      }

      // release all locks and verify the app thread insert operation succeeds
      testEnvironment.sendCommand(releaseLockCommand(lockId))
      assertThat(appInsertDone.get()).isFalse()
      testEnvironment.sendCommand(releaseLockCommand(lockId))
      appInsertTask.get(2, SECONDS)
      assertThat(appInsertDone.get()).isTrue()
      assertThat(getValueSum(db)).isEqualTo(value1 + value2)
    }

  @Suppress("SameParameterValue")
  private fun withLockingTimeoutOverride(overrideMs: Long, block: () -> Any) {
    val current = DatabaseLockRegistry.TIMEOUT_MS
    try {
      DatabaseLockRegistry.TIMEOUT_MS = overrideMs
      block()
    } finally {
      DatabaseLockRegistry.TIMEOUT_MS = current
    }
  }

  private fun acquireLockCommand(databaseId: Int) =
    Command.newBuilder()
      .setAcquireDatabaseLock(AcquireDatabaseLockCommand.newBuilder().setDatabaseId(databaseId))
      .build()

  private fun releaseLockCommand(lockId: Int) =
    Command.newBuilder()
      .setReleaseDatabaseLock(ReleaseDatabaseLockCommand.newBuilder().setLockId(lockId))
      .build()

  private fun getValueSum(instance: SQLiteDatabase): Int =
    instance
      .compileStatement("select sum(${column.name}) from ${table.name}")
      .simpleQueryForLong()
      .toInt()

  private fun insertValue(instance: SQLiteDatabase, value: Int) =
    instance.execSQL("insert into ${table.name} values ($value)")
}
