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
package com.android.adblib.tools.debugging.packets.ddms

import com.android.adblib.AdbInputChannel
import com.android.adblib.tools.debugging.packets.impl.PayloadProvider
import com.android.adblib.utils.ResizableBuffer

/**
 * A [DdmsChunkView] implementation that is immutable and allows on-demand access to the
 * chunk payload.
 */
internal class EphemeralDdmsChunk(
  override val type: DdmsChunkType,
  override val length: Int,
  private val payloadProvider: PayloadProvider
): DdmsChunkView, AutoCloseable {

    override suspend fun acquirePayload(): AdbInputChannel {
        return payloadProvider.acquirePayload()
    }

    override fun releasePayload() {
        return payloadProvider.releasePayload()
    }

    /**
     * Shuts down this [EphemeralDdmsChunk], releasing resources if necessary.
     */
    suspend fun shutdown(workBuffer: ResizableBuffer) {
        payloadProvider.shutdown(workBuffer)
    }

    override fun close() {
        payloadProvider.close()
    }

    override fun toString(): String {
        return "DDMS Chunk: type=$type (${type.value}), length=$length, payloadProvider=$payloadProvider"
    }
}
