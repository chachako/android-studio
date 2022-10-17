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

import com.google.common.truth.Truth.assertThat
import org.junit.Assert
import org.junit.Test

class AgpVersionTest {

    @Test
    fun testTryParse() {
        /** Valid versions */

        /** Stable versions */
        assertThat(convertAGPVersionToString(AgpVersion.tryParse("3.0.0"))).isEqualTo("3.0.0")
        assertThat(convertAGPVersionToString(AgpVersion.tryParse("3.0.1"))).isEqualTo("3.0.1")
        assertThat(convertAGPVersionToString(AgpVersion.tryParse("3.1.0"))).isEqualTo("3.1.0")

        /** 3.0.0 has no zero-padding on preview versions */
        assertThat(convertAGPVersionToString(AgpVersion.tryParse("3.0.0-alpha1")))
            .isEqualTo("3.0.0-alpha1")
        assertThat(convertAGPVersionToString(AgpVersion.tryParse("3.0.0-beta1")))
            .isEqualTo("3.0.0-beta1")
        assertThat(convertAGPVersionToString(AgpVersion.tryParse("3.0.0-rc1")))
            .isEqualTo("3.0.0-rc1")
        /** 3.1.0 has zero-padding on alpha and rc but not beta preview versions */
        assertThat(convertAGPVersionToString(AgpVersion.tryParse("3.1.0-alpha01")))
            .isEqualTo("3.1.0-alpha1")
        assertThat(convertAGPVersionToString(AgpVersion.tryParse("3.1.0-beta1")))
            .isEqualTo("3.1.0-beta1")
        assertThat(convertAGPVersionToString(AgpVersion.tryParse("3.1.0-rc01")))
            .isEqualTo("3.1.0-rc1")
        /** 3.2.0 has zero-padding on all preview versions */
        assertThat(convertAGPVersionToString(AgpVersion.tryParse("3.2.0-alpha01")))
            .isEqualTo("3.2.0-alpha1")
        assertThat(convertAGPVersionToString(AgpVersion.tryParse("3.2.0-beta01")))
            .isEqualTo("3.2.0-beta1")
        assertThat(convertAGPVersionToString(AgpVersion.tryParse("3.2.0-rc01")))
            .isEqualTo("3.2.0-rc1")
        /** dev versions */
        assertThat(convertAGPVersionToString(AgpVersion.tryParse("3.0.0-dev")))
            .isEqualTo("3.0.0-dev")
        assertThat(convertAGPVersionToString(AgpVersion.tryParse("3.1.0-dev")))
            .isEqualTo("3.1.0-dev")

        /** Invalid AGP versions */
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
        assertThat(AgpVersion.tryParse("3.0.0-alpha01")).isNull()
        assertThat(AgpVersion.tryParse("3.0.0-beta01")).isNull()
        assertThat(AgpVersion.tryParse("3.0.0-rc01")).isNull()
        assertThat(AgpVersion.tryParse("3.1.0-alpha1")).isNull()
        assertThat(AgpVersion.tryParse("3.1.0-beta01")).isNull()
        assertThat(AgpVersion.tryParse("3.1.0-rc1")).isNull()
        assertThat(AgpVersion.tryParse("3.2.0-alpha1")).isNull()
        assertThat(AgpVersion.tryParse("3.2.0-beta1")).isNull()
        assertThat(AgpVersion.tryParse("3.2.0-rc1")).isNull()
        assertThat(AgpVersion.tryParse("3.1.0dev")).isNull()
        assertThat(AgpVersion.tryParse("3.1.0.dev")).isNull()
        assertThat(AgpVersion.tryParse("3.1.0-dev-01")).isNull()
        assertThat(AgpVersion.tryParse("3.1.0-dev.0")).isNull()
        assertThat(AgpVersion.tryParse("3.1.0-dev-0")).isNull()
        assertThat(AgpVersion.tryParse("3.1.0-dev1")).isNull()
        assertThat(AgpVersion.tryParse("3.1.0-dev01")).isNull()
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
            assertThat(e.message).isEqualTo("3.1 is not a valid AGP version string.")
        }
    }

    @Test
    fun testCompareTo() {
        assertThat(compareAGPVersions("3.1.0", "3.1.0")).isEqualTo(0)
        assertThat(compareAGPVersions("3.1.0-alpha01", "3.1.0-alpha01")).isEqualTo(0)
        assertThat(compareAGPVersions("3.1.0-beta1", "3.1.0-beta1")).isEqualTo(0)
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
        assertThat(compareAGPVersions("3.1.0-alpha01", "3.1.0-beta1")).isLessThan(0)
        assertThat(compareAGPVersions("3.1.0-beta1", "3.1.0-rc01")).isLessThan(0)
        assertThat(compareAGPVersions("3.1.0-rc01", "3.1.0")).isLessThan(0)
        assertThat(compareAGPVersions("3.1.0", "3.0.0-alpha1")).isGreaterThan(0)

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

    @Test
    fun testCompareIgnoringQualifiers() {
        assertThat(compareAgpVersionsIgnoringQualifiers("1.0.0", "1.0.0-alpha1")).isEqualTo(0)
        assertThat(compareAgpVersionsIgnoringQualifiers("1.0.0-alpha1", "1.0.0-dev")).isEqualTo(0)
        assertThat(compareAgpVersionsIgnoringQualifiers("1.0.0-alpha1", "1.0.0")).isEqualTo(0)
        assertThat(compareAgpVersionsIgnoringQualifiers("1.0.0", "1.0.0-beta2")).isEqualTo(0)
        assertThat(compareAgpVersionsIgnoringQualifiers("1.0.0-beta2", "1.0.0-dev")).isEqualTo(0)
        assertThat(compareAgpVersionsIgnoringQualifiers("1.0.0-beta2", "1.0.0")).isEqualTo(0)
        assertThat(compareAgpVersionsIgnoringQualifiers("1.0.0", "1.0.0-rc3")).isEqualTo(0)
        assertThat(compareAgpVersionsIgnoringQualifiers("1.0.0-rc3", "1.0.0-dev")).isEqualTo(0)
        assertThat(compareAgpVersionsIgnoringQualifiers("1.0.0-rc3", "1.0.0")).isEqualTo(0)
        assertThat(compareAgpVersionsIgnoringQualifiers("1.0.0", "1.0.0-dev")).isEqualTo(0)
        assertThat(compareAgpVersionsIgnoringQualifiers("1.0.0-dev", "1.0.0")).isEqualTo(0)
        assertThat(compareAgpVersionsIgnoringQualifiers("1.0.0", "1.0.0")).isEqualTo(0)

        assertThat(compareAgpVersionsIgnoringQualifiers("1.0.1", "1.0.0-alpha1")).isGreaterThan(0)
        assertThat(compareAgpVersionsIgnoringQualifiers("1.0.1-alpha1", "1.0.0")).isGreaterThan(0)
        assertThat(compareAgpVersionsIgnoringQualifiers("1.0.0-alpha1", "1.0.1")).isLessThan(0)
        assertThat(compareAgpVersionsIgnoringQualifiers("1.0.0", "1.0.1-alpha1")).isLessThan(0)
    }

    @Test
    fun testIsAtLeast() {
        AgpVersion.parse("2.1.3").let { version ->
            assertThat(version.isAtLeast(2, 1, 3)).isTrue()
            assertThat(version.isAtLeast(2, 1, 4)).isFalse()
            assertThat(version.isAtLeast(2, 2, 0)).isFalse()
            assertThat(version.isAtLeast(3, 0, 0)).isFalse()

            assertThat(version.isAtLeast(2, 1, 2)).isTrue()
            assertThat(version.isAtLeast(1, 2, 3)).isTrue()
        }

        AgpVersion.parse("2.3.0-dev").let { version ->
            assertThat(version.isAtLeast(2, 2, 0)).isTrue()
            assertThat(version.isAtLeast(2, 3, 0, null, 0, true)).isTrue()
            assertThat(version.isAtLeast(2, 3, 0)).isFalse()
            assertThat(version.isAtLeast(2, 4, 0)).isFalse()
        }

        AgpVersion.parse("7.0.0-rc02").let { version ->
            assertThat(version.isAtLeast(6, 9, 3)).isTrue()
            assertThat(version.isAtLeast(7, 0, 0, "rc", 1, false)).isTrue()
            assertThat(version.isAtLeast(7, 0, 0, null, 0, false)).isFalse()
            assertThat(version.isAtLeast(7, 0, 0, null, 0, true)).isFalse() // -dev compares higher
            assertThat(version.isAtLeast(7, 0, 0)).isFalse()
            assertThat(version.isAtLeast(7, 1, 0)).isFalse()
        }

        AgpVersion.parse("2.3.0-beta1").let { version ->
            assertThat(version.isAtLeast(2, 3, 0, "beta", 1, false)).isTrue()
            assertThat(version.isAtLeast(2, 3, 0, "alpha", 8, false)).isTrue()
            assertThat(version.isAtLeast(2, 3, 0, "beta", 2, false)).isFalse()
            assertThat(version.isAtLeast(2, 3, 0, "rc", 1, false)).isFalse()
        }
    }

    @Test
    fun testIsAtLeastIncludingPreviews() {
        AgpVersion.parse("2.1.3").let { version ->
            assertThat(version.isAtLeastIncludingPreviews(2, 1, 3)).isTrue()
            assertThat(version.isAtLeastIncludingPreviews(2, 1, 4)).isFalse()
            assertThat(version.isAtLeastIncludingPreviews(2, 2, 0)).isFalse()
            assertThat(version.isAtLeastIncludingPreviews(3, 0, 0)).isFalse()

            assertThat(version.isAtLeastIncludingPreviews(2, 1, 2)).isTrue()
            assertThat(version.isAtLeastIncludingPreviews(1, 2, 3)).isTrue()
        }

        AgpVersion.parse("2.3.0-dev").let { version ->
            assertThat(version.isAtLeastIncludingPreviews(2, 2, 0)).isTrue()
            assertThat(version.isAtLeastIncludingPreviews(2, 3, 0)).isTrue()
            assertThat(version.isAtLeastIncludingPreviews(2, 4, 0)).isFalse()
        }

        AgpVersion.parse("7.0.0-rc02").let { version ->
            assertThat(version.isAtLeastIncludingPreviews(6, 9, 3)).isTrue()
            assertThat(version.isAtLeastIncludingPreviews(7, 0, 0)).isTrue()
            assertThat(version.isAtLeastIncludingPreviews(7, 1, 0)).isFalse()
        }

        AgpVersion.parse("2.3.0-beta1").let { version ->
            assertThat(version.isAtLeastIncludingPreviews(2, 2, 0)).isTrue()
            assertThat(version.isAtLeastIncludingPreviews(2, 3, 0)).isTrue()
            assertThat(version.isAtLeastIncludingPreviews(2, 4, 0)).isFalse()
        }
    }

    @Test
    fun testToString() {
        assertThat(AgpVersion(1, 0).toString()).isEqualTo("1.0.0");
        assertThat(AgpVersion(1, 0, 0).toString()).isEqualTo("1.0.0");
        assertThat(AgpVersion.parse("1.23.456").toString()).isEqualTo("1.23.456")
        assertThat(AgpVersion.parse("1.2.3-alpha1").toString()).isEqualTo("1.2.3-alpha1")
        assertThat(AgpVersion.parse("1.2.3-beta2").toString()).isEqualTo("1.2.3-beta2")
        assertThat(AgpVersion.parse("1.2.3-rc3").toString()).isEqualTo("1.2.3-rc3")
        assertThat(AgpVersion.parse("3.0.0-alpha1").toString()).isEqualTo("3.0.0-alpha1")
        assertThat(AgpVersion.parse("3.0.0-beta2").toString()).isEqualTo("3.0.0-beta2")
        assertThat(AgpVersion.parse("3.0.0-rc3").toString()).isEqualTo("3.0.0-rc3")
        assertThat(AgpVersion.parse("3.1.0-alpha01").toString()).isEqualTo("3.1.0-alpha01")
        assertThat(AgpVersion.parse("3.1.0-beta2").toString()).isEqualTo("3.1.0-beta2")
        assertThat(AgpVersion.parse("3.1.0-rc03").toString()).isEqualTo("3.1.0-rc03")
        assertThat(AgpVersion.parse("3.2.0-alpha01").toString()).isEqualTo("3.2.0-alpha01")
        assertThat(AgpVersion.parse("3.2.0-beta02").toString()).isEqualTo("3.2.0-beta02")
        assertThat(AgpVersion.parse("3.2.0-rc03").toString()).isEqualTo("3.2.0-rc03")
        assertThat(AgpVersion.parse("1.2.3-dev").toString()).isEqualTo("1.2.3-dev")
    }

    private fun convertAGPVersionToString(version: AgpVersion?): String {
        assertThat(version).isNotNull()
        version!!.run {
            // We reimplement this instead of using version.toString() directly to prevent the
            // AgpVersion class from "remembering" the input string without actually parsing it.
            // We deliberately do not do field width formatting here (e.g. -alpha1 not -alpha01)
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

    private fun compareAgpVersionsIgnoringQualifiers(version1: String, version2: String): Int =
        AgpVersion.parse(version1).compareIgnoringQualifiers(AgpVersion.parse(version2))
}
