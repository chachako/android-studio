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

import android.database.sqlite.SQLiteDatabase
import androidx.inspection.ArtTooling
import com.google.common.truth.Truth

internal const val OPEN_DATABASE_COMMAND_SIGNATURE_API11: String =
  "openDatabase" +
    "(" +
    "Ljava/lang/String;" +
    "Landroid/database/sqlite/SQLiteDatabase\$CursorFactory;" +
    "I" +
    "Landroid/database/DatabaseErrorHandler;" +
    ")" +
    "Landroid/database/sqlite/SQLiteDatabase;"

internal const val OPEN_DATABASE_COMMAND_SIGNATURE_API27: String =
  "openDatabase" +
    "(" +
    "Ljava/io/File;" +
    "Landroid/database/sqlite/SQLiteDatabase\$OpenParams;" +
    ")" +
    "Landroid/database/sqlite/SQLiteDatabase;"

internal const val CREATE_IN_MEMORY_DATABASE_COMMAND_SIGNATURE_API27 =
  "createInMemory" +
    "(" +
    "Landroid/database/sqlite/SQLiteDatabase\$OpenParams;" +
    ")" +
    "Landroid/database/sqlite/SQLiteDatabase;"

private const val ALL_REFERENCES_RELEASED_COMMAND_SIGNATURE = "onAllReferencesReleased()V"

private const val RELEASE_REFERENCE_COMMAND_SIGNATURE = "releaseReference()V"

@Suppress("UNCHECKED_CAST")
internal fun List<Hook>.triggerOnOpenedExit(db: SQLiteDatabase) {
  val onOpen =
    filterIsInstance<Hook.ExitHook>().filter {
      it.originMethod == OPEN_DATABASE_COMMAND_SIGNATURE_API11
    }
  Truth.assertThat(onOpen).hasSize(1)
  (onOpen.first().asExitHook as ArtTooling.ExitHook<SQLiteDatabase>).onExit(db)
}

internal fun List<Hook>.triggerOnOpenedEntry(thisObj: Any?, vararg args: Any) {
  val onOpen =
    filterIsInstance<Hook.EntryHook>().filter {
      it.originMethod == OPEN_DATABASE_COMMAND_SIGNATURE_API11
    }
  Truth.assertThat(onOpen).hasSize(1)
  onOpen.first().asEntryHook.onEntry(thisObj, args.asList())
}

internal fun List<Hook>.triggerReleaseReference(db: SQLiteDatabase) {
  val onOpen = filter { it.originMethod == RELEASE_REFERENCE_COMMAND_SIGNATURE }
  Truth.assertThat(onOpen).hasSize(1)
  onOpen.first().asEntryHook.onEntry(db, emptyList())
}

internal fun List<Hook>.triggerOnAllReferencesReleased(db: SQLiteDatabase) {
  val onReleasedHooks = this.filter { it.originMethod == ALL_REFERENCES_RELEASED_COMMAND_SIGNATURE }
  Truth.assertThat(onReleasedHooks).hasSize(2)
  val onReleasedEntry = (onReleasedHooks.first { it is Hook.EntryHook }.asEntryHook)::onEntry
  val onReleasedExit = (onReleasedHooks.first { it is Hook.ExitHook }.asExitHook)::onExit
  onReleasedEntry(db, emptyList())
  onReleasedExit(null)
}
