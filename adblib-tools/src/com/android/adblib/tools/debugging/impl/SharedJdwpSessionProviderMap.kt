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
package com.android.adblib.tools.debugging.impl

import com.android.adblib.ConnectedDevice
import com.android.adblib.thisLogger
import com.android.adblib.withPrefix
import java.util.concurrent.ConcurrentHashMap
import java.util.function.Function

/**
 * A map from [pid][Int] to [SharedJdwpSessionProvider] for a given [ConnectedDevice]
 */
internal class SharedJdwpSessionProviderMap(private val device: ConnectedDevice) : AutoCloseable {

    private val logger = thisLogger(device.session).withPrefix("$device: ")

    private val map = ConcurrentHashMap<Int, SharedJdwpSessionProvider>()

    private var closed = false

    /**
     * Callback to [ConcurrentHashMap.computeIfAbsent], stored in a field to avoid heap
     * allocation on every call.
     */
    private val createProvider = Function<Int, SharedJdwpSessionProvider> { pid ->
        SharedJdwpSessionProvider.create(device, pid, this::onProviderClosed).also {
            logger.debug { "Created provider for pid=$pid: $it" }
        }
    }

    /**
     * Returns the [SharedJdwpSessionProvider] instance for the process [pid] of this
     * [device].
     */
    fun computeIfAbsent(pid: Int): SharedJdwpSessionProvider {
        check(!closed) { "${this::class.simpleName} is closed" }

        return map.computeIfAbsent(pid, createProvider).also {
            logger.verbose { "# of entries=${map.size}" }
        }
    }

    /**
     * Called when the corresponding session scope is closed
     */
    override fun close() {
        closed = true
        logger.debug { "close(): # of entries=${map.size}" }
        map.values.forEach { it.close() }
        map.clear()
    }

    private fun remove(pid: Int) {
        map.remove(pid).also {
            logger.debug { "removing pid=$pid, provider=$it (# of entries left=${map.size})" }
        }
    }

    private fun onProviderClosed(provider: SharedJdwpSessionProvider) {
        remove(provider.pid)
    }
}
