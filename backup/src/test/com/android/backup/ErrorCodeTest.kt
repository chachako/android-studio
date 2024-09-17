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
 * Tests for [ErrorCode]
 */
@RunWith(JUnit4::class)
class ErrorCodeTest {
  /**
   * Ordinals ore used to track in studio_stats so they must not change.
   *
   * Add new values to the test as needed.
   */
  @Test
  fun ordinals() {
    assertThat(ErrorCode.SUCCESS.ordinal).isEqualTo(0)
    assertThat(ErrorCode.CANNOT_ENABLE_BMGR.ordinal).isEqualTo(1)
    assertThat(ErrorCode.TRANSPORT_NOT_SELECTED.ordinal).isEqualTo(2)
    assertThat(ErrorCode.TRANSPORT_INIT_FAILED.ordinal).isEqualTo(3)
    assertThat(ErrorCode.GMSCORE_NOT_FOUND.ordinal).isEqualTo(4)
    assertThat(ErrorCode.GMSCORE_IS_TOO_OLD.ordinal).isEqualTo(5)
    assertThat(ErrorCode.BACKUP_FAILED.ordinal).isEqualTo(6)
    assertThat(ErrorCode.RESTORE_FAILED.ordinal).isEqualTo(7)
    assertThat(ErrorCode.INVALID_BACKUP_FILE.ordinal).isEqualTo(8)
    assertThat(ErrorCode.PLAY_STORE_NOT_INSTALLED.ordinal).isEqualTo(9)
    assertThat(ErrorCode.BACKUP_NOT_ALLOWED.ordinal).isEqualTo(10)
    assertThat(ErrorCode.UNEXPECTED_ERROR.ordinal).isEqualTo(11)
    assertThat(ErrorCode.entries).hasSize(12)
  }
}
