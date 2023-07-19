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
package com.android.tools.screenshot.junit.engine

import org.junit.platform.engine.support.descriptor.EngineDescriptor
import org.junit.platform.engine.EngineDiscoveryRequest
import org.junit.platform.engine.ExecutionRequest
import org.junit.platform.engine.TestDescriptor
import org.junit.platform.engine.TestEngine
import org.junit.platform.engine.UniqueId
import org.junit.platform.engine.TestExecutionResult

class PreviewScreenshotTestEngine : TestEngine {

    override fun getId(): String {
        return "preview-screenshot-test-engine"
    }

    override fun discover(discoveryRequest: EngineDiscoveryRequest, uniqueId: UniqueId): TestDescriptor {
        return EngineDescriptor(uniqueId, "Preview Screenshot Test Engine")
    }

    override fun execute(request: ExecutionRequest) {
        val descriptor = request.rootTestDescriptor
        val listener = request.engineExecutionListener

        listener.executionStarted(descriptor)
        var testSuiteExecutionResult: TestExecutionResult = when (val processExitValue = getParam("process.exit.value").toInt()) {
            in listOf(0,3) -> TestExecutionResult.successful()
            in listOf(1,2) -> {
                TestExecutionResult.failed(Exception("Tests failed. See report for details"))
            }
            else -> TestExecutionResult.aborted(Exception("Unknown error code $processExitValue returned"))
        }
        listener.executionFinished(descriptor, testSuiteExecutionResult)
    }

    private fun getParam(key: String): String {
        return System.getProperty("com.android.tools.screenshot.junit.engine.${key}")
    }
}
