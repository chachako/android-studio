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

package com.android.tools.appinspection.database.testing

import android.app.Application
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import androidx.inspection.ArtTooling
import androidx.inspection.testing.DefaultTestInspectorEnvironment
import androidx.inspection.testing.InspectorTester
import androidx.inspection.testing.TestInspectorExecutors
import androidx.sqlite.inspection.SqliteInspectorProtocol.Command
import androidx.sqlite.inspection.SqliteInspectorProtocol.Command.OneOfCase.TRACK_DATABASES
import androidx.sqlite.inspection.SqliteInspectorProtocol.DatabaseOpenedEvent
import androidx.sqlite.inspection.SqliteInspectorProtocol.Event
import androidx.sqlite.inspection.SqliteInspectorProtocol.Event.OneOfCase.DATABASE_OPENED
import androidx.sqlite.inspection.SqliteInspectorProtocol.QueryResponse
import androidx.sqlite.inspection.SqliteInspectorProtocol.Response
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.truth.Truth.assertThat
import java.io.File
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit.SECONDS
import kotlin.coroutines.CoroutineContext
import kotlin.test.fail
import kotlinx.coroutines.*
import org.junit.rules.ExternalResource
import org.junit.rules.TemporaryFolder
import org.robolectric.RuntimeEnvironment

internal const val SQLITE_INSPECTOR_ID = "androidx.sqlite.inspection"
internal const val OPEN_DATABASE_COMMAND_SIGNATURE_API11: String =
  "openDatabase" +
    "(" +
    "Ljava/lang/String;" +
    "Landroid/database/sqlite/SQLiteDatabase\$CursorFactory;" +
    "I" +
    "Landroid/database/DatabaseErrorHandler;" +
    ")" +
    "Landroid/database/sqlite/SQLiteDatabase;"

internal const val OPEN_DATABASE_COMMAND_SIGNATURE_API27: String =
  "openDatabase" +
    "(" +
    "Ljava/io/File;" +
    "Landroid/database/sqlite/SQLiteDatabase\$OpenParams;" +
    ")" +
    "Landroid/database/sqlite/SQLiteDatabase;"

internal const val CREATE_IN_MEMORY_DATABASE_COMMAND_SIGNATURE_API27 =
  "createInMemory" +
    "(" +
    "Landroid/database/sqlite/SQLiteDatabase\$OpenParams;" +
    ")" +
    "Landroid/database/sqlite/SQLiteDatabase;"

private const val RELEASE_REFERENCE_COMMAND_SIGNATURE = "releaseReference()V"
private const val ALL_REFERENCES_RELEASED_COMMAND_SIGNATURE = "onAllReferencesReleased()V"

class SqliteInspectorTestEnvironment(
  private val ioExecutorOverride: ExecutorService = Executors.newFixedThreadPool(4),
  ioCoroutineContextOverride: CoroutineContext = ioExecutorOverride.asCoroutineDispatcher(),
) : ExternalResource(), AutoCloseable {
  private val artTooling = FakeArtTooling()
  private val job = Job()
  private val inspectorEnvironment =
    DefaultTestInspectorEnvironment(TestInspectorExecutors(job, ioExecutorOverride), artTooling)
  private val inspectorFactory = TestInspectorFactory(ioCoroutineContextOverride)
  private val inspectorTester: InspectorTester = runBlocking {
    InspectorTester(SQLITE_INSPECTOR_ID, inspectorEnvironment, inspectorFactory)
  }
  private val databases = mutableListOf<SQLiteDatabase>()
  private var trackingStarted = false
  private val temporaryFolder: TemporaryFolder = TemporaryFolder()

  init {
    // TODO(b/334351830): Remove when bug is fixed
    job.invokeOnCompletion { ioExecutorOverride.shutdown() }
  }

  override fun before() {
    temporaryFolder.create()
  }

  override fun after() {
    databases.forEach { it.close() }
    inspectorTester.dispose()
    runBlocking { job.cancelAndJoin() }
    assertThat(ioExecutorOverride.awaitTermination(5, SECONDS)).isTrue()
    temporaryFolder.delete()
  }

  override fun close() {
    after()
  }

  @OptIn(ExperimentalCoroutinesApi::class)
  fun assertNoQueuedEvents() {
    assertThat(inspectorTester.channel.isEmpty).isTrue()
  }

  suspend fun sendCommand(command: Command): Response {
    if (command.oneOfCase == TRACK_DATABASES) {
      if (trackingStarted) {
        throw IllegalStateException("Already tracking")
      }
      trackingStarted = true
    }
    inspectorTester.sendCommand(command.toByteArray()).let { responseBytes ->
      assertThat(responseBytes).isNotEmpty()
      return Response.parseFrom(responseBytes)
    }
  }

  suspend fun receiveEvent(): Event {
    inspectorTester.channel.receive().let { responseBytes ->
      assertThat(responseBytes).isNotEmpty()
      return Event.parseFrom(responseBytes)
    }
  }

  fun registerAlreadyOpenDatabases(databases: List<SQLiteDatabase>) {
    artTooling.registerInstancesToFind(databases)
  }

  fun registerApplication(application: Application) {
    artTooling.registerInstancesToFind(listOf(application))
  }

  fun registerApplication(vararg databases: SQLiteDatabase) {
    val application =
      object : Application() {
        override fun databaseList(): Array<String> =
          databases.map { it.absolutePath }.toTypedArray()

        override fun getDatabasePath(name: String?) =
          InstrumentationRegistry.getInstrumentation().context.getDatabasePath(name)
      }

    artTooling.registerInstancesToFind(listOf(application))
  }

  fun consumeRegisteredHooks(): List<Hook> = artTooling.consumeRegisteredHooks()

  suspend fun inspectDatabase(database: SQLiteDatabase) = inspectDatabases(database).first()

  fun getRegisteredHooks(): List<Hook> = artTooling.registeredHooks

  suspend fun inspectDatabases(vararg databases: SQLiteDatabase) =
    inspectDatabases(databases.toList())

  suspend fun inspectDatabases(databases: List<SQLiteDatabase>): List<Int> {
    registerAlreadyOpenDatabases(databases)
    sendCommand(MessageFactory.createTrackDatabasesCommand())
    val ids =
      databases.map {
        val event = receiveEvent()
        assertThat(event.oneOfCase).isEqualTo(DATABASE_OPENED)
        event.databaseOpened.databaseId
      }
    return ids
  }

  fun triggerOnOpenedEntry(path: String) {
    if (trackingStarted) {
      artTooling.triggerOnOpenedEntry(path)
    }
  }

  fun triggerOnOpenedExit(db: SQLiteDatabase) {
    if (trackingStarted) {
      artTooling.triggerOnOpenedExit(db)
    }
  }

  fun triggerReleaseReference(db: SQLiteDatabase) {
    if (trackingStarted) {
      artTooling.triggerReleaseReference(db)
    }
  }

  fun triggerOnAllReferencesReleased(db: SQLiteDatabase) {
    if (trackingStarted) {
      artTooling.triggerOnAllReferencesReleased(db)
    }
  }

  fun openDatabase(path: String?) = openDatabase(Database(path))

  fun openDatabase(database: Database, writeAheadLoggingEnabled: Boolean = false): SQLiteDatabase {
    if (database.name != null) {
      // If database.name is null, this is an inMemory database, and we don't hook entry
      triggerOnOpenedEntry(database.name)
    }
    val db = database.createInstance(temporaryFolder, writeAheadLoggingEnabled)
    triggerOnOpenedExit(db)
    databases.add(db)
    return db
  }

  fun closeDatabase(database: SQLiteDatabase) {
    artTooling.triggerReleaseReference(database)
    database.close()
    if (!database.isOpen) {
      artTooling.triggerOnAllReferencesReleased(database)
    }
  }

  /** Assumes an event with the relevant database will be fired. */
  suspend fun awaitDatabaseOpenedEvent(databasePath: String): DatabaseOpenedEvent {
    while (true) {
      val event = receiveEvent().databaseOpened
      if (event.path == databasePath) {
        return event
      }
    }
  }
}

suspend fun SqliteInspectorTestEnvironment.issueQuery(
  databaseId: Int,
  command: String,
  queryParams: List<String?>? = null,
): QueryResponse {
  val response = sendCommand(MessageFactory.createQueryCommand(databaseId, command, queryParams))
  if (response.hasErrorOccurred()) {
    fail("Unexpected error: $${response.errorOccurred.content.stackTrace}")
  }
  return response.query
}

/**
 * Fake inspector environment with the following behaviour:
 * - [findInstances] returns pre-registered values from [registerInstancesToFind].
 * - [registerEntryHook] and [registerExitHook] record the calls which can later be retrieved in
 *   [consumeRegisteredHooks].
 */
private class FakeArtTooling : ArtTooling {
  private val instancesToFind = mutableListOf<Any>()
  val registeredHooks = mutableListOf<Hook>()

  fun registerInstancesToFind(instances: List<Any>) {
    instancesToFind.addAll(instances)
  }

  fun triggerOnOpenedEntry(path: String) {
    val onOpen =
      registeredHooks.filterIsInstance<Hook.EntryHook>().filter {
        it.originMethod == OPEN_DATABASE_COMMAND_SIGNATURE_API11
      }
    assertThat(onOpen).named("hooks").hasSize(1)
    val hook = onOpen.first().asEntryHook
    hook.onEntry(null, arrayOf<Any>(path).asList())
  }

  fun triggerOnOpenedExit(db: SQLiteDatabase) {
    val onOpen =
      registeredHooks.filterIsInstance<Hook.ExitHook>().filter {
        it.originMethod == OPEN_DATABASE_COMMAND_SIGNATURE_API11
      }
    assertThat(onOpen).named("hooks").hasSize(1)
    @Suppress("UNCHECKED_CAST")
    val hook = onOpen.first().asExitHook as ArtTooling.ExitHook<SQLiteDatabase>
    hook.onExit(db)
  }

  fun triggerReleaseReference(db: SQLiteDatabase) {
    val onOpen = registeredHooks.filter { it.originMethod == RELEASE_REFERENCE_COMMAND_SIGNATURE }
    assertThat(onOpen).named("hooks").hasSize(1)
    val hook = onOpen.first().asEntryHook
    hook.onEntry(db, emptyList())
  }

  fun triggerOnAllReferencesReleased(db: SQLiteDatabase) {
    val onReleasedHooks =
      registeredHooks.filter { it.originMethod == ALL_REFERENCES_RELEASED_COMMAND_SIGNATURE }
    assertThat(onReleasedHooks).named("hooks").hasSize(2)
    val entryHook = (onReleasedHooks.first { it is Hook.EntryHook }.asEntryHook)
    val exitHook = (onReleasedHooks.first { it is Hook.ExitHook }.asExitHook)
    entryHook.onEntry(db, emptyList())
    exitHook.onExit(null)
  }

  /**
   * Returns instances pre-registered in [registerInstancesToFind]. By design crashes in case of the
   * wrong setup - indicating an issue with test code.
   */
  @Suppress("UNCHECKED_CAST")
  // TODO: implement actual findInstances behaviour
  override fun <T : Any?> findInstances(clazz: Class<T>): MutableList<T> =
    instancesToFind.filter { clazz.isInstance(it) }.map { it as T }.toMutableList()

  override fun registerEntryHook(
    originClass: Class<*>,
    originMethod: String,
    entryHook: ArtTooling.EntryHook,
  ) {
    // TODO: implement actual registerEntryHook behaviour
    registeredHooks.add(Hook.EntryHook(originClass, originMethod, entryHook))
  }

  override fun <T : Any?> registerExitHook(
    originClass: Class<*>,
    originMethod: String,
    exitHook: ArtTooling.ExitHook<T>,
  ) {
    // TODO: implement actual registerExitHook behaviour
    registeredHooks.add(Hook.ExitHook(originClass, originMethod, exitHook))
  }

  fun consumeRegisteredHooks(): List<Hook> =
    registeredHooks.toList().also { registeredHooks.clear() }
}

sealed class Hook(val originClass: Class<*>, val originMethod: String) {
  class ExitHook(
    originClass: Class<*>,
    originMethod: String,
    val exitHook: ArtTooling.ExitHook<*>,
  ) : Hook(originClass, originMethod)

  class EntryHook(
    originClass: Class<*>,
    originMethod: String,
    @Suppress("unused") val entryHook: ArtTooling.EntryHook,
  ) : Hook(originClass, originMethod)
}

val Hook.asEntryHook
  get() = (this as Hook.EntryHook).entryHook
val Hook.asExitHook
  get() = (this as Hook.ExitHook).exitHook

private fun Database.createInstance(
  temporaryFolder: TemporaryFolder,
  writeAheadLoggingEnabled: Boolean? = null,
): SQLiteDatabase {
  val path =
    if (name == null) null
    else
      File(temporaryFolder.root, name)
        .also { it.createNewFile() } // can handle an existing file
        .absolutePath

  val context = RuntimeEnvironment.getApplication()
  val openHelper =
    object : SQLiteOpenHelper(context, path, null, 1) {
      override fun onCreate(db: SQLiteDatabase?) = Unit

      override fun onUpgrade(db: SQLiteDatabase?, oldVersion: Int, newVersion: Int) = Unit
    }

  writeAheadLoggingEnabled?.let { openHelper.setWriteAheadLoggingEnabled(it) }
  val db = openHelper.readableDatabase
  tables.forEach { t -> db.addTable(t) }
  return db
}
