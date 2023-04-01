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
import org.gradle.api.provider.ListProperty

/**
 * A DSL for configuring test results.
 */
@Incubating
interface Results {
    /**
     * The name of the cloud storage bucket where the test results will be stored.
     *
     * If unspecified, Firebase provides the default bucket.
     */
    @get:Incubating
    @set:Incubating
    var cloudStorageBucket: String

    /**
     * History name of test results.
     *
     * All tests with the same history name will have their results grouped
     * together in the Firebase console in a time-ordered test history list.
     *
     * If unspecified, the application label in Android manifest is used.
     */
    @get:Incubating
    @set:Incubating
    var resultsHistoryName: String

    /**
     * List of paths that will be copied from the test device's storage to the test result folder.
     *
     * This will copy from GCloud to local storage.
     * These must be absolute paths under /sdcard or /data/local/tmp.
     */
    @get:Incubating
    val directoriesToPull: ListProperty<String>

    /**
     * Enable Video recording during the test.
     *
     * Default value is false.
     */
    @get:Incubating
    @set:Incubating
    var recordVideo: Boolean

    /**
     * Whether performance metrics are enabled.
     * Monitor and record performance metrics: CPU, memory, network usage, etc.
     *
     * Default value is false.
     */
    @get:Incubating
    @set:Incubating
    var performanceMetrics: Boolean
}
