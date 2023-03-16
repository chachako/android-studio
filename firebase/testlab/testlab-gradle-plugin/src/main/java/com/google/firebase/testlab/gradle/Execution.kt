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

/**
 * A DSL for configuring test execution.
 */
@Incubating
interface Execution {
    /**
     * The maximum time to run the test execution before cancellation
     * measured in minutes. Does not include the setup/teardown of device and
     * is handled server side.
     *
     * The maximum possible testing time is 45 minutes on physical devices
     * and 60 minutes on virtual devices. As specified by FTL.
     *
     * Default value is 15 minutes.
     */
    @get:Incubating
    @set:Incubating
    var timeoutMinutes: Int

    /**
     * Number of times the test should be rerun if tests fail.
     * The number of times a Test Execution should be re-attempted if one
     * or more of its test cases fail.
     *
     * The maximum possible test reruns are 10. Default value is 0.
     */
    @get:Incubating
    @set:Incubating
    var maxTestReruns: Int

    /**
     * Ensures only a single attempt will be made for each execution if
     * an infrastructure issue occurs.
     * This does not affect [maxTestReruns]. Normally, 2 or more attempts
     * are made by FTL if a potential infrastructure issue is detected. This
     * is best enabled for latency sensitive workloads. The # of execution
     * failures may be significantly greater with failFast enabled.
     *
     * Default value is false.
     */
    @get:Incubating
    @set:Incubating
    var failFast: Boolean
}
