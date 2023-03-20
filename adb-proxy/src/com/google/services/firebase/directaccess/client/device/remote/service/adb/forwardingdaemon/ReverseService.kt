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
import java.util.concurrent.ConcurrentHashMap
import java.util.logging.Logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * A local implementation of the "reverse:" service on an Android device.
 *
 * The "reverse:" service on an Android device allows the user to create port forwards from the
 * device to the host. However, its current implementation works by sending OPEN requests from the
 * device to the host, which means that any reverse forwards opened from the device will land on the
 * **first** host that exists. For the forwarding daemon, this means that it would land on the lab
 * machine, which is both a feature gap and a security issue.
 *
 * The ReverseService should know about all instances of [ReverseForwardStream] which are currently
 * active. This allows for the implementation of `adb reverse --list`, `adb reverse --remove-all`,
 * and `adb reverse --remove <remote>`.
 */
internal class ReverseService(
  private val deviceId: String,
  private val scope: CoroutineScope,
  private val responseWriter: ResponseWriter,
  private val adbSession: AdbSession,
  private val reverseForwardStreamFactory: (String, String, Int) -> ReverseForwardStream =
    { devicePort, localPort, streamId ->
      ReverseForwardStream(
        devicePort,
        localPort,
        streamId,
        deviceId,
        adbSession,
        responseWriter,
        scope
      )
    }
) {
  private val openReverses = ConcurrentHashMap<String, ReverseForwardStream>()
  private val openReversesLock = Mutex()

  /** Handle a command to the "reverse:" service. */
  suspend fun handleReverse(command: String, streamId: Int) {
    val subcommand = command.substringAfter("reverse:").substringBefore('\u0000')
    logger.info("Handling '$command'")
    when (subcommand.substringBefore(":")) {
      "forward" -> handleForward(subcommand, streamId)
      "killforward" -> handleKillForward(streamId, subcommand.substringAfter("killforward:"))
      "killforward-all" -> handleKillForwardAll(streamId)
      "list-forward" -> handleListForward(streamId)
      else -> responseWriter.writeFailResponse(streamId, command)
    }
  }

  private suspend fun handleForward(subcommand: String, streamId: Int): Stream? {
    val arguments = subcommand.substringAfter("forward:").substringAfter("norebind:").split(';')
    // TODO: validate arguments
    val devicePort = arguments[0]
    val localPort = arguments[1]
    openReversesLock.withLock {
      val existingStream = openReverses[devicePort]
      if (existingStream != null) {
        if (subcommand.substringAfter("forward:").substringBefore(":") == "norebind") {
          responseWriter.writeFailResponse(streamId, "'$devicePort' already bound")
          return null
        }
        existingStream.rebind(localPort)
        responseWriter.writeOkayResponse(streamId)
        return null
      }
      val stream = reverseForwardStreamFactory(devicePort, localPort, streamId)
      openReverses[devicePort] = stream
      scope.launch {
        // TODO(247652380): error handling
        stream.run()
      }
      return stream
    }
  }

  private suspend fun handleKillForward(streamId: Int, devicePort: String) {
    killForward(devicePort)
    responseWriter.writeOkayResponse(streamId)
  }

  private suspend fun handleKillForwardAll(streamId: Int) {
    killAll()
    responseWriter.writeOkayResponse(streamId)
  }

  private suspend fun handleListForward(streamId: Int) {
    // Build the list of all forward connections
    val stringBuilder = StringBuilder()
    openReversesLock.withLock {
      for (value in openReverses.elements()) {
        // Format from
        // https://cs.android.com/android/platform/superproject/+/3a52886262ae22477a7d8ffb12adba64daf6aafa:packages/modules/adb/adb_listeners.cpp;l=136;drc=6951984bbefb96423970b82005ae381065e36704
        stringBuilder.append("(reverse) ${value.devicePort} ${value.localPort}\n")
      }
    }
    responseWriter.writeOkayResponse(streamId, stringBuilder.toString())
  }

  /**
   * Kill all open reverse forward connections.
   *
   * Note that any currently open sockets will remain open. However, the server sockets on the
   * device will be closed, so no new connections will be made.
   */
  suspend fun killAll() {
    openReversesLock.withLock { openReverses.forEach { (key, _) -> killForwardUnsafe(key) } }
  }

  private suspend fun killForward(key: String) {
    logger.info("Handling killforward for $key")
    openReversesLock.withLock { killForwardUnsafe(key) }
  }

  /**
   * This function is unsafe to call directly. Instead, call to this function should be wrapped with
   * a lock on [openReversesLock].
   *
   * For example, see [killForward], [killAll]
   */
  private suspend fun killForwardUnsafe(key: String) {
    assert(openReversesLock.isLocked)
    openReverses.remove(key)?.kill()
  }

  companion object {
    private val logger = Logger.getLogger(ReverseService::class.qualifiedName)
  }
}
