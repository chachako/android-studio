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

package com.android.build.gradle.internal.r8

import com.android.Version
import com.android.builder.dexing.R8Version
import com.android.ide.common.repository.AgpVersion
import org.junit.Test

/** Tests that AGP RC or stable versions do not ship with R8 dev versions (b/367319573). */
class R8ReleaseVersionTest {

    @Test
    fun `test that AGP RC or stable versions do not ship with R8 dev versions`() {
        val agpVersion = AgpVersion.parse(Version.ANDROID_GRADLE_PLUGIN_VERSION)
        if (agpVersion.previewKind in setOf(AgpVersion.PreviewKind.NONE, AgpVersion.PreviewKind.RC)) {
            val r8Version = R8Version.VERSION_AGP_WAS_SHIPPED_WITH
            if (r8Version.endsWith("-dev")) {
                error("AGP RC or stable versions must not ship with R8 dev versions. Current AGP version: $agpVersion; current R8 version: $r8Version")
            }
        }
    }
}
