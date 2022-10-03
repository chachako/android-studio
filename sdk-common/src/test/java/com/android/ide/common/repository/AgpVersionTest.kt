/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.ide.common.repository

import com.android.ide.common.repository.GradleVersion.AgpVersion
import com.google.common.truth.Truth.assertThat
import org.junit.Assert
import org.junit.Test

class AgpVersionTest {

    @Test
    fun testTryParse() {
        /*
         * Valid versions
         */
        assertThat(convertAGPVersionToString(AgpVersion.tryParse("3.0.0"))).isEqualTo("3.0.0")
        assertThat(convertAGPVersionToString(AgpVersion.tryParse("3.0.1"))).isEqualTo("3.0.1")
        assertThat(convertAGPVersionToString(AgpVersion.tryParse("3.1.0"))).isEqualTo("3.1.0")
        assertThat(convertAGPVersionToString(AgpVersion.tryParse("3.0.0-alpha1")))
            .isEqualTo("3.0.0-alpha1")
        assertThat(convertAGPVersionToString(AgpVersion.tryParse("3.0.0-beta1")))
            .isEqualTo("3.0.0-beta1")
        assertThat(convertAGPVersionToString(AgpVersion.tryParse("3.0.0-rc1")))
            .isEqualTo("3.0.0-rc1")
        assertThat(convertAGPVersionToString(AgpVersion.tryParse("3.1.0-alpha01")))
            .isEqualTo("3.1.0-alpha1")
        assertThat(convertAGPVersionToString(AgpVersion.tryParse("3.1.0-beta01")))
            .isEqualTo("3.1.0-beta1")
        assertThat(convertAGPVersionToString(AgpVersion.tryParse("3.1.0-rc01")))
            .isEqualTo("3.1.0-rc1")
        assertThat(convertAGPVersionToString(AgpVersion.tryParse("3.0.0-dev")))
            .isEqualTo("3.0.0-dev")
        assertThat(convertAGPVersionToString(AgpVersion.tryParse("3.0.0-dev01")))
            .isEqualTo("3.0.0-dev1")
        assertThat(convertAGPVersionToString(AgpVersion.tryParse("3.1.0-dev")))
            .isEqualTo("3.1.0-dev")

        /*
         * Invalid versions
         */
        assertThat(AgpVersion.tryParse("")).isNull()
        assertThat(AgpVersion.tryParse("foo")).isNull()
        assertThat(AgpVersion.tryParse("3.1")).isNull()
        assertThat(AgpVersion.tryParse("3.1.")).isNull()
        assertThat(AgpVersion.tryParse("3.1-0")).isNull()
        assertThat(AgpVersion.tryParse("3.1.foo")).isNull()
        assertThat(AgpVersion.tryParse("3.1.0.0")).isNull()
        assertThat(AgpVersion.tryParse("3.1.0-0")).isNull()
        assertThat(AgpVersion.tryParse("3.1.0alpha01")).isNull()
        assertThat(AgpVersion.tryParse("3.1.0-01")).isNull()
        assertThat(AgpVersion.tryParse("3.1.0-alpha")).isNull()
        assertThat(AgpVersion.tryParse("3.1.0.alpha01")).isNull()
        assertThat(AgpVersion.tryParse("3.1.0-gamma01")).isNull()
        assertThat(AgpVersion.tryParse("3.1.0.alpha-01")).isNull()
        assertThat(AgpVersion.tryParse("3.1.0-alpha01.0")).isNull()
        assertThat(AgpVersion.tryParse("3.1.0-alpha01-0")).isNull()
        assertThat(AgpVersion.tryParse("3.1.0dev")).isNull()
        assertThat(AgpVersion.tryParse("3.1.0.dev")).isNull()
        assertThat(AgpVersion.tryParse("3.1.0-dev-01")).isNull()
        assertThat(AgpVersion.tryParse("3.1.0-dev.0")).isNull()
        assertThat(AgpVersion.tryParse("3.1.0-dev-0")).isNull()
    }

    @Test
    fun testTryParseStable() {
        /*
         * Valid versions
         */
        assertThat(convertAGPVersionToString(AgpVersion.tryParseStable("0.0.0"))).isEqualTo("0.0.0")
        assertThat(convertAGPVersionToString(AgpVersion.tryParseStable("3.0.0"))).isEqualTo("3.0.0")
        assertThat(convertAGPVersionToString(AgpVersion.tryParseStable("10.10.10")))
            .isEqualTo("10.10.10")

        /*
         * Invalid versions
         */
        assertThat(AgpVersion.tryParseStable("")).isNull()
        assertThat(AgpVersion.tryParseStable("foo")).isNull()
        assertThat(AgpVersion.tryParseStable("3")).isNull()
        assertThat(AgpVersion.tryParseStable("3.0")).isNull()
        assertThat(AgpVersion.tryParseStable("3.0.0-alpha01")).isNull()
        assertThat(AgpVersion.tryParseStable("3.0.0-beta01")).isNull()
        assertThat(AgpVersion.tryParseStable("3.0.0-rc01")).isNull()
        assertThat(AgpVersion.tryParseStable("3.0.0-dev")).isNull()
    }

    @Test
    fun testParse() {
        // Valid version
        assertThat(convertAGPVersionToString(AgpVersion.parse("3.0.0"))).isEqualTo("3.0.0")

        // Invalid version
        try {
            AgpVersion.parse("3.1")
            Assert.fail("Expect IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            assertThat(e.message).isEqualTo("3.1 is not a valid Android Gradle plugin version")
        }
    }

    @Test
    fun testCompareVersions() {
        assertThat(compareAGPVersions("3.1.0", "3.1.0")).isEqualTo(0)
        assertThat(compareAGPVersions("3.1.0-alpha01", "3.1.0-alpha01")).isEqualTo(0)
        assertThat(compareAGPVersions("3.1.0-beta01", "3.1.0-beta01")).isEqualTo(0)
        assertThat(compareAGPVersions("3.1.0-rc01", "3.1.0-rc01")).isEqualTo(0)
        assertThat(compareAGPVersions("3.1.0-dev", "3.1.0-dev")).isEqualTo(0)
        assertThat(compareAGPVersions("3.0.0-alpha1", "3.0.0-alpha2")).isLessThan(0)
        assertThat(compareAGPVersions("3.0.0-alpha2", "3.0.0-beta1")).isLessThan(0)
        assertThat(compareAGPVersions("3.0.0-beta1", "3.0.0-beta2")).isLessThan(0)
        assertThat(compareAGPVersions("3.0.0-beta2", "3.0.0-rc1")).isLessThan(0)
        assertThat(compareAGPVersions("3.0.0-rc1", "3.0.0-rc2")).isLessThan(0)
        assertThat(compareAGPVersions("3.0.0-rc2", "3.0.0")).isLessThan(0)
        assertThat(compareAGPVersions("3.0.0", "3.0.1")).isLessThan(0)
        assertThat(compareAGPVersions("3.0.1", "3.0.2")).isLessThan(0)
        assertThat(compareAGPVersions("3.0.2", "3.1.0-alpha01")).isLessThan(0)
        assertThat(compareAGPVersions("3.1.0-alpha01", "3.1.0-beta01")).isLessThan(0)
        assertThat(compareAGPVersions("3.1.0-beta01", "3.1.0-rc01")).isLessThan(0)
        assertThat(compareAGPVersions("3.1.0-rc01", "3.1.0")).isLessThan(0)
        assertThat(compareAGPVersions("3.1.0", "3.0.0-alpha01")).isGreaterThan(0)

        // Dev versions should be older than stable versions
        assertThat(compareAGPVersions("3.0.0-dev", "3.0.0")).isLessThan(0)
        assertThat(compareAGPVersions("3.0.0", "3.1.0-dev")).isLessThan(0)
        assertThat(compareAGPVersions("3.1.0-dev", "3.1.0")).isLessThan(0)
        assertThat(compareAGPVersions("3.1.0", "3.0.0-dev")).isGreaterThan(0)

        // Dev versions are currently considered to be newer than preview versions (although they
        // are not exactly compare-able)
        assertThat(compareAGPVersions("3.0.0-dev", "3.1.0-alpha01")).isLessThan(0)
        assertThat(compareAGPVersions("3.1.0-rc02", "3.1.0-dev")).isLessThan(0)
        assertThat(compareAGPVersions("3.1.0-dev", "3.0.0-dev")).isGreaterThan(0)
    }

    private fun convertAGPVersionToString(version: AgpVersion?): String {
        assertThat(version).isNotNull()
        version!!.run {
            // We reimplement this instead of using version.toString() directly to prevent the
            // GradleVersion class from "remembering" the input string without actually parsing it.
            // We  deliberately do not do field width formatting here (e.g. -alpha1 not -alpha01)
            // for the preview number as this is purely for internal consistency checks.
            return when {
                isPreview -> when (previewType) {
                    null -> "$major.$minor.$micro-dev"
                    else -> "$major.$minor.$micro-${previewType}$preview"
                }
                else -> "$major.$minor.$micro"
            }
        }
    }

    private fun compareAGPVersions(version1: String, version2: String): Int =
        AgpVersion.parse(version1).compareTo(AgpVersion.parse(version2))
}
