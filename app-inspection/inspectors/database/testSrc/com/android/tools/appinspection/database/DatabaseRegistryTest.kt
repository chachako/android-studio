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
import android.database.sqlite.SQLiteOpenHelper
import android.os.Build
import com.android.testutils.CloseablesRule
import com.android.tools.appinspection.common.testing.LogPrinterRule
import com.android.tools.appinspection.database.DatabaseRegistry.OnDatabaseClosedCallback
import com.android.tools.appinspection.database.DatabaseRegistry.OnDatabaseOpenedCallback
import com.android.tools.appinspection.database.DatabaseRegistryTest.DbEvent.DbClosedEvent
import com.android.tools.appinspection.database.DatabaseRegistryTest.DbEvent.DbOpenedEvent
import com.google.common.truth.Truth.assertThat
import kotlin.test.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.mockito.Mockito.spy
import org.mockito.Mockito.`when` as whenever
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.SQLiteMode
import org.robolectric.junit.rules.CloseGuardRule

/** Tests for [DatabaseRegistry] */
@RunWith(RobolectricTestRunner::class)
@Config(
  manifest = Config.NONE,
  minSdk = Build.VERSION_CODES.O,
  maxSdk = Build.VERSION_CODES.UPSIDE_DOWN_CAKE,
)
@SQLiteMode(SQLiteMode.Mode.NATIVE)
class DatabaseRegistryTest {
  private val temporaryFolder = TemporaryFolder()
  private val closeablesRule = CloseablesRule()

  @get:Rule
  val rule: RuleChain =
    RuleChain.outerRule(CloseGuardRule())
      .around(closeablesRule)
      .around(temporaryFolder)
      .around(LogPrinterRule())

  private val events = mutableListOf<DbEvent>()

  @Test
  fun getConnection_withReadOnly_returnsReadOnly() {
    val openHelper = OpenHelper("${temporaryFolder.root}/db")
    val registry = databaseRegistry(events)
    val readOnlyDb = openHelper.getReadOnlyDb()
    registry.notifyDatabaseOpened(readOnlyDb)

    val connection = registry.getConnection(events.first().id)

    assertThat(connection).isEqualTo(readOnlyDb)
  }

  @Test
  fun getConnection_withReadWrite_returnsReadWrite() {
    val openHelper = OpenHelper("${temporaryFolder.root}/db")
    val registry = databaseRegistry(events)
    registry.notifyDatabaseOpened(openHelper.getReadOnlyDb())
    val readWriteDb = openHelper.getReadWriteDb()
    registry.notifyDatabaseOpened(readWriteDb)
    registry.notifyDatabaseOpened(openHelper.getReadOnlyDb())

    val connection = registry.getConnection(events.first().id)

    assertThat(connection).isEqualTo(readWriteDb)
  }

  @Test
  fun getConnection_withForced_returnsForced() {
    val openHelper = OpenHelper("${temporaryFolder.root}/db")
    val registry = databaseRegistry(events, forceOpen = true)
    openHelper.createAndClose()
    registry.notifyOnDiskDatabase(openHelper.databaseName)

    val connection = registry.getConnection(events.first().id) ?: fail("No database found")

    assertThat(registry.isForcedConnection(connection)).isTrue()
  }

  @Test
  fun getConnection_withForcedAndReadOnly_returnsForced() {
    val openHelper = OpenHelper("${temporaryFolder.root}/db")
    val registry = databaseRegistry(events, forceOpen = true)
    openHelper.createAndClose()
    registry.notifyOnDiskDatabase(openHelper.databaseName)
    registry.notifyDatabaseOpened(openHelper.getReadOnlyDb())

    val connection = registry.getConnection(events.first().id) ?: fail("No database found")

    assertThat(registry.isForcedConnection(connection)).isTrue()
  }

  @Test
  fun getConnection_withForcedAndReadWrite_returnsForced() {
    val openHelper = OpenHelper("${temporaryFolder.root}/db")
    val registry = databaseRegistry(events, forceOpen = true)
    openHelper.createAndClose()
    registry.notifyOnDiskDatabase(openHelper.databaseName)
    val readWriteDb = openHelper.getReadWriteDb()
    registry.notifyDatabaseOpened(readWriteDb)

    val connection = registry.getConnection(events.first().id) ?: fail("No database found")

    assertThat(connection).isEqualTo(readWriteDb)
  }

  @Test
  fun getConnection_alreadyOpen_notForced() {
    val path = "${temporaryFolder.root}/db"
    val openHelper = OpenHelper(path)
    val registry = databaseRegistry(events, forceOpen = true)
    registry.notifyDatabaseOpened(openHelper.getReadWriteDb())
    registry.notifyOnDiskDatabase(openHelper.databaseName)

    val id = registry.getIdForPath(path) ?: fail("No database found")
    val database = registry.getConnection(id) ?: fail("No database found")
    assertThat(registry.isForcedConnection(database)).isFalse()
  }

  @Test
  fun notifyKeepOpenToggle_doNotKeepForcedConnections() {
    val openHelper = OpenHelper("${temporaryFolder.root}/db")
    val registry = databaseRegistry(events, forceOpen = true)
    openHelper.createAndClose()
    registry.notifyOnDiskDatabase(openHelper.databaseName)

    registry.notifyKeepOpenToggle(true)

    assertThat(registry.keepOpenReferences).isEmpty()
  }

  @Test
  fun notifyKeepOpenToggle__replaceReadonlyWithWriteable() {
    val openHelper = OpenHelper("${temporaryFolder.root}/db")
    val readOnlyDb = openHelper.getReadOnlyDb(autoClose = false)
    val readWriteDb = openHelper.getReadWriteDb(autoClose = false)
    val registry = databaseRegistry(events)
    registry.notifyKeepOpenToggle(true)

    // Open and close a read-only database
    registry.notifyDatabaseOpenAndClose(readOnlyDb)
    val id = events.first().id

    assertThat(readOnlyDb.isOpen).isTrue()
    assertThat(registry.getConnection(id)).isEqualTo(readOnlyDb)
    assertThat(registry.getDatabases(id)).containsExactly(readOnlyDb)

    // Open and close a read-write database
    registry.notifyDatabaseOpenAndClose(readWriteDb)

    assertThat(readOnlyDb.isOpen).isFalse()
    //    // simulate hook called when readWriteDb was closed
    //    registry.notifyAllDatabaseReferencesReleased(readOnlyDb)
    assertThat(readWriteDb.isOpen).isTrue()
    assertThat(registry.getConnection(id)).isEqualTo(readWriteDb)
    assertThat(registry.getDatabases(id)).containsExactly(readWriteDb)
  }

  @Test
  fun notifyDatabaseOpened_sendsEventsWhenDatabaseChanges() {
    val path = "${temporaryFolder.root}/db"
    val openHelper1 = OpenHelper(path)
    val openHelper2 = OpenHelper(path)
    val openHelper3 = OpenHelper(path)
    val readOnlyDb = openHelper1.getReadOnlyDb(autoClose = false)
    val readWriteDb1 = openHelper2.getReadWriteDb(autoClose = false)
    val readWriteDb2 = openHelper3.getReadWriteDb(autoClose = false)
    val registry = databaseRegistry(events)

    // Transition from no db to a read-only db
    registry.notifyDatabaseOpened(readOnlyDb)
    assertThat(events).containsExactly(DbOpenedEvent(1, path, isReadOnly = true))
    events.clear()

    // Transition from read-only db to writable db
    registry.notifyDatabaseOpened(readWriteDb1)
    assertThat(events).containsExactly(DbOpenedEvent(1, path, isReadOnly = false))
    events.clear()

    // Opening another writeable db does not trigger an event
    registry.notifyDatabaseOpened(readWriteDb2)
    assertThat(events).isEmpty()

    // Closing the first writeable db does not trigger an event because we still have another one
    readWriteDb1.close()
    registry.notifyAllDatabaseReferencesReleased(readWriteDb1)
    assertThat(events).isEmpty()

    // Closing the second writeable db does results in a transition to read-only
    readWriteDb2.close()
    registry.notifyAllDatabaseReferencesReleased(readWriteDb2)
    assertThat(events).containsExactly(DbOpenedEvent(1, path, isReadOnly = true))
    events.clear()

    // Closing the read-only db triggers a `close` event.
    readOnlyDb.close()
    registry.notifyAllDatabaseReferencesReleased(readOnlyDb)
    assertThat(events).containsExactly(DbClosedEvent(1, path))
  }

  private class DbOpenedCallback(private val events: MutableList<DbEvent>) :
    OnDatabaseOpenedCallback {

    override fun onDatabaseOpened(
      databaseId: Int,
      path: String,
      isForced: Boolean,
      isReadOnly: Boolean,
    ) {
      events.add(DbOpenedEvent(databaseId, path, isReadOnly))
    }
  }

  private class DbClosedCallback(private val events: MutableList<DbEvent>) :
    OnDatabaseClosedCallback {

    override fun onDatabaseClosed(databaseId: Int, path: String) {
      events.add(DbClosedEvent(databaseId, path))
    }
  }

  private sealed class DbEvent(open val id: Int, open val path: String) {
    data class DbOpenedEvent(
      override val id: Int,
      override val path: String,
      val isReadOnly: Boolean,
    ) : DbEvent(id, path)

    data class DbClosedEvent(override val id: Int, override val path: String) : DbEvent(id, path)
  }

  private fun databaseRegistry(events: MutableList<DbEvent>, forceOpen: Boolean = false) =
    DatabaseRegistry(DbOpenedCallback(events), DbClosedCallback(events), testMode = true).apply {
      if (forceOpen) {
        enableForceOpen()
      }
      closeablesRule.register(AutoCloseable { dispose() })
    }

  private inner class OpenHelper(path: String) :
    SQLiteOpenHelper(RuntimeEnvironment.getApplication(), path, null, 1) {

    override fun onCreate(db: SQLiteDatabase) {}

    override fun onUpgrade(db: SQLiteDatabase, fromVersion: Int, toVersion: Int) {}

    fun getReadOnlyDb(autoClose: Boolean = true): SQLiteDatabase {
      val db = readableDatabase
      if (autoClose) {
        closeablesRule.register(db)
      }
      val mock = spy(db)
      whenever(mock.isReadOnly).thenReturn(true)
      return mock
    }

    fun getReadWriteDb(autoClose: Boolean = true): SQLiteDatabase {
      val db = writableDatabase
      if (autoClose) {
        closeablesRule.register(db)
      }
      return db
    }

    fun createAndClose() {
      readableDatabase.close()
    }
  }
}

private fun DatabaseRegistry.notifyDatabaseOpenAndClose(db: SQLiteDatabase) {
  notifyDatabaseOpened(db)
  notifyReleaseReference(db)
  // call close() only after calling notifyReleaseReference() so the registry can secure a reference
  db.close()
}
