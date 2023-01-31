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

        // large (outside Int and Long range representation)
        assertThat(Version.parse("1.2147483648").toString()).isEqualTo("1.2147483648")
        assertThat(Version.parse("2.4294967296").toString()).isEqualTo("2.4294967296")
        assertThat(Version.parse("3.9223372036854775808").toString())
            .isEqualTo("3.9223372036854775808")
        assertThat(Version.parse("4.18446744073709551616").toString())
            .isEqualTo("4.18446744073709551616")
        assertThat(Version.parse("1.2212222019").toString()).isEqualTo("1.2212222019")
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
    fun testVersionOrderLeadingZeroIndifference() {
        listOf("1.9", "01.9", "1.09", "01.09").let { vss ->
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

    @Test
    fun testPrefixInfimum() {
        assertThat(Version.prefixInfimum("1").toString())
            .isEqualTo("prefix infimum version for \"1\"")
    }

    @Test
    fun testPrefixInfimumEquality() {
        assertThat(Version.prefixInfimum("1")).isEqualTo(Version.prefixInfimum("1"))
        assertThat(Version.prefixInfimum("1").hashCode()).isEqualTo(Version.prefixInfimum("1").hashCode())
        assertThat(Version.prefixInfimum("1")).isNotEqualTo(Version.parse("1"))
        assertThat(Version.parse("1")).isNotEqualTo(Version.prefixInfimum("1"))
        assertThat(Version.parse("1-dev")).isNotEqualTo(Version.prefixInfimum("1"))
        assertThat(Version.prefixInfimum("1")).isNotEqualTo(Version.parse("1-dev"))

        // prefixInfimum of a dev version is equivalent to prefixInfimum of the base version
        assertThat(Version.prefixInfimum("1-dev")).isEqualTo(Version.prefixInfimum("1"))
        assertThat(Version.prefixInfimum("1")).isEqualTo(Version.prefixInfimum("1-dev"))
    }

    @Test
    fun testPrefixInfimumEquivalence() {
        listOf("1+dev-dev_dev", "1.dev-dev", "1+dev", "1").let { vss ->
            vss.forEach { v1s ->
                val v1 = Version.prefixInfimum(v1s)
                vss.forEach { v2s ->
                    val v2 = Version.prefixInfimum(v2s)
                    assertThat(v1).isEquivalentAccordingToCompareTo(v2)
                    assertThat(v2).isEquivalentAccordingToCompareTo(v1)
                    assertThat(v1).isEqualTo(v2)
                    assertThat(v2).isEqualTo(v1)
                }
            }
        }
    }

    @Test
    fun testPrefixInfimumComparison() {
        listOf("1+dev-dev_dev", "1.dev-dev", "1+dev", "1").let { vss ->
            vss.forEachIndexed { i1, v1s ->
                val v1 = Version.parse(v1s)
                vss.forEachIndexed { i2, v2s ->
                    val v2 = Version.prefixInfimum(v2s)
                    assertThat(v1).isGreaterThan(v2)
                    assertThat(v2).isLessThan(v1)
                    assertThat(v1).isNotEqualTo(v2)
                    assertThat(v2).isNotEqualTo(v1)
                }
            }
        }
    }

    @Test
    fun testLeastPrefixInfimum() {
        val zero = Version.prefixInfimum("dev")
        // Why this assertion (and why `zero`)?  The version denoted by the empty string is, in
        // practice, an ordinary non-numeric part that sorts lexicographically before all other
        // non-special non-numeric parts; its prefix infimum is the earliest version starting with
        // any non-special non-numeric part.  However, since it is a non-special non-numeric part,
        // there is one part that compares less than it: the special non-numeric part denoted by
        // "dev", and consequently the least prefix infimum of all is the prefix infimum of "dev".
        assertThat(zero).isLessThan(Version.prefixInfimum(""))
    }

    @Test
    fun testMajor() {
        assertThat(Version.parse("").major).isNull()
        assertThat(Version.parse("dev").major).isNull()
        assertThat(Version.parse("alpha").major).isNull()
        assertThat(Version.parse("rc").major).isNull()
        assertThat(Version.parse("1").major).isEqualTo(1)
        assertThat(Version.parse("2.3").major).isEqualTo(2)
        assertThat(Version.parse("4.5.6").major).isEqualTo(4)
        assertThat(Version.parse("7.8.9.10").major).isEqualTo(7)
        assertThat(Version.parse("2147483648").major).isNull()
        assertThat(Version.parse("4294967296").major).isNull()
        assertThat(Version.parse("9223372036854775808").major).isNull()
        assertThat(Version.parse("18446744073709551616").major).isNull()
    }

    @Test
    fun testMinor() {
        assertThat(Version.parse("dev").minor).isNull()
        assertThat(Version.parse("").minor).isNull()
        assertThat(Version.parse("alpha").minor).isNull()
        assertThat(Version.parse("rc").minor).isNull()
        assertThat(Version.parse("1.dev").minor).isNull()
        assertThat(Version.parse("1.").minor).isNull()
        assertThat(Version.parse("1.alpha1").minor).isNull()
        assertThat(Version.parse("1.rc1").minor).isNull()
        assertThat(Version.parse(".1").minor).isNull()
        assertThat(Version.parse("alpha.1").minor).isNull()
        assertThat(Version.parse("rc.1").minor).isNull()
        assertThat(Version.parse("1").minor).isNull()
        assertThat(Version.parse("2.3").minor).isEqualTo(3)
        assertThat(Version.parse("4.5.6").minor).isEqualTo(5)
        assertThat(Version.parse("7.8.9.10").minor).isEqualTo(8)
        assertThat(Version.parse("1.2147483648").minor).isNull()
        assertThat(Version.parse("1.4294967296").minor).isNull()
        assertThat(Version.parse("1.9223372036854775808").minor).isNull()
        assertThat(Version.parse("1.18446744073709551616").minor).isNull()
    }

    @Test
    fun testMicro() {
        assertThat(Version.parse("dev").micro).isNull()
        assertThat(Version.parse("").micro).isNull()
        assertThat(Version.parse("alpha").micro).isNull()
        assertThat(Version.parse("rc").micro).isNull()
        assertThat(Version.parse("1.dev").micro).isNull()
        assertThat(Version.parse("1.").micro).isNull()
        assertThat(Version.parse("1.alpha1").micro).isNull()
        assertThat(Version.parse("1.rc1").micro).isNull()
        assertThat(Version.parse(".1").micro).isNull()
        assertThat(Version.parse("alpha.1").micro).isNull()
        assertThat(Version.parse("rc.1").micro).isNull()
        assertThat(Version.parse("0..1").micro).isNull()
        assertThat(Version.parse("0.alpha.1").micro).isNull()
        assertThat(Version.parse("0.rc.1").micro).isNull()
        assertThat(Version.parse("1").micro).isNull()
        assertThat(Version.parse("2.3").micro).isNull()
        assertThat(Version.parse("4.5.6").micro).isEqualTo(6)
        assertThat(Version.parse("7.8.9.10").micro).isEqualTo(9)
        assertThat(Version.parse("1.2.2147483648").micro).isNull()
        assertThat(Version.parse("1.2.4294967296").micro).isNull()
        assertThat(Version.parse("1.2.9223372036854775808").micro).isNull()
        assertThat(Version.parse("1.2.18446744073709551616").micro).isNull()
    }

    @Test
    fun testNextPrefix() {
        assertThat(Version.parse("1.dev").nextPrefix()).isEqualTo(Version.prefixInfimum("1."))
        assertThat(Version.parse("1.").nextPrefix()).isEqualTo(Version.prefixInfimum("1.\u0000"))
        assertThat(Version.parse("1.a").nextPrefix()).isEqualTo(Version.prefixInfimum("1.a\u0000"))
        assertThat(Version.parse("1.rc").nextPrefix())
            .isEqualTo(Version.prefixInfimum("1.snapshot"))
        assertThat(Version.parse("1.snapshot").nextPrefix())
            .isEqualTo(Version.prefixInfimum("1.final"))
        assertThat(Version.parse("1.final").nextPrefix()).isEqualTo(Version.prefixInfimum("1.ga"))
        assertThat(Version.parse("1.ga").nextPrefix()).isEqualTo(Version.prefixInfimum("1.release"))
        assertThat(Version.parse("1.release").nextPrefix()).isEqualTo(Version.prefixInfimum("1.sp"))
        assertThat(Version.parse("1.sp").nextPrefix()).isEqualTo(Version.prefixInfimum("1.0"))
        assertThat(Version.parse("1.0").nextPrefix()).isEqualTo(Version.prefixInfimum("1.1"))
        assertThat(Version.parse("1.9").nextPrefix()).isEqualTo(Version.prefixInfimum("1.10"))
        assertThat(Version.parse("1.2147483648").nextPrefix())
            .isEqualTo(Version.prefixInfimum("1.2147483649"))
        assertThat(Version.parse("2.4294967296").nextPrefix())
            .isEqualTo(Version.prefixInfimum("2.4294967297"))
        assertThat(Version.parse("3.9223372036854775808").nextPrefix())
            .isEqualTo(Version.prefixInfimum("3.9223372036854775809"))
        assertThat(Version.parse("4.18446744073709551616").nextPrefix())
            .isEqualTo(Version.prefixInfimum("4.18446744073709551617"))
        assertThat(Version.parse("1.2212222019").nextPrefix())
            .isEqualTo(Version.prefixInfimum("1.2212222020"))
    }

    @Test
    fun testNextPrefixSmaller() {
        assertThat(Version.parse("1.dev").nextPrefix(1)).isEqualTo(Version.prefixInfimum("2"))
    }

    @Test
    fun testNextPrefixLarger() {
        assertThat(Version.parse("1").nextPrefix(2)).isEqualTo(Version.prefixInfimum("1.0"))
    }

    @Test
    fun testIsSnapshot() {
        assertThat(Version.parse("2").isSnapshot).isFalse()
        assertThat(Version.parse("2-alpha1").isSnapshot).isFalse()
        assertThat(Version.parse("2-alpha1-SNAPSHOT").isSnapshot).isTrue()
        assertThat(Version.parse("2-SNAPSHOT").isSnapshot).isTrue()
        assertThat(Version.parse("2-snapshot").isSnapshot).isTrue()
        assertThat(Version.parse("2-dev").isSnapshot).isTrue()
        assertThat(Version.parse("1.2").isSnapshot).isFalse()
        assertThat(Version.parse("1.2-alpha3").isSnapshot).isFalse()
        assertThat(Version.parse("1.2-alpha3-SNAPSHOT").isSnapshot).isTrue()
        assertThat(Version.parse("1.2-SNAPSHOT").isSnapshot).isTrue()
        assertThat(Version.parse("1.2.3").isSnapshot).isFalse()
        assertThat(Version.parse("1.2.3-alpha4").isSnapshot).isFalse()
        assertThat(Version.parse("1.2.3-alpha-4").isSnapshot).isFalse()
        assertThat(Version.parse("1.2.3-alpha4-SNAPSHOT").isSnapshot).isTrue()
        assertThat(Version.parse("1.2.3-alpha-4-SNAPSHOT").isSnapshot).isTrue()
        assertThat(Version.parse("1.2.3-SNAPSHOT").isSnapshot).isTrue()
        assertThat(Version.parse("1.2.3.4").isSnapshot).isFalse()
        assertThat(Version.parse("1.2.3.4.5-SNAPSHOT").isSnapshot).isTrue()
        assertThat(Version.parse("1.2.3.4.5.6-alpha9-SNAPSHOT").isSnapshot).isTrue()
        assertThat(Version.parse("7.0.0-dev03").isSnapshot).isFalse()

        assertThat(Version.parse("1-SNAPSHOT.3").isSnapshot).isFalse()
        assertThat(Version.parse("1-dev.3").isSnapshot).isFalse()
    }

    @Test
    fun testIsPreview() {
        assertThat(Version.parse("2").isPreview).isFalse()
        assertThat(Version.parse("2-alpha1").isPreview).isTrue()
        assertThat(Version.parse("2-alpha1-SNAPSHOT").isPreview).isTrue()
        assertThat(Version.parse("2-SNAPSHOT").isPreview).isTrue()
        assertThat(Version.parse("2-snapshot").isPreview).isTrue()
        assertThat(Version.parse("2-dev").isPreview).isTrue()
        assertThat(Version.parse("1.2").isPreview).isFalse()
        assertThat(Version.parse("1.2-alpha3").isPreview).isTrue()
        assertThat(Version.parse("1.2-alpha3-SNAPSHOT").isPreview).isTrue()
        assertThat(Version.parse("1.2-SNAPSHOT").isPreview).isTrue()
        assertThat(Version.parse("1.2.3").isPreview).isFalse()
        assertThat(Version.parse("1.2.3-alpha4").isPreview).isTrue()
        assertThat(Version.parse("1.2.3-alpha-4").isPreview).isTrue()
        assertThat(Version.parse("1.2.3-alpha4-SNAPSHOT").isPreview).isTrue()
        assertThat(Version.parse("1.2.3-alpha-4-SNAPSHOT").isPreview).isTrue()
        assertThat(Version.parse("1.2.3-SNAPSHOT").isPreview).isTrue()
        assertThat(Version.parse("1.2.3.4").isPreview).isFalse()
        assertThat(Version.parse("1.2.3.4.5-SNAPSHOT").isPreview).isTrue()
        assertThat(Version.parse("1.2.3.4.5.6-alpha9-SNAPSHOT").isPreview).isTrue()
        assertThat(Version.parse("7.0.0-dev03").isPreview).isTrue()

        assertThat(Version.parse("1-SNAPSHOT.3").isPreview).isTrue()
        assertThat(Version.parse("1-dev.3").isPreview).isTrue()
    }

    @Test
    fun testIsPrefixInfimum() {
        assertThat(Version.parse("1").isPrefixInfimum).isFalse()
        assertThat(Version.parse("1-dev").isPrefixInfimum).isFalse()
        assertThat(Version.prefixInfimum("1").isPrefixInfimum).isTrue()
        assertThat(Version.prefixInfimum("1-dev").isPrefixInfimum).isTrue()
    }

    @Test
    fun testPrefixVersion() {
        assertThat(Version.parse("1").prefixVersion()).isEqualTo(Version.parse("1"))
        assertThat(Version.prefixInfimum("1").prefixVersion()).isEqualTo(Version.parse("1"))
        assertThat(Version.parse("1-dev").prefixVersion()).isEqualTo(Version.parse("1-dev"))
        assertThat(Version.prefixInfimum("1-dev").prefixVersion()).isEqualTo(Version.parse("1-dev"))
    }
}
