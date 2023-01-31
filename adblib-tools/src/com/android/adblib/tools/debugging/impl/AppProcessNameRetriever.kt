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
package com.android.adblib.tools.debugging.impl

import com.android.adblib.selector
import com.android.adblib.shellCommand
import com.android.adblib.thisLogger
import com.android.adblib.tools.debugging.AppProcess
import com.android.adblib.tools.debugging.JdwpProcess
import com.android.adblib.tools.debugging.scope
import com.android.adblib.withTextCollector
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.withContext
import java.io.IOException
import java.time.Duration

internal class AppProcessNameRetriever(private val process: AppProcess) {

    val logger = thisLogger(process.device.session)

    suspend fun retrieve(
        retryCount: Int = 5,
        retryDelay: Duration = Duration.ofMillis(200)
    ): String {
        return process.jdwpProcess?.let {
            retrieveProcessNameFromJdwpProcess(it)
        } ?: run {
            retrieveProcessNameFromProc(retryCount, retryDelay)
        }
    }

    private suspend fun retrieveProcessNameFromJdwpProcess(jdwpProcess: JdwpProcess): String {
        logger.debug { "Looking for JDWP process name of $this" }
        return withContext(process.scope.coroutineContext) {
            // Wait for the process name to be valid (i.e. not empty)
            val processName = jdwpProcess.propertiesFlow.mapNotNull { props ->
                val name = props.processName
                if (name.isNullOrEmpty()) {
                    null
                } else {
                    name
                }
            }.first()

            logger.debug { "Found JDWP process name of $this: $processName" }
            processName
        }
    }

    private suspend fun retrieveProcessNameFromProc(
        retryCount: Int = 5,
        retryDelay: Duration = Duration.ofMillis(200)
    ): String {
        return withContext(process.scope.coroutineContext) {
            val cmd = "cat /proc/${process.pid}/cmdline"
            logger.debug { "Looking for process name of $process using '$cmd'" }
            for (retry in 0 until retryCount) {
                val output = try {
                    process.device.session.deviceServices
                        .shellCommand(process.device.selector, cmd)
                        .withTextCollector()
                        .execute()
                        .first()
                } catch (t: Throwable) {
                    logger.warn(
                        t,
                        "Error executing shell command '$cmd' on device ${process.device}"
                    )
                    // try again
                    continue
                }

                // 'cmdline' is argv, where each arg is terminated by a `nul`, so
                // the process name is all characters of the output until the first `nul`.
                val name = output.stdout.takeWhile { it != 0.toChar() }
                when {
                    name.isBlank() || name == "<pre-initialized>" -> {
                        // Try again a little later
                        delay(retryDelay.toMillis())
                        continue
                    }

                    output.stderr.contains("No such file or directory") -> {
                        logger.debug { "`cmdline` file for process ${process.pid} does not exist: stderr=${output.stderr}" }
                        throw IOException("Unable to retrieve process name of process ${process.pid}, the process has exited")
                    }

                    else -> {
                        logger.debug { "Found app process name of $process: '$name'" }
                        return@withContext name
                    }
                }
            }

            throw IOException("Process name could not be retrieved from the device")
        }
    }
}
