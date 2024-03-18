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
import com.android.tools.appinspection.database.DatabaseRegistry.Callback
import com.android.tools.appinspection.database.DatabaseRegistryTest.EventType.CLOSE
import com.android.tools.appinspection.database.DatabaseRegistryTest.EventType.OPEN
import com.google.common.truth.Truth.assertThat
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

  @get:Rule val rule: RuleChain = RuleChain.outerRule(temporaryFolder).around(closeablesRule)

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
    val forcedDb = registry.forcedOpen.first()
    registry.notifyDatabaseOpened(forcedDb)

    val connection = registry.getConnection(events.first().id)

    assertThat(connection).isEqualTo(forcedDb)
  }

  @Test
  fun getConnection_withForcedAndReadOnly_returnsForced() {
    val openHelper = OpenHelper("${temporaryFolder.root}/db")
    val registry = databaseRegistry(events, forceOpen = true)
    openHelper.createAndClose()
    registry.notifyOnDiskDatabase(openHelper.databaseName)
    val forcedDb = registry.forcedOpen.first()
    registry.notifyDatabaseOpened(forcedDb)
    registry.notifyDatabaseOpened(openHelper.getReadOnlyDb())

    val connection = registry.getConnection(events.first().id)

    assertThat(connection).isSameInstanceAs(forcedDb)
  }

  @Test
  fun getConnection_withForcedAndReadWrite_returnsForced() {
    val openHelper = OpenHelper("${temporaryFolder.root}/db")
    val registry = databaseRegistry(events, forceOpen = true)
    openHelper.createAndClose()
    registry.notifyOnDiskDatabase(openHelper.databaseName)
    val forcedDb = registry.forcedOpen.first()
    val readWriteDb = openHelper.getReadWriteDb()
    registry.notifyDatabaseOpened(forcedDb)
    registry.notifyDatabaseOpened(readWriteDb)

    val connection = registry.getConnection(events.first().id)

    assertThat(connection).isEqualTo(readWriteDb)
  }

  @Test
  fun getConnection_alreadyOpen_notForced() {
    val openHelper = OpenHelper("${temporaryFolder.root}/db")
    val registry = databaseRegistry(events, forceOpen = true)
    registry.notifyDatabaseOpened(openHelper.getReadWriteDb())
    registry.notifyOnDiskDatabase(openHelper.databaseName)

    assertThat(registry.forcedOpen).isEmpty()
  }

  @Test
  fun notifyKeepOpenToggle_doNotKeepForcedConnections() {
    val openHelper = OpenHelper("${temporaryFolder.root}/db")
    val registry = databaseRegistry(events, forceOpen = true)
    openHelper.createAndClose()
    registry.notifyOnDiskDatabase(openHelper.databaseName)
    val forcedDb = registry.forcedOpen.first()
    registry.notifyDatabaseOpened(forcedDb)

    registry.notifyKeepOpenToggle(true)

    assertThat(registry.keepOpenReferences).isEmpty()
  }

  private class DbCallback(private val type: EventType, private val events: MutableList<DbEvent>) :
    Callback {

    override fun onPostEvent(databaseId: Int, path: String) {
      events.add(DbEvent(type, databaseId, path))
    }
  }

  private enum class EventType {
    OPEN,
    CLOSE,
  }

  private data class DbEvent(val type: EventType, val id: Int, val path: String)

  private fun databaseRegistry(events: MutableList<DbEvent>, forceOpen: Boolean = false) =
    DatabaseRegistry(DbCallback(OPEN, events), DbCallback(CLOSE, events)).apply {
      if (forceOpen) {
        enableForceOpen()
      }
      closeablesRule.register(AutoCloseable { dispose() })
    }

  private class OpenHelper(path: String) :
    SQLiteOpenHelper(RuntimeEnvironment.getApplication(), path, null, 1) {

    override fun onCreate(db: SQLiteDatabase) {}

    override fun onUpgrade(db: SQLiteDatabase, fromVersion: Int, toVersion: Int) {}

    fun getReadOnlyDb(): SQLiteDatabase {
      val mock = spy(readableDatabase)
      whenever(mock.isReadOnly).thenReturn(true)
      return mock
    }

    fun getReadWriteDb(): SQLiteDatabase {
      return writableDatabase
    }

    fun createAndClose() {
      readableDatabase.close()
    }
  }
}
