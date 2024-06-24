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
package com.example.backuprestore

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.room.Room
import com.example.backuprestore.db.AppDatabase
import com.example.backuprestore.db.User
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

private const val StopTimeoutMillis: Long = 5000
private val WhileUiSubscribed: SharingStarted = SharingStarted.WhileSubscribed(StopTimeoutMillis)

internal class MainViewModel(application: Application) : ViewModel() {
  private val db = Room.databaseBuilder(application, AppDatabase::class.java, "database.db").build()

  val users: StateFlow<List<User>> =
    db.userDao().observeAll().stateIn(viewModelScope, WhileUiSubscribed, emptyList())

  fun addUser(name: String) {
    viewModelScope.launch { db.userDao().insert(User(name)) }
  }
}
