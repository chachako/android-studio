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
package com.android.fakeadbserver.services

import com.android.fakeadbserver.shellcommandhandlers.ShellConstants

// TODO: Add all package management app (create,write,commit,abandon) and list here.
class PackageManager : Service {
    companion object {

        const val BAD_FLAG = "-BAD_FLAG"

        const val FAIL_ME_SESSION = "FAIL_ME_SESSION"

        const val FAIL_ME_SESSION_TEST_ONLY = "FAIL_ME_TEST_ONLY"

        const val SESSION_TEST_ONLY_CODE = "INSTALL_FAILED_TEST_ONLY"
        const val SESSION_TEST_ONLY_MSG = "installPackageLI"

        val BAD_SESSIONS : Map<String, String> = mapOf(
            FAIL_ME_SESSION to "Failure [REQUESTED_FAILURE_VIA_SESSION]",
            FAIL_ME_SESSION_TEST_ONLY to "Failure [$SESSION_TEST_ONLY_CODE: $SESSION_TEST_ONLY_MSG]",
        )

        const val SERVICE_NAME = "package"
    }

    override fun process(args: List<String>, shellCommandOutput: ShellCommandOutput) {
        val cmd = args[0]

        return when {
            cmd == "list users" -> {
                shellCommandOutput.writeStdout("Users:\n\tUserInfo{0:Owner:13} running\n")
                shellCommandOutput.writeExitCode(0)
            }
            cmd.startsWith("uninstall") -> {
                if (args.size == 1) {
                    shellCommandOutput.writeStdout("Error: package name not specified")
                    shellCommandOutput.writeExitCode(1)
                    return
                }
                val applicationId = args.last()
                if (applicationId == ShellConstants.NON_INSTALLED_APP_ID) {
                    shellCommandOutput.writeStdout("Failure [DELETE_FAILED_INTERNAL_ERROR]")
                } else {
                    shellCommandOutput.writeStdout("Success")
                }
            }
            cmd == "path" -> {
                val appId = args[1]
                shellCommandOutput.writeStdout("/data/app/$appId/base.apk")
                shellCommandOutput.writeExitCode(0)
            }

            cmd.startsWith("install-create") -> {
                if (args.contains(BAD_FLAG)) {
                    shellCommandOutput.writeStderr("Error: (requested to fail via flag))")
                    shellCommandOutput.writeExitCode(1)
                    return
                } else {
                    shellCommandOutput.writeStdout("Success: created install session [1234]")
                    shellCommandOutput.writeExitCode(0)
                }
            }

            cmd.startsWith("install-write") -> {
                installWrite(args.joinToString(" "), shellCommandOutput)
            }

            cmd.startsWith("install-commit") -> {
                val sessionID = args[1]
                if (BAD_SESSIONS.containsKey(sessionID)) {
                    BAD_SESSIONS.get(sessionID)?.let { shellCommandOutput.writeStderr(it) }
                    shellCommandOutput.writeExitCode(1)
                } else {
                    commit(args.drop(1), shellCommandOutput)
                }
            }
            cmd.startsWith("install-abandon") -> {
                shellCommandOutput.writeStdout("Success\n")
                shellCommandOutput.writeExitCode(0)
            }
            cmd.startsWith("install") -> {
                commit(args.drop(1), shellCommandOutput)
            }

            else -> {
                shellCommandOutput.writeStderr("Error: Package command '$cmd' is not supported")
                shellCommandOutput.writeExitCode(1)
            }
        }
    }

    private fun commit(slice: List<String>, shellCommandOutput: ShellCommandOutput) {
        val sessionID = slice[0]
        if (sessionID == "FAIL_ME") {
            shellCommandOutput.writeStderr("Error (requested a FAIL_ME session)\n")
            shellCommandOutput.writeExitCode(1)
        } else {
            shellCommandOutput.writeStdout("Success\n")
            shellCommandOutput.writeExitCode(0)
        }
    }

    private fun installWrite(args: String, shellCommandOutput: ShellCommandOutput) {
        val parameters = args.split(" ")
        if (parameters.isEmpty()) {
            shellCommandOutput.writeStderr("Malformed install-write request")
            shellCommandOutput.writeExitCode(1)
            return
        }

        if (parameters.last() != "-") {
            val sessionID = parameters[1]
            if (BAD_SESSIONS.containsKey(sessionID)) {
                BAD_SESSIONS.get(sessionID)?.let { shellCommandOutput.writeStderr(it) }
                shellCommandOutput.writeExitCode(1)
                return
            }
            // This is a remote apk write (the apk is somewhere on the device, likely /data/local"..)
            // Use a random value
            shellCommandOutput.writeStdout("Success: streamed 123456789 bytes\n")
            shellCommandOutput.writeExitCode(0)
            return
        }

        // This is a streamed install
        val sizeIndex = parameters.indexOf("-S") + 1
        if (sizeIndex == 0) {
            shellCommandOutput.writeStderr("Malformed install-write request")
            shellCommandOutput.writeExitCode(1)
            return
        }

        val expectedBytesLength = parameters[sizeIndex].toInt()
        val buffer = ByteArray(1024)
        var totalBytesRead = 0
        while (totalBytesRead < expectedBytesLength) {
            val length = Integer.min(buffer.size, expectedBytesLength - totalBytesRead)
            val numRead = shellCommandOutput.readStdin(buffer, 0, length)
            if (numRead < 0) {
                break
            }
            totalBytesRead += numRead
        }

        shellCommandOutput.writeStdout("Success: streamed $totalBytesRead bytes\n")
        shellCommandOutput.writeExitCode(0)
    }
}
