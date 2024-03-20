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

package com.android.testing.utils

import org.junit.Test
import kotlin.test.assertEquals

class ManagedDeviceUtilsTest {

    @Test
    fun isGradleManagedDevice_detectsManagedDevices() {
        assertEquals(
            true,
            isGradleManagedDevice("dev29_google_apis_x86_Pixel_2__something__snapshot")
        )
        assertEquals(
            true,
            isGradleManagedDevice("dev32_default_arm64-v8a_Pixel_3_snapshot")
        )

        assertEquals(
            true,
            isGradleManagedDevice("app:myDeviceAndroidTest")
        )
        assertEquals(
            true,
            isGradleManagedDevice("app:device1AndroidTest_0")
        )
        assertEquals(
            true,
            isGradleManagedDevice("sub-project:complex_device_nameAndroidTest_12")
        )
    }

    @Test
    fun isGradleManagedDevice_identifiesNonManagedDevices() {
        assertEquals(
            false,
            isGradleManagedDevice("some_model_id")
        )
        // Even a valid GMD AVD should fail, if it doesn't conform to a setup task format.
        assertEquals(
            false,
            isGradleManagedDevice("dev32_default_arm64-v8a_Pixel_3")
        )
        assertEquals(
            false,
            isGradleManagedDevice(":toLevelAndroidTest")
        )
        assertEquals(
            false,
            isGradleManagedDevice("app:device1AndroidTest_")
        )
    }
}
