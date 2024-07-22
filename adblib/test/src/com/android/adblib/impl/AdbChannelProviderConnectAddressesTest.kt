/*
 * Copyright (C) 2024 The Android Open Source Project
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

package com.android.adblib.impl

import com.android.adblib.testingutils.CloseablesRule
import com.android.adblib.testingutils.TestingAdbSessionHost
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.util.concurrent.TimeUnit
import kotlin.test.assertNotNull

class AdbChannelProviderConnectAddressesTest {

    @JvmField
    @Rule
    val closeables = CloseablesRule()

    private lateinit var serverSocket: ServerSocket

    @Before
    fun setUp() {
        // Find an available port
        serverSocket = ServerSocket(0)
    }

    @After
    fun tearDown() {
        serverSocket.close()
    }

    @Test
    fun testCreateChannel(): Unit = runBlocking {
        // Setup
        val host = TestingAdbSessionHost()
        val socketAddresses = listOf(
            InetSocketAddress("127.0.0.1", serverSocket.localPort),
            InetSocketAddress("::1", serverSocket.localPort)
        )
        val channelProvider = AdbChannelProviderConnectAddresses(host) { socketAddresses }

        // Act
        val channel =
            closeables.register(
                channelProvider.createChannel(
                    Long.MAX_VALUE,
                    TimeUnit.MILLISECONDS
                )
            )

        // Assert
        assertNotNull(channel)
    }
}
