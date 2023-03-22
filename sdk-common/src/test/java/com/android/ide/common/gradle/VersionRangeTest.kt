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

import com.google.common.collect.Range
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class VersionRangeTest {
    @Test
    fun testParseAll() {
        assertThat(VersionRange.parse("+").toString()).isEqualTo("+")
        assertThat(VersionRange.parse("+").toIdentifier()).isEqualTo("+")
        assertThat(VersionRange.parse("[,]").toString()).isEqualTo("+")
        assertThat(VersionRange.parse("[,]").toIdentifier()).isEqualTo("+")
        assertThat(VersionRange.parse("(,]").toString()).isEqualTo("+")
        assertThat(VersionRange.parse("(,]").toIdentifier()).isEqualTo("+")
        assertThat(VersionRange.parse("],]").toString()).isEqualTo("+")
        assertThat(VersionRange.parse("],]").toIdentifier()).isEqualTo("+")
        assertThat(VersionRange.parse("[,)").toString()).isEqualTo("+")
        assertThat(VersionRange.parse("[,)").toIdentifier()).isEqualTo("+")
        assertThat(VersionRange.parse("(,)").toString()).isEqualTo("+")
        assertThat(VersionRange.parse("(,)").toIdentifier()).isEqualTo("+")
        assertThat(VersionRange.parse("],)").toString()).isEqualTo("+")
        assertThat(VersionRange.parse("],)").toIdentifier()).isEqualTo("+")
        assertThat(VersionRange.parse("[,[").toString()).isEqualTo("+")
        assertThat(VersionRange.parse("[,[").toIdentifier()).isEqualTo("+")
        assertThat(VersionRange.parse("(,[").toString()).isEqualTo("+")
        assertThat(VersionRange.parse("(,[").toIdentifier()).isEqualTo("+")
        assertThat(VersionRange.parse("],[").toString()).isEqualTo("+")
        assertThat(VersionRange.parse("],[").toIdentifier()).isEqualTo("+")
    }

    @Test
    fun testParsePrefixRange() {
        assertThat(VersionRange.parse("1.+").toString()).isEqualTo("1.+")
        assertThat(VersionRange.parse("1.+").toIdentifier()).isEqualTo("1.+")
        assertThat(VersionRange.parse("1.2.+").toString()).isEqualTo("1.2.+")
        assertThat(VersionRange.parse("1.2.+").toIdentifier()).isEqualTo("1.2.+")
        assertThat(VersionRange.parse("1.rc.0.sp.dev.+").toString()).isEqualTo("1.rc.0.sp.dev.+")
        assertThat(VersionRange.parse("1.rc.0.sp.dev.+").toIdentifier()).isEqualTo("1.rc.0.sp.dev.+")
        assertThat(VersionRange.parse(".+").toString()).isEqualTo(".+")
        assertThat(VersionRange.parse(".+").toIdentifier()).isEqualTo(".+")
    }

    @Test
    fun testParseMavenRange() {
        fun Char.canonicalOpen() = if (this == '[') '[' else '('
        fun Char.canonicalClose() = if (this == ']') ']' else ')'
        for (open in listOf('[', ']', '(')) {
            for (close in listOf(']', '[', ')')) {
                assertThat(VersionRange.parse("${open}1.0,${close}").toString())
                    .isEqualTo("${open.canonicalOpen()}1.0,)")
                assertThat(VersionRange.parse("${open}1.0,${close}").toIdentifier())
                    .isEqualTo("${open.canonicalOpen()}1.0,)")
                assertThat(VersionRange.parse("${open},2.0${close}").toString())
                    .isEqualTo("(,2.0${close.canonicalClose()}")
                assertThat(VersionRange.parse("${open},2.0${close}").toIdentifier())
                    .isEqualTo("(,2.0${close.canonicalClose()}")
                assertThat(VersionRange.parse("${open}1.0,2.0${close}").toString())
                    .isEqualTo("${open.canonicalOpen()}1.0,2.0${close.canonicalClose()}")
                assertThat(VersionRange.parse("${open}1.0,2.0${close}").toIdentifier())
                    .isEqualTo("${open.canonicalOpen()}1.0,2.0${close.canonicalClose()}")
                // test also the edge case where the top is the next prefix (1.0 -> 1.1)
                assertThat(VersionRange.parse("${open}1.0,1.1${close}").toString())
                    .isEqualTo("${open.canonicalOpen()}1.0,1.1${close.canonicalClose()}")
                assertThat(VersionRange.parse("${open}1.0,1.1${close}").toIdentifier())
                    .isEqualTo("${open.canonicalOpen()}1.0,1.1${close.canonicalClose()}")
            }
        }
    }

    @Test
    fun testExactRangeNumeric() {
        VersionRange.parse("1.0").let { range ->
            assertThat(range.contains(Version.parse("1.0"))).isTrue()
            assertThat(range.contains(Version.parse("1.0.0"))).isFalse()
            assertThat(range.contains(Version.parse("1.1.alpha"))).isFalse()
            assertThat(range.contains(Version.parse("2.0"))).isFalse()
            assertThat(range.contains(Version.parse("1.0-rc"))).isFalse()
            assertThat(range.contains(Version.parse("1.alpha"))).isFalse()
            assertThat(range.contains(Version.parse("1-dev"))).isFalse()
            assertThat(range.contains(Version.parse("0.99"))).isFalse()
        }
    }

    @Test
    fun testExactRangeNonNumeric() {
        VersionRange.parse("1.0-alpha").let { range ->
            assertThat(range.contains(Version.parse("1.0-alpha"))).isTrue()
            assertThat(range.contains(Version.parse("1.0-alpha\u0000"))).isFalse()
            assertThat(range.contains(Version.parse("1.0-alphaA"))).isFalse()
            assertThat(range.contains(Version.parse("1.0-alphb"))).isFalse()
            assertThat(range.contains(Version.parse("1.0.0"))).isFalse()
            assertThat(range.contains(Version.parse("1.0-alpha.dev"))).isFalse()
            assertThat(range.contains(Version.parse("1.0-alpha.alpha"))).isFalse()
            assertThat(range.contains(Version.parse("1.0-alphZ"))).isFalse()
            assertThat(range.contains(Version.parse("1.0-dev"))).isFalse()
            assertThat(range.contains(Version.parse("0.99"))).isFalse()
        }
    }

    @Test
    fun testExactRangeNumericPlusSeparator() {
        VersionRange.parse("1+0").let { range ->
            assertThat(range.contains(Version.parse("1.0"))).isTrue()
            assertThat(range.contains(Version.parse("1.0.0"))).isFalse()
            assertThat(range.contains(Version.parse("1.1.alpha"))).isFalse()
            assertThat(range.contains(Version.parse("2.0"))).isFalse()
            assertThat(range.contains(Version.parse("1.0-rc"))).isFalse()
            assertThat(range.contains(Version.parse("1.alpha"))).isFalse()
            assertThat(range.contains(Version.parse("1-dev"))).isFalse()
            assertThat(range.contains(Version.parse("0.99"))).isFalse()
        }
    }

    @Test
    fun testPrefixUniversalRange() {
        VersionRange.parse("+").let { range ->
            assertThat(range.contains(Version.parse("1.0"))).isTrue()
            assertThat(range.contains(Version.parse("1.0.0"))).isTrue()
            assertThat(range.contains(Version.parse("1.1.alpha"))).isTrue()
            assertThat(range.contains(Version.parse("2.0"))).isTrue()
            assertThat(range.contains(Version.parse("2"))).isTrue()
            assertThat(range.contains(Version.parse("2.dev"))).isTrue()
            assertThat(range.contains(Version.parse("1.0-rc"))).isTrue()
            assertThat(range.contains(Version.parse("1.alpha"))).isTrue()
            assertThat(range.contains(Version.parse("1-dev"))).isTrue()
            assertThat(range.contains(Version.parse("0.99"))).isTrue()
            assertThat(range.contains(Version.parse("dev.dev"))).isTrue()
        }
    }

    @Test
    fun testPrefixRangeNumeric() {
        VersionRange.parse("1.+").let { range ->
            assertThat(range.contains(Version.parse("1.0"))).isTrue()
            assertThat(range.contains(Version.parse("1.0.0"))).isTrue()
            assertThat(range.contains(Version.parse("1.1.alpha"))).isTrue()
            assertThat(range.contains(Version.parse("2.0"))).isFalse()
            assertThat(range.contains(Version.parse("2"))).isFalse()
            assertThat(range.contains(Version.parse("2.dev"))).isFalse()
            assertThat(range.contains(Version.parse("1.0-rc"))).isTrue()
            assertThat(range.contains(Version.parse("1.alpha"))).isTrue()
            assertThat(range.contains(Version.parse("1-dev"))).isTrue()
            assertThat(range.contains(Version.parse("0.99"))).isFalse()
        }
    }

    @Test
    fun testMavenUniversalRange() {
        VersionRange.parse("[,]").let { range ->
            assertThat(range.contains(Version.parse("1.0"))).isTrue()
            assertThat(range.contains(Version.parse("1.0.0"))).isTrue()
            assertThat(range.contains(Version.parse("1.1.alpha"))).isTrue()
            assertThat(range.contains(Version.parse("2.0.dev"))).isTrue()
            assertThat(range.contains(Version.parse("2.0"))).isTrue()
            assertThat(range.contains(Version.parse("2.0.0"))).isTrue()
            assertThat(range.contains(Version.parse("2"))).isTrue()
            assertThat(range.contains(Version.parse("2.dev"))).isTrue()
            assertThat(range.contains(Version.parse("1.0-rc"))).isTrue()
            assertThat(range.contains(Version.parse("1.alpha"))).isTrue()
            assertThat(range.contains(Version.parse("1-dev"))).isTrue()
            assertThat(range.contains(Version.parse("0.99"))).isTrue()
            assertThat(range.contains(Version.parse("dev.dev"))).isTrue()
        }
    }

    @Test
    fun testMavenAtMostRange() {
        VersionRange.parse("[,1.0]").let { range ->
            assertThat(range.contains(Version.parse("1.0"))).isTrue()
            assertThat(range.contains(Version.parse("1.0.0"))).isFalse()
            assertThat(range.contains(Version.parse("1.1.alpha"))).isFalse()
            assertThat(range.contains(Version.parse("2.0.dev"))).isFalse()
            assertThat(range.contains(Version.parse("2.0"))).isFalse()
            assertThat(range.contains(Version.parse("2.0.0"))).isFalse()
            assertThat(range.contains(Version.parse("2"))).isFalse()
            assertThat(range.contains(Version.parse("2.dev"))).isFalse()
            assertThat(range.contains(Version.parse("1.0-rc"))).isTrue()
            assertThat(range.contains(Version.parse("1.alpha"))).isTrue()
            assertThat(range.contains(Version.parse("1-dev"))).isTrue()
            assertThat(range.contains(Version.parse("0.99"))).isTrue()
            assertThat(range.contains(Version.parse("dev.dev"))).isTrue()
        }
    }

    @Test
    fun testMavenUpperPrefixExcludeRange() {
        for (char in listOf(')', '[')) {
            VersionRange.parse("[,1.0$char").let { range ->
                assertThat(range.contains(Version.parse("1.0"))).isFalse()
                assertThat(range.contains(Version.parse("1.0.0"))).isFalse()
                assertThat(range.contains(Version.parse("1.1.alpha"))).isFalse()
                assertThat(range.contains(Version.parse("2.0.dev"))).isFalse()
                assertThat(range.contains(Version.parse("2.0"))).isFalse()
                assertThat(range.contains(Version.parse("2.0.0"))).isFalse()
                assertThat(range.contains(Version.parse("2"))).isFalse()
                assertThat(range.contains(Version.parse("2.dev"))).isFalse()
                assertThat(range.contains(Version.parse("1.0-rc"))).isFalse()
                assertThat(range.contains(Version.parse("1.alpha"))).isTrue()
                assertThat(range.contains(Version.parse("1-dev"))).isTrue()
                assertThat(range.contains(Version.parse("0.99"))).isTrue()
                assertThat(range.contains(Version.parse("dev.dev"))).isTrue()
            }
        }
    }

    @Test
    fun testMavenAtLeastRange() {
        VersionRange.parse("[1.0,]").let { range ->
            assertThat(range.contains(Version.parse("1.0"))).isTrue()
            assertThat(range.contains(Version.parse("1.0.0"))).isTrue()
            assertThat(range.contains(Version.parse("1.1.alpha"))).isTrue()
            assertThat(range.contains(Version.parse("2.0.dev"))).isTrue()
            assertThat(range.contains(Version.parse("2.0"))).isTrue()
            assertThat(range.contains(Version.parse("2.0.0"))).isTrue()
            assertThat(range.contains(Version.parse("2"))).isTrue()
            assertThat(range.contains(Version.parse("2.dev"))).isTrue()
            assertThat(range.contains(Version.parse("1.0-rc"))).isFalse()
            assertThat(range.contains(Version.parse("1.alpha"))).isFalse()
            assertThat(range.contains(Version.parse("1-dev"))).isFalse()
            assertThat(range.contains(Version.parse("0.99"))).isFalse()
            assertThat(range.contains(Version.parse("dev.dev"))).isFalse()
        }
    }

    @Test
    fun testMavenGreaterThanRange() {
        for (char in listOf(']', '(')) {
            VersionRange.parse("${char}1.0,]").let { range ->
                assertThat(range.contains(Version.parse("1.0"))).isFalse()
                assertThat(range.contains(Version.parse("1.0.0"))).isTrue()
                assertThat(range.contains(Version.parse("1.1.alpha"))).isTrue()
                assertThat(range.contains(Version.parse("2.0.dev"))).isTrue()
                assertThat(range.contains(Version.parse("2.0"))).isTrue()
                assertThat(range.contains(Version.parse("2.0.0"))).isTrue()
                assertThat(range.contains(Version.parse("2"))).isTrue()
                assertThat(range.contains(Version.parse("2.dev"))).isTrue()
                assertThat(range.contains(Version.parse("1.0-rc"))).isFalse()
                assertThat(range.contains(Version.parse("1.alpha"))).isFalse()
                assertThat(range.contains(Version.parse("1-dev"))).isFalse()
                assertThat(range.contains(Version.parse("0.99"))).isFalse()
                assertThat(range.contains(Version.parse("dev.dev"))).isFalse()
            }
        }
    }

    @Test
    fun testMavenClosedRange() {
        VersionRange.parse("[1.0,2.0]").let { range ->
            assertThat(range.contains(Version.parse("1.0"))).isTrue()
            assertThat(range.contains(Version.parse("1.0.0"))).isTrue()
            assertThat(range.contains(Version.parse("1.1.alpha"))).isTrue()
            assertThat(range.contains(Version.parse("2.0.dev"))).isTrue()
            assertThat(range.contains(Version.parse("2.0"))).isTrue()
            assertThat(range.contains(Version.parse("2.0.0"))).isFalse()
            assertThat(range.contains(Version.parse("2"))).isTrue()
            assertThat(range.contains(Version.parse("2.dev"))).isTrue()
            assertThat(range.contains(Version.parse("1.0-rc"))).isFalse()
            assertThat(range.contains(Version.parse("1.alpha"))).isFalse()
            assertThat(range.contains(Version.parse("1-dev"))).isFalse()
            assertThat(range.contains(Version.parse("0.99"))).isFalse()
            assertThat(range.contains(Version.parse("dev.dev"))).isFalse()
        }
    }

    @Test
    fun testMavenOpenClosedRange() {
        for (char in listOf(']', '(')) {
            VersionRange.parse("${char}1.0,2.0]").let { range ->
                assertThat(range.contains(Version.parse("1.0"))).isFalse()
                assertThat(range.contains(Version.parse("1.0.0"))).isTrue()
                assertThat(range.contains(Version.parse("1.1.alpha"))).isTrue()
                assertThat(range.contains(Version.parse("2.0.dev"))).isTrue()
                assertThat(range.contains(Version.parse("2.0"))).isTrue()
                assertThat(range.contains(Version.parse("2.0.0"))).isFalse()
                assertThat(range.contains(Version.parse("2"))).isTrue()
                assertThat(range.contains(Version.parse("2.dev"))).isTrue()
                assertThat(range.contains(Version.parse("1.0-rc"))).isFalse()
                assertThat(range.contains(Version.parse("1.alpha"))).isFalse()
                assertThat(range.contains(Version.parse("1-dev"))).isFalse()
                assertThat(range.contains(Version.parse("0.99"))).isFalse()
                assertThat(range.contains(Version.parse("dev.dev"))).isFalse()
            }
        }
    }

    @Test
    fun testMavenClosedUpperPrefixExcludeRange() {
        for (char in listOf('[', ')')) {
            VersionRange.parse("[1.0,2.0${char}").let { range ->
                assertThat(range.contains(Version.parse("1.0"))).isTrue()
                assertThat(range.contains(Version.parse("1.0.0"))).isTrue()
                assertThat(range.contains(Version.parse("1.1.alpha"))).isTrue()
                assertThat(range.contains(Version.parse("2.0.dev"))).isFalse()
                assertThat(range.contains(Version.parse("2.0"))).isFalse()
                assertThat(range.contains(Version.parse("2.0.0"))).isFalse()
                assertThat(range.contains(Version.parse("2"))).isTrue()
                assertThat(range.contains(Version.parse("2.dev"))).isTrue()
                assertThat(range.contains(Version.parse("1.0-rc"))).isFalse()
                assertThat(range.contains(Version.parse("1.alpha"))).isFalse()
                assertThat(range.contains(Version.parse("1-dev"))).isFalse()
                assertThat(range.contains(Version.parse("0.99"))).isFalse()
                assertThat(range.contains(Version.parse("dev.dev"))).isFalse()
            }
        }
    }

    @Test
    fun testMavenOpenUpperPrefixExcludeRange() {
        for (openChar in listOf(']', '(')) {
            for (closeChar in listOf('[', ')')) {
                VersionRange.parse("${openChar}1.0,2.0${closeChar}").let { range ->
                    assertThat(range.contains(Version.parse("1.0"))).isFalse()
                    assertThat(range.contains(Version.parse("1.0.0"))).isTrue()
                    assertThat(range.contains(Version.parse("1.1.alpha"))).isTrue()
                    assertThat(range.contains(Version.parse("2.0.dev"))).isFalse()
                    assertThat(range.contains(Version.parse("2.0"))).isFalse()
                    assertThat(range.contains(Version.parse("2.0.0"))).isFalse()
                    assertThat(range.contains(Version.parse("2"))).isTrue()
                    assertThat(range.contains(Version.parse("2.dev"))).isTrue()
                    assertThat(range.contains(Version.parse("1.0-rc"))).isFalse()
                    assertThat(range.contains(Version.parse("1.alpha"))).isFalse()
                    assertThat(range.contains(Version.parse("1-dev"))).isFalse()
                    assertThat(range.contains(Version.parse("0.99"))).isFalse()
                    assertThat(range.contains(Version.parse("dev.dev"))).isFalse()
                }
            }
        }
    }

    @Test
    fun testUnrepresentableIdentifiers() {
        // [,1.0) is a prefix upper bound
        assertThat(VersionRange(Range.lessThan(Version.parse("1.0"))).toIdentifier()).isNull()
        assertThat(VersionRange(Range.lessThan(Version.parse("1.0"))).toString()).matches("^VersionRange\\(.*\\)$")
        // can't construct prefix lower bounds with an upper bound unless the upper bound is the
        // next prefix
        assertThat(VersionRange(Range.open(Version.prefixInfimum("1.0"), Version.parse("2.0"))).toIdentifier()).isNull()
        assertThat(VersionRange(Range.open(Version.prefixInfimum("1.0"), Version.parse("2.0"))).toString()).matches("^VersionRange\\(.*\\)$")
        assertThat(VersionRange(Range.closed(Version.prefixInfimum("1.0"), Version.parse("2.0"))).toIdentifier()).isNull()
        assertThat(VersionRange(Range.closed(Version.prefixInfimum("1.0"), Version.parse("2.0"))).toString()).matches("^VersionRange\\(.*\\)$")
        assertThat(VersionRange(Range.open(Version.prefixInfimum("1.0"), Version.prefixInfimum("2.0"))).toIdentifier()).isNull()
        assertThat(VersionRange(Range.open(Version.prefixInfimum("1.0"), Version.prefixInfimum("2.0"))).toString()).matches("^VersionRange\\(.*\\)$")
        assertThat(VersionRange(Range.closed(Version.prefixInfimum("1.0"), Version.prefixInfimum("2.0"))).toIdentifier()).isNull()
        assertThat(VersionRange(Range.closed(Version.prefixInfimum("1.0"), Version.prefixInfimum("2.0"))).toString()).matches("^VersionRange\\(.*\\)$")
        // Can't have closed prefix upper bounds
        assertThat(VersionRange(Range.closed(Version.parse("1.0"), Version.prefixInfimum("2.0"))).toIdentifier()).isNull()
        assertThat(VersionRange(Range.closed(Version.parse("1.0"), Version.prefixInfimum("2.0"))).toString()).matches("^VersionRange\\(.*\\)$")
    }
}
