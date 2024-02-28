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

package android.database

interface Cursor {

  fun getCount(): Int

  fun getColumnNames(): Array<String>

  fun getColumnName(col: Int): String

  fun getColumnCount(): Int

  fun moveToNext(): Boolean

  fun getType(col: Int): Int

  fun getBlob(col: Int): ByteArray?

  fun getString(col: Int): String?

  fun getInt(col: Int): Int

  fun getLong(col: Int): Long

  fun getDouble(col: Int): Double

  fun getColumnIndex(col: String): Int

  fun close() {}

  companion object {
    const val FIELD_TYPE_BLOB: Int = 4
    const val FIELD_TYPE_FLOAT: Int = 2
    const val FIELD_TYPE_INTEGER: Int = 1
    const val FIELD_TYPE_NULL: Int = 0
    const val FIELD_TYPE_STRING: Int = 3
  }
}
