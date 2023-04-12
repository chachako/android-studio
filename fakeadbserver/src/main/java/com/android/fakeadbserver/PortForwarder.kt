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
package com.android.fakeadbserver

/**
 * Represents a port forward pair from [.mSource] to [.mDestination].
 */
class PortForwarder private constructor(hostPort: Int) {
  val source = ForwarderSource()
  val destination = ForwarderDestination()

  /** Use one of the static factory methods to create an instance of this class.  */
  init {
    source.port = hostPort
  }

  class ForwarderSource {
    var port = INVALID_PORT
  }

  class ForwarderDestination {
    var port = INVALID_PORT
    var jdwpPid = INVALID_PORT
    var unixDomain: String? = null
  }

  companion object {
    /**
     * An invalid/uninitialized port number.
     */
    const val INVALID_PORT = -1
    @JvmStatic
    fun createPortForwarder(hostPort: Int, port: Int): PortForwarder {
      val forwarder = PortForwarder(hostPort)
      forwarder.destination.port = port
      return forwarder
    }

    fun createJdwpForwarder(hostPort: Int, pid: Int): PortForwarder {
      val forwarder = PortForwarder(hostPort)
      forwarder.destination.jdwpPid = pid
      return forwarder
    }

    @JvmStatic
    fun createUnixForwarder(hostPort: Int, unixDomain: String): PortForwarder {
      val forwarder = PortForwarder(hostPort)
      forwarder.destination.unixDomain = unixDomain
      return forwarder
    }
  }
}
