/*
 * Copyright (C) 2024 The Android Open Source Project
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

package android.database.sqlite

import android.database.Cursor
import android.os.CancellationSignal
import java.sql.DriverManager
import java.sql.PreparedStatement

/**
 * A simple Fake database.
 *
 * This is not a real SQL database. It supports a small number of "command" that are designed to be
 * able to drive the Database Inspector.
 *
 * For example, `select-table <table>` generates a cursor that represents table named `table`.
 */
class SQLiteDatabase(val path: String) {
  private val connection = DriverManager.getConnection("jdbc:sqlite::memory:")

  @Suppress("UNUSED_PARAMETER")
  @JvmOverloads
  fun execSQL(sql: String, bindArgs: Array<Any?> = emptyArray()) {
    connection.createStatement().use { it.execute(sql) }
  }

  @Suppress("UNUSED_PARAMETER")
  @JvmOverloads
  fun rawQuery(
    sql: String,
    selectionArgs: Array<String>?,
    cancellationSignal: CancellationSignal? = null,
  ): Cursor {
    return SQLiteCursor(null, null, SQLiteQuery(prepareStatement(sql)))
  }

  fun compileStatement(sql: String): SQLiteStatement {
    return SQLiteStatement(prepareStatement(sql))
  }

  @Suppress("UNUSED_PARAMETER")
  fun rawQueryWithFactory(
    cursorFactory: CursorFactory,
    sql: String,
    selectionArgs: Array<String>?,
    editTable: String?,
    cancellationSignal: CancellationSignal?,
  ): Cursor {
    return cursorFactory.newCursor(this, null, editTable, SQLiteQuery(prepareStatement(sql)))
  }

  fun isOpen() = true

  fun isReadOnly(): Boolean = false

  fun isWriteAheadLoggingEnabled(): Boolean = false

  fun acquireReference() {}

  fun releaseReference() {}

  interface CursorFactory {

    fun newCursor(
      database: SQLiteDatabase,
      driver: SQLiteCursorDriver?,
      editTable: String?,
      query: SQLiteQuery,
    ): Cursor
  }

  private fun prepareStatement(sql: String): PreparedStatement {
    val prepareStatement =
      try {
        connection.prepareStatement(sql)
      } catch (e: Throwable) {
        throw SQLiteException(e.message, e.cause)
      }
    return prepareStatement
  }
}
