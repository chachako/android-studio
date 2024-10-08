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
package com.android.backup

import java.io.InputStream
import java.io.OutputStream
import kotlin.coroutines.CoroutineContext

/** Provides backup services for a specific device */
interface AdbServices {
  val ioContext: CoroutineContext

  /** Report progress of backup/restore */
  suspend fun reportProgress(text: String)

  /**
   * Execute a block of code after setting up the device for a backup/restore
   *
   * @param transport The backup transport to use for the operation
   */
  suspend fun withSetup(transport: String, block: suspend () -> Unit)

  /**
   * Initialize a backup transport
   *
   * @param transport The backup transport to initialize
   */
  suspend fun initializeTransport(transport: String)

  /**
   * Execute a command on a device
   *
   * @param command A command to execute
   * @return The stdout of the command.
   * @throws BackupException on error
   */
  suspend fun executeCommand(
    command: String,
    errorCode: ErrorCode = ErrorCode.UNEXPECTED_ERROR,
  ): AdbOutput

  /**
   * Reads a file from a Content Provider
   *
   * @param outputStream An [outputStream] to write the file to
   * @param uri The content URI
   */
  suspend fun readContent(outputStream: OutputStream, uri: String)

  /**
   * Pushes a file to a device
   *
   * @param inputStream An [InputStream] to read the file contents from
   * @param uri The content URI
   */
  suspend fun writeContent(inputStream: InputStream, uri: String)

  suspend fun backupNow(applicationId: String, type: BackupType)

  suspend fun restore(token: String, applicationId: String)

  suspend fun sendUpdateGmsIntent()

  suspend fun getForegroundApplicationId(): String

  suspend fun isInstalled(applicationId: String): Boolean

  class AdbOutput(val stdout: String, val stderr: String)
}
