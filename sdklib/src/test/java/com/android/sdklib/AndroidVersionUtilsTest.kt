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

    fun testComputeApiNameAndDetails() {
        assertEquals(
            NameDetails("API 16", "\"Jelly Bean\"; Android 4.1"),
            computeApiNameAndDetails(
                apiLevel = 16,
                extensionLevel = null,
                includeReleaseName = true,
                includeCodeName = true,
            )
        )

        assertEquals(
            NameDetails("API 20 ext. 4", "\"KitKat Wear\"; Android 4.4W"),
            computeApiNameAndDetails(
                apiLevel = 20,
                extensionLevel = 4,
                includeReleaseName = true,
                includeCodeName = true,
            )
        )

        assertEquals(
            NameDetails("API 25", "Android 7.1.1"),
            computeApiNameAndDetails(
                apiLevel = 25,
                extensionLevel = null,
                includeReleaseName = true,
                includeCodeName = false,
            )
        )

        assertEquals(
            NameDetails("API 27", "\"Oreo\""),
            computeApiNameAndDetails(
                apiLevel = 27,
                extensionLevel = null,
                includeReleaseName = false,
                includeCodeName = true,
            )
        )

        assertEquals(
            NameDetails("API 28", null),
            computeApiNameAndDetails(
                apiLevel = 28,
                extensionLevel = null,
                includeReleaseName = false,
                includeCodeName = false,
            )
        )

        assertEquals(
            NameDetails("API 29 ext. 4", null),
            computeApiNameAndDetails(
                apiLevel = 29,
                extensionLevel = 4,
                includeReleaseName = false,
                includeCodeName = false,
            )
        )

        // Future: if we don't have a name, don't include "null" as a name
        assertEquals(NameDetails("API 500", null), computeApiNameAndDetails(500, null))
        assertEquals(NameDetails("API 500 ext. 12", null), computeApiNameAndDetails(500, 12))
    }

    fun testComputeFullApiName() {
        assertEquals("API 16 (\"Jelly Bean\"; Android 4.1)", computeFullApiName(
            apiLevel = 16,
            extensionLevel = null,
            includeReleaseName = true,
            includeCodeName = true,
        ))

        assertEquals("API 28", computeFullApiName(
            apiLevel = 28,
            extensionLevel = null,
            includeReleaseName = false,
            includeCodeName = false,
        ))
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

    fun testGetApiNameAndDetails() {
        assertEquals(
            NameDetails("API 16 ext. 14", "\"Jelly Bean\"; Android 4.1"),
            AndroidVersion(
                /* apiLevel = */ 16,
                /* codename = */ null,
                /* extensionLevel = */ 14,
                /* isBaseExtension = */ false
            ).getApiNameAndDetails(
                includeReleaseName = true,
                includeCodeName = true,
            )
        )

        assertEquals(
            NameDetails("API 16", "\"Jelly Bean\"; Android 4.1"),
            AndroidVersion(
                /* apiLevel = */ 16,
                /* codename = */ null,
                /* extensionLevel = */ 14,
                /* isBaseExtension = */ true
            ).getApiNameAndDetails(
                includeReleaseName = true,
                includeCodeName = true,
            )
        )

        assertEquals(
            NameDetails("API Foo Preview", null),
            AndroidVersion(99, "Foo").getApiNameAndDetails(
                includeReleaseName = true,
                includeCodeName = true,
            )
        )
    }

    fun testComputeReleaseNameAndDetails() {
        assertEquals(
            NameDetails("Android 4.1", "\"Jelly Bean\"; API 16"),
            computeReleaseNameAndDetails(
                apiLevel = 16,
                extensionLevel = null,
                includeApiLevel = true,
                includeCodeName = true,
            )
        )

        assertEquals(
            NameDetails("Android 4.4W", "\"KitKat Wear\"; API 20 ext. 4"),
            computeReleaseNameAndDetails(
                apiLevel = 20,
                extensionLevel = 4,
                includeApiLevel = true,
                includeCodeName = true,
            )
        )

        assertEquals(
            NameDetails("Android 7.1.1", "API 25"),
            computeReleaseNameAndDetails(
                apiLevel = 25,
                extensionLevel = null,
                includeApiLevel = true,
                includeCodeName = false,
            )
        )

        assertEquals(
            NameDetails("Android 8.1", "\"Oreo\""),
            computeReleaseNameAndDetails(
                apiLevel = 27,
                extensionLevel = null,
                includeApiLevel = false,
                includeCodeName = true,
            )
        )

        assertEquals(
            NameDetails("Android 9.0", null),
            computeReleaseNameAndDetails(
                apiLevel = 28,
                extensionLevel = 4,
                includeApiLevel = false,
                includeCodeName = false,
            )
        )

        assertEquals(
            NameDetails("Android 9.0", "\"Pie\""),
            computeReleaseNameAndDetails(
                apiLevel = 28,
                extensionLevel = 4,
                includeApiLevel = false,
                includeCodeName = true,
            )
        )

        // Future: if we don't have a name, don't include "null" as a name
        assertEquals(NameDetails("Android API 500", null), computeReleaseNameAndDetails(500, null))
        assertEquals(NameDetails("Android API 500", null), computeReleaseNameAndDetails(500, 12))
    }


    fun testComputeFullReleaseName() {
        assertEquals("Android 4.1 (\"Jelly Bean\"; API 16)", computeFullReleaseName(
            apiLevel = 16,
            extensionLevel = null,
            includeApiLevel = true,
            includeCodeName = true,
        ))

        assertEquals("Android 9.0", computeFullReleaseName(
            apiLevel = 28,
            extensionLevel = 4,
            includeApiLevel = false,
            includeCodeName = false,
        ))
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

    fun testGetReleaseNameAndDetails() {
        assertEquals(
            NameDetails("Android 4.1", "\"Jelly Bean\"; API 16 ext. 14"),
            AndroidVersion(
                /* apiLevel = */ 16,
                /* codename = */ null,
                /* extensionLevel = */ 14,
                /* isBaseExtension = */ false
            ).getReleaseNameAndDetails(
                includeApiLevel = true,
                includeCodeName = true,
            )
        )

        assertEquals(
            NameDetails("Android 4.1", "\"Jelly Bean\"; API 16"),
            AndroidVersion(
                /* apiLevel = */ 16,
                /* codename = */ null,
                /* extensionLevel = */ 14,
                /* isBaseExtension = */ true
            ).getReleaseNameAndDetails(
                includeApiLevel = true,
                includeCodeName = true,
            )
        )

        assertEquals(
            NameDetails("Android Foo Preview", null),
            AndroidVersion(99, "Foo").getReleaseNameAndDetails(
                includeApiLevel = true,
                includeCodeName = true,
            )
        )
    }
}
