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

import com.android.backup.BackupResult.Error
import com.android.backup.BackupResult.Success
import com.android.backup.BackupType.CLOUD
import com.android.backup.BackupType.DEVICE_TO_DEVICE
import com.android.backup.ErrorCode.BACKUP_FAILED
import com.android.backup.ErrorCode.BACKUP_NOT_ALLOWED
import com.android.backup.ErrorCode.CANNOT_ENABLE_BMGR
import com.android.backup.ErrorCode.GMSCORE_NOT_FOUND
import com.android.backup.ErrorCode.INVALID_BACKUP_FILE
import com.android.backup.ErrorCode.RESTORE_FAILED
import com.android.backup.ErrorCode.TRANSPORT_INIT_FAILED
import com.android.backup.ErrorCode.TRANSPORT_NOT_SELECTED
import com.android.backup.testing.FakeAdbServices
import com.android.backup.testing.FakeAdbServices.CommandOverride.Output
import com.android.backup.testing.FakeAdbServices.CommandOverride.Throw
import com.android.backup.testing.asBackupResult
import com.google.common.truth.Truth.assertThat
import com.jetbrains.rd.generator.nova.fail
import java.nio.file.Path
import java.util.zip.ZipEntry
import java.util.zip.ZipException
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream
import kotlin.io.path.createFile
import kotlin.io.path.exists
import kotlin.io.path.notExists
import kotlin.io.path.outputStream
import kotlin.io.path.pathString
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertThrows
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

private const val TRANSPORT_NOT_SET_MESSAGE =
  "Requested transport was not set: Selected transport com.google.android.gms/.backup.BackupTransportService (formerly com.google.android.gms/.backup.BackupTransportService)"

class BackupServiceImplTest {
  @get:Rule val temporaryFolder = TemporaryFolder()

  @Test
  fun backup_d2d(): Unit = runBlocking {
    val backupFile = Path.of(temporaryFolder.root.path, "file.backup")
    val adbServicesFactory = FakeAdbServicesFactory()
    val backupService = BackupServiceImpl(adbServicesFactory)

    val result = backupService.backup("serial", "com.app", DEVICE_TO_DEVICE, backupFile, null)

    val adbServices = adbServicesFactory.adbServices
    assertThat(result).isEqualTo(Success)
    assertThat(adbServices.getCommands())
      .containsExactly(
        "dumpsys package com.google.android.gms",
        "bmgr enabled",
        "bmgr enable true",
        "settings put secure backup_enable_android_studio_mode 1",
        "bmgr transport com.google.android.gms/.backup.migrate.service.D2dTransport",
        "bmgr list transports",
        "bmgr init com.google.android.gms/.backup.migrate.service.D2dTransport",
        "bmgr backupnow @pm@ com.app --non-incremental",
        "bmgr transport com.google.android.gms/.backup.BackupTransportService",
        "settings put secure backup_enable_android_studio_mode 0",
        "bmgr enable false",
      )
      .inOrder()
    assertThat(adbServices.testMode).isEqualTo(0)
    assertThat(backupFile.exists()).isTrue()
    assertThat(backupFile.unzip())
      .containsExactly(
        "pm_backup_data" to "content://com.google.android.gms.fileprovider/android_studio_backup_data/pm_backup_data",
        "restore_token_file" to "content://com.google.android.gms.fileprovider/android_studio_backup_data/restore_token_file",
        "app_backup_data" to "content://com.google.android.gms.fileprovider/android_studio_backup_data/app_backup_data",
        "app_id" to "com.app",
      )
  }

  @Test
  fun backup_cloud(): Unit = runBlocking {
    val backupFile = Path.of(temporaryFolder.root.path, "file.backup")
    val adbServicesFactory = FakeAdbServicesFactory()
    val backupService = BackupServiceImpl(adbServicesFactory)

    val result = backupService.backup("serial", "com.app", CLOUD, backupFile, null)

    val adbServices = adbServicesFactory.adbServices
    assertThat(result).isEqualTo(Success)
    assertThat(adbServices.getCommands())
      .containsExactly(
        "dumpsys package com.google.android.gms",
        "bmgr enabled",
        "bmgr enable true",
        "settings put secure backup_enable_android_studio_mode 1",
        "bmgr transport com.google.android.gms/.backup.migrate.service.D2dTransport",
        "bmgr list transports",
        "bmgr init com.google.android.gms/.backup.migrate.service.D2dTransport",
        "bmgr backupnow @pm@ com.app --non-incremental",
        "bmgr transport com.google.android.gms/.backup.BackupTransportService",
        "settings put secure backup_enable_android_studio_mode 0",
        "bmgr enable false",
      )
      .inOrder()
    assertThat(adbServices.testMode).isEqualTo(0)
    assertThat(backupFile.exists()).isTrue()
    assertThat(backupFile.unzip())
      .containsExactly(
        "pm_backup_data" to "content://com.google.android.gms.fileprovider/android_studio_backup_data/pm_backup_data",
        "restore_token_file" to "content://com.google.android.gms.fileprovider/android_studio_backup_data/restore_token_file",
        "app_backup_data" to "content://com.google.android.gms.fileprovider/android_studio_backup_data/app_backup_data",
        "app_id" to "com.app",
      )
  }

  @Test
  fun backup_bmgrAlreadyEnabled(): Unit = runBlocking {
    val backupFile = Path.of(temporaryFolder.root.path, "file.backup")
    val adbServicesFactory = FakeAdbServicesFactory { it.bmgrEnabled = true }
    val backupServices = BackupServiceImpl(adbServicesFactory)

    val result = backupServices.backup("serial", "com.app", DEVICE_TO_DEVICE, backupFile, null)

    val adbServices = adbServicesFactory.adbServices
    assertThat(result).isEqualTo(Success)
    assertThat(adbServices.getCommands())
      .containsExactly(
        "dumpsys package com.google.android.gms",
        "bmgr enabled",
        "settings put secure backup_enable_android_studio_mode 1",
        "bmgr transport com.google.android.gms/.backup.migrate.service.D2dTransport",
        "bmgr list transports",
        "bmgr init com.google.android.gms/.backup.migrate.service.D2dTransport",
        "bmgr backupnow @pm@ com.app --non-incremental",
        "bmgr transport com.google.android.gms/.backup.BackupTransportService",
        "settings put secure backup_enable_android_studio_mode 0",
      )
      .inOrder()
  }

  @Test
  fun backup_transportAlreadySelected(): Unit = runBlocking {
    val backupFile = Path.of(temporaryFolder.root.path, "file.backup")
    val adbServicesFactory = FakeAdbServicesFactory {
      it.activeTransport = "com.google.android.gms/.backup.migrate.service.D2dTransport"
    }
    val backupService = BackupServiceImpl(adbServicesFactory)

    val result = backupService.backup("serial", "com.app", DEVICE_TO_DEVICE, backupFile, null)

    val adbServices = adbServicesFactory.adbServices
    assertThat(result).isEqualTo(Success)
    assertThat(adbServices.getCommands())
      .containsExactly(
        "dumpsys package com.google.android.gms",
        "bmgr enabled",
        "bmgr enable true",
        "settings put secure backup_enable_android_studio_mode 1",
        "bmgr transport com.google.android.gms/.backup.migrate.service.D2dTransport",
        "bmgr list transports",
        "bmgr init com.google.android.gms/.backup.migrate.service.D2dTransport",
        "bmgr backupnow @pm@ com.app --non-incremental",
        "settings put secure backup_enable_android_studio_mode 0",
        "bmgr enable false",
      )
      .inOrder()
  }

  @Test
  fun backup_assertProgress(): Unit = runBlocking {
    val backupFile = Path.of(temporaryFolder.root.path, "file.backup")
    val adbServicesFactory = FakeAdbServicesFactory()
    val backupService = BackupServiceImpl(adbServicesFactory)

    backupService.backup("serial", "com.app", DEVICE_TO_DEVICE, backupFile, null)

    val adbServices = adbServicesFactory.adbServices
    assertThat(adbServices.getProgress())
      .containsExactly(
        "1/10: Verifying Google services",
        "2/10: Checking if BMGR is enabled",
        "3/12: Enabling BMGR",
        "4/12: Enabling test mode",
        "5/12: Setting backup transport",
        "6/13: Initializing backup transport",
        "7/13: Running backup",
        "8/13: Fetching backup",
        "9/13: Cleaning up",
        "10/13: Restoring backup transport",
        "11/13: Disabling test mode",
        "12/13: Disabling BMGR",
        "13/13: Done",
      )
      .inOrder()
  }

  @Test
  fun backup_deletesExistingFileBeforeRunning(): Unit = runBlocking {
    val backupFile = Path.of(temporaryFolder.root.path, "file.backup")
    backupFile.createFile()
    val backupService = BackupServiceImpl(FakeAdbServicesFactory { it.transports = emptyList() })

    backupService.backup("serial", "com.app", DEVICE_TO_DEVICE, backupFile, null)

    assertThat(backupFile.notExists()).isTrue()
  }

  @Test
  fun backup_pullFails_deletesFile(): Unit = runBlocking {
    val backupFile = Path.of(temporaryFolder.root.path, "file.backup")
    val backupService = BackupServiceImpl(FakeAdbServicesFactory { it.failReadWriteContent = true })

    backupService.backup("serial", "com.app", DEVICE_TO_DEVICE, backupFile, null)

    assertThat(backupFile.notExists()).isTrue()
  }

  @Test
  fun backup_initTransportFails(): Unit = runBlocking {
    val backupFile = Path.of(temporaryFolder.root.path, "file.backup")
    val backupService =
      BackupServiceImpl(
        FakeAdbServicesFactory {
          it.addCommandOverride(
            Throw("bmgr init com.google.android.gms/.backup.migrate.service.D2dTransport")
          )
        }
      )

    val result = backupService.backup("serial", "com.app", DEVICE_TO_DEVICE, backupFile, null)

    assertThat(result).isEqualTo(TRANSPORT_INIT_FAILED.asBackupResult())
  }

  @Test
  fun backup_gmsCoreNotFound(): Unit = runBlocking {
    val backupFile = Path.of(temporaryFolder.root.path, "file.backup")
    val backupService =
      BackupServiceImpl(
        FakeAdbServicesFactory {
          it.addCommandOverride(
            Output(
              "dumpsys package com.google.android.gms",
              "dumpsys package com.google.android.gms",
            )
          )
        }
      )

    val result = backupService.backup("serial", "com.app", DEVICE_TO_DEVICE, backupFile, null)

    assertThat(result)
      .isEqualTo(GMSCORE_NOT_FOUND.asBackupResult("Google Services not found on device"))
  }

  @Test
  fun backup_backupFailed(): Unit = runBlocking {
    val backupFile = Path.of(temporaryFolder.root.path, "file.backup")
    val backupService =
      BackupServiceImpl(
        FakeAdbServicesFactory { it.addCommandOverride(Output("bmgr backupnow @pm@ com.app --non-incremental", "Error")) }
      )

    val result = backupService.backup("serial", "com.app", DEVICE_TO_DEVICE, backupFile, null)

    assertThat(result).isEqualTo(BACKUP_FAILED.asBackupResult("Failed to backup 'com.app`: Error"))
  }

  @Test
  fun backup_backupNotAllowed(): Unit = runBlocking {
    val backupFile = Path.of(temporaryFolder.root.path, "file.backup")
    val backupService =
      BackupServiceImpl(
        FakeAdbServicesFactory {
          it.addCommandOverride(
            Output("bmgr backupnow @pm@ com.app --non-incremental", "Package com.app with result: Backup is not allowed")
          )
        }
      )

    val result = backupService.backup("serial", "com.app", DEVICE_TO_DEVICE, backupFile, null)

    assertThat(result)
      .isEqualTo(BACKUP_NOT_ALLOWED.asBackupResult("Backup of 'com.app` is not allowed"))
  }

  @Test
  fun restore(): Unit = runBlocking {
    val backupFile = createBackupFile("com.app", "11223344556677889900")
    val adbServicesFactory = FakeAdbServicesFactory()
    val backupService = BackupServiceImpl(adbServicesFactory)

    val result = backupService.restore("serial", backupFile, null)

    val adbServices = adbServicesFactory.adbServices
    assertThat(result).isEqualTo(Success)
    assertThat(adbServices.getCommands())
      .containsExactly(
        "dumpsys package com.google.android.gms",
        "bmgr enabled",
        "bmgr enable true",
        "settings put secure backup_enable_android_studio_mode 1",
        "bmgr transport com.google.android.gms/.backup.BackupTransportService",
        "bmgr list transports",
        "bmgr init com.google.android.gms/.backup.BackupTransportService",
        "bmgr restore 9bc1546914997f6c com.app",
        "settings put secure backup_enable_android_studio_mode 0",
        "bmgr enable false",
      )
      .inOrder()
    assertThat(adbServices.testMode).isEqualTo(0)
  }

  @Test
  fun restore_bmgrAlreadyEnabled(): Unit = runBlocking {
    val backupFile = createBackupFile("com.app", "11223344556677889900")
    val adbServicesFactory = FakeAdbServicesFactory { it.bmgrEnabled = true }
    val backupService = BackupServiceImpl(adbServicesFactory)

    val result = backupService.restore("serial", backupFile, null)

    val adbServices = adbServicesFactory.adbServices
    (result as? Error)?.throwable?.printStackTrace()
    assertThat(result).isEqualTo(Success)
    assertThat(adbServices.getCommands())
      .containsExactly(
        "dumpsys package com.google.android.gms",
        "bmgr enabled",
        "settings put secure backup_enable_android_studio_mode 1",
        "bmgr transport com.google.android.gms/.backup.BackupTransportService",
        "bmgr list transports",
        "bmgr init com.google.android.gms/.backup.BackupTransportService",
        "bmgr restore 9bc1546914997f6c com.app",
        "settings put secure backup_enable_android_studio_mode 0",
      )
      .inOrder()
  }

  @Test
  fun restore_transportNotSet(): Unit = runBlocking {
    val backupFile = createBackupFile("com.app", "11223344556677889900")
    val adbServicesFactory = FakeAdbServicesFactory {
      it.activeTransport = "com.android.localtransport/.LocalTransport"
    }
    val backupService = BackupServiceImpl(adbServicesFactory)

    backupService.restore("serial", backupFile, null)

    val adbServices = adbServicesFactory.adbServices
    assertThat(adbServices.getCommands())
      .containsExactly(
        "dumpsys package com.google.android.gms",
        "bmgr enabled",
        "bmgr enable true",
        "settings put secure backup_enable_android_studio_mode 1",
        "bmgr transport com.google.android.gms/.backup.BackupTransportService",
        "bmgr list transports",
        "bmgr init com.google.android.gms/.backup.BackupTransportService",
        "bmgr restore 9bc1546914997f6c com.app",
        "bmgr transport com.android.localtransport/.LocalTransport",
        "settings put secure backup_enable_android_studio_mode 0",
        "bmgr enable false",
      )
      .inOrder()
  }

  @Test
  fun restore_assertProgress(): Unit = runBlocking {
    val backupFile = createBackupFile("com.app", "11223344556677889900")
    val adbServicesFactory = FakeAdbServicesFactory()
    val backupService = BackupServiceImpl(adbServicesFactory)

    backupService.restore("serial", backupFile, null)

    val adbServices = adbServicesFactory.adbServices
    assertThat(adbServices.getProgress())
      .containsExactly(
        "1/9: Verifying Google services",
        "2/9: Checking if BMGR is enabled",
        "3/11: Enabling BMGR",
        "4/11: Enabling test mode",
        "5/11: Setting backup transport",
        "6/11: Initializing backup transport",
        "7/11: Pushing backup file",
        "8/11: Restoring com.app",
        "9/11: Disabling test mode",
        "10/11: Disabling BMGR",
        "11/11: Done",
      )
      .inOrder()
  }

  @Test
  fun validateBackupFile() {
    BackupService.validateBackupFile(createBackupFile("com.app", "11223344556677889900"))
  }

  @Test
  fun validateBackupFile_noApplicationId() {
    assertThrows(BackupException::class.java) {
      BackupService.validateBackupFile(
        createZipFile(FileInfo("@pm@", ""), FileInfo("restore_token_file", "11223344556677889900"))
      )
    }
  }

  @Test
  fun validateBackupFile_invalidToken() {
    assertThrows(BackupException::class.java) {
      BackupService.validateBackupFile(createBackupFile("com.app", "foobar"))
    }
  }

  @Test
  fun validateBackupFile_notZipFile() {
    assertThrows(ZipException::class.java) {
      val path = Path.of(temporaryFolder.root.path, "file.backup").createFile()
      BackupService.validateBackupFile(path)
    }
  }

  @Test
  fun validateBackupFile_unexpectedFile() {
    assertThrows(BackupException::class.java) {
      BackupService.validateBackupFile(
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
      BackupService.validateBackupFile(
        createZipFile(
          FileInfo("restore_token_file", "11223344556677889900"),
          FileInfo("com.app", ""),
        )
      )
    }
  }

  @Test
  fun restore_enableBmgrFails(): Unit = runBlocking {
    val backupFile = createBackupFile("com.app", "11223344556677889900")
    val backupService =
      BackupServiceImpl(FakeAdbServicesFactory { it.addCommandOverride(Throw("bmgr enabled")) })

    val result = backupService.restore("serial", backupFile, null)

    assertThat(result).isEqualTo(CANNOT_ENABLE_BMGR.asBackupResult())
  }

  @Test
  fun restore_setTransportFails(): Unit = runBlocking {
    val backupFile = createBackupFile("com.app", "11223344556677889900")
    val backupService =
      BackupServiceImpl(
        FakeAdbServicesFactory { it.addCommandOverride(Output("bmgr list transports", "")) }
      )

    val result = backupService.restore("serial", backupFile, null)

    assertThat(result).isEqualTo(TRANSPORT_NOT_SELECTED.asBackupResult(TRANSPORT_NOT_SET_MESSAGE))
  }

  @Test
  fun restore_invalidBackupFile(): Unit = runBlocking {
    val backupFile = createZipFile(FileInfo("some-file", ""))

    val backupService = BackupServiceImpl(FakeAdbServicesFactory())

    val error =
      backupService.restore("serial", backupFile, null) as? Error ?: fail("Expected an Error")

    assertThat(error.errorCode).isEqualTo(INVALID_BACKUP_FILE)
    assertThat(error.throwable.message)
      .isEqualTo("Backup file does not contain a valid token: ${backupFile.pathString}")
  }

  @Test
  fun restore_restoreFailed(): Unit = runBlocking {
    val backupFile = createBackupFile("com.app", "11223344556677889900")
    val backupService =
      BackupServiceImpl(
        FakeAdbServicesFactory {
          it.addCommandOverride(Output("bmgr restore 9bc1546914997f6c com.app", "Error"))
        }
      )

    val result = backupService.restore("serial", backupFile, null)

    assertThat(result).isEqualTo(RESTORE_FAILED.asBackupResult("Error restoring app: Error"))
  }

  @Suppress("SameParameterValue")
  private fun createBackupFile(applicationId: String, token: String) =
    createZipFile(
      FileInfo("pm_backup_data", ""),
      FileInfo("app_backup_data", ""),
      FileInfo("restore_token_file", token),
      FileInfo("app_id", applicationId),
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

  private class FakeAdbServicesFactory(private val configure: (FakeAdbServices) -> Unit = {}) :
    AdbServicesFactory {
    lateinit var adbServices: FakeAdbServices

    override fun createAdbServices(
      serialNumber: String,
      listener: BackupProgressListener?,
      steps: Int,
    ): AdbServices {
      adbServices = FakeAdbServices(serialNumber, steps)
      configure(adbServices)
      return adbServices
    }
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
