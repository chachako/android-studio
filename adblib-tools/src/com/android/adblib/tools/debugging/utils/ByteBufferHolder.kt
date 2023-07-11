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
package com.android.adblib.tools.debugging.utils

import com.android.adblib.tools.debugging.packets.copy
import java.nio.ByteBuffer

/**
 * A wrapper for a [ByteBuffer] that behaves like a [ByteBuffer] but can also grow by calling
 * [ensureCapacity]
 */
internal class ByteBufferHolder(initialCapacity: Int = 16) {
    private var buffer: ByteBuffer = ByteBuffer.allocate(initialCapacity).limit(0)

    /**
     * Resets [position] to `0` and [limit] to [capacity], keeping the underlying
     * [ByteBuffer] content untouched
     */
    fun clear() {
        buffer.clear()
    }

    fun position(value: Int) {
        buffer.position(value)
    }

    fun position(): Int {
        return buffer.position()
    }

    fun limit(value: Int) {
        buffer.limit(value)
    }

    fun limit(): Int {
        return buffer.limit()
    }

    fun remaining(): Int {
        return buffer.remaining()
    }

    fun slice(): ByteBuffer {
        return buffer.slice()
    }

    fun put(buffer: ByteBuffer): ByteBuffer {
        return this.buffer.put(buffer)
    }

    fun duplicate(): ByteBuffer {
        return buffer.duplicate()
    }

    fun capacity(): Int {
        return buffer.capacity()
    }

    fun ensureCapacity(minCapacity: Int) {
        require(minCapacity >= 0) {
            "Capacity should be a positive value"
        }
        val newCapacity = nextCapacity(minCapacity)
        if (newCapacity != buffer.capacity()) {
            // `buffer` has data from [0, limit]
            val newBuffer = ByteBuffer.allocate(newCapacity)
            newBuffer.order(buffer.order())

            // `buffer` has data from [0, limit]
            assert(buffer.position() == buffer.limit())
            buffer.position(0)
            newBuffer.put(buffer)
            assert(buffer.position() == buffer.limit())
            newBuffer.position(buffer.limit())
            newBuffer.limit(buffer.limit())
            buffer = newBuffer
        }
    }

    private fun nextCapacity(minCapacity: Int): Int {
        var newCapacity = buffer.capacity()
        while (newCapacity < minCapacity) {
            newCapacity *= 2
        }
        return newCapacity
    }

    /**
     * Returns a new [ByteBuffer] containing the content of the underlying [ByteBuffer]
     * from [position] to [limit]
     */
    fun copyBuffer(): ByteBuffer {
        return buffer.copy()
    }
}
