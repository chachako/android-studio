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
import java.util.zip.ZipFile
import kotlin.io.path.exists
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

    handler.backup()

    assertThat(backupServices.getCommands())
      .containsExactly(
        "bmgr enabled",
        "bmgr enable true",
        "settings put secure backup_enable_android_studio_mode 1",
        "bmgr transport com.google.android.gms/.backup.migrate.service.D2dTransport",
        "bmgr list transports",
        "bmgr init com.google.android.gms/.backup.migrate.service.D2dTransport",
        "bmgr backupnow com.app",
        "rm -rf /sdcard/Android/data/com.google.android.gms/files/android_studio_backup_data",
        "bmgr transport com.google.android.gms/.backup.BackupTransportService",
        "bmgr list transports",
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

    handler.backup()

    assertThat(backupServices.getCommands())
      .containsExactly(
        "bmgr enabled",
        "settings put secure backup_enable_android_studio_mode 1",
        "bmgr transport com.google.android.gms/.backup.migrate.service.D2dTransport",
        "bmgr list transports",
        "bmgr init com.google.android.gms/.backup.migrate.service.D2dTransport",
        "bmgr backupnow com.app",
        "rm -rf /sdcard/Android/data/com.google.android.gms/files/android_studio_backup_data",
        "bmgr transport com.google.android.gms/.backup.BackupTransportService",
        "bmgr list transports",
        "settings put secure backup_enable_android_studio_mode 0",
      )
      .inOrder()
  }

  @Test
  fun backup_transportAlreadySelected(): Unit = runBlocking {
    val backupFile = Path.of(temporaryFolder.root.path, "file.backup")
    val handler = BackupHandler(backupServices, backupFile, "com.app")
    backupServices.activeTransport = "com.google.android.gms/.backup.migrate.service.D2dTransport"

    handler.backup()

    assertThat(backupServices.getCommands())
      .containsExactly(
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
        "1/10: Checking if BMGR is enabled",
        "2/12: Enabling BMGR",
        "3/12: Enabling test mode",
        "4/12: Setting backup transport",
        "5/13: Initializing backup transport",
        "6/13: Running backup",
        "7/13: Fetching backup",
        "8/13: Cleaning up",
        "9/13: Deleting backup directory",
        "10/13: Restoring backup transport",
        "11/13: Disabling test mode",
        "12/13: Disabling BMGR",
        "13/13: Done",
      )
      .inOrder()
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
