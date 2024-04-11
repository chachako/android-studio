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
import androidx.inspection.ArtTooling.ExitHook
import androidx.sqlite.inspection.SqliteInspectorProtocol.Event
import androidx.sqlite.inspection.SqliteInspectorProtocol.Response
import com.android.testutils.CloseablesRule
import com.android.tools.appinspection.database.testing.CREATE_IN_MEMORY_DATABASE_COMMAND_SIGNATURE_API27
import com.android.tools.appinspection.database.testing.Database
import com.android.tools.appinspection.database.testing.Hook
import com.android.tools.appinspection.database.testing.MessageFactory.createKeepDatabasesOpenCommand
import com.android.tools.appinspection.database.testing.MessageFactory.createKeepDatabasesOpenResponse
import com.android.tools.appinspection.database.testing.MessageFactory.createTrackDatabasesCommand
import com.android.tools.appinspection.database.testing.MessageFactory.createTrackDatabasesResponse
import com.android.tools.appinspection.database.testing.OPEN_DATABASE_COMMAND_SIGNATURE_API11
import com.android.tools.appinspection.database.testing.OPEN_DATABASE_COMMAND_SIGNATURE_API27
import com.android.tools.appinspection.database.testing.SqliteInspectorTestEnvironment
import com.android.tools.appinspection.database.testing.absolutePath
import com.android.tools.appinspection.database.testing.asExitHook
import com.android.tools.appinspection.database.testing.createInstance
import com.android.tools.appinspection.database.testing.displayName
import com.android.tools.appinspection.database.testing.triggerOnAllReferencesReleased
import com.android.tools.appinspection.database.testing.triggerOnOpenedExit
import com.android.tools.appinspection.database.testing.triggerReleaseReference
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
  private val closeablesRule = CloseablesRule()

  @get:Rule
  val rule: RuleChain =
    RuleChain.outerRule(testEnvironment).around(temporaryFolder).around(closeablesRule)

  @Test
  fun test_track_databases() = runBlocking {
    val alreadyOpenDatabases =
      listOf(
        Database("db1").createInstance(closeablesRule, temporaryFolder),
        Database("db2").createInstance(closeablesRule, temporaryFolder),
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
      testEnvironment.consumeRegisteredHooks().filter {
        possibleSignatures.contains(it.originMethod)
      }
    val exitHooks = hookEntries.filterIsInstance<Hook.ExitHook>()
    assertThat(exitHooks.map { it.originMethod }).containsExactlyElementsIn(exitHookSignatures)
    exitHooks.forEachIndexed { ix, entry ->
      // expect one exit hook tracking database open events
      assertThat(entry).isInstanceOf(Hook.ExitHook::class.java)
      assertThat(entry.originClass.name).isEqualTo(SQLiteDatabase::class.java.name)

      // verify that executing the registered hook will result in tracking events
      testEnvironment.assertNoQueuedEvents()
      @Suppress("UNCHECKED_CAST") val exitHook = entry.asExitHook as ExitHook<SQLiteDatabase>
      val database = Database("db3_$ix").createInstance(closeablesRule, temporaryFolder)
      assertThat(exitHook.onExit(database)).isSameInstanceAs(database)
      testEnvironment.receiveEvent().let { event ->
        assertThat(event.databaseOpened.path).isEqualTo(database.displayName)
      }
    }
    val entryHooks = hookEntries.filterIsInstance<Hook.EntryHook>()
    assertThat(entryHooks.map { it.originMethod }).containsExactlyElementsIn(entryHookSignatures)

    assertThat(testEnvironment.consumeRegisteredHooks()).isEmpty()
  }

  @Test
  fun test_track_databases_the_same_database_opened_multiple_times() = runBlocking {
    // given
    testEnvironment.sendCommand(createTrackDatabasesCommand())
    val onOpenHook =
      testEnvironment.consumeRegisteredHooks().first {
        it is Hook.ExitHook && it.originMethod == OPEN_DATABASE_COMMAND_SIGNATURE_API11
      }
    @Suppress("UNCHECKED_CAST")
    val onOpen = (onOpenHook.asExitHook as ExitHook<SQLiteDatabase>)::onExit

    val seenDbIds = mutableSetOf<Int>()

    fun checkDbOpenedEvent(event: Event, database: SQLiteDatabase) {
      assertThat(event.hasDatabaseOpened()).isEqualTo(true)
      val isNewId = seenDbIds.add(event.databaseOpened.databaseId)
      assertThat(isNewId).isEqualTo(true)
      assertThat(event.databaseOpened.path).isEqualTo(database.displayName)
    }

    // file based db: first open
    val fileDbPath = "db1"
    val fileDb = Database(fileDbPath).createInstance(closeablesRule, temporaryFolder)
    onOpen(fileDb)
    checkDbOpenedEvent(testEnvironment.receiveEvent(), fileDb)

    // file based db: same instance
    onOpen(fileDb)
    testEnvironment.assertNoQueuedEvents()

    // file based db: same path
    onOpen(Database(fileDbPath).createInstance(closeablesRule, temporaryFolder))
    testEnvironment.assertNoQueuedEvents()

    // in-memory database: first open
    val inMemDb = Database(null).createInstance(closeablesRule, temporaryFolder)
    onOpen(inMemDb)
    checkDbOpenedEvent(testEnvironment.receiveEvent(), inMemDb)

    // in-memory database: same instance
    onOpen(inMemDb)
    testEnvironment.assertNoQueuedEvents()

    // in-memory database: new instances (same path = :memory:)
    repeat(3) {
      val db = Database(null).createInstance(closeablesRule, temporaryFolder)
      assertThat(db.path).isEqualTo(":memory:")
      onOpen(db)
      checkDbOpenedEvent(testEnvironment.receiveEvent(), db)
    }
  }

  @Test
  fun test_track_databases_keep_db_open_toggle() = runBlocking {
    // given
    val hooks = startTracking()

    // without inspecting
    Database("db1").createInstance(closeablesRule, temporaryFolder).let { db ->
      db.close()
      assertClosed(db)
    }

    // with inspecting (initially keepOpen = false)
    assertNoQueuedEvents()
    listOf("db2", null).forEach { path ->
      openDatabase(path, hooks).let { db ->
        val id = receiveOpenedEventId(db)
        closeDatabase(db, hooks)
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
        val db = openDatabase(path, hooks)
        val id = receiveOpenedEventId(db)
        id to db
      }
    dbs.forEach { (_, db) ->
      closeDatabase(db, hooks)
      assertOpen(db) // keep-open has worked
    }
    assertNoQueuedEvents()

    // toggle keepOpen = false
    issueKeepDatabasesOpenCommand(false)
    assertNoQueuedEvents()
    dbs.forEach { (id, db) ->
      assertClosed(db)
      hooks.triggerOnAllReferencesReleased(db)
      receiveClosedEvent(id, db.displayName)
    }
    assertNoQueuedEvents()

    // keepOpen = true with some of the same databases as before (they are not revived)
    issueKeepDatabasesOpenCommand(true)
    dbs.forEach { (_, db) -> assertClosed(db) }
    assertNoQueuedEvents()

    // keepOpen = false with a database with more than one reference
    issueKeepDatabasesOpenCommand(false)
    openDatabase("db4", hooks).let { db ->
      db.acquireReference() // extra reference

      closeDatabase(db, hooks)
      assertOpen(db)

      closeDatabase(db, hooks)
      assertClosed(db)
    }
  }

  @Test
  fun test_track_databases_force_open_implies_keep_open() = runBlocking {
    // given
    val hooks = startTracking(forceOpen = true)

    assertNoQueuedEvents()

    openDatabase("db2", hooks).let { db ->
      receiveOpenedEventId(db)
      closeDatabase(db, hooks)
      assertOpen(db)
    }
  }

  @Test
  fun test_on_closed_notification() = runBlocking {
    // given
    val hooks = startTracking()

    // simple flow
    assertNoQueuedEvents()
    openDatabase("db1", hooks).let { db ->
      val id = receiveOpenedEventId(db)
      closeDatabase(db, hooks)
      receiveClosedEvent(id, db.displayName)
      assertClosed(db)
      assertNoQueuedEvents()
    }

    // test that doesn't fire on each db.closed()
    assertNoQueuedEvents()
    openDatabase("db2", hooks).let { db ->
      val id = receiveOpenedEventId(db)

      db.acquireReference() // extra reference

      // pass 1
      closeDatabase(db, hooks)
      assertOpen(db)
      assertNoQueuedEvents()

      // pass 2
      closeDatabase(db, hooks)
      assertClosed(db)
      receiveClosedEvent(id, db.displayName)
      assertNoQueuedEvents()
    }
  }

  @Test
  fun test_findInstances_closed() = runBlocking {
    val db1a = Database("db1").createInstance(closeablesRule, temporaryFolder)
    val db2 = Database("db2").createInstance(closeablesRule, temporaryFolder)
    assertOpen(db1a)
    assertOpen(db2)
    db1a.close()
    assertClosed(db1a)

    // given
    testEnvironment.registerAlreadyOpenDatabases(listOf(db1a, db2))
    val hooks = startTracking()
    val id1 = receiveClosedEventId(db1a)
    val id2 = receiveOpenedEventId(db2)
    assertNoQueuedEvents()

    val db1b = openDatabase("db1", hooks)
    assertThat(receiveOpenedEventId(db1a)).isEqualTo(id1)
    assertNoQueuedEvents()

    closeDatabase(db1b, hooks)
    receiveClosedEvent(id1, db1a.displayName)

    closeDatabase(db2, hooks)
    receiveClosedEvent(id2, db2.displayName)
  }

  @Test
  fun test_findInstances_disk() = runBlocking {
    val db1a = Database("db1").createInstance(closeablesRule, temporaryFolder)
    val db2 = Database("db2").createInstance(closeablesRule, temporaryFolder)

    testEnvironment.registerApplication(db1a, db2)
    val hooks = startTracking()

    val id1 = receiveClosedEventId(db1a.absolutePath)
    val id2 = receiveClosedEventId(db2.absolutePath)
    assertNoQueuedEvents()

    val db1b = openDatabase("db1", hooks)
    receiveOpenedEvent(id1, db1a.absolutePath)
    assertNoQueuedEvents()

    openDatabase("db2", hooks)
    receiveOpenedEvent(id2, db2.absolutePath)
    assertNoQueuedEvents()

    closeDatabase(db1b, hooks)
    receiveClosedEvent(id1, db1a.absolutePath)
    assertNoQueuedEvents()
  }

  @Test
  fun test_findInstances_disk_forceOpen(): Unit = runBlocking {
    val db = Database("db1").createInstance(closeablesRule, temporaryFolder)

    testEnvironment.registerApplication(db)
    val hooks = startTracking(forceOpen = true)

    // We have to simulate a call to the hooks
    val forcedInstance = testEnvironment.getDatabaseRegistry().forcedOpen.first()
    hooks.triggerOnOpenedExit(forcedInstance)

    // We can't assert that `isForced = true` because the hooks are called too late
    receiveOpenedEventId(db.displayName)
  }

  @Test
  fun test_findInstances_disk_forceOpenThenOpenNative(): Unit = runBlocking {
    val database = Database("db1")
    val db = database.createInstance(closeablesRule, temporaryFolder)

    testEnvironment.registerApplication(db)
    val hooks = startTracking(forceOpen = true)

    // We have to simulate a call to the hooks
    val forcedInstance = testEnvironment.getDatabaseRegistry().forcedOpen.first()
    hooks.triggerOnOpenedExit(forcedInstance)
    hooks.triggerOnOpenedExit(db)

    // We can't assert that the first `isForced = true` because the hooks are called too late
    receiveOpenedEventId(db.displayName)
    receiveOpenedEventId(db.displayName, isForced = false)
  }

  @Test
  fun test_findInstances_disk_forceOpenThenOpenNativeAndClosed(): Unit = runBlocking {
    val database = Database("db1")
    val db = database.createInstance(closeablesRule, temporaryFolder)

    testEnvironment.registerApplication(db)
    val hooks = startTracking(forceOpen = true)

    // We have to simulate a call to the hooks
    val forcedInstance = testEnvironment.getDatabaseRegistry().forcedOpen.first()
    hooks.triggerOnOpenedExit(forcedInstance)
    hooks.triggerOnOpenedExit(db)
    db.close()
    hooks.triggerOnAllReferencesReleased(db)

    // We can't assert that the first `isForced = true` because the hooks are called too late
    receiveOpenedEventId(db.displayName)
    receiveOpenedEventId(db.displayName, isForced = false)
    receiveOpenedEventId(db.displayName, isForced = true)
  }

  @Test
  fun test_findInstances_disk_filters_helper_files() = runBlocking {
    val db = Database("db1").createInstance(closeablesRule, temporaryFolder, false)

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
    val hooks = startTracking()

    val id = receiveOpenedEventId(db)
    assertNoQueuedEvents()

    closeDatabase(db, hooks)
    receiveClosedEvent(id, db.absolutePath)
    assertNoQueuedEvents()
  }

  @Test
  fun test_on_closed_and_reopened() = runBlocking {
    // given
    val hooks = startTracking()

    // simple flow
    val databaseName = "db1"

    assertNoQueuedEvents()
    var id: Int
    openDatabase(databaseName, hooks).let { db ->
      id = receiveOpenedEventId(db)
      closeDatabase(db, hooks)
      receiveClosedEvent(id, db.displayName)
      assertClosed(db)
    }
    testEnvironment.assertNoQueuedEvents()

    openDatabase(databaseName, hooks).let { db ->
      assertThat(receiveOpenedEventId(db)).isEqualTo(id)
      closeDatabase(db, hooks)
      receiveClosedEvent(id, db.displayName)
      assertClosed(db)
    }
    testEnvironment.assertNoQueuedEvents()
  }

  @Test
  fun test_temporary_databases_same_path_different_database() {
    // given
    val db1 = Database(null).createInstance(closeablesRule, temporaryFolder)
    val db2 = Database(null).createInstance(closeablesRule, temporaryFolder)
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
    val hooks = startTracking()

    val db1a = openDatabase("path1", hooks)
    val id1a = receiveOpenedEventId(db1a)

    val db1b = openDatabase("path1", hooks)
    assertNoQueuedEvents()

    val db1c = openDatabase("path1", hooks)
    assertNoQueuedEvents()

    closeDatabase(db1a, hooks)
    assertNoQueuedEvents()

    closeDatabase(db1c, hooks)
    assertNoQueuedEvents()

    closeDatabase(db1b, hooks)
    receiveClosedEvent(id1a, db1a.displayName)
  }

  @Test
  fun test_keep_open_while_user_attempts_to_close() = runBlocking {
    val hooks = startTracking()
    assertNoQueuedEvents()

    val db = openDatabase("db", hooks)
    val id = receiveOpenedEventId(db)
    assertNoQueuedEvents()

    issueKeepDatabasesOpenCommand(true)
    assertNoQueuedEvents()

    var count = 0
    closeDatabase(db, hooks)
    count++
    closeDatabase(db, hooks)
    count++
    closeDatabase(db, hooks)
    count++
    assertNoQueuedEvents()

    issueKeepDatabasesOpenCommand(false)
    repeat(count) {
      hooks.triggerReleaseReference(db)
      if (!db.isOpen) {
        hooks.triggerOnAllReferencesReleased(db)
      }
    }

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
    val hooks = startTracking()
    assertNoQueuedEvents()

    val db = openDatabase("db", hooks) // #dbRef=1 | #kpoRef=0 | #usrRef=1
    receiveOpenedEventId(db)
    assertThat(db.referenceCount).isEqualTo(1)
    assertNoQueuedEvents()

    issueKeepDatabasesOpenCommand(true) // #dbRef=1 | #kpoRef=0 | #usrRef=1
    assertThat(db.referenceCount).isEqualTo(1)
    assertNoQueuedEvents()

    var count = 0
    closeDatabase(db, hooks)
    count++ // #dbRef=1 | #kpoRef=1 | #usrRef=0
    assertThat(db.referenceCount).isEqualTo(1)
    closeDatabase(db, hooks)
    count++ // #dbRef=1 | #kpoRef=2 | #usrRef=-1
    assertThat(db.referenceCount).isEqualTo(1)
    closeDatabase(db, hooks)
    count++ // #dbRef=1 | #kpoRef=3 | #usrRef=-2
    assertThat(db.referenceCount).isEqualTo(1)
    assertNoQueuedEvents()

    repeat(count) {
      db.acquireReference() // user offsetting the closed calls they made
    } // #dbRef=4 | #kpoRef=3 | #usrRef=1
    assertThat(db.referenceCount).isEqualTo(4)

    issueKeepDatabasesOpenCommand(false) // #dbRef=1 | #kpoRef=0 | #usrRef=1
    repeat(count + 1) {
      hooks.triggerReleaseReference(db)
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
    val hooks = startTracking()
    assertNoQueuedEvents()

    // keep-open = false (default)

    val db1 = openDatabase("db1", hooks)
    val db2 = openDatabase("db2", hooks)

    assertThat(db1.referenceCount).isEqualTo(1) // #dbRef=1 | #kpoRef=0 | #usrRef=1
    val id1 = receiveOpenedEventId(db1)
    val id2 = receiveOpenedEventId(db2)
    assertThat(db1.referenceCount).isEqualTo(1)
    assertThat(db2.referenceCount).isEqualTo(1)
    assertNoQueuedEvents()

    closeDatabase(db1, hooks) // #dbRef=0 | #kpoRef=0 | #usrRef=0
    assertThat(db1.referenceCount).isEqualTo(0)
    assertThat(db2.referenceCount).isEqualTo(1)
    receiveClosedEvent(id1, db1.displayName)
    assertNoQueuedEvents()

    // keep-open = true

    issueKeepDatabasesOpenCommand(true)
    assertNoQueuedEvents()
    assertThat(db2.referenceCount).isEqualTo(1) // #dbRef=1 | #kpoRef=0 | #usrRef=1
    assertNoQueuedEvents()

    closeDatabase(db2, hooks) // #dbRef=1 | #kpoRef=1 | #usrRef=0
    assertThat(db2.referenceCount).isEqualTo(1)
    assertNoQueuedEvents()

    db2.acquireReference() // #dbRef=2 | #kpoRef=1 | #usrRef=1
    assertThat(db2.referenceCount).isEqualTo(2)

    // keep-open = false

    issueKeepDatabasesOpenCommand(false)
    hooks.triggerReleaseReference(db2) // #dbRef=1 | #kpoRef=0 | #usrRef=1
    assertThat(db2.referenceCount).isEqualTo(1)
    assertNoQueuedEvents()

    closeDatabase(db2, hooks) // #dbRef=0 | #kpoRef=0 | #usrRef=0
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

  private suspend fun startTracking(forceOpen: Boolean = false): List<Hook> {
    testEnvironment.sendCommand(createTrackDatabasesCommand(forceOpen))
    return testEnvironment.consumeRegisteredHooks()
  }

  private fun openDatabase(path: String?, hooks: List<Hook>): SQLiteDatabase =
    Database(path).createInstance(closeablesRule, temporaryFolder).also {
      hooks.triggerOnOpenedExit(it)
    }

  private fun closeDatabase(database: SQLiteDatabase, hooks: List<Hook>) {
    hooks.triggerReleaseReference(database)
    database.close()
    if (!database.isOpen) {
      hooks.triggerOnAllReferencesReleased(database)
    }
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
