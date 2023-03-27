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

package com.android.build.gradle.internal.dsl

import com.google.common.truth.Truth
import org.junit.Test

class SdkConstantsSyncTest {
    @Test
    fun checkBuildToolsVersion() {
        Truth.assertThat(SdkConstants.BUILD_TOOLS_VERSION)
            .isEqualTo(com.android.SdkConstants.CURRENT_BUILD_TOOLS_VERSION)
    }

    @Test
    fun checkNdkVersion() {
        Truth.assertThat(SdkConstants.NDK_VERSION)
            .isEqualTo(com.android.SdkConstants.NDK_DEFAULT_VERSION)
    }

}
