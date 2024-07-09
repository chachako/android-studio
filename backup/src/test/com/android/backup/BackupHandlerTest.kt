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
import com.android.backup.ErrorCode.BACKUP_FAILED
import com.android.backup.ErrorCode.GMSCORE_NOT_FOUND
import com.android.backup.ErrorCode.TRANSPORT_INIT_FAILED
import com.android.backup.testing.FakeBackupServices
import com.android.backup.testing.FakeBackupServices.CommandOverride.Output
import com.android.backup.testing.FakeBackupServices.CommandOverride.Throw
import com.android.backup.testing.asBackupResult
import com.google.common.truth.Truth.assertThat
import java.nio.file.Path
import java.util.zip.ZipFile
import kotlin.io.path.createFile
import kotlin.io.path.exists
import kotlin.io.path.notExists
import kotlin.io.path.pathString
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** Tests for [BackupHandler] */
class BackupHandlerTest {

  @get:Rule val temporaryFolder = TemporaryFolder()

  private val backupServices = FakeBackupServices("serial", BackupHandler.NUMBER_OF_STEPS)

  @Test
  fun backup(): Unit = runBlocking {
    val backupFile = Path.of(temporaryFolder.root.path, "file.backup")
    val handler = BackupHandler(backupServices, backupFile, "com.app")

    val result = handler.backup()

    assertThat(result).isEqualTo(Success)
    assertThat(backupServices.getCommands())
      .containsExactly(
        "dumpsys package com.google.android.gms",
        "bmgr enabled",
        "bmgr enable true",
        "settings put secure backup_enable_android_studio_mode 1",
        "bmgr transport com.google.android.gms/.backup.migrate.service.D2dTransport",
        "bmgr list transports",
        "bmgr init com.google.android.gms/.backup.migrate.service.D2dTransport",
        "bmgr backupnow com.app",
        "rm -rf /sdcard/Android/data/com.google.android.gms/files/android_studio_backup_data",
        "bmgr transport com.google.android.gms/.backup.BackupTransportService",
        "settings put secure backup_enable_android_studio_mode 0",
        "bmgr enable false",
      )
      .inOrder()
    assertThat(backupServices.testMode).isEqualTo(0)
    assertThat(backupFile.exists()).isTrue()
    assertThat(backupFile.unzip())
      .containsExactly(
        "@pm@" to "${BackupServices.BACKUP_DIR}/@pm@",
        "restore_token_file" to "${BackupServices.BACKUP_DIR}/restore_token_file",
        "com.app" to "${BackupServices.BACKUP_DIR}/com.app",
      )
  }

  @Test
  fun backup_bmgrAlreadyEnabled(): Unit = runBlocking {
    val backupFile = Path.of(temporaryFolder.root.path, "file.backup")
    val handler = BackupHandler(backupServices, backupFile, "com.app")
    backupServices.bmgrEnabled = true

    val result = handler.backup()

    assertThat(result).isEqualTo(Success)
    assertThat(backupServices.getCommands())
      .containsExactly(
        "dumpsys package com.google.android.gms",
        "bmgr enabled",
        "settings put secure backup_enable_android_studio_mode 1",
        "bmgr transport com.google.android.gms/.backup.migrate.service.D2dTransport",
        "bmgr list transports",
        "bmgr init com.google.android.gms/.backup.migrate.service.D2dTransport",
        "bmgr backupnow com.app",
        "rm -rf /sdcard/Android/data/com.google.android.gms/files/android_studio_backup_data",
        "bmgr transport com.google.android.gms/.backup.BackupTransportService",
        "settings put secure backup_enable_android_studio_mode 0",
      )
      .inOrder()
  }

  @Test
  fun backup_transportAlreadySelected(): Unit = runBlocking {
    val backupFile = Path.of(temporaryFolder.root.path, "file.backup")
    val handler = BackupHandler(backupServices, backupFile, "com.app")
    backupServices.activeTransport = "com.google.android.gms/.backup.migrate.service.D2dTransport"

    val result = handler.backup()

    assertThat(result).isEqualTo(Success)
    assertThat(backupServices.getCommands())
      .containsExactly(
        "dumpsys package com.google.android.gms",
        "bmgr enabled",
        "bmgr enable true",
        "settings put secure backup_enable_android_studio_mode 1",
        "bmgr transport com.google.android.gms/.backup.migrate.service.D2dTransport",
        "bmgr list transports",
        "bmgr init com.google.android.gms/.backup.migrate.service.D2dTransport",
        "bmgr backupnow com.app",
        "rm -rf /sdcard/Android/data/com.google.android.gms/files/android_studio_backup_data",
        "settings put secure backup_enable_android_studio_mode 0",
        "bmgr enable false",
      )
      .inOrder()
  }

  @Test
  fun backup_assertProgress(): Unit = runBlocking {
    val backupFile = Path.of(temporaryFolder.root.path, "file.backup")
    val handler = BackupHandler(backupServices, backupFile, "com.app")

    handler.backup()

    assertThat(backupServices.getProgress())
      .containsExactly(
        "1/11: Verifying Google services",
        "2/11: Checking if BMGR is enabled",
        "3/13: Enabling BMGR",
        "4/13: Enabling test mode",
        "5/13: Setting backup transport",
        "6/14: Initializing backup transport",
        "7/14: Running backup",
        "8/14: Fetching backup",
        "9/14: Cleaning up",
        "10/14: Deleting backup directory",
        "11/14: Restoring backup transport",
        "12/14: Disabling test mode",
        "13/14: Disabling BMGR",
        "14/14: Done",
      )
      .inOrder()
  }

  @Test
  fun backup_deletesExistingFileBeforeRunning(): Unit = runBlocking {
    val backupFile = Path.of(temporaryFolder.root.path, "file.backup")
    backupFile.createFile()
    val handler = BackupHandler(backupServices, backupFile, "com.app")
    backupServices.transports = emptyList()

    handler.backup()

    assertThat(backupFile.notExists()).isTrue()
  }

  @Test
  fun backup_pullFails_deletesFile(): Unit = runBlocking {
    val backupFile = Path.of(temporaryFolder.root.path, "file.backup")
    val handler = BackupHandler(backupServices, backupFile, "com.app")
    backupServices.failSync = true

    handler.backup()

    assertThat(backupFile.notExists()).isTrue()
  }

  @Test
  fun backup_initTransportFails(): Unit = runBlocking {
    val backupFile = Path.of(temporaryFolder.root.path, "file.backup")
    val handler = BackupHandler(backupServices, backupFile, "com.app")
    backupServices.addCommandOverride(
      Throw("bmgr init com.google.android.gms/.backup.migrate.service.D2dTransport")
    )

    val result = handler.backup()

    assertThat(result).isEqualTo(TRANSPORT_INIT_FAILED.asBackupResult())
  }

  @Test
  fun backup_gmsCoreNotFound(): Unit = runBlocking {
    val backupFile = Path.of(temporaryFolder.root.path, "file.backup")
    val handler = BackupHandler(backupServices, backupFile, "com.app")
    backupServices.addCommandOverride(
      Output("dumpsys package com.google.android.gms", "dumpsys package com.google.android.gms")
    )

    val result = handler.backup()

    assertThat(result)
      .isEqualTo(GMSCORE_NOT_FOUND.asBackupResult("Google Services not found on device"))
  }

  @Test
  fun backup_backupFailed(): Unit = runBlocking {
    val backupFile = Path.of(temporaryFolder.root.path, "file.backup")
    val handler = BackupHandler(backupServices, backupFile, "com.app")
    backupServices.addCommandOverride(Output("bmgr backupnow com.app", "Error"))

    val result = handler.backup()

    assertThat(result).isEqualTo(BACKUP_FAILED.asBackupResult("Failed to backup 'com.app`: Error"))
  }
}

private fun Path.unzip(): List<Pair<String, String>> {
  ZipFile(pathString).use { zip ->
    return zip
      .entries()
      .asSequence()
      .map { it.name to zip.getInputStream(it).reader().readText() }
      .toList()
  }
}
