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

import android.app.Application
import android.database.sqlite.SQLiteClosable
import android.database.sqlite.SQLiteDatabase
import android.os.Build
import androidx.sqlite.inspection.SqliteInspectorProtocol.Event
import androidx.sqlite.inspection.SqliteInspectorProtocol.Response
import com.android.tools.appinspection.common.testing.LogPrinterRule
import com.android.tools.appinspection.database.testing.*
import com.android.tools.appinspection.database.testing.MessageFactory.createKeepDatabasesOpenCommand
import com.android.tools.appinspection.database.testing.MessageFactory.createKeepDatabasesOpenResponse
import com.android.tools.appinspection.database.testing.MessageFactory.createTrackDatabasesCommand
import com.android.tools.appinspection.database.testing.MessageFactory.createTrackDatabasesResponse
import com.google.common.truth.Truth.assertThat
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.rules.TemporaryFolder
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
class TrackDatabasesTest {
  private val testEnvironment = SqliteInspectorTestEnvironment()
  private val temporaryFolder = TemporaryFolder()

  @get:Rule
  val rule: RuleChain =
    RuleChain.outerRule(CloseGuardRule())
      .around(testEnvironment)
      .around(temporaryFolder)
      .around(LogPrinterRule())

  @Test
  fun test_track_databases(): Unit = runBlocking {
    val alreadyOpenDatabases =
      listOf(
        testEnvironment.openDatabase(Database("db1")),
        testEnvironment.openDatabase(Database("db2")),
      )

    testEnvironment.registerAlreadyOpenDatabases(alreadyOpenDatabases)

    testEnvironment.sendCommand(createTrackDatabasesCommand()).let { response ->
      assertThat(response).isEqualTo(createTrackDatabasesResponse())
    }

    // evaluate 'already-open' instances are found
    alreadyOpenDatabases.let { expected ->
      val actual = expected.indices.map { testEnvironment.receiveEvent().databaseOpened }
      testEnvironment.assertNoQueuedEvents()
      assertThat(actual.map { it.databaseId }.distinct()).hasSize(expected.size)
      expected.forEachIndexed { ix, _ ->
        assertThat(actual[ix].path).isEqualTo(expected[ix].displayName)
      }
    }

    // evaluate registered hooks
    val possibleSignatures =
      listOf(
        OPEN_DATABASE_COMMAND_SIGNATURE_API11,
        OPEN_DATABASE_COMMAND_SIGNATURE_API27,
        CREATE_IN_MEMORY_DATABASE_COMMAND_SIGNATURE_API27,
      )
    val exitHookSignatures = buildSet {
      add(OPEN_DATABASE_COMMAND_SIGNATURE_API11)
      if (Build.VERSION.SDK_INT >= 27) {
        add(OPEN_DATABASE_COMMAND_SIGNATURE_API27)
        add(CREATE_IN_MEMORY_DATABASE_COMMAND_SIGNATURE_API27)
      }
    }
    val entryHookSignatures = buildSet {
      add(OPEN_DATABASE_COMMAND_SIGNATURE_API11)
      if (Build.VERSION.SDK_INT >= 27) {
        add(OPEN_DATABASE_COMMAND_SIGNATURE_API27)
      }
    }

    val hookEntries =
      testEnvironment.getRegisteredHooks().filter { possibleSignatures.contains(it.originMethod) }
    val exitHooks = hookEntries.filterIsInstance<Hook.ExitHook>()
    assertThat(exitHooks.map { it.originMethod }).containsExactlyElementsIn(exitHookSignatures)
    exitHooks.forEachIndexed { ix, entry ->
      // expect one exit hook tracking database open events
      assertThat(entry).isInstanceOf(Hook.ExitHook::class.java)
      assertThat(entry.originClass.name).isEqualTo(SQLiteDatabase::class.java.name)

      // verify that executing the registered hook will result in tracking events
      testEnvironment.assertNoQueuedEvents()
      val database = testEnvironment.openDatabase(Database("db3_$ix"))
      testEnvironment.receiveEvent().let { event ->
        assertThat(event.databaseOpened.path).isEqualTo(database.displayName)
      }
    }
    val entryHooks = hookEntries.filterIsInstance<Hook.EntryHook>()
    assertThat(entryHooks.map { it.originMethod }).containsExactlyElementsIn(entryHookSignatures)
  }

  @Test
  fun test_track_databases_the_same_database_opened_multiple_times() = runBlocking {
    // given
    testEnvironment.sendCommand(createTrackDatabasesCommand())
    val seenDbIds = mutableSetOf<Int>()

    fun checkDbOpenedEvent(event: Event, database: SQLiteDatabase) {
      assertThat(event.hasDatabaseOpened()).isEqualTo(true)
      val isNewId = seenDbIds.add(event.databaseOpened.databaseId)
      assertThat(isNewId).isEqualTo(true)
      assertThat(event.databaseOpened.path).isEqualTo(database.displayName)
    }

    // file based db: first open
    val fileDbPath = "db1"
    val fileDb = testEnvironment.openDatabase(Database(fileDbPath))
    checkDbOpenedEvent(testEnvironment.receiveEvent(), fileDb)

    // file based db: same instance
    testEnvironment.assertNoQueuedEvents()

    // file based db: same path
    testEnvironment.openDatabase(Database(fileDbPath))
    testEnvironment.assertNoQueuedEvents()

    // in-memory database: first open
    val inMemDb = testEnvironment.openDatabase(Database(null))
    checkDbOpenedEvent(testEnvironment.receiveEvent(), inMemDb)

    // in-memory database: same instance
    testEnvironment.assertNoQueuedEvents()

    // in-memory database: new instances (same path = :memory:)
    repeat(3) {
      val db = testEnvironment.openDatabase(Database(null))
      assertThat(db.path).isEqualTo(":memory:")
      checkDbOpenedEvent(testEnvironment.receiveEvent(), db)
    }
  }

  @Test
  fun test_track_databases_keep_db_open_toggle() = runBlocking {
    // without inspecting
    testEnvironment.openDatabase(Database("db1")).let { db ->
      db.close()
      assertClosed(db)
    }

    startTracking()
    // with inspecting (initially keepOpen = false)
    assertNoQueuedEvents()
    listOf("db2", null).forEach { path ->
      testEnvironment.openDatabase(path).let { db ->
        val id = receiveOpenedEventId(db)
        testEnvironment.closeDatabase(db)
        receiveClosedEvent(id, db.displayName)
        assertClosed(db)
      }
    }
    assertNoQueuedEvents()

    // toggle keepOpen = true
    issueKeepDatabasesOpenCommand(true)
    assertNoQueuedEvents()

    // with inspecting (now keepOpen = true)
    val dbs =
      listOf("db3", null).map { path ->
        val db = testEnvironment.openDatabase(path)
        val id = receiveOpenedEventId(db)
        id to db
      }
    dbs.forEach { (_, db) ->
      testEnvironment.closeDatabase(db)
      assertOpen(db) // keep-open has worked
    }
    assertNoQueuedEvents()

    // toggle keepOpen = false
    issueKeepDatabasesOpenCommand(false)
    dbs.forEach { (id, db) ->
      assertClosed(db)
      receiveClosedEvent(id, db.displayName)
    }
    assertNoQueuedEvents()

    // keepOpen = true with some of the same databases as before (they are not revived)
    issueKeepDatabasesOpenCommand(true)
    dbs.forEach { (_, db) -> assertClosed(db) }
    assertNoQueuedEvents()

    // keepOpen = false with a database with more than one reference
    issueKeepDatabasesOpenCommand(false)
    testEnvironment.openDatabase("db4").let { db ->
      db.acquireReference() // extra reference

      testEnvironment.closeDatabase(db)
      assertOpen(db)

      testEnvironment.closeDatabase(db)
      assertClosed(db)
    }
  }

  @Test
  fun test_track_databases_force_open_implies_keep_open() = runBlocking {
    // given
    startTracking(forceOpen = true)

    assertNoQueuedEvents()

    testEnvironment.openDatabase("db2").let { db ->
      receiveOpenedEventId(db)
      testEnvironment.closeDatabase(db)
      assertOpen(db)
    }
  }

  @Test
  fun test_on_closed_notification() = runBlocking {
    // given
    startTracking()

    // simple flow
    assertNoQueuedEvents()
    testEnvironment.openDatabase("db1").let { db ->
      val id = receiveOpenedEventId(db)
      testEnvironment.closeDatabase(db)
      receiveClosedEvent(id, db.displayName)
      assertClosed(db)
      assertNoQueuedEvents()
    }

    // test that doesn't fire on each db.closed()
    assertNoQueuedEvents()
    testEnvironment.openDatabase("db2").let { db ->
      val id = receiveOpenedEventId(db)

      db.acquireReference() // extra reference

      // pass 1
      testEnvironment.closeDatabase(db)
      assertOpen(db)
      assertNoQueuedEvents()

      // pass 2
      testEnvironment.closeDatabase(db)
      assertClosed(db)
      receiveClosedEvent(id, db.displayName)
      assertNoQueuedEvents()
    }
  }

  @Test
  fun test_findInstances_closed() = runBlocking {
    val db1a = testEnvironment.openDatabase(Database("db1"))
    val db2 = testEnvironment.openDatabase(Database("db2"))
    assertOpen(db1a)
    assertOpen(db2)
    db1a.close()
    assertClosed(db1a)

    // given
    testEnvironment.registerAlreadyOpenDatabases(listOf(db1a, db2))
    startTracking()
    val id1 = receiveClosedEventId(db1a)
    val id2 = receiveOpenedEventId(db2)
    assertNoQueuedEvents()

    val db1b = testEnvironment.openDatabase("db1")
    assertThat(receiveOpenedEventId(db1a)).isEqualTo(id1)
    assertNoQueuedEvents()

    testEnvironment.closeDatabase(db1b)
    receiveClosedEvent(id1, db1a.displayName)

    testEnvironment.closeDatabase(db2)
    receiveClosedEvent(id2, db2.displayName)
  }

  @Test
  fun test_findInstances_disk() = runBlocking {
    val db1a = testEnvironment.openDatabase(Database("db1"))
    val db2 = testEnvironment.openDatabase(Database("db2"))

    testEnvironment.registerApplication(db1a, db2)
    startTracking()

    val id1 = receiveClosedEventId(db1a.absolutePath)
    val id2 = receiveClosedEventId(db2.absolutePath)
    assertNoQueuedEvents()

    val db1b = testEnvironment.openDatabase("db1")
    receiveOpenedEvent(id1, db1a.absolutePath)
    assertNoQueuedEvents()

    testEnvironment.openDatabase("db2")
    receiveOpenedEvent(id2, db2.absolutePath)
    assertNoQueuedEvents()

    testEnvironment.closeDatabase(db1b)
    receiveClosedEvent(id1, db1a.absolutePath)
    assertNoQueuedEvents()
  }

  @Test
  fun test_findInstances_disk_forceOpen(): Unit = runBlocking {
    val db = testEnvironment.openDatabase(Database("db1"))
    testEnvironment.registerApplication(db)
    startTracking(forceOpen = true)

    receiveOpenedEventId(db.displayName, isForced = true)
  }

  @Test
  fun test_findInstances_disk_forceOpenThenOpenNative(): Unit = runBlocking {
    val database = Database("db1")
    val db = testEnvironment.openDatabase(database)

    testEnvironment.registerApplication(db)
    startTracking(forceOpen = true)

    // We have to simulate a call to the hooks
    testEnvironment.triggerOnOpenedExit(db)

    receiveOpenedEventId(db.displayName, isForced = true)
    receiveOpenedEventId(db.displayName, isForced = false)
  }

  @Test
  fun test_findInstances_disk_forceOpenThenOpenNativeAndClosed(): Unit = runBlocking {
    val database = Database("db1")
    val db = testEnvironment.openDatabase(database)

    testEnvironment.registerApplication(db)
    startTracking(forceOpen = true)

    // We have to simulate a call to the hooks
    testEnvironment.triggerOnOpenedExit(db)
    db.close()
    testEnvironment.triggerOnAllReferencesReleased(db)

    receiveOpenedEventId(db.displayName, isForced = true)
    receiveOpenedEventId(db.displayName, isForced = false)
    receiveOpenedEventId(db.displayName, isForced = true)
  }

  @Test
  fun test_findInstances_disk_filters_helper_files() = runBlocking {
    val db = testEnvironment.openDatabase(Database("db1"))

    val application =
      object : Application() {
        override fun databaseList(): Array<String> = temporaryFolder.root.list() as Array<String>

        override fun getDatabasePath(name: String) = File(temporaryFolder.root, name)
      }

    // trigger some query to establish connection
    val cursor = db.rawQuery("select * from sqlite_master", emptyArray())
    cursor.count
    cursor.close()

    testEnvironment.registerApplication(application)
    testEnvironment.registerAlreadyOpenDatabases(listOf(db))
    startTracking()

    val id = receiveOpenedEventId(db)
    assertNoQueuedEvents()

    testEnvironment.closeDatabase(db)
    receiveClosedEvent(id, db.absolutePath)
    assertNoQueuedEvents()
  }

  @Test
  fun test_on_closed_and_reopened() = runBlocking {
    // given
    startTracking()

    // simple flow
    val databaseName = "db1"

    assertNoQueuedEvents()
    var id: Int
    testEnvironment.openDatabase(databaseName).let { db ->
      id = receiveOpenedEventId(db)
      testEnvironment.closeDatabase(db)
      receiveClosedEvent(id, db.displayName)
      assertClosed(db)
    }
    testEnvironment.assertNoQueuedEvents()

    testEnvironment.openDatabase(databaseName).let { db ->
      assertThat(receiveOpenedEventId(db)).isEqualTo(id)
      testEnvironment.closeDatabase(db)
      receiveClosedEvent(id, db.displayName)
      assertClosed(db)
    }
    testEnvironment.assertNoQueuedEvents()
  }

  @Test
  fun test_temporary_databases_same_path_different_database() {
    // given
    val db1 = testEnvironment.openDatabase(Database(null))
    val db2 = testEnvironment.openDatabase(Database(null))
    fun queryTableCount(db: SQLiteDatabase): Long =
      db.compileStatement("select count(*) from sqlite_master").simpleQueryForLong()
    assertThat(queryTableCount(db1)).isEqualTo(1) // android_metadata sole table
    assertThat(queryTableCount(db2)).isEqualTo(1) // android_metadata sole table
    assertThat(db1.path).isEqualTo(db2.path)
    assertThat(db1.path).isEqualTo(":memory:")

    // when
    db1.execSQL("create table t1 (c1 int)")

    // then
    assertThat(queryTableCount(db1)).isEqualTo(2)
    assertThat(queryTableCount(db2)).isEqualTo(1)
  }

  @Test
  fun test_three_references_edge_ones_closed() = runBlocking {
    startTracking()

    val db1a = testEnvironment.openDatabase("path1")
    val id1a = receiveOpenedEventId(db1a)

    val db1b = testEnvironment.openDatabase("path1")
    assertNoQueuedEvents()

    val db1c = testEnvironment.openDatabase("path1")
    assertNoQueuedEvents()

    testEnvironment.closeDatabase(db1a)
    assertNoQueuedEvents()

    testEnvironment.closeDatabase(db1c)
    assertNoQueuedEvents()

    testEnvironment.closeDatabase(db1b)
    receiveClosedEvent(id1a, db1a.displayName)
  }

  @Test
  fun test_keep_open_while_user_attempts_to_close() = runBlocking {
    startTracking()
    assertNoQueuedEvents()

    val db = testEnvironment.openDatabase("db")
    val id = receiveOpenedEventId(db)
    assertNoQueuedEvents()

    issueKeepDatabasesOpenCommand(true)
    assertNoQueuedEvents()

    testEnvironment.closeDatabase(db)
    testEnvironment.closeDatabase(db)
    testEnvironment.closeDatabase(db)
    assertNoQueuedEvents()

    issueKeepDatabasesOpenCommand(false)

    receiveClosedEvent(id, db.displayName)
    assertClosed(db)
    assertNoQueuedEvents()
  }

  /**
   * #dbRef -- the number of references as seen by the SQLiteDatabase object #kpoRef=0 -- the number
   * of references acquired by KeepOpen objects #usrRef=1 -- the 'balance' of references the user
   * owns
   */
  @Test
  fun test_keep_open_keeps_count() = runBlocking {
    startTracking()
    assertNoQueuedEvents()

    val db = testEnvironment.openDatabase("db") // #dbRef=1 | #kpoRef=0 | #usrRef=1
    receiveOpenedEventId(db)
    assertThat(db.referenceCount).isEqualTo(1)
    assertNoQueuedEvents()

    issueKeepDatabasesOpenCommand(true) // #dbRef=1 | #kpoRef=0 | #usrRef=1
    assertThat(db.referenceCount).isEqualTo(1)
    assertNoQueuedEvents()

    var count = 0
    testEnvironment.closeDatabase(db)
    count++ // #dbRef=1 | #kpoRef=1 | #usrRef=0
    assertThat(db.referenceCount).isEqualTo(1)
    testEnvironment.closeDatabase(db)
    count++ // #dbRef=1 | #kpoRef=2 | #usrRef=-1
    assertThat(db.referenceCount).isEqualTo(1)
    testEnvironment.closeDatabase(db)
    count++ // #dbRef=1 | #kpoRef=3 | #usrRef=-2
    assertThat(db.referenceCount).isEqualTo(1)
    assertNoQueuedEvents()

    repeat(count) {
      db.acquireReference() // user offsetting the closed calls they made
    } // #dbRef=4 | #kpoRef=3 | #usrRef=1
    assertThat(db.referenceCount).isEqualTo(4)

    issueKeepDatabasesOpenCommand(false) // #dbRef=1 | #kpoRef=0 | #usrRef=1
    repeat(count + 1) {
      testEnvironment.triggerReleaseReference(db)
      assertOpen(db)
    } // #dbRef=1 | #kpoRef=0 | #usrRef=1
    assertThat(db.referenceCount).isEqualTo(1)

    assertOpen(db)
    assertNoQueuedEvents()
  }

  /**
   * #dbRef -- the number of references as seen by the SQLiteDatabase object #kpoRef=0 -- the number
   * of references acquired by KeepOpen objects #usrRef=1 -- the 'balance' of references the user
   * owns
   */
  @Test
  fun test_keep_open_off_on_off() = runBlocking {
    startTracking()
    assertNoQueuedEvents()

    // keep-open = false (default)

    val db1 = testEnvironment.openDatabase("db1")
    val db2 = testEnvironment.openDatabase("db2")

    assertThat(db1.referenceCount).isEqualTo(1) // #dbRef=1 | #kpoRef=0 | #usrRef=1
    val id1 = receiveOpenedEventId(db1)
    val id2 = receiveOpenedEventId(db2)
    assertThat(db1.referenceCount).isEqualTo(1)
    assertThat(db2.referenceCount).isEqualTo(1)
    assertNoQueuedEvents()

    testEnvironment.closeDatabase(db1) // #dbRef=0 | #kpoRef=0 | #usrRef=0
    assertThat(db1.referenceCount).isEqualTo(0)
    assertThat(db2.referenceCount).isEqualTo(1)
    receiveClosedEvent(id1, db1.displayName)
    assertNoQueuedEvents()

    // keep-open = true

    issueKeepDatabasesOpenCommand(true)
    assertNoQueuedEvents()
    assertThat(db2.referenceCount).isEqualTo(1) // #dbRef=1 | #kpoRef=0 | #usrRef=1
    assertNoQueuedEvents()

    testEnvironment.closeDatabase(db2) // #dbRef=1 | #kpoRef=1 | #usrRef=0
    assertThat(db2.referenceCount).isEqualTo(1)
    assertNoQueuedEvents()

    db2.acquireReference() // #dbRef=2 | #kpoRef=1 | #usrRef=1
    assertThat(db2.referenceCount).isEqualTo(2)

    // keep-open = false

    issueKeepDatabasesOpenCommand(false)
    testEnvironment.triggerReleaseReference(db2) // #dbRef=1 | #kpoRef=0 | #usrRef=1
    assertThat(db2.referenceCount).isEqualTo(1)
    assertNoQueuedEvents()

    testEnvironment.closeDatabase(db2) // #dbRef=0 | #kpoRef=0 | #usrRef=0
    assertThat(db2.referenceCount).isEqualTo(0)
    receiveClosedEvent(id2, db2.displayName)
    assertNoQueuedEvents()
  }

  private val SQLiteClosable.referenceCount: Int
    get() = getFieldValue(SQLiteClosable::class.java, "mReferenceCount", this)

  @Suppress("SameParameterValue")
  private fun <T> getFieldValue(clazz: Class<*>, fieldName: String, target: Any?): T {
    val field = clazz.declaredFields.first { it.name == fieldName }
    field.isAccessible = true
    val result = field.get(target)
    @Suppress("UNCHECKED_CAST") return result as T
  }

  private fun assertNoQueuedEvents() {
    testEnvironment.assertNoQueuedEvents()
  }

  private suspend fun startTracking(forceOpen: Boolean = false) {
    testEnvironment.sendCommand(createTrackDatabasesCommand(forceOpen))
  }

  private suspend fun issueKeepDatabasesOpenCommand(setEnabled: Boolean) {
    testEnvironment.sendCommand(createKeepDatabasesOpenCommand(setEnabled)).let { response ->
      assertThat(response.oneOfCase).isEqualTo(Response.OneOfCase.KEEP_DATABASES_OPEN)
      assertThat(response).isEqualTo(createKeepDatabasesOpenResponse())
    }
  }

  private suspend fun receiveOpenedEventId(database: SQLiteDatabase): Int =
    receiveOpenedEventId(database.displayName)

  private suspend fun receiveOpenedEventId(displayName: String, isForced: Boolean = false): Int =
    testEnvironment.receiveEvent().let {
      assertThat(it.oneOfCase).isEqualTo(Event.OneOfCase.DATABASE_OPENED)
      assertThat(it.databaseOpened.path).isEqualTo(displayName)
      assertThat(it.databaseOpened.isForcedConnection).isEqualTo(isForced)
      it.databaseOpened.databaseId
    }

  private suspend fun receiveClosedEventId(displayName: String): Int =
    testEnvironment.receiveEvent().let {
      assertThat(it.oneOfCase).isEqualTo(Event.OneOfCase.DATABASE_CLOSED)
      assertThat(it.databaseClosed.path).isEqualTo(displayName)
      it.databaseClosed.databaseId
    }

  private suspend fun receiveClosedEventId(database: SQLiteDatabase): Int =
    receiveClosedEventId(database.displayName)

  private suspend fun receiveOpenedEvent(id: Int, path: String) =
    testEnvironment.receiveEvent().let {
      assertThat(it.oneOfCase).isEqualTo(Event.OneOfCase.DATABASE_OPENED)
      assertThat(it.databaseOpened.databaseId).isEqualTo(id)
      assertThat(it.databaseOpened.path).isEqualTo(path)
    }

  private suspend fun receiveClosedEvent(id: Int, path: String) =
    testEnvironment.receiveEvent().let {
      assertThat(it.oneOfCase).isEqualTo(Event.OneOfCase.DATABASE_CLOSED)
      assertThat(it.databaseClosed.databaseId).isEqualTo(id)
      assertThat(it.databaseClosed.path).isEqualTo(path)
    }

  private fun assertOpen(db: SQLiteDatabase) {
    assertThat(db.isOpen).isTrue()
  }

  private fun assertClosed(db: SQLiteDatabase) {
    assertThat(db.isOpen).isFalse()
  }
}
