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

import ai.grazie.utils.dropPrefix
import com.android.adblib.DeviceSelector
import com.android.backup.AdbServices.Companion.BACKUP_DIR
import com.android.backup.BackupProgressListener.Step
import com.android.backup.ErrorCode.BACKUP_FAILED
import com.android.backup.ErrorCode.CANNOT_ENABLE_BMGR
import com.android.backup.ErrorCode.GMSCORE_IS_TOO_OLD
import com.android.backup.ErrorCode.GMSCORE_NOT_FOUND
import com.android.backup.ErrorCode.TRANSPORT_INIT_FAILED
import com.android.backup.ErrorCode.TRANSPORT_NOT_SELECTED
import com.android.backup.ErrorCode.UNEXPECTED_ERROR
import com.android.tools.environment.Logger

private val TRANSPORT_COMMAND_REGEX =
  "Selected transport [^ ]+ \\(formerly (?<old>[^ ]+)\\)".toRegex()
private val PACKAGE_VERSION_CODE_REGEX = "^ {4}versionCode=(?<version>\\d+).*$".toRegex()

abstract class AbstractAdbServices(
  protected val serialNumber: String,
  protected val logger: Logger,
  protected val progressListener: BackupProgressListener?,
  private var totalSteps: Int,
  private var minGmsVersion: Int,
) : AdbServices {

  private var step = 0

  protected val deviceSelector = DeviceSelector.fromSerialNumber(serialNumber)

  override suspend fun reportProgress(text: String) {
    logger.info(text)
    if (step > totalSteps) {
      totalSteps = step
    }
    progressListener?.onStep(Step(++step, totalSteps, text))
  }

  override suspend fun withSetup(transport: String, block: suspend () -> Unit) {
    verifyGmsCore()
    withBmgr { withTestMode { withTransport(transport) { block() } } }
  }

  override suspend fun deleteBackupDir() {
    reportProgress("Deleting backup directory")
    executeCommand("rm -rf $BACKUP_DIR")
  }

  override suspend fun initializeTransport(transport: String) {
    val out = executeCommand("bmgr init $transport", TRANSPORT_INIT_FAILED)
    if (out.lines().last() != "Initialization result: 0") {
      throw BackupException(TRANSPORT_INIT_FAILED, "Failed to initialize '$transport`: $out")
    }
  }

  override suspend fun backupNow(applicationId: String) {
    val out = executeCommand("bmgr backupnow $applicationId", BACKUP_FAILED)
    if (out.lines().last() != "Backup finished with result: Success") {
      throw BackupException(BACKUP_FAILED, "Failed to backup '$applicationId`: $out")
    }
  }

  override suspend fun restore(token: String, applicationId: String) {
    val out = executeCommand("bmgr restore $token $applicationId", ErrorCode.RESTORE_FAILED)
    if (out.indexOf("restoreFinished: 0\n") < 0) {
      throw BackupException(ErrorCode.RESTORE_FAILED, "Error restoring app: $out")
    }
  }

  private suspend fun withBmgr(block: suspend () -> Unit) {
    reportProgress("Checking if BMGR is enabled")
    val bmgrEnabled = isBmgrEnabled()
    if (!bmgrEnabled) {
      totalSteps += 2
      reportProgress("Enabling BMGR")
      enableBmgr(true)
    }
    try {
      block()
    } finally {
      if (!bmgrEnabled) {
        reportProgress("Disabling BMGR")
        enableBmgr(false)
      }
    }
  }

  private suspend fun verifyGmsCore() {
    reportProgress("Verifying Google services")
    val lines =
      executeCommand("dumpsys package com.google.android.gms").lineSequence().dropWhile {
        it != "Packages:"
      }
    val versionMatch = lines.firstNotNullOfOrNull { PACKAGE_VERSION_CODE_REGEX.matchEntire(it) }
    if (versionMatch == null) {
      throw BackupException(GMSCORE_NOT_FOUND, "Google Services not found on device")
    }
    val versionString = versionMatch.getGroup("version")
    val version = versionString.toIntOrNull() ?: 0
    if (version < minGmsVersion) {
      throw BackupException(
        GMSCORE_IS_TOO_OLD,
        "Google Services version is too old ($versionString).  Min version is $minGmsVersion",
      )
    }
  }

  private suspend fun withTestMode(block: suspend () -> Unit) {
    reportProgress("Enabling test mode")
    enableTestMode(true)
    try {
      block()
    } finally {
      reportProgress("Disabling test mode")
      enableTestMode(false)
    }
  }

  private suspend fun withTransport(transport: String, block: suspend () -> Unit) {
    reportProgress("Setting backup transport")
    val oldTransport = setTransport(transport, verify = true)
    if (oldTransport != transport) {
      totalSteps++
    }
    try {
      block()
    } finally {
      if (oldTransport != transport) {
        reportProgress("Restoring backup transport")
        // It's possible to set to a "transport" that does not exist. If the device was already in
        // this state, trying to restore to it will result in the "transport" being set but not
        // marked as "current" (prefix of "*" in list transports).
        // In order to not fail the entire operation when this happens, we do not verify that the
        // "transport" is set when we restore it.
        setTransport(oldTransport, verify = false)
      }
    }
  }

  private suspend fun isBmgrEnabled(): Boolean {
    return when (val out = executeCommand("bmgr enabled", CANNOT_ENABLE_BMGR).trim()) {
      "Backup Manager currently enabled" -> true
      "Backup Manager currently disabled" -> false
      else ->
        throw BackupException(CANNOT_ENABLE_BMGR, "Unexpected output from 'bmgr enabled':\n$out")
    }
  }

  private suspend fun setTransport(transport: String, verify: Boolean): String {
    val out = executeCommand("bmgr transport $transport", TRANSPORT_NOT_SELECTED).trim()
    val result =
      TRANSPORT_COMMAND_REGEX.matchEntire(out)
        ?: throw BackupException(
          TRANSPORT_NOT_SELECTED,
          "Unexpected result from 'bmgr transport' command: $out",
        )

    if (verify) {
      val transports = executeCommand("bmgr list transports", TRANSPORT_NOT_SELECTED).lines()
      val currentTransport = transports.find { it.startsWith("  *") }?.dropPrefix("  * ")
      if (currentTransport != transport) {
        throw throw BackupException(TRANSPORT_NOT_SELECTED, "Requested transport was not set: $out")
      }
    }
    return result.getGroup("old")
  }

  private suspend fun enableBmgr(enabled: Boolean) {
    executeCommand("bmgr enable $enabled", CANNOT_ENABLE_BMGR)
  }

  private suspend fun enableTestMode(enabled: Boolean) {
    executeCommand("settings put secure backup_enable_android_studio_mode ${if (enabled) 1 else 0}")
  }
}

/** Get a named group value. Should only throw if the regex is bad. */
private fun MatchResult.getGroup(name: String) =
  groups[name]?.value ?: throw BackupException(UNEXPECTED_ERROR, "Group $name not found")
