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

@file:JvmName("DatabaseExtensions")

package com.android.tools.appinspection.database

import android.database.sqlite.SQLiteDatabase
import java.io.File

private const val IN_MEMORY_DATABASE_PATH = ":memory:"

/** Placeholder `%x` is for database's hashcode */
private const val IN_MEMORY_DATABASE_NAME_FORMAT = "$IN_MEMORY_DATABASE_PATH {hashcode=0x%x}"

/** Thread-safe as [SQLiteDatabase.getPath] and [Any.hashCode] are thread-safe */
fun SQLiteDatabase.pathForDatabase(): String =
  when {
    isInMemoryDatabase() -> IN_MEMORY_DATABASE_NAME_FORMAT.format(hashCode())
    else -> File(path).absolutePath
  }

/** Thread-safe as [SQLiteDatabase.getPath] is thread-safe. */
fun SQLiteDatabase.isInMemoryDatabase() = IN_MEMORY_DATABASE_PATH == path

/**
 * Attempts to call [SQLiteDatabase.acquireReference] on the provided object.
 *
 * @return true if the operation was successful; false if unsuccessful because the database was
 *   already closed; otherwise re-throws the exception thrown by
 *   [ ][SQLiteDatabase.acquireReference].
 */
fun SQLiteDatabase.tryAcquireReference(): Boolean {
  if (!isOpen) {
    return false
  }

  try {
    acquireReference()
    return true // success
  } catch (e: IllegalStateException) {
    if (e.isAttemptAtUsingClosedDatabase()) {
      return false
    }
    throw e
  }
}

/**
 * Note that this is best-effort as relies on Exception message parsing, which could break in the
 * future. Use in the context where false negatives (more likely) and false positives (less likely
 * due to the specificity of the message) are tolerable, e.g. to assign error codes where if it
 * fails we will just send an 'unknown' error.
 */
fun Throwable.isAttemptAtUsingClosedDatabase(): Boolean {
  val message = this.message ?: return false
  return (message.contains("attempt to re-open an already-closed object") ||
    message.contains("Cannot perform this operation because the connection pool has been closed"))
}
