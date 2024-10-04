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
import com.android.adblib.OutputStreamCollector
import com.android.adblib.ShellCommandOutput
import com.android.adblib.TextShellV2Collector
import com.android.adblib.shellCommand
import com.android.adblib.withTextCollector
import com.android.backup.AdbServices.AdbOutput
import com.android.tools.environment.Logger
import kotlinx.coroutines.flow.first
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream

/** Provides backup services for a specific device */
internal class AdbServicesImpl(
  private val adbSession: AdbSession,
  serialNumber: String,
  logger: Logger,
  progressListener: BackupProgressListener?,
  totalSteps: Int,
  minGmsVersion: Int,
) : AbstractAdbServices(serialNumber, logger, progressListener, totalSteps, minGmsVersion) {

  override val ioContext = adbSession.ioDispatcher

  override suspend fun executeCommand(command: String, errorCode: ErrorCode): AdbOutput {
    val output =
      adbSession.deviceServices
        .shellCommand(deviceSelector, command)
        .withCollector(TextShellV2Collector())
        .execute()
        .first()

    if (logger.isDebugEnabled) {
      logger.debug("Executed on `$serialNumber`: '$command' ${output.describe()}")
      // Log each line separately, so it shows up in the log file with the complete prefix
      output.describe().lines().filter { it.isNotEmpty() }.forEach { logger.debug("  $it") }
    }
    if (output.exitCode != 0) {
      throw BackupException(
        errorCode,
        "Failed to run '$command' on $serialNumber\n${output.describe()}",
      )
    }
    return AdbOutput(output.stdout.trimEnd('\n'), output.stderr.trimEnd('\n'))
  }

  override suspend fun readContent(outputStream: OutputStream, uri: String) {
    val stderrStream = ByteArrayOutputStream()
    stderrStream.use {
      adbSession.deviceServices
        .shellCommand(deviceSelector, "content read --uri $uri")
        .withCollector(OutputStreamCollector(adbSession, outputStream, stderrStream))
        .execute()
        .first()
    }
    val stderr = stderrStream.toString()
    if (stderr.isNotEmpty()) {
      throw BackupException(ErrorCode.READ_CONTENT_FAILED, "Error reading content $uri: $stderr")
    }
  }

  override suspend fun writeContent(inputStream: InputStream, uri: String) {
    val output = adbSession.deviceServices.shellCommand(deviceSelector, "content write --uri $uri")
      .withStdin(adbSession.channelFactory.wrapInputStream(inputStream))
      .withTextCollector()
      .execute().first()
    val stderr = output.stderr
    if (stderr.isNotEmpty()) {
      throw BackupException(ErrorCode.READ_CONTENT_FAILED, "Error writing content $uri: $stderr")
    }
  }
}

private fun ShellCommandOutput.describe() = buildString {
  append("Exit code: $exitCode\n")
  if (stdout.isNotEmpty()) {
    append("Stdout:\n${stdout}\n")
  }
  if (stderr.isNotEmpty()) {
    append("Stdout:\n${stderr}\n")
  }
}
