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

import com.android.backup.testing.FakeAdbServices
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.runBlocking
import org.junit.Test

/** Tests for [AbstractAdbServices] */
class AbstractAdbServicesTest {

  @Test
  fun setTransport_withBadExistingTransport_doesNotThrow() = runBlocking {
    val transport = "com.google.android.gms/.backup.migrate.service.D2dTransport"
    val invalidTransport = "Invalid"
    val backupServices = FakeAdbServices("serial", 10)
    backupServices.activeTransport = invalidTransport
    backupServices.withSetup(transport) {
      assertThat(backupServices.activeTransport).isEqualTo(transport)
    }
    assertThat(backupServices.activeTransport).isEqualTo(invalidTransport)
  }
}
