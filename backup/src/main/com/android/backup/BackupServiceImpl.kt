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
import com.android.backup.BackupService.Companion.APP_DATA_FILE
import com.android.backup.BackupService.Companion.APP_ID
import com.android.backup.BackupService.Companion.BACKUP_FILES
import com.android.backup.BackupService.Companion.PM_DATA_FILE
import com.android.backup.BackupService.Companion.TOKEN_FILE
import com.android.backup.BackupService.Companion.getApplicationId
import com.android.backup.BackupService.Companion.getRestoreToken
import io.ktor.utils.io.core.toByteArray
import kotlinx.coroutines.withContext
import java.nio.file.Path
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream
import kotlin.io.path.deleteIfExists
import kotlin.io.path.outputStream
import kotlin.io.path.pathString

private const val TRANSPORT_DTD = "com.google.android.gms/.backup.migrate.service.D2dTransport"
private const val TRANSPORT_CLOUD = "com.google.android.gms/.backup.BackupTransportService"
private const val CONTENT_URI =
  "content://com.google.android.gms.fileprovider/android_studio_backup_data/"

internal class BackupServiceImpl(private val factory: AdbServicesFactory) : BackupService {

  override suspend fun backup(
    serialNumber: String,
    applicationId: String,
    type: BackupType,
    backupFile: Path,
    listener: BackupProgressListener?,
  ): BackupResult {
    val adbServices = factory.createAdbServices(serialNumber, listener, BACKUP_STEPS)
    return try {
      with(adbServices) {
        // Backup is always handled by the D2D transport
        val transport = TRANSPORT_DTD
        withSetup(transport) {
          reportProgress("Initializing backup transport")
          initializeTransport(transport)
          try {
            reportProgress("Running backup")
            adbServices.backupNow(applicationId)
            reportProgress("Fetching backup")
            pullBackup(adbServices, applicationId, backupFile)
          } finally {
            reportProgress("Cleaning up")
          }
        }
        reportProgress("Done")
      }
      Success
    } catch (e: Throwable) {
      backupFile.deleteIfExists()
      e.toBackupResult()
    }
  }

  override suspend fun restore(
    serialNumber: String,
    backupFile: Path,
    listener: BackupProgressListener?,
  ): BackupResult {
    return try {
      val adbServices = factory.createAdbServices(serialNumber, listener, RESTORE_STEPS)
      with(adbServices) {
        // Restore is always handled by the Cloud transport
        withSetup(TRANSPORT_CLOUD) {
          reportProgress("Initializing backup transport")
          initializeTransport(TRANSPORT_CLOUD)
          reportProgress("Pushing backup file")
          val (token, applicationId) = pushBackup(adbServices, backupFile)
          reportProgress("Restoring $applicationId")
          restore(token, applicationId)
        }
        reportProgress("Done")
        Success
      }
    } catch (e: Throwable) {
      e.toBackupResult()
    }
  }

  override suspend fun sendUpdateGmsIntent(serialNumber: String): BackupResult {
    return try {
      factory.createAdbServices(serialNumber, null, 1).sendUpdateGmsIntent()
      Success
    } catch (e: Throwable) {
      e.toBackupResult()
    }
  }

  override suspend fun getForegroundApplicationId(serialNumber: String): String {
    return factory.createAdbServices(serialNumber, null, 1).getForegroundApplicationId()
  }

  override suspend fun isInstalled(serialNumber: String, applicationId: String): Boolean {
    return factory.createAdbServices(serialNumber, null, 1).isInstalled(serialNumber)
  }

  private suspend fun pullBackup(
    adbServices: AdbServices,
    applicationId: String,
    backupFile: Path,
  ) {
    ZipOutputStream(backupFile.outputStream()).use { zip ->
      zip.putContent(adbServices, TOKEN_FILE)
      zip.putContent(adbServices, PM_DATA_FILE)
      zip.putContent(adbServices, APP_DATA_FILE)
      zip.putNextEntry(ZipEntry(APP_ID))
      zip.write(applicationId.toByteArray())
    }
  }

  private suspend fun pushBackup(adbServices: AdbServices, path: Path): Metadata {
    with(adbServices) {
      ZipFile(path.pathString).use { zip ->
        val token = zip.getRestoreToken()
        val applicationId = zip.getApplicationId()
        BACKUP_FILES.forEach {
          writeContent(zip.getInputStream(zip.getEntry(it)), CONTENT_URI + it)
        }
        return Metadata(token, applicationId)
      }
    }
  }

  private suspend fun ZipOutputStream.putContent(
    adbServices: AdbServices,
    name: String,
  ) {
    withContext(adbServices.ioContext) {
      putNextEntry(ZipEntry(name))
    }
    adbServices.readContent(this@putContent, CONTENT_URI + name)
  }

  private class Metadata(val token: String, val applicationId: String) {

    operator fun component1(): String = token

    operator fun component2(): String = applicationId
  }

  companion object {
    const val BACKUP_STEPS = 10
    const val RESTORE_STEPS = 9
  }
}
