/*
 * Copyright (C) 2023 The Android Open Source Project
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
package com.android.processmonitor.common

import com.android.processmonitor.common.ProcessEvent.ProcessAdded
import com.android.processmonitor.monitor.ProcessNames
import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Tests for [ProcessEvent]
 */
class ProcessEventTest {

    @Test
    fun processAdded_toProcessNames() {
        assertThat(ProcessAdded(1, "applicationId", "processName").toProcessNames())
            .isEqualTo(ProcessNames("applicationId", "processName"))
    }

    @Test
    fun processAdded_toProcessNames_nullApplicationId() {
        assertThat(ProcessAdded(1, null, "processName").toProcessNames())
            .isEqualTo(ProcessNames("processName", "processName"))
    }

    @Test
    fun processAdded_toProcessNames_extractsApplicationId() {
        assertThat(ProcessAdded(1, null, "com.google.app:service").toProcessNames())
            .isEqualTo(ProcessNames("com.google.app", "com.google.app:service"))
    }

    @Test
    fun processAdded_toProcessNames_doesNotExtractApplicationIdIfNotNull() {
        assertThat(ProcessAdded(1, "applicationId", "com.google.app:service").toProcessNames())
            .isEqualTo(ProcessNames("applicationId", "com.google.app:service"))
    }
}
