/*
 * Copyright (C) 2021 The Android Open Source Project
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

package com.android.build.gradle.internal.testing.utp.worker

import com.android.build.gradle.internal.testing.utp.UtpDependency
import org.mockito.kotlin.mock
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.Answers
import org.mockito.Mockito.CALLS_REAL_METHODS
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.mockito.kotlin.withSettings
import java.io.File

/**
 * Unit tests for [RunUtpWorkAction].
 */
class RunUtpWorkActionTest {
    private val mockRunUtpWorkParameters: RunUtpWorkParameters = mock(defaultAnswer = Answers.RETURNS_DEEP_STUBS)

    private val mockProcessBuilder: ProcessBuilder = mock(defaultAnswer = Answers.RETURNS_DEEP_STUBS)

    private var argList: List<String>? = null

    @Before
    fun setupMocks() {
        whenever(mockRunUtpWorkParameters.jvm.asFile.get().absolutePath)
            .thenReturn("java")
        whenever(mockRunUtpWorkParameters.launcherJar.iterator())
            .then {
                mutableListOf(
                    mock<File>().apply { whenever(absolutePath).thenReturn("launcherJar1") },
                    mock<File>().apply { whenever(absolutePath).thenReturn("launcherJar2") },
                ).iterator()
            }
        whenever(mockRunUtpWorkParameters.coreJar.iterator())
            .then {
                mutableListOf(
                    mock<File>().apply { whenever(absolutePath).thenReturn("coreJar1") },
                    mock<File>().apply { whenever(absolutePath).thenReturn("coreJar2") },
                ).iterator()
            }
        whenever(mockRunUtpWorkParameters.runnerConfig.asFile.get().absolutePath)
            .thenReturn("runnerConfig")
        whenever(mockRunUtpWorkParameters.serverConfig.asFile.get().absolutePath)
            .thenReturn("serverConfig")
        whenever(mockRunUtpWorkParameters.loggingProperties.asFile.get().absolutePath)
            .thenReturn("loggingProperties")
    }

    private fun createRunUtpWorkAction(): RunUtpWorkAction =
         mock<RunUtpWorkAction>(defaultAnswer = CALLS_REAL_METHODS).also {
            whenever(it.processFactory()).thenReturn { args ->
                argList = args
                mockProcessBuilder
            }
            whenever(it.parameters).thenReturn(mockRunUtpWorkParameters)
        }

    @Test
    fun execute() {
        createRunUtpWorkAction().execute()

        assertThat(argList).isEqualTo(
            listOf(
                "java",
                "-Djava.awt.headless=true",
                "-Djava.util.logging.config.file=loggingProperties",
                "-Dfile.encoding=UTF-8",
                "-cp",
                "launcherJar1${File.pathSeparator}launcherJar2",
                UtpDependency.LAUNCHER.mainClass,
                "coreJar1${File.pathSeparator}coreJar2",
                "--proto_config=runnerConfig",
                "--proto_server_config=serverConfig"
            )
        )
    }
}
