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
package com.android.adblib.tools.debugging.packets.ddms

import com.android.adblib.AdbInputChannel
import com.android.adblib.tools.debugging.packets.impl.PayloadProvider

/**
 * Provides access to various elements of a DDMS "chunk". A DDMS "chunk" always starts with
 * an 8-byte header, followed by variable size buffer of [length] bytes.
 */
interface DdmsChunkView {
    /**
     * The chunk type, a 4-byte integer, see [DdmsChunkType]
     */
    val type: DdmsChunkType

    /**
     * The length (in bytes) of the `payload`, a 4-byte integer.
     */
    val length: Int

    /**
     * **Note: Do NOT use directly, use [withPayload] instead**
     *
     * Returns the payload of this [DdmsChunkView] as an [AdbInputChannel] instance.
     * [releasePayload] must be called when the returned [AdbInputChannel] is not used anymore.
     *
     * @throws IllegalStateException if the `payload` of this [DdmsChunkView] instance is not
     *  available anymore.
     * @see [PayloadProvider.acquirePayload]
     */
    suspend fun acquirePayload(): AdbInputChannel

    /**
     * **Note: Do NOT use directly, use [withPayload] instead**
     *
     * Releases the [AdbInputChannel] previously returned by [acquirePayload].
     *
     * @see [PayloadProvider.releasePayload]
     */
    fun releasePayload()
}

/**
 * Invokes [block] with payload of this [DdmsChunkView]. The payload is passed to [block]
 * as an [AdbInputChannel] instance that is valid only during the [block] invocation.
 *
 * @throws IllegalStateException if the `payload` of this [DdmsChunkView] instance is not
 *  available anymore.
 */
suspend inline fun <R> DdmsChunkView.withPayload(block: (AdbInputChannel) -> R): R {
    val payload = acquirePayload()
    return try {
        block(payload)
    } finally {
        releasePayload()
    }
}
