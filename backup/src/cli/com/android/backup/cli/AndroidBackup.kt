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
import com.android.backup.BackupType
import com.android.backup.BackupType.CLOUD
import com.android.backup.BackupType.DEVICE_TO_DEVICE
import com.android.backup.cli.CommandLineOptions.createCommonOptions
import com.android.backup.cli.CommandLineOptions.getBackupProgressListener
import com.android.backup.cli.CommandLineOptions.getDeviceSelector
import com.android.backup.cli.CommandLineOptions.help
import com.android.tools.environment.log.NoopLogger
import java.nio.file.Path
import kotlinx.coroutines.runBlocking
import org.apache.commons.cli.CommandLine
import org.apache.commons.cli.DefaultParser
import org.apache.commons.cli.HelpFormatter
import org.apache.commons.cli.Option

private const val USAGE_TITLE = "android-backup [options] <OUTPUT-FILE>"

private val packageNameOption =
  Option.builder()
    .option("p")
    .hasArg()
    .argName("PACKAGE-NAME")
    .desc("Backup application identified by PACKAGE-NAME")
    .build()

private val backupTypeOption =
  Option.builder()
    .option("type")
    .hasArg()
    .argName("BACKUP-TYPE")
    .desc("Type of backup to perform (d2d [default], cloud)")
    .build()

object AndroidBackup {
  @JvmStatic
  fun main(args: Array<String>) {

    val options = createCommonOptions().addOption(packageNameOption).addOption(backupTypeOption)

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

    val deviceSelector = commandLine.getDeviceSelector()
    val backupType = commandLine.getBackupType()

    val adbSession = createStandaloneSession(AdbNoopLoggerFactory())
    val backupService = BackupService.getInstance(adbSession, NoopLogger(), MIN_GMSCORE_VERSION)

    runBlocking {
      val serialNumber = adbSession.hostServices.getSerialNo(deviceSelector, true)
      val applicationId =
        when {
          commandLine.hasOption(packageNameOption) -> commandLine.getOptionValue(packageNameOption)
          else -> backupService.getForegroundApplicationId(serialNumber)
        }
      println("Creating a '${backupType.displayName}' backup of $applicationId on $serialNumber")

      val file = commandLine.args.first()
      val listener = commandLine.getBackupProgressListener()

      val result =
        backupService.backup(serialNumber, applicationId, backupType, Path.of(file), listener)

      if (result is BackupResult.Error) {
        println("Error: ${result.throwable.message}")
      } else {
        println("Saved backup to $file")
      }
    }
  }
}

private fun CommandLine.getBackupType(): BackupType {
  return when (val value = getOptionValue(backupTypeOption, "d2d")) {
    "cloud" -> CLOUD
    "d2d" -> DEVICE_TO_DEVICE
    else -> throw IllegalArgumentException("Invalid backup type: $value.")
  }
}
