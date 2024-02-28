/*
 * Copyright 2024 The Android Open Source Project
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
import java.sql.ResultSet

/**
 * A simple fake [Cursor]
 *
 * Creates a Cursor from a [SQLiteQuery]
 */
class SQLiteCursor
@Suppress("UNUSED_PARAMETER")
constructor(driver: SQLiteCursorDriver?, editTable: String?, query: SQLiteQuery) : Cursor {
  private val _resultSet = query.execute()

  private val resultSet
    get() = _resultSet ?: throw RuntimeException("Empty result set")

  private val metaData
    get() = resultSet.metaData

  private val _columnNames =
    when (_resultSet) {
      null -> emptyArray()
      else -> _resultSet.getColumnNames()
    }

  override fun getCount(): Int {
    // Counting the rows is not trivial. We need to iterate them and count and then restore to the
    // original position.
    // Will implement as needed.
    TODO("Not yet implemented")
  }

  override fun getColumnNames(): Array<String> = _columnNames

  override fun getColumnName(col: Int): String = metaData.getColumnName(col + 1)

  override fun getColumnCount(): Int = _columnNames.size

  override fun moveToNext(): Boolean = _resultSet?.next() ?: false

  override fun getType(col: Int): Int = getTypeOfObject(resultSet.getObject(col + 1))

  override fun getBlob(col: Int): ByteArray? = resultSet.getBytes(col + 1)

  override fun getString(col: Int): String? = resultSet.getString(col + 1)

  override fun getInt(col: Int): Int = resultSet.getInt(col + 1)

  override fun getLong(col: Int): Long = resultSet.getLong(col + 1)

  override fun getDouble(col: Int): Double = resultSet.getDouble(col + 1)

  override fun getColumnIndex(col: String): Int = _columnNames.indexOf(col)
}

/** Copied from the Android Framework `DatabaseUtils. getTypeOfObject()`. */
private fun getTypeOfObject(obj: Any?): Int {
  return when (obj) {
    null -> Cursor.FIELD_TYPE_NULL
    is ByteArray -> Cursor.FIELD_TYPE_BLOB
    is Float,
    is Double -> Cursor.FIELD_TYPE_FLOAT
    is Long,
    is Int,
    is Short,
    is Byte -> Cursor.FIELD_TYPE_INTEGER
    else -> Cursor.FIELD_TYPE_STRING
  }
}

private fun ResultSet.getColumnNames() =
  (1..metaData.columnCount).map { metaData.getColumnName(it) }.toTypedArray()
