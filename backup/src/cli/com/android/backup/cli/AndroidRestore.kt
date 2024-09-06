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

package com.android.backup.cli

import com.android.adblib.tools.createStandaloneSession
import com.android.backup.BackupResult
import com.android.backup.BackupService
import com.android.backup.cli.CommandLineOptions.createCommonOptions
import com.android.backup.cli.CommandLineOptions.getBackupProgressListener
import com.android.backup.cli.CommandLineOptions.getDeviceSelector
import com.android.backup.cli.CommandLineOptions.help
import com.android.tools.environment.log.NoopLogger
import java.nio.file.Path
import kotlinx.coroutines.runBlocking
import org.apache.commons.cli.DefaultParser
import org.apache.commons.cli.HelpFormatter

private const val USAGE_TITLE = "android-restore [options] <INPUT-FILE>"

object AndroidRestore {

  @JvmStatic
  fun main(args: Array<String>) {
    val options = createCommonOptions()

    val commandLine =
      try {
        val commandLine = DefaultParser().parse(options, args)
        if (commandLine.hasOption(help) || commandLine.args.count() != 1) {
          HelpFormatter().printHelp(USAGE_TITLE, options)
          return
        }
        commandLine
      } catch (e: Exception) {
        HelpFormatter().printHelp(USAGE_TITLE, options)
        return
      }
    val file = commandLine.args.first()

    val deviceSelector = commandLine.getDeviceSelector()
    val applicationId = BackupService.getApplicationId(Path.of(file))
    val adbSession = createStandaloneSession(AdbNoopLoggerFactory())

    runBlocking {
      val serialNumber = adbSession.hostServices.getSerialNo(deviceSelector, true)
      println("Restoring to $applicationId from $file on $serialNumber")
      val backupService = BackupService.getInstance(adbSession, NoopLogger(), MIN_GMSCORE_VERSION)
      val listener = commandLine.getBackupProgressListener()

      val result = backupService.restore(serialNumber, Path.of(file), listener)

      if (result is BackupResult.Error) {
        println("Error: ${result.throwable.message}")
      }
    }
  }
}
