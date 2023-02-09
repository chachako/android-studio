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
package com.android.adblib.tools.testutils

import com.android.adblib.tools.debugging.JdwpCommandProgress
import com.android.adblib.tools.debugging.packets.JdwpPacketView

class FakeJdwpCommandProgress : JdwpCommandProgress {
    var afterSendIsCalled = false
    var beforeSendIsCalled = false
    var onReplyIsCalled = false
    var onReplyTimeoutIsCalled = false

    override suspend fun afterSend(packet: JdwpPacketView) {
        afterSendIsCalled = true
    }

    override suspend fun beforeSend(packet: JdwpPacketView) {
        beforeSendIsCalled = true
    }

    override suspend fun onReply(packet: JdwpPacketView) {
        onReplyIsCalled = true
    }

    override suspend fun onReplyTimeout() {
        onReplyTimeoutIsCalled = true
    }
}
