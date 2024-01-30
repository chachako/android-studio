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

package com.android.build.gradle.internal

import groovy.test.GroovyTestCase.assertEquals
import org.junit.Test

class MavenCoordinatesTest {

    @Test
    fun MavenCoordinateFormatsCorrectlyAsAString() {
        assertEquals(
            MavenCoordinates.ANDROIDX_PRIVACY_SANDBOX_SDK_API_GENERATOR_1_0_0_ALPHA03.toString(),
            "androidx.privacysandbox.tools:tools-apigenerator:1.0.0-alpha03"
        )
    }
}
