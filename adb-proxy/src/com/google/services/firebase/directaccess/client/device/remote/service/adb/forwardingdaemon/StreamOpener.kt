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

import com.android.adblib.AdbOutputChannel

/**
 * Interface for handling stream creation requests.
 *
 * Each [ForwardingDaemon] creates exactly one [StreamOpener], which it uses to open new [Stream]s.
 * The StreamOpener is responsible for
 * * Opening new streams when requested by the ForwardingDaemon. For this, the StreamOpener may
 *   reach out to a remote service.
 * * Handling Stream events (likely forwarding them along to the server)
 * * Dispatching new commands received from a remote service back to the *ForwardingDaemon* so that
 *   it can appropriately manage the lifecycle of streams.
 */
interface StreamOpener : AutoCloseable {
  /** Connect to the remote service and register the provided ForwardingDaemon for callbacks. */
  fun connect(forwardingDaemon: ForwardingDaemon)

  /**
   * Open a new service stream on the remote device. The returned stream will be managed by the
   * [ForwardingDaemon].
   */
  fun open(service: String, streamId: Int, adbOutputChannel: AdbOutputChannel): Stream
}
