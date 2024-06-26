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
import com.android.backup.BackupProgressListener.Step
import com.android.backup.BackupServices.Companion.BACKUP_DIR
import com.android.tools.environment.Logger

private val TRANSPORT_COMMAND_REGEX =
  "Selected transport [^ ]+ \\(formerly (?<old>[^ ]+)\\)".toRegex()

abstract class AbstractBackupServices(
  protected val serialNumber: String,
  protected val logger: Logger,
  protected val progressListener: BackupProgressListener?,
  private var totalSteps: Int,
) : BackupServices {

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
    withBmgr { withTestMode { withTransport(transport) { block() } } }
  }

  override suspend fun deleteBackupDir() {
    reportProgress("Deleting backup directory")
    executeCommand("rm -rf $BACKUP_DIR")
  }

  override suspend fun initializeTransport(transport: String) {
    val out = executeCommand("bmgr init $transport")
    if (out.lines().last() != "Initialization result: 0") {
      throw BackupException("Failed to initialize '$transport`: $out")
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
    val oldTransport = setTransport(transport)
    if (oldTransport != transport) {
      totalSteps++
    }
    try {
      block()
    } finally {
      if (oldTransport != transport) {
        reportProgress("Restoring backup transport")
        setTransport(oldTransport)
      }
    }
  }

  private suspend fun isBmgrEnabled(): Boolean {
    return when (val out = executeCommand("bmgr enabled").trim()) {
      "Backup Manager currently enabled" -> true
      "Backup Manager currently disabled" -> false
      else -> throw BackupException("Unexpected output from 'bmgr enabled':\n$out")
    }
  }

  private suspend fun setTransport(transport: String): String {
    val out = executeCommand("bmgr transport $transport").trim()
    val result =
      TRANSPORT_COMMAND_REGEX.matchEntire(out)
        ?: throw BackupException("Unexpected result from 'bmgr transport' command: $out")

    val transports = executeCommand("bmgr list transports").lines()
    val currentTransport = transports.find { it.startsWith("  *") }?.dropPrefix("  * ")
    if (currentTransport != transport) {
      throw throw BackupException("Requested transport was not set: $out")
    }
    return result.getGroup("old")
  }

  private suspend fun enableBmgr(enabled: Boolean) {
    executeCommand("bmgr enable $enabled")
  }

  private suspend fun enableTestMode(enabled: Boolean) {
    executeCommand("settings put secure backup_enable_android_studio_mode ${if (enabled) 1 else 0}")
  }
}

/** Get a named group value. Should only throw if the regex is bad. */
private fun MatchResult.getGroup(name: String) =
  groups[name]?.value ?: throw BackupException("Group $name not found")
