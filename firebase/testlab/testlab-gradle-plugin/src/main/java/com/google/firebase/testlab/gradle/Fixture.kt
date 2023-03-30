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

package com.google.firebase.testlab.gradle

import org.gradle.api.Incubating
import org.gradle.api.provider.MapProperty

/**
 * A DSL for configuring test fixture.
 */
@Incubating
interface Fixture {
    /**
     * Whether to grant permissions on the device before tests begin.
     *
     * Value must be "all" or "none". By default, all permissions are granted.
     */
    @get:Incubating
    @set:Incubating
    var grantedPermissions: String

    /**
     * Map of files to push to the device before starting the test.
     *
     * The key is location on the device.
     * The value is the location of the file, either local or in Google Cloud.
     */
    @get:Incubating
    val extraDeviceFiles: MapProperty<String, String>

    /**
     * The name of the network traffic profile
     *
     * Specifies network conditions to emulate when running tests.
     */
    @get:Incubating
    var networkProfile: String
}
