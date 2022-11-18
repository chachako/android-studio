/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.ide.common.repository;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import com.android.ide.common.gradle.Version;
import org.junit.Test;

public class GradleVersionRangeTest {

    @Test
    public void testParseSingleVersion() {
        GradleVersionRange range = GradleVersionRange.parse("2.0.0");
        assertEquals(2, range.getMin().getMajor().intValue());
        assertEquals(0, range.getMin().getMinor().intValue());
        assertEquals(0, range.getMin().getMicro().intValue());
        assertEquals(2, range.getMax().getMajor().intValue());
        assertEquals(0, range.getMax().getMinor().intValue());
        assertEquals(1, range.getMax().getMicro().intValue());
    }

    @Test
    public void testParseExplicitRange() {
        GradleVersionRange range = GradleVersionRange.parse("[2.3.0,4.1.0)");
        assertEquals(2, range.getMin().getMajor().intValue());
        assertEquals(3, range.getMin().getMinor().intValue());
        assertEquals(0, range.getMin().getMicro().intValue());
        assertEquals(4, range.getMax().getMajor().intValue());
        assertEquals(1, range.getMax().getMinor().intValue());
        assertEquals(0, range.getMax().getMicro().intValue());
    }

    @Test
    public void testParseImplicitRangeWithAndroidX() {
        GradleVersionRange range = GradleVersionRange.parse("2.3.0", KnownVersionStability.SEMANTIC);
        assertEquals(2, range.getMin().getMajor().intValue());
        assertEquals(3, range.getMin().getMinor().intValue());
        assertEquals(0, range.getMin().getMicro().intValue());
        assertEquals(3, range.getMax().getMajor().intValue());
        assertNull(range.getMax().getMinor());
        assertNull(range.getMax().getMicro());
    }

    @Test
    public void testParseImplicitRangeWithIncrementalStability() {
        GradleVersionRange range =
                GradleVersionRange.parse("2.3.2", KnownVersionStability.INCREMENTAL);
        assertEquals(2, range.getMin().getMajor().intValue());
        assertEquals(3, range.getMin().getMinor().intValue());
        assertEquals(2, range.getMin().getMicro().intValue());
        assertEquals(2, range.getMax().getMajor().intValue());
        assertEquals(4, range.getMax().getMinor().intValue());
        assertNull(range.getMax().getMicro());
    }

    @Test
    public void testParseImplicitRangeWithFullStability() {
        GradleVersionRange range = GradleVersionRange.parse("2.3.2", KnownVersionStability.STABLE);
        assertEquals(2, range.getMin().getMajor().intValue());
        assertEquals(3, range.getMin().getMinor().intValue());
        assertEquals(2, range.getMin().getMicro().intValue());
        assertEquals(Integer.MAX_VALUE, range.getMax().getMajor().intValue());
        assertNull(range.getMax().getMinor());
        assertNull(range.getMax().getMicro());
    }

    @Test
    public void testIntersectionTwoFullRanges() {
        GradleVersionRange range1 = GradleVersionRange.parse("[2.3.0,4.1.0)");
        GradleVersionRange range2 = GradleVersionRange.parse("[3.1.0,5.3.0)");
        assertEquals("[3.1.0,4.1.0)", range1.intersection(range2).toString());
    }

    @Test
    public void testIntersectionTwoFullRangesNoOverlap() {
        GradleVersionRange range1 = GradleVersionRange.parse("[2.3.0,4.1.0)");
        GradleVersionRange range2 = GradleVersionRange.parse("[4.1.0,5.3.0)");
        assertNull(range1.intersection(range2));
    }

    @Test
    public void testIntersectionOneRangeOneSingleVersion() {
        GradleVersionRange range1 = GradleVersionRange.parse("[2.3.0,4.1.0)");
        GradleVersionRange range2 = GradleVersionRange.parse("3.1.0");
        assertEquals("[3.1.0,3.1.1)", range1.intersection(range2).toString());
    }

    @Test
    public void testIntersectionOneRangeOneSingleVersion2() {
        GradleVersionRange range = GradleVersionRange.parse("[2.3,4.1)");
        Version version = Version.Companion.parse("3.1");
        assertEquals("3.1", range.intersection(version).toString());
    }

    @Test
    public void testIntersectionOneRangeOneSingleVersionNoOverlap1() {
        GradleVersionRange range1 = GradleVersionRange.parse("[2.3,4.1)");
        GradleVersionRange range2 = GradleVersionRange.parse("1.1");
        assertNull(range1.intersection(range2));
    }

    @Test
    public void testIntersectionOneRangeOneSingleVersionNoOverlap2() {
        GradleVersionRange range1 = GradleVersionRange.parse("[2.3,4.1)");
        GradleVersionRange range2 = GradleVersionRange.parse("7.3");
        assertNull(range1.intersection(range2));
    }
}
