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

package com.android.backup.testing

import ai.grazie.utils.dropPrefix
import com.android.backup.AbstractBackupServices
import com.android.tools.environment.log.NoopLogger
import java.io.InputStream
import java.io.OutputStream

private const val BMGR_ENABLE = "bmgr enable "
private const val SET_TEST_MODE = "settings put secure backup_enable_android_studio_mode "
private const val SET_TRANSPORT = "bmgr transport "
private const val LIST_TRANSPORT = "bmgr list transports"
private const val INIT_TRANSPORT = "bmgr init "
private const val BACKUP_NOW = "bmgr backupnow "
private const val RESTORE = "bmgr restore "
private const val DELETE_FILES = "rm -rf "

/** A fake [com.android.backup.BackupServices] */
internal class FakeBackupServices(serialNumber: String, totalSteps: Int) :
  AbstractBackupServices(serialNumber, NoopLogger(), FakeProgressListener(), totalSteps) {

  var bmgrEnabled = false
  var testMode = 0
  private var transports =
    listOf(
      "com.android.localtransport/.LocalTransport",
      "com.google.android.gms/.backup.migrate.service.D2dTransport",
      "com.google.android.gms/.backup.BackupTransportService",
    )
  var activeTransport = "com.google.android.gms/.backup.BackupTransportService"
  private val commands = mutableListOf<String>()
  private val pushedFiles = mutableListOf<String>()

  fun getCommands(): List<String> = commands

  fun getProgress() = (progressListener as FakeProgressListener).getSteps()

  override suspend fun executeCommand(command: String): String {
    commands.add(command)
    return when {
      command == "bmgr enabled" -> handleBmgrEnabled()
      command.startsWith(BMGR_ENABLE) -> handleEnableBmgr(command)
      command.startsWith(SET_TEST_MODE) -> handleSetTestMode(command)
      command.startsWith(SET_TRANSPORT) -> handleSetTransport(command)
      command == LIST_TRANSPORT -> handleListTransports()
      command.startsWith(INIT_TRANSPORT) -> handleInitTransport(command)
      command.startsWith(BACKUP_NOW) -> handleBackupNow()
      command.startsWith(RESTORE) -> handleRestore()
      command.startsWith(DELETE_FILES) -> ""
      else -> throw NotImplementedError("Command '$command' is not implemented")
    }
  }

  @Suppress("BlockingMethodInNonBlockingContext")
  override suspend fun syncRecv(outputStream: OutputStream, remoteFilePath: String) {
    outputStream.write(remoteFilePath.toByteArray())
    // AdbLib closes the stream after a recv, so we do as well.
    outputStream.close()
  }

  override suspend fun syncSend(inputStream: InputStream, remoteFilePath: String) {
    pushedFiles.add(remoteFilePath)
    @Suppress("BlockingMethodInNonBlockingContext") inputStream.close()
  }

  private fun handleBmgrEnabled(): String {
    return when (bmgrEnabled) {
      true -> "Backup Manager currently enabled"
      false -> "Backup Manager currently disabled"
    }
  }

  private fun handleEnableBmgr(command: String): String {
    bmgrEnabled = command.dropPrefix(BMGR_ENABLE).toBoolean()
    return ""
  }

  private fun handleSetTestMode(command: String): String {
    testMode = command.dropPrefix(SET_TEST_MODE).toInt()
    return ""
  }

  private fun handleSetTransport(command: String): String {
    val oldTransport = activeTransport
    activeTransport = command.dropPrefix(SET_TRANSPORT)
    return "Selected transport $activeTransport (formerly $oldTransport)"
  }

  private fun handleListTransports(): String {
    return transports.joinToString("\n") {
      val active = if (it == activeTransport) "*" else " "
      "  $active $it"
    }
  }

  private fun handleInitTransport(command: String): String {
    val transport = command.dropPrefix(INIT_TRANSPORT)
    // In production, even if the transport doesn't exist, the commends succeeds. We just use this
    // condition as an easy way to trigger an error.
    return when (transport in transports) {
      true -> "Initialization result: 0"
      false -> "Error: $transport not supported"
    }
  }

  private fun handleBackupNow(): String {
    return "Backup finished with result: Success"
  }

  private fun handleRestore(): String {
    return "restoreFinished: 0\n"
  }
}
