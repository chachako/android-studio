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
package com.android.adblib.tools.tcpserver

import com.android.adblib.AdbServerSocket
import kotlinx.coroutines.Job

/**
 * A TCP server that can be launched on given [AdbServerSocket]
 */
internal interface TcpServer : AutoCloseable {

    /**
     * Starts a new instance of this [TcpServer] on the given [AdbServerSocket].
     * Returns the [Job] associated to the server, which can be cancelled to shut the
     * server down.
     */
    fun launch(serverSocket: AdbServerSocket): Job
}
