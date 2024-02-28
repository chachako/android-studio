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

import android.content.Context

abstract class SQLiteOpenHelper
@Suppress("UNUSED_PARAMETER")
constructor(
  context: Context?,
  private val name: String,
  factory: SQLiteDatabase.CursorFactory?,
  version: Int,
) {

  abstract fun onCreate(db: SQLiteDatabase)

  abstract fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int)

  @Suppress("UNUSED_PARAMETER") fun setWriteAheadLoggingEnabled(enabled: Boolean) {}

  val readableDatabase
    get() = SQLiteDatabase(name)
}
