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

import java.io.File

data class ProjectSettings (

    val name: String,

    val storageBucket: String?,

    val testHistoryName: String?,

    val grantedPermissions: String?,

    val networkProfile: String?,

    val extraDeviceFiles: Map<String, String>,

    val directoriesToPull: List<String>,

    val useOrchestrator: Boolean,

    val ftlTimeoutSeconds: Int,

    val performanceMetrics: Boolean,

    val videoRecording: Boolean,

    val maxTestReruns: Int,

    val failFast: Boolean,

    val numUniformShards: Int,

    val targetedShardDurationSeconds: Int,

    val stubAppApk: File
)
