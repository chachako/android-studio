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
package com.android.adblib.tools.debugging

import com.android.adblib.AdbInputChannel
import com.android.adblib.tools.debugging.packets.ddms.DdmsChunkType
import com.android.adblib.tools.debugging.packets.ddms.DdmsFailException
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Default buffer size for profiler operations. The value of `zero` implies
 * a value of 8 MBytes, see
 * [dalvik.system.VMDebug.java](https://cs.android.com/android/platform/superproject/+/c26d2480913a2afda0e87cf978a10beb3109980a:libcore/dalvik/src/main/java/dalvik/system/VMDebug.java;l=313)
 */
private const val DEFAULT_PROFILING_BUFFER_SIZE = 0


/**
 * Handles collecting CPU profiling information (instrumentation or sample) from a [JdwpProcess]
 * using the DDMS protocol commands (See [DdmsChunkType.SPSS], [DdmsChunkType.MPSS], etc.)
 *
 * Note: Modern profilers use a custom Java agent to collect profiling data, so this API
 * should only be used for legacy devices with API < 26 (i.e. Android "N-MR1" and earlier).
 */
interface JdwpProcessProfiler {

    /**
     * The JDWP process associated to this [JdwpProcessProfiler]
     */
    val process: JdwpProcess

    /**
     * Returns the current [ProfilerStatus] of this [process] by querying the Android VM.
     */
    suspend fun queryStatus(progress: JdwpCommandProgress? = null): ProfilerStatus

    /**
     * Starts instrumentation profiling on the [JdwpProcess]. The profiling data is collected
     * by the AndroidVM in an in-memory buffer of [bufferSize] bytes. To access to profiling data,
     * [stopInstrumentationProfiling] must be invoked.
     *
     * @param bufferSize The size of the in-memory buffer the AndroidVM uses to collected
     * profiling data. A value of `zero` implies a default buffer size of 8MB.
     *
     * @throws DdmsFailException if the AndroidVM could not start the profiling session
     * @throws DdmsCommandException if there was a DDMS protocol issue
     * @throws IOException if there was an error sending/receiving data
     * @throws Exception if there was any other kind of error
     */
    suspend fun startInstrumentationProfiling(
        bufferSize: Int = DEFAULT_PROFILING_BUFFER_SIZE,
        progress: JdwpCommandProgress? = null)

    /**
     * Stops a method profiling session started with [startInstrumentationProfiling], invoking
     * [block] with the profiling data collected during the session. `data` is an
     * [AdbInputChannel] of `dataLength` bytes and is streamed directly from the underlying
     * JDWP network connection.
     */
    suspend fun <R> stopInstrumentationProfiling(
        progress: JdwpCommandProgress? = null,
        block: suspend (data: AdbInputChannel, dataLength: Int) -> R
    ): R

    /**
     * Starts a sample profiling on the [JdwpProcess]. The profiling data is collected on the
     * AndroidVM in an in-memory buffer of [bufferSize] bytes. To access to profiling data
     * (and stop the profiling session), [stopSampleProfiling] must be invoked.
     */
    suspend fun startSampleProfiling(
        interval: Long,
        intervalUnit: TimeUnit,
        bufferSize: Int = DEFAULT_PROFILING_BUFFER_SIZE,
        progress: JdwpCommandProgress? = null,
    )

    /**
     * Stops a method profiling session previously started with [startSampleProfiling], invoking
     * [block] with the profiling data collected during the session. `data` is an
     * [AdbInputChannel] of `dataLength` bytes and is streamed directly from the underlying
     * JDWP network connection.
     */
    suspend fun <R> stopSampleProfiling(
        progress: JdwpCommandProgress? = null,
        block: suspend (data: AdbInputChannel, dataLength: Int) -> R
    ): R
}

enum class ProfilerStatus {
    Off,
    InstrumentationProfilerRunning,
    SamplingProfilerRunning,
}
