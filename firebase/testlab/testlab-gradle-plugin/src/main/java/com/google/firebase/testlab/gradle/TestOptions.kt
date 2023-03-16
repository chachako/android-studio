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
 * A DSL for configuring test options.
 */
@Incubating
interface TestOptions {
    /**
     * A configuration block for test setup options.
     */
    @get:Incubating
    val setup: Setup

    @Incubating
    fun setup(action: Setup.() -> Unit)

    /**
     * A configuration block for test execution options.
     */
    @get:Incubating
    val execution: Execution

    @Incubating
    fun execution(action: Execution.() -> Unit)

    /**
     * A configuration block for test results options.
     */
    @get:Incubating
    val results: Results

    @Incubating
    fun results(action: Results.() -> Unit)
}
