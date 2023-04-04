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

package com.android.tools.firebase.testlab.gradle

import com.google.firebase.testlab.gradle.ManagedDevice
import javax.inject.Inject
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Internal

/**
 * Implementation for [ManagedDevice] to be registered with the
 * Android Plugin for Gradle
 */
open class ManagedDeviceImpl @Inject constructor(private val name: String) : ManagedDevice {
    @Internal
    override fun getName(): String = name

    @get:Input
    override var device = ""

    @get:Input
    override var apiLevel = -1

    private var _orientation: Orientation = Orientation.DEFAULT

    @get:Input
    override var orientation: String
        set(value) {
            _orientation = try {
                Orientation.valueOf(value.uppercase())
            } catch (_: IllegalArgumentException) {
                error("$value is invalid. Available options are " +
                        "[${Orientation.values().joinToString(", ")}].")
            }
        }
        get() {
            return _orientation.name
        }

    /**
     * Specifies the Orientation that tests should be run on the [ManagedDevice]
     */
    enum class Orientation {
        /** The default orientation for that device. */
        DEFAULT,
        /** Explicitly set the orientation to portrait. */
        PORTRAIT,
        /** Explicitly set the orientation to landscape. */
        LANDSCAPE,
    }


    @get:Input
    override var locale = "en-US"
}
