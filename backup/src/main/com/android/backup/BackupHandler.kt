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

import com.android.adblib.AdbSession
import com.android.backup.BackupServices.Companion.BACKUP_DIR
import com.android.backup.BackupServices.Companion.BACKUP_METADATA_FILES
import com.android.tools.environment.Logger
import java.io.OutputStream
import java.nio.file.Path
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.io.path.outputStream

private const val TRANSPORT = "com.google.android.gms/.backup.migrate.service.D2dTransport"

/** Performs an app backup on a device */
class BackupHandler
internal constructor(
  private val backupServices: BackupServices,
  private val path: Path,
  private val applicationId: String,
) {

  constructor(
    adbSession: AdbSession,
    serialNumber: String,
    logger: Logger,
    progressListener: BackupProgressListener?,
    path: Path,
    applicationId: String,
  ) : this(
    BackupServicesImpl(adbSession, serialNumber, logger, progressListener),
    path,
    applicationId,
  )

  suspend fun backup() {
    with(backupServices) {
      withSetup(TRANSPORT) {
        reportProgress("Initializing backup transport")
        initializeTransport(TRANSPORT)

        reportProgress("Running backup")
        doBackup()

        reportProgress("Fetching backup")
        pullBackup()

        reportProgress("Cleaning up")
        deleteBackupDir()
      }
      reportProgress("Done")
    }
  }

  private suspend fun doBackup() {
    with(backupServices) {
      val out = executeCommand("bmgr backupnow $applicationId")
      if (out.lines().last() != "Backup finished with result: Success") {
        throw BackupException("Failed to backup '$applicationId`: $out")
      }
    }
  }

  private suspend fun pullBackup() {
    ZipOutputStream(path.outputStream()).use { zip ->
      (BACKUP_METADATA_FILES + applicationId).forEach {
        zip.putNextEntry(ZipEntry(it))
        backupServices.syncRecv(KeepOpenOutputStream(zip), "$BACKUP_DIR/$it")
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
}
