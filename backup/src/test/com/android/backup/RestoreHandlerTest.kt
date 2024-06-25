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

import com.android.backup.testing.FakeBackupServices
import com.google.common.truth.Truth.assertThat
import java.nio.file.Path
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.io.path.createFile
import kotlin.io.path.outputStream
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** Tests for [RestoreHandler] */
class RestoreHandlerTest {

  @get:Rule val temporaryFolder = TemporaryFolder()

  private val backupServices = FakeBackupServices("serial", RestoreHandler.NUMBER_OF_STEPS)

  @Test
  fun restore(): Unit = runBlocking {
    val backupFile = createBackupFile("com.app", "11223344556677889900")
    val handler = RestoreHandler(backupServices, backupFile)

    handler.restore()

    assertThat(backupServices.getCommands())
      .containsExactly(
        "bmgr enabled",
        "bmgr enable true",
        "settings put secure backup_enable_android_studio_mode 1",
        "bmgr transport com.google.android.gms/.backup.BackupTransportService",
        "bmgr list transports",
        "bmgr restore 9bc1546914997f6c com.app",
        "settings put secure backup_enable_android_studio_mode 0",
        "bmgr enable false",
        "rm -rf /sdcard/Android/data/com.google.android.gms/files/android_studio_backup_data",
      )
      .inOrder()
    assertThat(backupServices.testMode).isEqualTo(0)
  }

  @Test
  fun restore_bmgrAlreadyEnabled(): Unit = runBlocking {
    val backupFile = createBackupFile("com.app", "11223344556677889900")
    val handler = RestoreHandler(backupServices, backupFile)
    backupServices.bmgrEnabled = true

    handler.restore()

    assertThat(backupServices.getCommands())
      .containsExactly(
        "bmgr enabled",
        "settings put secure backup_enable_android_studio_mode 1",
        "bmgr transport com.google.android.gms/.backup.BackupTransportService",
        "bmgr list transports",
        "bmgr restore 9bc1546914997f6c com.app",
        "settings put secure backup_enable_android_studio_mode 0",
        "rm -rf /sdcard/Android/data/com.google.android.gms/files/android_studio_backup_data",
      )
      .inOrder()
  }

  @Test
  fun restore_transportNotSet(): Unit = runBlocking {
    val backupFile = createBackupFile("com.app", "11223344556677889900")
    val handler = RestoreHandler(backupServices, backupFile)
    backupServices.activeTransport = "com.android.localtransport/.LocalTransport"

    handler.restore()

    assertThat(backupServices.getCommands())
      .containsExactly(
        "bmgr enabled",
        "bmgr enable true",
        "settings put secure backup_enable_android_studio_mode 1",
        "bmgr transport com.google.android.gms/.backup.BackupTransportService",
        "bmgr list transports",
        "bmgr restore 9bc1546914997f6c com.app",
        "bmgr transport com.android.localtransport/.LocalTransport",
        "bmgr list transports",
        "settings put secure backup_enable_android_studio_mode 0",
        "bmgr enable false",
        "rm -rf /sdcard/Android/data/com.google.android.gms/files/android_studio_backup_data",
      )
      .inOrder()
  }

  @Test
  fun restore_assertProgress(): Unit = runBlocking {
    val backupFile = createBackupFile("com.app", "11223344556677889900")
    val handler = RestoreHandler(backupServices, backupFile)

    handler.restore()

    assertThat(backupServices.getProgress())
      .containsExactly(
        "1/8: Pushing backup file",
        "2/8: Checking if BMGR is enabled",
        "3/10: Enabling BMGR",
        "4/10: Enabling test mode",
        "5/10: Setting backup transport",
        "6/10: Restoring com.app",
        "7/10: Disabling test mode",
        "8/10: Disabling BMGR",
        "9/10: Deleting backup directory",
        "10/10: Done",
      )
      .inOrder()
  }

  @Suppress("SameParameterValue")
  private fun createBackupFile(applicationId: String, token: String): Path {
    val path = Path.of(temporaryFolder.root.path, "file.backup")
    path.createFile()
    ZipOutputStream(path.outputStream()).use { zip ->
      zip.putNextEntry(ZipEntry("@pm@"))
      zip.putNextEntry(ZipEntry(applicationId))
      zip.putNextEntry(ZipEntry("restore_token_file"))
      zip.write(token.toByteArray())
    }
    return path
  }
}
