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
package com.android.testutils

import com.android.utils.FlightRecorder
import com.android.utils.TraceUtils
import org.junit.rules.ExternalResource
import org.junit.runner.Description
import org.junit.runners.model.Statement

/**
 * Initializes [FlightRecorder] before the test and prints its contents if the test fails.
 * Intended for troubleshooting flaky tests. Use as the outermost rule in a chain.
 * When debugging hanging tests use in combination with the [org.junit.rules.Timeout] rule.
 */
class FlightRecorderRule(private val sizeLimit: Int = 1000) : ExternalResource() {

    override fun apply(base: Statement, description: Description): Statement {
        return object : Statement() {
            override fun evaluate() {
                FlightRecorder.initialize(sizeLimit)
                FlightRecorder.log(
                    "${TraceUtils.currentTime()} ${description.testClass.simpleName}.${description.methodName}")
                before()
                try {
                    base.evaluate()
                } catch (e: Throwable) {
                    FlightRecorder.log(
                        "${TraceUtils.currentTime()} ${description.testClass.simpleName}.${description.methodName} failed: ${TraceUtils.getStackTrace(e)}")
                    FlightRecorder.print()
                    throw e
                } finally {
                    try {
                        after()
                    }
                    finally {
                        FlightRecorder.initialize(0)
                    }
                }
            }
        }
    }
}
