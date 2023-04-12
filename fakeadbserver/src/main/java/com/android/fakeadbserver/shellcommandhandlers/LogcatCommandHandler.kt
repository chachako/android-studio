/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.fakeadbserver.shellcommandhandlers

import com.android.fakeadbserver.DeviceState
import com.android.fakeadbserver.FakeAdbServer
import com.android.fakeadbserver.ShellProtocolType
import com.android.fakeadbserver.services.ShellCommandOutput
import com.android.fakeadbserver.statechangehubs.ClientStateChangeHandlerFactory
import com.android.fakeadbserver.statechangehubs.StateChangeHandlerFactory
import java.nio.charset.Charset
import java.util.concurrent.Callable

/**
 * shell:logcat command is a persistent command issued to grab the output of logcat. In this
 * implementation, the command handler can be driven to send messages to the client of the fake ADB
 * Server.
 */
open class LogcatCommandHandler(shellProtocolType: ShellProtocolType?) : SimpleShellHandler(
    shellProtocolType!!,
    "logcat"
) {

    override fun execute(
        fakeAdbServer: FakeAdbServer,
        statusWriter: StatusWriter,
        shellCommandOutput: ShellCommandOutput,
        device: DeviceState,
        shellCommand: String,
        shellCommandArgs: String?
    ) {
        val parsedArgs = shellCommandArgs?.split(" +".toRegex()) ?: listOf()
        val formatIndex = parsedArgs.indexOf("-v")
        if (formatIndex + 1 > parsedArgs.size) {
            return
        }
        val format = parsedArgs[formatIndex + 1]
        // TODO format the output according {@code format} argument.
        statusWriter.writeOk()
        val subscriptionResult = device.subscribeLogcatChangeHandler(
            object : ClientStateChangeHandlerFactory {
                override fun createClientListChangedHandler(): Callable<StateChangeHandlerFactory.HandlerResult> {
                    return Callable { StateChangeHandlerFactory.HandlerResult(true) }
                }

                override fun createAppProcessListChangedHandler(): Callable<StateChangeHandlerFactory.HandlerResult> {
                    return Callable { StateChangeHandlerFactory.HandlerResult(true) }
                }

                override fun createLogcatMessageAdditionHandler(
                    message: String
                ): Callable<StateChangeHandlerFactory.HandlerResult> {
                    return Callable {
                        shellCommandOutput.writeStdout(
                            message.toByteArray(Charset.defaultCharset())
                        )
                        StateChangeHandlerFactory.HandlerResult(true)
                    }
                }
            }) ?: return
        try {
            for (message in subscriptionResult.mLogcatContents) {
                shellCommandOutput.writeStdout(message)
            }
            while (true) {
                try {
                    if (!subscriptionResult.mQueue.take().call().mShouldContinue) {
                        break
                    }
                } catch (ignored: InterruptedException) {
                    Thread.currentThread().interrupt()
                    break
                }
            }
        } catch (ignored: Exception) {
        } finally {
            device.clientChangeHub.unsubscribe(subscriptionResult.mQueue)
        }
    }
}
