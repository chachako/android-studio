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

package com.google.services.firebase.directaccess.client.device.remote.service.adb.forwardingdaemon

import com.android.adblib.AdbSession
import java.time.Duration
import java.util.concurrent.TimeoutException
import kotlinx.coroutines.CoroutineScope

/**
 * A ForwardingDaemon behaves as an ADB daemon, connecting to a remote Android device.
 *
 * A forwarding daemon has two primary components:
 * 1. A local server socket, where it speaks the ADB server to device protocol, and it behaves
 *    exactly as a local TCP Android device (such as an emulator or device on the same network)
 *    would.
 * 2. A "StreamOpener" that forwards connections opened locally to a remote service of some kind
 *    (possibly gRPC or even just the ADB client to server protocol).
 *
 * In the context of the ForwardingDaemon and its surrounding components, "local" refers to
 * everything on the machine that the forwarding daemon is running on, and "remote" refers to the
 * ADB server or device that we are connecting to.
 */
interface ForwardingDaemon : AutoCloseable {
  var devicePort: Int

  interface Factory {
    fun create(
      streamOpener: StreamOpener,
    ): ForwardingDaemon
  }

  /**
   * Start the daemon and wait for the device's socket to be ready and listening.
   *
   * @param timeout the maximum time to wait for the device to become ready
   * @throws TimeoutException if it took longer than the specified duration to become ready
   */
  @Throws(TimeoutException::class) suspend fun start(timeout: Duration = Duration.ofMinutes(1))

  /**
   * Called when the device state changes.
   *
   * The "features" string should be exactly as it's returned from the
   * "host-serial:transport:features" service in the ADB server.
   */
  suspend fun onStateChanged(newState: DeviceState, features: String? = null)

  /**
   * Receive a command from the remote ADB server.
   *
   * Upon receipt of a remote command from the ADB server, the ForwardingDaemon should handle it by
   * routing it to the appropriate Stream.
   */
  suspend fun receiveRemoteCommand(command: StreamCommand)
}

fun ForwardingDaemon(
  streamOpener: StreamOpener,
  scope: CoroutineScope,
  adbSession: AdbSession
): ForwardingDaemon = ForwardingDaemonImpl(streamOpener, scope, adbSession)
