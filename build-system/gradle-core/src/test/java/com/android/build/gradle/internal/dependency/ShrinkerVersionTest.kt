/*
 * Copyright (C) 2019 The Android Open Source Project
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

package com.android.build.gradle.internal.dependency

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ShrinkerVersionTest {

    @Test
    fun testParseVersionString() {
        listOf(
            "ProGuard, version 6.0.1" to "6.0.1",
            "ProGuard, version 6.0.1-dev" to "6.0.1-dev",
            "ProGuard, version 6.0.1 FOO" to "6.0.1",
            "ProGuard, version 6.0.1 FOO" to "6.0.1",
            "1.6.51 (build 94162e from go/r8bot (luci-r8-ci-archive-0-t0i8))" to "1.6.51",
            "1.6.51-dev (build 94162e from go/r8bot (luci-r8-ci-archive-0-t0i8))" to "1.6.51-dev"
        ).onEach { (input, output) ->
            assertThat(ShrinkerVersion.parse(input)!!.asString()).isEqualTo(output)
        }
    }
}
