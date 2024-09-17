/*
 * Copyright 2024 The Android Open Source Project
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

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

/**
 * Tests for [BackupType]
 */
@RunWith(JUnit4::class)
class BackupTypeTest {

  /**
   * Ordinals ore used to track in studio_stats so they must not change.
   *
   * Add new values to the test as needed.
   */
  @Test
  fun ordinals() {
    assertThat(BackupType.CLOUD.ordinal).isEqualTo(0)
    assertThat(BackupType.DEVICE_TO_DEVICE.ordinal).isEqualTo(1)
    assertThat(BackupType.entries).hasSize(2)
  }
}
