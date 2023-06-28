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

package com.android.build.api.dsl

import org.gradle.api.Incubating

/**
 * Generic options to configure test configurations for kotlin multiplatform android
 */
@Incubating
interface KotlinMultiplatformAndroidTestConfiguration {

    /**
     * By default, this will be equal to the compilation name specified when enabling the test
     * component prefixed by "android". (i.e. when the compilation name is "testOnJvm" then the
     * default sourceSet name will be "androidTestOnJvm").
     */
    @get:Incubating
    @set:Incubating
    var defaultSourceSetName: String

    /**
     * Configure the SourceSetTree for test components/sourceset in order to change the default
     * behaviour of source set hierarchies
     */
    @get:Incubating
    @set:Incubating
    var sourceSetTree: String?
}
