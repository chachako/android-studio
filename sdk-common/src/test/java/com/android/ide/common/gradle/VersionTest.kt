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
package com.android.ide.common.gradle

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class VersionTest {
    @Test
    fun testParseExactVersion() {
        assertThat(Version.parse("1.3").toString()).isEqualTo("1.3")
        assertThat(Version.parse("1.3.0-beta3").toString()).isEqualTo("1.3.0-beta3")
        assertThat(Version.parse("1.0-20150201.131010-1").toString())
            .isEqualTo("1.0-20150201.131010-1")

        // case-preserving even of case-insensitive parts
        assertThat(Version.parse("1.3-DeV").toString()).isEqualTo("1.3-DeV")
        assertThat(Version.parse("1.3-Rc").toString()).isEqualTo("1.3-Rc")
        assertThat(Version.parse("1.3-SnApShOt").toString()).isEqualTo("1.3-SnApShOt")
        assertThat(Version.parse("1.3-FiNaL").toString()).isEqualTo("1.3-FiNaL")
        assertThat(Version.parse("1.3-Ga").toString()).isEqualTo("1.3-Ga")
        assertThat(Version.parse("1.3-ReLeAsE").toString()).isEqualTo("1.3-ReLeAsE")
        assertThat(Version.parse("1.3-Sp").toString()).isEqualTo("1.3-Sp")

        // string-preserving of leading zeros
        assertThat(Version.parse("1.3-alpha01").toString()).isEqualTo("1.3-alpha01")

        // empty parts
        assertThat(Version.parse(".-+_").toString()).isEqualTo(".-+_")
        assertThat(Version.parse("").toString()).isEqualTo("")
    }

    @Test
    fun testVersionOrderingNoSeparators() {
        val vDots = Version.parse("1.a.1")
        val vNoDots = Version.parse("1a1")
        assertThat(vDots).isEqualTo(vNoDots)
        assertThat(vNoDots).isEqualTo(vDots)
        assertThat(vDots).isEquivalentAccordingToCompareTo(vNoDots)
        assertThat(vNoDots).isEquivalentAccordingToCompareTo(vDots)
        assertThat(vNoDots.hashCode()).isEqualTo(vDots.hashCode())
    }

    @Test
    fun testVersionOrderingSeparatorIndifference() {
        listOf("1.a.1", "1-a+1", "1.a-1", "1a1").let { vss ->
            vss.forEach { v1s ->
                val v1 = Version.parse(v1s)
                vss.forEach { v2s ->
                    val v2 = Version.parse(v2s)
                    assertThat(v1).isEquivalentAccordingToCompareTo(v2)
                    assertThat(v1).isEqualTo(v2)
                    assertThat(v1.hashCode()).isEqualTo(v2.hashCode())
                }
            }
        }
    }

    @Test
    fun testBothNumericHigher() {
        assertThat(Version.parse("1.1")).isLessThan(Version.parse("1.2"))
    }

    @Test
    fun testNumericHigherThanNonNumeric() {
        assertThat(Version.parse("1.a")).isLessThan(Version.parse("1.1"))
    }

    @Test
    fun testNonNumericLexicographic() {
        listOf("1.A", "1.B", "1.a", "1.b").let {
            it.zipWithNext().forEach { pair ->
                assertThat(Version.parse(pair.first)).isLessThan(Version.parse(pair.second))
            }
        }
    }

    @Test
    fun testExtraNumericPartHigher() {
        assertThat(Version.parse("1.1")).isLessThan(Version.parse("1.1.0"))
    }

    @Test
    fun testExtraNonNumericPartLower() {
        assertThat(Version.parse("1.1.a")).isLessThan(Version.parse("1.1"))
    }

    @Test
    fun testDevLowerThanOthers() {
        listOf("1.0-dev", "1.0-ALPHA", "1.0-alpha", "1.0-rc").let {
            it.zipWithNext().forEach { pair ->
                assertThat(Version.parse(pair.first)).isLessThan(Version.parse(pair.second))
            }
        }
    }

    @Test
    fun testSpecialHigherThanOtherNonNumeric() {
        listOf("1.0-zeta", "1.0-rc", "1.0-snapshot", "1.0-final",
               "1.0-ga", "1.0-release", "1.0-sp", "1.0")
            .let {
                it.zipWithNext().forEach {pair ->
                    assertThat(Version.parse(pair.first)).isLessThan(Version.parse(pair.second))
                }
        }
    }

    @Test
    fun testSpecialNotCaseSensitive() {
        val v1 = Version.parse("1.0-RC-1")
        val v2 = Version.parse("1.0.rc.1")
        assertThat(v1).isEqualTo(v2)
        assertThat(v2).isEqualTo(v1)
        assertThat(v1).isEquivalentAccordingToCompareTo(v2)
        assertThat(v2).isEquivalentAccordingToCompareTo(v1)
        assertThat(v1.hashCode()).isEqualTo(v2.hashCode())
    }

    @Test
    fun testNonSpecialCaseSensitive() {
        val v1 = Version.parse("1.0-ALPHA-1")
        val v2 = Version.parse("1.0.alpha.1")
        assertThat(v1).isLessThan(v2)
        assertThat(v1).isNotEqualTo(v2)
    }

    @Test
    fun testNumericEquivalence() {
        val v1 = Version.parse("1.0-alpha01")
        val v2 = Version.parse("1.0-alpha1")
        assertThat(v1).isEquivalentAccordingToCompareTo(v2)
        assertThat(v1).isEqualTo(v2)
        assertThat(v1.hashCode()).isEqualTo(v2.hashCode())
    }

    @Test
    fun testRelationProperties() {
        listOf("1-sp.0", "1.0-dev", "1.0-", "1.0-alpha", "1.0-zeta", "1.0-rc", "1.0-snapshot",
               "1.0-final", "1.0-ga", "1.0-release", "1.0-sp", "1.0", "1.0.0")
            .let { specifiers ->
                specifiers.forEachIndexed { i1, s1 ->
                    val v1 = Version.parse(s1)
                    specifiers.forEachIndexed { i2, s2 ->
                        val v2 = Version.parse(s2)
                        if (i1 == i2) {
                            assertThat(v1).isEqualTo(v2)
                            assertThat(v2).isEqualTo(v1)
                            assertThat(v1).isEquivalentAccordingToCompareTo(v2)
                            assertThat(v2).isEquivalentAccordingToCompareTo(v1)
                            assertThat(v1.hashCode()).isEqualTo(v2.hashCode())
                        }
                        else if (i1 < i2) {
                            assertThat(v1).isLessThan(v2)
                            assertThat(v1).isNotEqualTo(v2)
                        }
                        else {
                            assertThat(v1).isGreaterThan(v2)
                            assertThat(v1).isNotEqualTo(v2)
                        }
                    }
                }
            }
    }

    @Test
    fun testIsNotEqualToString() {
        "1.0".let {
            assertThat(it).isNotEqualTo(Version.parse(it))
            assertThat(Version.parse(it)).isNotEqualTo(it)
        }
    }
}
