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

import com.android.backup.AdbServices.Companion.BACKUP_DIR
import com.android.backup.AdbServices.Companion.BACKUP_METADATA_FILES
import com.android.backup.BackupResult.Success
import com.android.backup.BackupService.Companion.getApplicationId
import com.android.backup.BackupService.Companion.getRestoreToken
import java.io.OutputStream
import java.nio.file.Path
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream
import kotlin.io.path.deleteIfExists
import kotlin.io.path.outputStream
import kotlin.io.path.pathString

private const val TRANSPORT_D2D = "com.google.android.gms/.backup.migrate.service.D2dTransport"
private const val TRANSPORT_CLOUD = "com.google.android.gms/.backup.BackupTransportService"

internal class BackupServiceImpl(private val factory: AdbServicesFactory) : BackupService {

  override suspend fun backup(
    serialNumber: String,
    applicationId: String,
    backupFile: Path,
    listener: BackupProgressListener?,
  ): BackupResult {
    val adbServices = factory.createAdbServices(serialNumber, listener, BACKUP_STEPS)
    return try {
      with(adbServices) {
        withSetup(TRANSPORT_D2D) {
          reportProgress("Initializing backup transport")
          initializeTransport(TRANSPORT_D2D)

          reportProgress("Running backup")
          adbServices.backupNow(applicationId)

          reportProgress("Fetching backup")
          pullBackup(adbServices, applicationId, backupFile)

          reportProgress("Cleaning up")
          deleteBackupDir()
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
      val (token, applicationId) = pushBackup(adbServices, backupFile)
      with(adbServices) {
        try {
          withSetup(TRANSPORT_CLOUD) {
            reportProgress("Restoring $applicationId")
            adbServices.restore(token, applicationId)
          }
        } finally {
          deleteBackupDir()
        }
        reportProgress("Done")
        Success
      }
    } catch (e: Throwable) {
      e.toBackupResult()
    }
  }

  private suspend fun pullBackup(
    adbServices: AdbServices,
    applicationId: String,
    backupFile: Path,
  ) {
    ZipOutputStream(backupFile.outputStream()).use { zip ->
      (BACKUP_METADATA_FILES + applicationId).forEach {
        zip.putNextEntry(ZipEntry(it))
        adbServices.syncRecv(KeepOpenOutputStream(zip), "$BACKUP_DIR/$it")
      }
    }
  }

  private suspend fun pushBackup(adbServices: AdbServices, path: Path): Metadata {
    with(adbServices) {
      reportProgress("Pushing backup file")
      ZipFile(path.pathString).use { zip ->
        val token = zip.getRestoreToken()
        val applicationId = zip.getApplicationId()
        zip.entries().asSequence().forEach {
          syncSend(zip.getInputStream(it), "$BACKUP_DIR/${it.name}")
        }
        return Metadata(token, applicationId)
      }
    }
  }

  /** A [OutputStream] wrapper that doesn't close the underlying stream. */
  private class KeepOpenOutputStream(private val delegate: OutputStream) : OutputStream() {

    override fun write(b: Int) {
      delegate.write(b)
    }

    override fun close() {
      // Do not close
    }
  }

  private class Metadata(val token: String, val applicationId: String) {

    operator fun component1(): String = token

    operator fun component2(): String = applicationId
  }

  companion object {
    const val BACKUP_STEPS = 11
    const val RESTORE_STEPS = 9
  }
}
