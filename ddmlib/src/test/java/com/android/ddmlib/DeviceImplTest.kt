/*
 * Copyright (C) 2015 The Android Open Source Project
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
package com.android.ddmlib

import com.android.ddmlib.testing.FakeAdbRule
import com.google.common.truth.Truth.assertThat
import org.junit.Assert.assertThrows
import org.junit.Rule
import org.junit.Test

class DeviceImplTest {

    @get:Rule
    var adbRule = FakeAdbRule()

    @Test
    fun testComputeUserDataIfPresent() {
        // Prepare
        adbRule.attachDevice("42", "Google", "Pix3l", "versionX", "29")
        val device: IDevice = adbRule.bridge.devices.single()
        val key = IUserDataMap.Key<MyClass>()

        // Act
        val value = device.computeUserDataIfAbsent(key) { myKey -> MyClass(myKey) }

        // Assert
        assertThat(value).isNotNull()
        assertThat(value.key).isSameAs(key)
    }

    @Test
    fun testComputeUserDataIfPresentDoesNotAllowNull() {
        // Prepare
        adbRule.attachDevice("42", "Google", "Pix3l", "versionX", "29")
        val device: IDevice = adbRule.bridge.devices.single()
        val key = IUserDataMap.Key<MyClass>()

        // Act/Assert
        assertThrows(IllegalArgumentException::class.java) {
            device.computeUserDataIfAbsent(key) { null }
        }
    }

    @Test
    fun testGetUserDataOrNullReturnsValueIfPresent() {
        // Prepare
        adbRule.attachDevice("42", "Google", "Pix3l", "versionX", "29")
        val device: IDevice = adbRule.bridge.devices.single()
        val key = IUserDataMap.Key<MyClass>()
        device.computeUserDataIfAbsent(key) { myKey -> MyClass(myKey) }

        // Act
        val value = device.getUserDataOrNull(key)

        // Assert
        assertThat(value).isNotNull()
        assertThat(value?.key).isSameAs(key)
    }

    @Test
    fun testGetUserDataOrNullReturnsNullIfNotPresent() {
        // Prepare
        adbRule.attachDevice("42", "Google", "Pix3l", "versionX", "29")
        val device: IDevice = adbRule.bridge.devices.single()
        val key = IUserDataMap.Key<MyClass>()

        // Act
        val value = device.getUserDataOrNull(key)

        // Assert
        assertThat(value).isNull()
    }

    @Test
    fun testRemoveUserData() {
        // Prepare
        adbRule.attachDevice("42", "Google", "Pix3l", "versionX", "29")
        val device: IDevice = adbRule.bridge.devices.single()
        val key = IUserDataMap.Key<MyClass>()
        val value = device.computeUserDataIfAbsent(key) { myKey -> MyClass(myKey) }

        // Act
        val removedValue = device.removeUserData(key)

        // Assert
        assertThat(removedValue).isNotNull()
        assertThat(removedValue).isSameAs(value)
    }

    private class MyClass(val key: IUserDataMap.Key<MyClass>)
}
