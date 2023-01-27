/*
 * Copyright (C) 2013 The Android Open Source Project
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
package com.android.sdklib;

import static com.android.sdklib.SdkVersionInfo.HIGHEST_KNOWN_API;
import static com.android.sdklib.SdkVersionInfo.HIGHEST_KNOWN_STABLE_API;
import static com.android.sdklib.SdkVersionInfo.HIGHEST_SUPPORTED_API;
import static com.android.sdklib.SdkVersionInfo.LOWEST_ACTIVE_API;
import static com.android.sdklib.SdkVersionInfo.RECOMMENDED_MIN_SDK_VERSION;
import static com.android.sdklib.SdkVersionInfo.camelCaseToUnderlines;
import static com.android.sdklib.SdkVersionInfo.getApiByBuildCode;
import static com.android.sdklib.SdkVersionInfo.getApiByPreviewName;
import static com.android.sdklib.SdkVersionInfo.getBuildCode;
import static com.android.sdklib.SdkVersionInfo.getCodeName;
import static com.android.sdklib.SdkVersionInfo.getVersion;
import static com.android.sdklib.SdkVersionInfo.getVersionString;
import static com.android.sdklib.SdkVersionInfo.underlinesToCamelCase;

import com.android.testutils.TestUtils;
import junit.framework.TestCase;

public class SdkVersionInfoTest extends TestCase {
    @SuppressWarnings("ConstantConditions")
    public void testValidRanges() {
        assertTrue(1 < LOWEST_ACTIVE_API &&
                   LOWEST_ACTIVE_API <= RECOMMENDED_MIN_SDK_VERSION &&
                   RECOMMENDED_MIN_SDK_VERSION <= HIGHEST_KNOWN_STABLE_API &&
                   HIGHEST_KNOWN_STABLE_API <= HIGHEST_KNOWN_API);
    }

    public void testHighestSupportedVersion() {
        // Make sure we keep HIGHEST_SUPPORTED_API up to date along with the stable
        // SDKs. We should at least supported the latest stable known API; at times,
        // we'll also support the current preview image.
        assertTrue(HIGHEST_SUPPORTED_API >= HIGHEST_KNOWN_STABLE_API);
        assertTrue(HIGHEST_SUPPORTED_API <= HIGHEST_KNOWN_API);
    }

    public void testGetBuildCode() {
        assertEquals("JELLY_BEAN", getBuildCode(16));
    }

    public void testGetApiByPreviewName() {
        assertEquals(5, getApiByPreviewName("Eclair", false));
        assertEquals(10, getApiByPreviewName("GINGERBREAD_MR1", false));
        assertEquals(18, getApiByPreviewName("JellyBeanMR2", false));
        assertEquals(21, getApiByPreviewName("Lollipop", false));
        assertEquals(21, getApiByPreviewName("L", false));
        assertEquals(21, getApiByPreviewName("Lollipop", false));
        assertEquals(26, getApiByPreviewName("O", false));
        assertEquals(26, getApiByPreviewName("Oreo", false));
        assertEquals(28, getApiByPreviewName("P", false));
        assertEquals(28, getApiByPreviewName("Pie", false));
        assertEquals(29, getApiByPreviewName("Q", false));
        assertEquals(30, getApiByPreviewName("R", true));
        assertEquals(32, getApiByPreviewName("S_V2", true));
        assertEquals(32, getApiByPreviewName("Sv2", true));

        assertEquals(-1, getApiByPreviewName("UnknownName", false));
        assertEquals(HIGHEST_KNOWN_API + 1, getApiByPreviewName("UnknownName", true));
    }

    public void testGetApiByBuildCode() {
        assertEquals(7, getApiByBuildCode("ECLAIR_MR1", false));
        assertEquals(16, getApiByBuildCode("JELLY_BEAN", false));
        assertEquals(24, getApiByBuildCode("N", false));
        assertEquals(26, getApiByBuildCode("O", false));
        assertEquals(27, getApiByBuildCode("O_MR1", false));
        assertEquals(28, getApiByBuildCode("P", true));
        assertEquals(29, getApiByBuildCode("Q", true));
        assertEquals(30, getApiByBuildCode("R", true));
        assertEquals(32, getApiByBuildCode("S_V2", true));

        for (int api = 1; api <= HIGHEST_KNOWN_API; api++) {
            assertEquals(api, getApiByBuildCode(getBuildCode(api), false));
        }

        assertEquals(-1, getApiByBuildCode("K_SURPRISE_SURPRISE", false));
        assertEquals(HIGHEST_KNOWN_API + 1, getApiByBuildCode("K_SURPRISE_SURPRISE", true));
    }

    public void testGetCodeName() {
        assertNull(getCodeName(1));
        assertNull(getCodeName(2));
        assertEquals("Cupcake", getCodeName(3));
        assertEquals("KitKat", getCodeName(19));
        assertEquals("Lollipop", getCodeName(21));
        assertEquals("Nougat", getCodeName(24));
        assertEquals("Oreo", getCodeName(26));
        assertEquals("Oreo", getCodeName(27));
        assertEquals("Pie", getCodeName(28));

        // make sure all known codenames are non-null
        for (int i = 3; i <= HIGHEST_KNOWN_API; i++) {
            assertNotNull(getCodeName(i));
        }
    }

    public void testCamelCaseToUnderlines() {
        assertEquals("", camelCaseToUnderlines(""));
        assertEquals("foo", camelCaseToUnderlines("foo"));
        assertEquals("foo", camelCaseToUnderlines("Foo"));
        assertEquals("foo_bar", camelCaseToUnderlines("FooBar"));
        assertEquals("test_xml", camelCaseToUnderlines("testXML"));
        assertEquals("test_foo", camelCaseToUnderlines("testFoo"));
        assertEquals("jelly_bean_mr2", camelCaseToUnderlines("JellyBeanMR2"));
    }

    public void testUnderlinesToCamelCase() {
        assertEquals("", underlinesToCamelCase(""));
        assertEquals("", underlinesToCamelCase("_"));
        assertEquals("Foo", underlinesToCamelCase("foo"));
        assertEquals("FooBar", underlinesToCamelCase("foo_bar"));
        assertEquals("FooBar", underlinesToCamelCase("foo__bar"));
        assertEquals("Foo", underlinesToCamelCase("foo_"));
        assertEquals("JellyBeanMr2", underlinesToCamelCase("jelly_bean_mr2"));
    }

    @SuppressWarnings("ConstantConditions")
    public void testGetAndroidVersion() {
        assertNull(getVersion("", null));
        assertNull(getVersion("4H", null));
        assertEquals(4, getVersion("4", null).getApiLevel());
        assertNull(getVersion("4", null).getCodename());
        assertEquals("4", getVersion("4", null).getApiString());
        assertEquals(19, getVersion("19", null).getApiLevel());
        // ICS is API 14, but when expressed as a preview platform, it's not yet 14
        assertEquals(13, getVersion("IceCreamSandwich", null).getApiLevel());
        assertEquals("IceCreamSandwich", getVersion("IceCreamSandwich", null).getCodename());
        assertEquals(HIGHEST_KNOWN_API, getVersion("BackToTheFuture", null).getApiLevel());
        assertEquals("BackToTheFuture", getVersion("BackToTheFuture", null).getCodename());
    }

    public void testHighestStableApiInTestUtils() throws Exception {
        assertEquals("android-" + HIGHEST_KNOWN_STABLE_API, TestUtils.getLatestAndroidPlatform());
    }

    public void testGetVersionString() {
        // make sure all known versions are non-null
        for (int i = 1; i <= HIGHEST_KNOWN_STABLE_API; i++) {
            assertNotNull(getVersionString(i));
        }
    }
}
