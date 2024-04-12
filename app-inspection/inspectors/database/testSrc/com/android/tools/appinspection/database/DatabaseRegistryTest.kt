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
import com.android.tools.appinspection.database.DatabaseRegistry.OnDatabaseClosedCallback
import com.android.tools.appinspection.database.DatabaseRegistry.OnDatabaseOpenedCallback
import com.android.tools.appinspection.database.DatabaseRegistryTest.EventType.CLOSE
import com.android.tools.appinspection.database.DatabaseRegistryTest.EventType.OPEN
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
    RuleChain.outerRule(CloseGuardRule()).around(closeablesRule).around(temporaryFolder)

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

  private class DbOpenedCallback(private val events: MutableList<DbEvent>) :
    OnDatabaseOpenedCallback {

    override fun onDatabaseOpened(databaseId: Int, path: String, isForced: Boolean) {
      events.add(DbEvent(OPEN, databaseId, path))
    }
  }

  private class DbClosedCallback(private val events: MutableList<DbEvent>) :
    OnDatabaseClosedCallback {

    override fun onDatabaseClosed(databaseId: Int, path: String) {
      events.add(DbEvent(CLOSE, databaseId, path))
    }
  }

  private enum class EventType {
    OPEN,
    CLOSE,
  }

  private data class DbEvent(val type: EventType, val id: Int, val path: String)

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

    fun getReadOnlyDb(): SQLiteDatabase {
      val mock = spy(closeablesRule.register(readableDatabase))
      whenever(mock.isReadOnly).thenReturn(true)
      return mock
    }

    fun getReadWriteDb(): SQLiteDatabase {
      return closeablesRule.register(writableDatabase)
    }

    fun createAndClose() {
      readableDatabase.close()
    }
  }
}
