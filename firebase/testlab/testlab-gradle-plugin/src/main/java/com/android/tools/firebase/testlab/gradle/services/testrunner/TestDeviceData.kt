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

package com.android.tools.firebase.testlab.gradle.services.testrunner

import com.android.tools.firebase.testlab.gradle.ManagedDeviceImpl
import com.google.api.services.testing.model.AndroidModel
import java.util.Locale

/**
 * Contains all data for a given device associated with a test run.
 */
data class TestDeviceData (
    val name: String,
    val deviceId: String,
    val apiLevel: Int,
    val locale: Locale,
    val orientation: ManagedDeviceImpl.Orientation,
    val ftlModel: AndroidModel
)
