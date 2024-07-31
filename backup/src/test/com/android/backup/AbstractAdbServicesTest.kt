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
import com.android.backup.testing.FakeAdbServices
import com.android.backup.testing.FakeAdbServices.CommandOverride.Output
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertThrows
import org.junit.Test

private const val DUMPSYS_GMSCORE_CMD = "dumpsys package com.google.android.gms"

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
}
