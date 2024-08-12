/*
 * Copyright (C) 2024 The Android Open Source Project
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
package com.android.fakeadbserver.services

import com.android.fakeadbserver.DeviceState

class ActivityManager(private val deviceState: DeviceState) : Service {

    companion object {
        const val SERVICE_NAME = "activity"
    }

    override fun process(args: List<String>, shellCommandOutput: ShellCommandOutput) {
        val cmd = args[0]

        when (cmd) {
            "force-stop" -> {
                if (args.size <= 1) {
                    shellCommandOutput.writeStderr(
                        "Exception occurred while executing 'force-stop':\n"
                                + "java.lang.IllegalArgumentException: Argument expected after \"force-stop\"")
                    shellCommandOutput.writeExitCode(0)
                    return
                }
                val packageName = args[1]
                deviceState.stopClients(packageName)
                shellCommandOutput.writeExitCode(0)
            }
            "crash" -> {
                if (args.size <= 1) {
                    shellCommandOutput.writeStderr(
                        "Exception occurred while executing 'crash':\n"
                                + "java.lang.IllegalArgumentException: Argument expected after \"crash\"")
                    shellCommandOutput.writeExitCode(0)
                    return
                }
                val packageName = args[1]
                deviceState.stopClients(packageName)
                shellCommandOutput.writeExitCode(0)
            }
            else -> {
                shellCommandOutput.writeStderr("[fakeadb] Error: Am '$cmd' is not supported")
                shellCommandOutput.writeExitCode(1)
            }
        }
    }
}
