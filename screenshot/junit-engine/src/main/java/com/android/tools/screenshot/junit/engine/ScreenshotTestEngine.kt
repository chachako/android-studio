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

class ScreenshotTestEngine : TestEngine {

    override fun getId(): String {
        return "screenshot-test-engine"
    }

    override fun discover(discoveryRequest: EngineDiscoveryRequest, uniqueId: UniqueId): TestDescriptor {
        return EngineDescriptor(uniqueId, "Screenshot Test Engine")
    }

    override fun execute(request: ExecutionRequest) {
        val descriptor = request.rootTestDescriptor
        val listener = request.engineExecutionListener

        val commands = mutableListOf(getParam("java"),
                "-cp", getParam("previewJar"), "com.android.screenshot.cli.Main",
                "--client-name", getParam("client.name"),
                "--client-version", getParam("client.version"),
                "--jdk-home", getParam("java.home"),
                "--sdk-home", getParam("androidsdk"),
                "--extraction-dir", getParam("extraction.dir"),
                "--jar-location", getParam("previewJar"),
                "--lint-model", getParam("lint.model"),
                "--cache-dir", getParam("lint.cache"),
                "--root-lint-model", getParam("lint.model"),
                "--output-location", getParam("output.location") + "/",
                "--golden-location", getParam("output.location") + "/",
                "--file-path", getParam("sources").split(",").first())
        if (getParam("record.golden").isNotEmpty()) {
          commands.add(getParam("record.golden"))
        }
        val process = ProcessBuilder(
            commands
        ).apply {
            environment().remove("TEST_WORKSPACE")
            redirectErrorStream(true)
            redirectOutput(ProcessBuilder.Redirect.INHERIT)
        }.start()
        process.waitFor()

        listener.executionStarted(descriptor)
        var testSuiteExecutionResult: TestExecutionResult = TestExecutionResult.successful()
        listener.executionFinished(descriptor, testSuiteExecutionResult)
    }

    private fun getParam(key: String): String {
        return System.getProperty("com.android.tools.screenshot.junit.engine.${key}")
    }
}
