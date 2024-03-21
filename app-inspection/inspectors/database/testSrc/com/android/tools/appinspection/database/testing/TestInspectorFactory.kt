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

package com.android.tools.appinspection.database.testing

import androidx.inspection.Connection
import androidx.inspection.InspectorEnvironment
import androidx.inspection.InspectorFactory
import com.android.tools.appinspection.database.SqliteInspector

/** An [InspectorFactory] that gives access to the created inspector. */
internal class TestInspectorFactory : InspectorFactory<SqliteInspector>(SQLITE_INSPECTOR_ID) {
  private var sqliteInspector: SqliteInspector? = null

  fun getSqliteInspector() =
    sqliteInspector ?: throw IllegalStateException("createInspector hasn't been called yet")

  override fun createInspector(
    connection: Connection,
    environment: InspectorEnvironment,
  ): SqliteInspector {
    synchronized(this) {
      val inspector =
        sqliteInspector.takeIf { it != null } ?: SqliteInspector(connection, environment)
      sqliteInspector = inspector
      return inspector
    }
  }
}
