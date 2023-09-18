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

@file:JvmMultifileClass
@file:JvmName("AdbLibMigrationUtils")

package com.android.adblib.ddmlibcompatibility.debugging

import com.android.adblib.AdbFailResponseException
import com.android.adblib.AdbSession
import com.android.adblib.ConnectedDevice
import com.android.adblib.DeviceSelector
import com.android.adblib.ShellCollector
import com.android.adblib.ShellCommand
import com.android.adblib.serialNumber
import com.android.adblib.shellCommand
import com.android.annotations.concurrency.WorkerThread
import com.android.ddmlib.AdbCommandRejectedException
import com.android.ddmlib.AdbHelper
import com.android.ddmlib.IDevice
import com.android.ddmlib.IShellOutputReceiver
import com.android.ddmlib.ShellCommandUnresponsiveException
import com.android.ddmlib.TimeoutException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.channels.trySendBlocking
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.single
import kotlinx.coroutines.runBlocking
import java.io.IOException
import java.io.InputStream
import java.nio.ByteBuffer
import java.time.Duration
import java.util.concurrent.TimeUnit

/**
 * Migration function for calls to [IDevice.executeShellCommand]
 */
@WorkerThread
@Throws(IOException::class,
        AdbCommandRejectedException::class,
        TimeoutException::class,
        ShellCommandUnresponsiveException::class
)
internal fun executeShellCommand(
    adbService: AdbHelper.AdbService,
    connectedDevice: ConnectedDevice,
    command: String,
    receiver: IShellOutputReceiver,
    maxTimeout: Long,
    maxTimeToOutputResponse: Long,
    maxTimeUnits: TimeUnit,
    inputStream: InputStream?
) {
    val deviceSelector = DeviceSelector.fromSerialNumber(connectedDevice.serialNumber)
    val shellCommand = connectedDevice.session.deviceServices.shellCommand(deviceSelector, command)
    setShellProtocol(shellCommand, adbService)
    if (maxTimeToOutputResponse > 0) {
        shellCommand.withCommandOutputTimeout(
            Duration.ofMillis(
                maxTimeUnits.toMillis(
                    maxTimeToOutputResponse
                )
            )
        )
    }
    if (maxTimeout > 0) {
        shellCommand.withCommandTimeout(Duration.ofMillis(maxTimeUnits.toMillis(maxTimeout)))
    }
    val stdoutCollector = ShellCollectorToIShellOutputReceiver(receiver)
    if (inputStream != null) {
      shellCommand.withStdin(connectedDevice.session.channelFactory.wrapInputStream(inputStream))
    }

    shellCommand.withLegacyCollector(stdoutCollector)
    runBlocking {
        mapToDdmlibException {
            // Note: We know there is only one item in the flow (Unit), because our
            //       ShellCollector implementation forwards buffers directly to
            //       the IShellOutputReceiver
            shellCommand.execute().single()
        }
    }
}

private fun setShellProtocol(shellCommand: ShellCommand<*>, adbService: AdbHelper.AdbService) {
    when (adbService) {
        // We are forcing a shell-v1 protocol here to match the behavior of the `DeviceImpl`
        AdbHelper.AdbService.SHELL -> shellCommand.forceLegacyShell()
        AdbHelper.AdbService.EXEC -> shellCommand.forceLegacyExec()
        AdbHelper.AdbService.ABB_EXEC -> throw IllegalArgumentException("ABB_EXEC is not supported by ShellCommand")
    }
}

/**
 * Coroutine-based wrapper around DDMLib's [IDevice.executeShellCommand], returning a Flow of shell output,
 * like that produced by [AdbDeviceServices.shell].
 */
@OptIn(ExperimentalCoroutinesApi::class)
fun <T> executeShellCommand(adbSession: AdbSession, device: IDevice, command: String, shellCollector: ShellCollector<T>): Flow<T> =
  flow {
    shellCollector.start(this)
    callbackFlow<ByteBuffer> {
      device.executeShellCommand(command, object : IShellOutputReceiver {
        override fun addOutput(data: ByteArray?, offset: Int, length: Int) {
          trySendBlocking(ByteBuffer.wrap(data, offset, length))
        }

        override fun flush() {
          close()
        }

        override fun isCancelled(): Boolean =
          channel.isClosedForSend
      })
      awaitClose()
    }.flowOn(adbSession.host.ioDispatcher).collect { value ->
      shellCollector.collect(this, value)
    }
    shellCollector.end(this)
  }

private inline fun <R> mapToDdmlibException(block: () -> R): R {
  return try {
    block()
  }
  catch (e: AdbFailResponseException) {
    throw AdbCommandRejectedException.create(e.failMessage)
  }
}
