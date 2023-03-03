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
package com.android.processmonitor.testutils

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.io.Closeable

/**
 * A Channel created from a [Flow]
 *
 * Allows elements to be read from a Flow interactively without canceling it.
 *
 * Example use:
 * ```
 *    someFlow.toChannel(scope).use {
 *      makeFlowEmit(1)
 *      assertThat(it.receive()).isEqual(1)
 *      makeFlowEmit(2)
 *      assertThat(it.receive()).isEqual(2)
 *    }
 * ```
 */
internal class FlowChannel<E>(scope: CoroutineScope, flow: Flow<E>) : Closeable {

    private val channel = Channel<E>(10)
    private val job = scope.launch { flow.collect { channel.send(it) } }

    suspend fun receive(): E = channel.receive()

    suspend fun receiveOrNull(timeout: Long = 1000): E? = withTimeoutOrNull(timeout) { receive() }

    suspend fun take(count: Int): List<E> {
        val list = mutableListOf<E>()
        repeat(count) {
            list.add(receive())
        }
        return list
    }

    override fun close() {
        job.cancel()
    }
}

internal fun <T> Flow<T>.toChannel(scope: CoroutineScope): FlowChannel<T> {
    return FlowChannel(scope, this)
}
