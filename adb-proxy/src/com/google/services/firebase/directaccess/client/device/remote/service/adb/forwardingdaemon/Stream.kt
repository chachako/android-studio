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

/** A stream of data opened from the local ADB server to the forwarding daemon. */
interface Stream {
  /** Send a write message from the local ADB server to the remote device. */
  fun sendWrite(command: WriteCommand)

  /** Send a close message from the local ADB server to the remote device. */
  fun sendClose()

  /**
   * Receive a command from the remote device, to either be handled internally in this stream or
   * sent back to the local ADB server.
   *
   * For services that we replace in the ForwardingDaemon, we may want to make additional or
   * alternative requests to the remote device. The responses to those requests should be handled
   * here.
   */
  fun receiveCommand(command: StreamCommand)
}
