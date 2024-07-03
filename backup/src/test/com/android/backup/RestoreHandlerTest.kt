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

import com.android.backup.BackupResult.Success
import com.android.backup.RestoreHandler.Companion.validateBackupFile
import com.android.backup.testing.FakeBackupServices
import com.google.common.truth.Truth.assertThat
import java.nio.file.Path
import java.util.zip.ZipEntry
import java.util.zip.ZipException
import java.util.zip.ZipOutputStream
import kotlin.io.path.createFile
import kotlin.io.path.outputStream
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertThrows
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

    val result = handler.restore()

    assertThat(result).isEqualTo(Success)
    assertThat(backupServices.getCommands())
      .containsExactly(
        "dumpsys package com.google.android.gms",
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
        "dumpsys package com.google.android.gms",
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
        "dumpsys package com.google.android.gms",
        "bmgr enabled",
        "bmgr enable true",
        "settings put secure backup_enable_android_studio_mode 1",
        "bmgr transport com.google.android.gms/.backup.BackupTransportService",
        "bmgr list transports",
        "bmgr restore 9bc1546914997f6c com.app",
        "bmgr transport com.android.localtransport/.LocalTransport",
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
        "1/9: Pushing backup file",
        "2/9: Verifying Google services",
        "3/9: Checking if BMGR is enabled",
        "4/11: Enabling BMGR",
        "5/11: Enabling test mode",
        "6/11: Setting backup transport",
        "7/11: Restoring com.app",
        "8/11: Disabling test mode",
        "9/11: Disabling BMGR",
        "10/11: Deleting backup directory",
        "11/11: Done",
      )
      .inOrder()
  }

  @Test
  fun validateBackupFile() {
    validateBackupFile(createBackupFile("com.app", "11223344556677889900"))
  }

  @Test
  fun validateBackupFile_noApplicationId() {
    assertThrows(BackupException::class.java) {
      validateBackupFile(
        createZipFile(FileInfo("@pm@", ""), FileInfo("restore_token_file", "11223344556677889900"))
      )
    }
  }

  @Test
  fun validateBackupFile_invalidToken() {
    assertThrows(BackupException::class.java) {
      validateBackupFile(createBackupFile("com.app", "foobar"))
    }
  }

  @Test
  fun validateBackupFile_notZipFile() {
    assertThrows(ZipException::class.java) {
      val path = Path.of(temporaryFolder.root.path, "file.backup").createFile()
      validateBackupFile(path)
    }
  }

  @Test
  fun validateBackupFile_unexpectedFile() {
    assertThrows(BackupException::class.java) {
      validateBackupFile(
        createZipFile(
          FileInfo("@pm@", ""),
          FileInfo("restore_token_file", "11223344556677889900"),
          FileInfo("com.app", ""),
          FileInfo("extra file", ""),
        )
      )
    }
  }

  @Test
  fun validateBackupFile_missingPmFile() {
    assertThrows(BackupException::class.java) {
      validateBackupFile(
        createZipFile(
          FileInfo("restore_token_file", "11223344556677889900"),
          FileInfo("com.app", ""),
        )
      )
    }
  }

  @Suppress("SameParameterValue")
  private fun createBackupFile(applicationId: String, token: String) =
    createZipFile(
      FileInfo("@pm@", ""),
      FileInfo(applicationId, ""),
      FileInfo("restore_token_file", token),
    )

  @Suppress("SameParameterValue")
  private fun createZipFile(vararg files: FileInfo): Path {
    val path = Path.of(temporaryFolder.root.path, "file.backup")
    ZipOutputStream(path.outputStream()).use { zip ->
      files.forEach {
        zip.putNextEntry(ZipEntry(it.name))
        zip.write(it.contents.toByteArray())
      }
    }
    return path
  }

  private class FileInfo(val name: String, val contents: String)
}
