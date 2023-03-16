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

package com.android.tools.firebase.testlab.gradle

import com.google.firebase.testlab.gradle.Execution
import com.google.firebase.testlab.gradle.Results
import com.google.firebase.testlab.gradle.Setup
import com.google.firebase.testlab.gradle.TestOptions
import org.gradle.api.Action
import org.gradle.api.model.ObjectFactory
import javax.inject.Inject

abstract class TestOptionsImpl @Inject constructor(objectFactory: ObjectFactory) : TestOptions {
    override val setup: Setup = objectFactory.newInstance(SetupImpl::class.java)

    // (Implementing interface for kotlin)
    override fun setup(action: Setup.() -> Unit) {
        setup.action()
    }

    // Runtime only for groovy decorator to generate the closure based block.
    fun setup(action: Action<Setup>) {
        action.execute(setup)
    }

    override val execution: Execution = objectFactory.newInstance(ExecutionImpl::class.java)

    // (Implementing interface for kotlin)
    override fun execution(action: Execution.() -> Unit) {
        execution.action()
    }

    // Runtime only for groovy decorator to generate the closure based block.
    fun execution(action: Action<Execution>) {
        action.execute(execution)
    }

    override val results: Results = objectFactory.newInstance(ResultsImpl::class.java)

    // (Implementing interface for kotlin)
    override fun results(action: Results.() -> Unit) {
        results.action()
    }

    // Runtime only for groovy decorator to generate the closure based block.
    fun results(action: Action<Results>) {
        action.execute(results)
    }
}
