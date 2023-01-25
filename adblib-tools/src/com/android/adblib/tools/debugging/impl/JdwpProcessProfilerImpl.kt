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
import com.android.adblib.tools.debugging.createDdmsMPSE
import com.android.adblib.tools.debugging.createDdmsMPSS
import com.android.adblib.tools.debugging.createDdmsSPSE
import com.android.adblib.tools.debugging.createDdmsSPSS
import com.android.adblib.tools.debugging.handleDdmsMPRQ
import com.android.adblib.tools.debugging.handleEmptyDdmsReplyPacket
import com.android.adblib.tools.debugging.handleEmptyReplyDdmsCommand
import com.android.adblib.tools.debugging.packets.ddms.DdmsChunkTypes
import com.android.adblib.tools.debugging.packets.ddms.ddmsChunks
import com.android.adblib.tools.debugging.packets.ddms.isDdmsCommand
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit

internal class JdwpProcessProfilerImpl(
    val process: JdwpProcess
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
            handleEmptyReplyDdmsCommand(requestPacket, DdmsChunkTypes.MPSS, progress)
        }
    }

    override suspend fun <R> stopInstrumentationProfiling(
        progress: JdwpCommandProgress?,
        block: suspend (data: AdbInputChannel, dataLength: Int) -> R
    ): R {
        // Send an "MPSE" DDMS command to the VM. Note that the AndroidVM treats "MPSE"
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

        return process.withJdwpSession {
            logger.debug { "Stopping method profiling session" }
            val requestPacket = createDdmsMPSE()
            var result: Result<R>? = null
            this.newPacketReceiver()
                .withName("stopMethodProfiling")
                .onActivation {
                    progress?.beforeSend(requestPacket)
                    sendPacket(requestPacket)
                    progress?.afterSend(requestPacket)
                }.flow()
                .first { packet ->
                    logger.verbose { "Receiving packet: $packet" }

                    if (packet.isDdmsCommand) {
                        packet.ddmsChunks().collect { chunk ->
                            if (chunk.type == DdmsChunkTypes.MPSE) {
                                result = Result.success(block(chunk.payload, chunk.length))
                            }
                        }
                    }

                    // Stop when we receive the reply to our MPSE command
                    val isReply = (packet.isReply && packet.id == requestPacket.id)
                    if (isReply) {
                        progress?.onReply(packet)
                        handleEmptyDdmsReplyPacket(packet, DdmsChunkTypes.MPSE)
                    }
                    isReply
                }
            result?.getOrNull()
                ?: throw DdmsCommandException("MPSE command packet was not sent by Android VM")
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
            handleEmptyReplyDdmsCommand(requestPacket, DdmsChunkTypes.SPSS, progress)
        }
    }

    override suspend fun <R> stopSampleProfiling(
        progress: JdwpCommandProgress?,
        block: suspend (data: AdbInputChannel, dataLength: Int) -> R): R {
        // Send an "SPSE" DDMS command to the VM. Note that the AndroidVM treats "SPSE"
        // in a special way: the profiling data comes back as an "MPSE" command (with the
        // profiling data as the payload), then we get back an empty reply packet to the "SPSE"
        // command we sent.
        //   Debugger        |  AndroidVM
        //   ----------------+-----------------------------------------
        //   SPSE command => |
        //                   | (prepare profiling data for sending)
        //                   | <=  SPSE command (with data as payload)
        //                   | <=  MPSE reply (empty payload)
        //                   | OR
        //                   | <=  FAIL command (with error code and message)

        return process.withJdwpSession {
            logger.debug { "Stopping sampling profiling session" }
            val requestPacket = createDdmsSPSE()
            var result: Result<R>? = null
            this.newPacketReceiver()
                .withName("stopSamplingProfiling")
                .onActivation {
                    progress?.beforeSend(requestPacket)
                    sendPacket(requestPacket)
                    progress?.afterSend(requestPacket)
                }.flow()
                .first { packet ->
                    logger.verbose { "Receiving packet: $packet" }

                    if (packet.isDdmsCommand) {
                        packet.ddmsChunks().collect { chunk ->
                            if (chunk.type == DdmsChunkTypes.MPSE) {
                                result = Result.success(block(chunk.payload, chunk.length))
                            }
                        }
                    }

                    // Stop when we receive the reply to our MPSE command
                    val isReply = (packet.isReply && packet.id == requestPacket.id)
                    if (isReply) {
                        progress?.onReply(packet)
                        handleEmptyDdmsReplyPacket(packet, DdmsChunkTypes.SPSE)
                    }
                    isReply
                }
            result?.getOrNull()
                ?: throw DdmsCommandException("SPSE command packet was not sent by Android VM")
        }
    }
}
