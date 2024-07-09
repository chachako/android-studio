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
import com.android.backup.BackupResult.Success
import com.android.backup.BackupServices.Companion.BACKUP_DIR
import com.android.backup.BackupServices.Companion.BACKUP_METADATA_FILES
import com.android.backup.ErrorCode.INVALID_BACKUP_FILE
import com.android.backup.ErrorCode.RESTORE_FAILED
import com.android.tools.environment.Logger
import java.math.BigInteger
import java.nio.file.Path
import java.util.zip.ZipFile
import kotlin.io.path.pathString

private const val TRANSPORT = "com.google.android.gms/.backup.BackupTransportService"

/** Restores an app on a device */
class RestoreHandler
internal constructor(private val backupServices: BackupServices, private val path: Path) {

  constructor(
    adbSession: AdbSession,
    logger: Logger,
    serialNumber: String,
    progressListener: BackupProgressListener?,
    path: Path,
  ) : this(
    BackupServicesImpl(adbSession, serialNumber, logger, progressListener, NUMBER_OF_STEPS),
    path,
  )

  suspend fun restore(): BackupResult {
    return try {
      val (token, applicationId) = pushBackup()
      with(backupServices) {
        try {
          withSetup(TRANSPORT) {
            reportProgress("Restoring $applicationId")
            restore(token, applicationId)
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

  private suspend fun restore(token: String, applicationId: String) {
    with(backupServices) {
      val out = executeCommand("bmgr restore $token $applicationId", RESTORE_FAILED)
      if (out.indexOf("restoreFinished: 0\n") < 0) {
        throw BackupException(RESTORE_FAILED, "Error restoring app: $out")
      }
    }
  }

  private suspend fun pushBackup(): Metadata {
    with(backupServices) {
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

  private class Metadata(val token: String, val applicationId: String) {

    operator fun component1(): String = token

    operator fun component2(): String = applicationId
  }

  companion object {

    const val NUMBER_OF_STEPS = 9

    /**
     * Verifies a backup file is valid and returns the application id of the associated app
     *
     * @param backupFile The path of a backup file to validate
     * @return The application id of the associated app
     * @throws Exception `backupFile` is not valid
     */
    fun validateBackupFile(backupFile: Path): String {
      ZipFile(backupFile.pathString).use { zip ->
        val applicationId = zip.getApplicationId()
        zip.getRestoreToken()
        val filenames = zip.entries().asSequence().mapTo(mutableSetOf()) { it.name }
        if (filenames != BACKUP_METADATA_FILES + applicationId) {
          throw BackupException(
            INVALID_BACKUP_FILE,
            "File is not a valid backup file: ${backupFile.pathString} ($filenames)",
          )
        }
        return applicationId
      }
    }
  }
}

private fun ZipFile.getRestoreToken(): String {
  return try {
    BigInteger(getInputStream(getEntry("restore_token_file")).reader().readText()).toString(16)
  } catch (e: Exception) {
    throw BackupException(
      INVALID_BACKUP_FILE,
      "Backup file does not contain a valid token: $name",
      e,
    )
  }
}

private fun ZipFile.getApplicationId(): String {
  return try {
    entries().asSequence().map { it.name }.first { it !in BACKUP_METADATA_FILES }
  } catch (e: Exception) {
    throw BackupException(
      INVALID_BACKUP_FILE,
      "Backup file does not contain an application file: $name",
      e,
    )
  }
}
