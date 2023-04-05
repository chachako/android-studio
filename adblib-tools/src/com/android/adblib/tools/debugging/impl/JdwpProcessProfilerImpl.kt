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

import com.android.adblib.AdbInputChannel
import com.android.adblib.AdbSession
import com.android.adblib.thisLogger
import com.android.adblib.tools.debugging.DdmsCommandException
import com.android.adblib.tools.debugging.JdwpCommandProgress
import com.android.adblib.tools.debugging.JdwpProcess
import com.android.adblib.tools.debugging.JdwpProcessProfiler
import com.android.adblib.tools.debugging.ProfilerStatus
import com.android.adblib.tools.debugging.SharedJdwpSession
import com.android.adblib.tools.debugging.Signal
import com.android.adblib.tools.debugging.createDdmsMPSE
import com.android.adblib.tools.debugging.createDdmsMPSS
import com.android.adblib.tools.debugging.createDdmsSPSE
import com.android.adblib.tools.debugging.createDdmsSPSS
import com.android.adblib.tools.debugging.handleDdmsCommandAndReplyProtocol
import com.android.adblib.tools.debugging.handleDdmsCommandWithEmptyReply
import com.android.adblib.tools.debugging.handleDdmsMPRQ
import com.android.adblib.tools.debugging.packets.JdwpPacketView
import com.android.adblib.tools.debugging.packets.ddms.DdmsChunkType
import com.android.adblib.tools.debugging.packets.ddms.ddmsChunks
import com.android.adblib.tools.debugging.packets.ddms.isDdmsCommand
import com.android.adblib.tools.debugging.processEmptyDdmsReplyPacket
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit

internal class JdwpProcessProfilerImpl(
    override val process: JdwpProcess
) : JdwpProcessProfiler {

    private val session: AdbSession
        get() = process.device.session

    private val logger = thisLogger(session)

    override suspend fun queryStatus(progress: JdwpCommandProgress?): ProfilerStatus {
        return process.withJdwpSession {
            when (handleDdmsMPRQ(progress)) {
                0 -> ProfilerStatus.Off
                1 -> ProfilerStatus.InstrumentationProfilerRunning
                2 -> ProfilerStatus.SamplingProfilerRunning
                else -> throw DdmsCommandException("Unknown profiler status value")
            }
        }
    }

    override suspend fun startInstrumentationProfiling(
        bufferSize: Int,
        progress: JdwpCommandProgress?
    ) {
        process.withJdwpSession {
            val requestPacket = createDdmsMPSS(bufferSize)
            handleDdmsCommandWithEmptyReply(requestPacket, DdmsChunkType.MPSS, progress)
        }
    }

    override suspend fun <R> stopInstrumentationProfiling(
        progress: JdwpCommandProgress?,
        block: suspend (data: AdbInputChannel, dataLength: Int) -> R
    ): R {
        return process.withJdwpSession {
            logger.debug { "Stopping method profiling session" }
            val requestPacket = createDdmsMPSE()
            handleMPSEReply(requestPacket, DdmsChunkType.MPSE, progress, block)
        }
    }

    override suspend fun startSampleProfiling(
        interval: Long,
        intervalUnit: TimeUnit,
        bufferSize: Int,
        progress: JdwpCommandProgress?,
    ) {
        process.withJdwpSession {
            logger.debug { "Starting sampling profiling session" }
            val requestPacket = createDdmsSPSS(bufferSize, interval, intervalUnit)
            handleDdmsCommandWithEmptyReply(requestPacket, DdmsChunkType.SPSS, progress)
        }
    }

    override suspend fun <R> stopSampleProfiling(
        progress: JdwpCommandProgress?,
        block: suspend (data: AdbInputChannel, dataLength: Int) -> R
    ): R {
        return process.withJdwpSession {
            logger.debug { "Stopping sampling profiling session" }
            val requestPacket = createDdmsSPSE()
            handleMPSEReply(requestPacket, DdmsChunkType.SPSE, progress, block)
        }
    }

    private suspend fun <R> SharedJdwpSession.handleMPSEReply(
        requestPacket: JdwpPacketView,
        chunkType: DdmsChunkType,
        progress: JdwpCommandProgress?,
        block: suspend (data: AdbInputChannel, dataLength: Int) -> R
    ): R {
        return handleDdmsCommandAndReplyProtocol(progress) { signal ->
            handleMPSEReplyImpl(requestPacket, chunkType, progress, signal, block)
        }
    }

    private suspend fun <R> SharedJdwpSession.handleMPSEReplyImpl(
        requestPacket: JdwpPacketView,
        chunkType: DdmsChunkType,
        progress: JdwpCommandProgress?,
        signal: Signal<R>,
        block: suspend (data: AdbInputChannel, dataLength: Int) -> R
    ) {
        assert(chunkType == DdmsChunkType.MPSE || chunkType == DdmsChunkType.SPSE)

        // Send an "MPSE" (or "SPSE") DDMS command to the VM. Note that the AndroidVM treats "MPSE"
        // in a special way: the profiling data comes back as an "MPSE" command (with the
        // profiling data as the payload), then we get back an empty reply packet to the "MPSE"
        // command we sent.
        //   Debugger        |  AndroidVM
        //   ----------------+-----------------------------------------
        //   MPSE command => |
        //                   | (collects data)
        //                   | <=  MPSE command (with data as payload)
        //                   | <=  MPSE reply (empty payload)
        //                   | OR
        //                   | <=  FAIL command (with error code and message)

        this.newPacketReceiver()
            .withName("handleMPSEReply")
            .onActivation {
                progress?.beforeSend(requestPacket)
                sendPacket(requestPacket)
                progress?.afterSend(requestPacket)
            }.flow()
            .first { packet ->
                logger.verbose { "Receiving packet: $packet" }

                if (packet.isDdmsCommand) {
                    packet.ddmsChunks().collect { chunk ->
                        if (chunk.type == DdmsChunkType.MPSE) {
                            val blockResult = block(chunk.payload, chunk.length)

                            // We got the result we want, signal so that the timeout waiting for
                            // reply packet starts now.
                            signal.complete(blockResult)
                        }
                    }
                }

                // Stop when we receive the reply to our MPSE command
                val isReply = (packet.isReply && packet.id == requestPacket.id)
                if (isReply) {
                    progress?.onReply(packet)
                    processEmptyDdmsReplyPacket(packet, chunkType)
                }
                isReply
            }
    }
}
