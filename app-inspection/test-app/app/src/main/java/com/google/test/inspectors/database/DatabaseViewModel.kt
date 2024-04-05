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

package com.google.test.inspectors.database

import android.app.Application
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteDatabase.OPEN_READONLY
import android.database.sqlite.SQLiteDatabase.OpenParams
import android.database.sqlite.SQLiteOpenHelper
import androidx.annotation.RequiresApi
import androidx.lifecycle.viewModelScope
import com.google.test.inspectors.Logger
import com.google.test.inspectors.ui.scafold.AppScaffoldViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

private const val NATIVE_DATABASE = "native-database.db"
private const val NATIVE_DATABASE_VERSION = 1
// language=SQLite
private const val NATIVE_DATABASE_CREATE =
  """
  CREATE TABLE Users (
    _id INTEGER PRIMARY KEY,
    name TEXT
  )
  """

@HiltViewModel
internal class DatabaseViewModel @Inject constructor(application: Application) :
  AppScaffoldViewModel(), DatabaseActions {

  private val exceptionHandler = CoroutineExceptionHandler { _, throwable ->
    setSnack("Error: ${throwable.message}")
    Logger.error("Error: ${throwable.message}", throwable)
  }

  private val scope = CoroutineScope(viewModelScope.coroutineContext + exceptionHandler)

  private val readWriteDatabaseOpenHelper = ReadWriteDatabaseOpenHelper(application)
  private val readWriteDatabaseFlow: MutableStateFlow<SQLiteDatabase?> = MutableStateFlow(null)
  @RequiresApi(28) private val readOnlyDatabaseOpenHelper = ReadOnlyDatabaseOpenHelper(application)
  private val readOnlyDatabaseFlow: MutableStateFlow<SQLiteDatabase?> = MutableStateFlow(null)

  val readWriteDatabaseState: StateFlow<Boolean> =
    readWriteDatabaseFlow.map { it != null }.stateIn(viewModelScope, WhileUiSubscribed, false)

  val readOnlyDatabaseState: StateFlow<Boolean> =
    readOnlyDatabaseFlow.map { it != null }.stateIn(viewModelScope, WhileUiSubscribed, false)

  override fun doOpenReadWriteDatabase() {
    scope.launch(IO) { readWriteDatabaseFlow.value = readWriteDatabaseOpenHelper.writableDatabase }
  }

  override fun doCloseReadWriteDatabase() {
    scope.launch(IO) {
      readWriteDatabaseFlow.value?.close()
      readWriteDatabaseFlow.value = null
    }
  }

  @RequiresApi(28)
  override fun doOpenReadOnlyDatabase() {
    scope.launch(IO) { readOnlyDatabaseFlow.value = readOnlyDatabaseOpenHelper.readableDatabase }
  }

  override fun doCloseReadOnlyDatabase() {
    scope.launch(IO) {
      readOnlyDatabaseFlow.value?.close()
      readOnlyDatabaseFlow.value = null
    }
  }

  /**
   * A [SQLiteOpenHelper] that manages [NATIVE_DATABASE_CREATE] as a read-write database.
   *
   * See [ReadOnlyDatabaseOpenHelper] which manages the same file as a readonly instance.
   */
  private class ReadWriteDatabaseOpenHelper(context: Context) :
    SQLiteOpenHelper(context, NATIVE_DATABASE, null, NATIVE_DATABASE_VERSION) {

    override fun onCreate(db: SQLiteDatabase) {
      db.execSQL(NATIVE_DATABASE_CREATE)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {}
  }

  /**
   * A [SQLiteOpenHelper] that manages [NATIVE_DATABASE_CREATE] as a readonly database.
   *
   * See [ReadOnlyDatabaseOpenHelper] which manages the same file as a read-write instance.
   */
  @RequiresApi(28)
  private class ReadOnlyDatabaseOpenHelper(context: Context) :
    SQLiteOpenHelper(
      context,
      NATIVE_DATABASE,
      NATIVE_DATABASE_VERSION,
      OpenParams.Builder().setOpenFlags(OPEN_READONLY).build(),
    ) {

    override fun onCreate(db: SQLiteDatabase) {
      db.execSQL(NATIVE_DATABASE_CREATE)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {}
  }
}
