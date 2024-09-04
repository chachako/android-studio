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
import com.android.tools.environment.log.NoopLogger
import java.nio.file.Path
import kotlinx.coroutines.runBlocking

object AndroidBackup {
  @JvmStatic
  fun main(args: Array<String>) {
    if (args.size != 3) {
      println("Usage: android-backup <serial-number> <application-id> <backup-file-path>")
      return
    }
    val serialNumber = args[0]
    val applicationId = args[1]
    val file = args[2]
    val backupService =
      BackupService.getInstance(
        createStandaloneSession(AdbNoopLoggerFactory()),
        NoopLogger(),
        MIN_GMSCORE_VERSION,
      )

    runBlocking {
      val result =
        backupService.backup(serialNumber, applicationId, Path.of(file)) { println(it.text) }

      if (result is BackupResult.Error) {
        println("Error: ${result.throwable.message}")
      }
    }
  }
}
