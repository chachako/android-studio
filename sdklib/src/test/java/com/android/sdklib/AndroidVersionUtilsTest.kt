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
package com.android.sdklib

import junit.framework.TestCase

class AndroidVersionUtilsTest : TestCase() {

    fun testComputeFullApiName() {
        assertEquals("API 16 (\"Jelly Bean\"; Android 4.1)", computeFullApiName(
            apiLevel = 16,
            extensionLevel = null,
            includeReleaseName = true,
            includeCodeName = true,
        ))

        assertEquals("API 20 ext. 4 (\"KitKat Wear\"; Android 4.4W)", computeFullApiName(
            apiLevel = 20,
            extensionLevel = 4,
            includeReleaseName = true,
            includeCodeName = true,
        ))

        assertEquals("API 25 (Android 7.1.1)", computeFullApiName(
            apiLevel = 25,
            extensionLevel = null,
            includeReleaseName = true,
            includeCodeName = false,
        ))

        assertEquals("API 27 (\"Oreo\")", computeFullApiName(
            apiLevel = 27,
            extensionLevel = null,
            includeReleaseName = false,
            includeCodeName = true,
        ))

        assertEquals("API 28", computeFullApiName(
            apiLevel = 28,
            extensionLevel = null,
            includeReleaseName = false,
            includeCodeName = false,
        ))

        assertEquals("API 29 ext. 4", computeFullApiName(
            apiLevel = 29,
            extensionLevel = 4,
            includeReleaseName = false,
            includeCodeName = false,
        ))

        // Future: if we don't have a name, don't include "null" as a name
        assertEquals("API 500", computeFullApiName(500, null))
        assertEquals("API 500 ext. 12", computeFullApiName(500, 12))
    }

    fun testGetFullApiName() {
        assertEquals(
            "API 16 ext. 14 (\"Jelly Bean\"; Android 4.1)",
            AndroidVersion(
                /* apiLevel = */ 16,
                /* codename = */ null,
                /* extensionLevel = */ 14,
                /* isBaseExtension = */ false
            ).getFullApiName(
                includeReleaseName = true,
                includeCodeName = true,
            )
        )

        assertEquals(
            "API 16 (\"Jelly Bean\"; Android 4.1)",
            AndroidVersion(
                /* apiLevel = */ 16,
                /* codename = */ null,
                /* extensionLevel = */ 14,
                /* isBaseExtension = */ true
            ).getFullApiName(
                includeReleaseName = true,
                includeCodeName = true,
            )
        )

        assertEquals(
            "API Foo Preview",
            AndroidVersion(99, "Foo").getFullApiName(
                includeReleaseName = true,
                includeCodeName = true,
            )
        )
    }

    fun testComputeFullReleaseName() {
        assertEquals("Android 4.1 (\"Jelly Bean\"; API 16)", computeFullReleaseName(
            apiLevel = 16,
            extensionLevel = null,
            includeApiLevel = true,
            includeCodeName = true,
        ))

        assertEquals("Android 4.4W (\"KitKat Wear\"; API 20 ext. 4)", computeFullReleaseName(
            apiLevel = 20,
            extensionLevel = 4,
            includeApiLevel = true,
            includeCodeName = true,
        ))

        assertEquals("Android 7.1.1 (API 25)", computeFullReleaseName(
            apiLevel = 25,
            extensionLevel = null,
            includeApiLevel = true,
            includeCodeName = false,
        ))

        assertEquals("Android 8.1 (\"Oreo\")", computeFullReleaseName(
            apiLevel = 27,
            extensionLevel = null,
            includeApiLevel = false,
            includeCodeName = true,
        ))

        assertEquals("Android 9.0", computeFullReleaseName(
            apiLevel = 28,
            extensionLevel = 4,
            includeApiLevel = false,
            includeCodeName = false,
        ))

        assertEquals("Android 9.0 (\"Pie\")", computeFullReleaseName(
            apiLevel = 28,
            extensionLevel = 4,
            includeApiLevel = false,
            includeCodeName = true,
        ))

        // Future: if we don't have a name, don't include "null" as a name
        assertEquals("Android API 500", computeFullReleaseName(500, null))
        assertEquals("Android API 500", computeFullReleaseName(500, 12))
    }

    fun testGetFullReleaseName() {
        assertEquals(
            "Android 4.1 (\"Jelly Bean\"; API 16 ext. 14)",
            AndroidVersion(
                /* apiLevel = */ 16,
                /* codename = */ null,
                /* extensionLevel = */ 14,
                /* isBaseExtension = */ false
            ).getFullReleaseName(
                includeApiLevel = true,
                includeCodeName = true,
            )
        )

        assertEquals(
            "Android 4.1 (\"Jelly Bean\"; API 16)",
            AndroidVersion(
                /* apiLevel = */ 16,
                /* codename = */ null,
                /* extensionLevel = */ 14,
                /* isBaseExtension = */ true
            ).getFullReleaseName(
                includeApiLevel = true,
                includeCodeName = true,
            )
        )

        assertEquals(
            "Android Foo Preview",
            AndroidVersion(99, "Foo").getFullReleaseName(
                includeApiLevel = true,
                includeCodeName = true,
            )
        )
    }
}
