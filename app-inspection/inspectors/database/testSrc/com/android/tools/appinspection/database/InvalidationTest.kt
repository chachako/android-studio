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

import android.database.AbstractCursor
import android.database.Cursor
import android.database.CursorWrapper
import android.database.sqlite.SQLiteCursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteStatement
import android.os.Build
import androidx.inspection.ArtTooling
import androidx.sqlite.inspection.SqliteInspectorProtocol
import androidx.sqlite.inspection.SqliteInspectorProtocol.Event.OneOfCase.DATABASE_POSSIBLY_CHANGED
import com.android.testutils.CloseablesRule
import com.android.tools.appinspection.common.testing.LogPrinterRule
import com.android.tools.appinspection.database.testing.Column
import com.android.tools.appinspection.database.testing.Database
import com.android.tools.appinspection.database.testing.Hook
import com.android.tools.appinspection.database.testing.MessageFactory
import com.android.tools.appinspection.database.testing.SqliteInspectorTestEnvironment
import com.android.tools.appinspection.database.testing.Table
import com.android.tools.appinspection.database.testing.asEntryHook
import com.android.tools.appinspection.database.testing.asExitHook
import com.android.tools.appinspection.database.testing.createInstance
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.runBlocking
import org.junit.Ignore
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
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
class InvalidationTest {
  private val testEnvironment = SqliteInspectorTestEnvironment()
  private val temporaryFolder = TemporaryFolder()
  private val closeablesRule = CloseablesRule()

  @get:Rule
  val rule: RuleChain =
    RuleChain.outerRule(CloseGuardRule())
      .around(closeablesRule)
      .around(testEnvironment)
      .around(temporaryFolder)
      .around(LogPrinterRule())

  private val shadowLooper = shadowOf(testEnvironment.getLooper())

  @Test
  fun test_execute_hook_methods() =
    test_simple_hook_methods("execute()V", SQLiteStatement::class.java)

  @Test
  fun test_executeInsert_hook_methods() =
    test_simple_hook_methods("executeInsert()J", SQLiteStatement::class.java)

  @Test
  fun test_executeUpdateDelete_hook_methods() =
    test_simple_hook_methods("executeUpdateDelete()I", SQLiteStatement::class.java)

  @Test
  fun test_end_transaction_hook_method() =
    test_simple_hook_methods("endTransaction()V", SQLiteDatabase::class.java)

  private fun test_simple_hook_methods(method: String, clazz: Class<*>) = runBlocking {
    // Starting to track databases makes the inspector register hooks
    testEnvironment.sendCommand(MessageFactory.createTrackDatabasesCommand())

    // Verification of hooks registration and triggering the DatabasePossiblyChangedEvent
    testEnvironment.consumeRegisteredHooks().let { hooks ->
      val hook = hooks.filter { hook -> hook.originMethod == method && hook.originClass == clazz }
      assertThat(hook).hasSize(1)

      testEnvironment.assertNoQueuedEvents()
      hook.first().asExitHook.onExit(null)
      shadowLooper.runToNextTask()
      testEnvironment.receiveEvent().let { event ->
        assertThat(event.oneOfCase).isEqualTo(DATABASE_POSSIBLY_CHANGED)
        assertThat(event.databasePossiblyChanged)
          .isEqualTo(SqliteInspectorProtocol.DatabasePossiblyChangedEvent.getDefaultInstance())
      }
      testEnvironment.assertNoQueuedEvents()
    }
  }

  @Test
  @Ignore("b/328819365") // Test was marked as flaky and is also ignored in the original repo
  fun test_throttling(): Unit = runBlocking {
    // Starting to track databases makes the inspector register hooks
    testEnvironment.sendCommand(MessageFactory.createTrackDatabasesCommand())

    // Any hook that triggers invalidation
    val hook =
      testEnvironment
        .consumeRegisteredHooks()
        .first { it.originMethod == "executeInsert()J" }
        .asExitHook

    testEnvironment.assertNoQueuedEvents()

    // First invalidation triggering event
    hook.onExit(null)
    shadowLooper.runToEndOfTasks()
    val event1 = testEnvironment.receiveEvent()

    // Shortly followed by many invalidation-triggering events
    repeat(50) { hook.onExit(null) }
    shadowLooper.runToEndOfTasks()
    val event2 = testEnvironment.receiveEvent()

    // Event validation
    listOf(event1, event2).forEach { assertThat(it.oneOfCase).isEqualTo(DATABASE_POSSIBLY_CHANGED) }

    // Only two invalidation events received
    shadowLooper.runToEndOfTasks()
    testEnvironment.assertNoQueuedEvents()
  }

  @Test
  @Ignore("b/328819365") // Test was marked as flaky and is also ignored in the original repo
  fun test_cursor_methods(): Unit = runBlocking {
    // Starting to track databases makes the inspector register hooks
    testEnvironment.sendCommand(MessageFactory.createTrackDatabasesCommand())

    // Hook method signatures
    val rawQueryMethodSignature =
      "rawQueryWithFactory(" +
        "Landroid/database/sqlite/SQLiteDatabase\$CursorFactory;" +
        "Ljava/lang/String;" +
        "[Ljava/lang/String;" +
        "Ljava/lang/String;" +
        "Landroid/os/CancellationSignal;" +
        ")Landroid/database/Cursor;"
    val closeMethodSignature = "close()V"

    val hooks: List<Hook> = testEnvironment.consumeRegisteredHooks()

    // Check for hooks being registered
    val hooksByClass = hooks.groupBy { it.originClass }
    val rawQueryHooks =
      hooksByClass[SQLiteDatabase::class.java]!!
        .filter { it.originMethod == rawQueryMethodSignature }
        .map { it::class }

    assertThat(rawQueryHooks).containsExactly(Hook.EntryHook::class, Hook.ExitHook::class)
    val hook = hooksByClass[SQLiteCursor::class.java]!!.single()
    assertThat(hook).isInstanceOf(Hook.EntryHook::class.java)
    assertThat(hook.originMethod).isEqualTo(closeMethodSignature)
    // Check for hook behaviour
    fun wrap(cursor: Cursor): Cursor = object : CursorWrapper(cursor) {}
    fun noOp(c: Cursor): Cursor = c
    listOf(::wrap, ::noOp).forEach { wrap ->
      listOf("insert into t1 values (1)" to true, "select * from sqlite_master" to false).forEach {
        (query, shouldCauseInvalidation) ->
        testEnvironment.assertNoQueuedEvents()

        val cursor = cursorForQuery(query)
        hooks.entryHookFor(rawQueryMethodSignature).onEntry(null, listOf(null, query))
        hooks.exitHookFor(rawQueryMethodSignature).onExit(wrap(wrap(cursor)))
        hooks.entryHookFor(closeMethodSignature).onEntry(cursor, emptyList())

        if (shouldCauseInvalidation) {
          shadowLooper.runToEndOfTasks()
          testEnvironment.receiveEvent()
        }
        testEnvironment.assertNoQueuedEvents()
      }
    }

    // no crash for unknown cursor class
    hooks.entryHookFor(rawQueryMethodSignature).onEntry(null, listOf(null, "select * from t1"))
    hooks
      .exitHookFor(rawQueryMethodSignature)
      .onExit(
        object : AbstractCursor() {
          override fun getLong(column: Int): Long = 0

          override fun getCount(): Int = 0

          override fun getColumnNames(): Array<String> = emptyArray()

          override fun getShort(column: Int): Short = 0

          override fun getFloat(column: Int): Float = 0f

          override fun getDouble(column: Int): Double = 0.0

          override fun isNull(column: Int): Boolean = false

          override fun getInt(column: Int): Int = 0

          override fun getString(column: Int): String = ""
        }
      )

    Unit
  }

  private fun cursorForQuery(query: String): SQLiteCursor {
    val db =
      Database("ignored", Table("t1", Column("c1", "int")))
        .createInstance(closeablesRule, temporaryFolder)
    val cursor = db.rawQuery(query, null)
    val context = RuntimeEnvironment.getApplication()
    context.deleteDatabase(db.path)
    return cursor as SQLiteCursor
  }

  private fun List<Hook>.entryHookFor(m: String): ArtTooling.EntryHook =
    this.first { it.originMethod == m && it is Hook.EntryHook }.asEntryHook

  @Suppress("UNCHECKED_CAST")
  private fun List<Hook>.exitHookFor(m: String): ArtTooling.ExitHook<Any> =
    this.first { it.originMethod == m && it is Hook.ExitHook }.asExitHook
      as ArtTooling.ExitHook<Any>
}
