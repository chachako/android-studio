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
package com.android.adblib.ddmlibcompatibility.debugging

import com.android.adblib.AdbChannel
import com.android.adblib.read
import com.android.ddmlib.SimpleConnectedSocket
import kotlinx.coroutines.runBlocking
import java.nio.ByteBuffer
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

/** Wraps {@link AdbChannel} and provides a way to perform blocking reads/writes with timeout. */
internal class AdblibChannelWrapper(
    val channel: AdbChannel
) : SimpleConnectedSocket {

    private var closed = false

    override fun read(dst: ByteBuffer, timeoutMs: Long): Int =
        runBlocking {
            try {
                channel.read(dst, timeoutMs, TimeUnit.MILLISECONDS)
            } catch (e: TimeoutException) {
                0
            }
        }

    override fun write(dst: ByteBuffer, timeoutMs: Long): Int =
        runBlocking {
            try {
                channel.write(dst, timeoutMs, TimeUnit.MILLISECONDS)
            } catch (e: TimeoutException) {
                0
            }
        }

    override fun isOpen(): Boolean {
        return closed
    }

    override fun close() {
        closed = true
        channel.close()
    }
}
