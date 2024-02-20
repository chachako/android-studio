/*
 * Copyright (C) 2024 The Android Open Source Project
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
package com.android.utils

import com.android.utils.JavaVersionUtil.classVersionToJdk
import org.junit.Assert.assertEquals
import org.junit.Test

class JavaVersionUtilTest {
    @Test
    fun testClassVersionToJdk() {
        assertEquals("1.5", classVersionToJdk(49));
        assertEquals("1.6", classVersionToJdk(50));
        assertEquals("1.7", classVersionToJdk(51));
        assertEquals("1.8", classVersionToJdk(52));
        assertEquals("1.4", classVersionToJdk(48));
        assertEquals("1.3", classVersionToJdk(47));
        assertEquals("1.2", classVersionToJdk(46));
        assertEquals("1.1", classVersionToJdk(45));
        assertEquals("9", classVersionToJdk(53));
        assertEquals("11", classVersionToJdk(55));
    }
}
