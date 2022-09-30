/*
 * Copyright 2022 The Android Open Source Project
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

package com.google.services.firebase.directaccess.client.device.remote.service.adb.forwardingdaemon.reverse

/**
 * The type of message sent from either the daemon or the service.
 *
 * There are 5 types of messages:
 * 1. OPEN: Opens a socket (sent only from the daemon to the service).
 * 2. DATA: Sends data to an open stream.
 * 3. CLSE: Closes an open stream.
 * 4. KILL: Kills the reverse forward by no longer accepting local connections. Pre-existing
 * connections may terminate normally.
 * 5. REDY: Indicates that the daemon is ready to accept client connections and write to a stream.
 */
enum class MessageType(val const: Int) {
  OPEN(0x4f50454e), // ASCII "OPEN"
  DATA(0x44415441), // ASCII "DATA"
  CLSE(0x434c5345), // ASCII "CLSE"
  KILL(0x4b494c4c), // ASCII "KILL"
  REDY(0x52454459), // ASCII "REDY"
  ;

  companion object {
    private val commandsByConstants = values().associateBy { it.const }
    fun fromConstant(int: Int) =
      commandsByConstants[int] ?: throw MessageParseException(int.toString())
  }
}
