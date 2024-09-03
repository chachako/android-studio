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

import com.android.backup.ErrorCode.GMSCORE_IS_TOO_OLD
import com.android.backup.ErrorCode.GMSCORE_NOT_FOUND
import com.android.backup.ErrorCode.PLAY_STORE_NOT_INSTALLED
import com.android.backup.ErrorCode.UNEXPECTED_ERROR
import com.android.backup.testing.FakeAdbServices
import com.android.backup.testing.FakeAdbServices.CommandOverride.Output
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertThrows
import org.junit.Test

private const val DUMPSYS_GMSCORE_CMD = "dumpsys package com.google.android.gms"

private const val LAUNCH_COMMAND = "am start market://details?id=com.google.android.gms"
private const val LAUNCH_COMMAND_STDOUT_VALID =
  "Starting: Intent { act=android.intent.action.VIEW dat=market://details/... }"
private const val LAUNCH_COMMAND_STDERR_MISSING_STORE =
  "Error: Activity not started, unable to resolve Intent"

/** Tests for [AbstractAdbServices] */
class AbstractAdbServicesTest {

  private val transport = "com.google.android.gms/.backup.migrate.service.D2dTransport"

  @Test
  fun setTransport_withBadExistingTransport_doesNotThrow() = runBlocking {
    val invalidTransport = "Invalid"
    val backupServices = FakeAdbServices("serial", totalSteps = 10)
    backupServices.activeTransport = invalidTransport
    backupServices.withSetup(transport) {
      assertThat(backupServices.activeTransport).isEqualTo(transport)
    }
    assertThat(backupServices.activeTransport).isEqualTo(invalidTransport)
  }

  @Test
  fun missingGmsCore() {
    val backupServices = FakeAdbServices("serial", 10)
    backupServices.addCommandOverride(
      Output(
        DUMPSYS_GMSCORE_CMD,
        """
          If GmsCore is not installed, there will be no line matching "^packages:$"
        """
          .trimIndent(),
      )
    )
    val exception =
      assertThrows(BackupException::class.java) {
        runBlocking { backupServices.withSetup(transport) {} }
      }
    assertThat(exception.errorCode).isEqualTo(GMSCORE_NOT_FOUND)
  }

  @Test
  fun oldGmsCore() {
    val backupServices = FakeAdbServices("serial", 10)
    backupServices.addCommandOverride(
      Output(
        DUMPSYS_GMSCORE_CMD,
        """
          Packages:
              versionCode=50 minSdk=31 targetSdk=34
        """
          .trimIndent(),
      )
    )
    val exception =
      assertThrows(BackupException::class.java) {
        runBlocking { backupServices.withSetup(transport) {} }
      }
    assertThat(exception.errorCode).isEqualTo(GMSCORE_IS_TOO_OLD)
  }

  @Test
  fun sendUpdateGmsIntent_success(): Unit = runBlocking {
    val adbServices = FakeAdbServices("serial", 10)
    adbServices.sendUpdateGmsIntent()
  }

  @Test
  fun sendUpdateGmsIntent_unexpectedStdout() {
    val adbServices = FakeAdbServices("serial", 10)
    adbServices.addCommandOverride(Output(LAUNCH_COMMAND, "unexpected"))

    val exception =
      assertThrows(BackupException::class.java) {
        runBlocking { adbServices.sendUpdateGmsIntent() }
      }
    assertThat(exception.errorCode).isEqualTo(UNEXPECTED_ERROR)
  }

  @Test
  fun sendUpdateGmsIntent_errorInStderr(): Unit = runBlocking {
    val adbServices = FakeAdbServices("serial", 10)
    adbServices.addCommandOverride(Output(LAUNCH_COMMAND, LAUNCH_COMMAND_STDOUT_VALID, "Warning"))

    adbServices.sendUpdateGmsIntent()
  }

  @Test
  fun sendUpdateGmsIntent_warningInStderr() {
    val adbServices = FakeAdbServices("serial", 10)
    adbServices.addCommandOverride(Output(LAUNCH_COMMAND, LAUNCH_COMMAND_STDOUT_VALID, "Error"))

    val exception =
      assertThrows(BackupException::class.java) {
        runBlocking { adbServices.sendUpdateGmsIntent() }
      }
    assertThat(exception.errorCode).isEqualTo(UNEXPECTED_ERROR)
  }

  @Test
  fun sendUpdateGmsIntent_missingPlayStore() {
    val adbServices = FakeAdbServices("serial", 10)
    adbServices.addCommandOverride(
      Output(LAUNCH_COMMAND, LAUNCH_COMMAND_STDOUT_VALID, LAUNCH_COMMAND_STDERR_MISSING_STORE)
    )

    val exception =
      assertThrows(BackupException::class.java) {
        runBlocking { adbServices.sendUpdateGmsIntent() }
      }
    assertThat(exception.errorCode).isEqualTo(PLAY_STORE_NOT_INSTALLED)
  }

  @Test
  fun getForegroundApplicationId() = runBlocking {
    val adbServices = FakeAdbServices("serial", 10)

    val applicationId = adbServices.getForegroundApplicationId()

    assertThat(applicationId).isEqualTo("com.app")
  }

  @Test
  fun isInstalled_installed() = runBlocking {
    val adbServices = FakeAdbServices("serial", 10)
    adbServices.addCommandOverride(Output("pm list packages com.app", "package:com.app"))

    assertThat(adbServices.isInstalled("com.app")).isTrue()
  }

  @Test
  fun isInstalled_not_installed() = runBlocking {
    val adbServices = FakeAdbServices("serial", 10)

    assertThat(adbServices.isInstalled("com.app")).isFalse()
  }
}
