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

import org.junit.Ignore
import org.junit.Test

class FakeAdbServerTest {

    /** Very basic test example. Remove `@Ignore` if you wish to run an interactive server.  */
    @Test
    @Ignore
    fun testInteractiveServer() {
        // Build the server and configure it to use the default ADB command handlers.
        val builder = FakeAdbServer.Builder()
        builder.installDefaultCommandHandlers()
        builder.build().use { server ->
            // Connect a test device to simulate device connection before server bring-up.
            server.connectDevice(
                SERIAL, MANUFACTURER, MODEL, RELEASE, SDK, DeviceState.HostConnectionType.USB
            )

            // Start server execution.
            server.start()

            // Optional: Since the server lives on a separate thread, we can pause the test to poke at
            // the server.
            server.awaitServerTermination()
        }
    }

    companion object {

        private const val SERIAL = "test_device_001"
        private const val MANUFACTURER = "Google"
        private const val MODEL = "Nexus Silver"
        private const val RELEASE = "8.0"
        private const val SDK = "26"
    }
}
