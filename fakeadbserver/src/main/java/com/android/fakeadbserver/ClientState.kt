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
package com.android.fakeadbserver

import java.net.Socket
import java.time.Duration
import java.util.Arrays
import java.util.concurrent.atomic.AtomicInteger

/**
 * JDWP processes state.
 *
 * @param isWaiting whether this client is waiting for a debugger connection or not.
 */
class ClientState internal constructor(
    pid: Int, val uid: Int, val processName: String, val packageName: String, isWaiting: Boolean
) : ProcessState(pid) {

    val viewsState = ClientViewsState()
    val profilerState = ProfilerState()

    // If non-empty then the STAG command(s) will be sent out after the HELO response after
    // the specified delay(s). STAG payload will be populated from the ClientState.stage value.
    val sendStagCommandAfterHelo = mutableListOf<Duration>()

    // If non-null then the WAIT command indicating that the client is waiting for debugger will be
    // sent out before or after the HELO reply.
    // Note that if the specified duration is negative then the WAIT command will be sent out right
    // before the HELO reply and the actual duration value is ignored.
    var sendWaitCommandAfterHelo: Duration? = null

    /**
     * Set of DDMS features for this process.
     *
     * See [HandleFEAT source code](https://cs.android.com/android/platform/superproject/+/android13-release:frameworks/base/core/java/android/ddm/DdmHandleHello.java;l=107)
     */
    private val mFeatures: MutableSet<String> = HashSet()
    private var jdwpSocket: Socket? = null
    var isAllocationTrackerEnabled = false
    var allocationTrackerDetails = ""

    // App boot stage
    var stage: AppStage? = null
        private set
    private val hgpcRequestsCount = AtomicInteger()
    private val nextDdmsCommandId = AtomicInteger(0x70000000)

    init {
        if (isWaiting) {
            sendWaitCommandAfterHelo = Duration.ZERO
        }
        mFeatures.addAll(Arrays.asList(*mBuiltinVMFeatures))
        mFeatures.addAll(Arrays.asList(*mBuiltinFrameworkFeatures))
    }

    override val debuggable: Boolean
        get() = true
    override val profileable: Boolean
        get() = false

    @Synchronized
    fun startJdwpSession(socket: Socket): Boolean {
        if (jdwpSocket != null) {
            return false
        }
        jdwpSocket = socket
        return true
    }

    @Synchronized
    fun stopJdwpSession() {
        if (jdwpSocket != null) {
            try {
                jdwpSocket!!.shutdownOutput()
                Thread.sleep(10) // So that FIN is received by peer
                jdwpSocket!!.close()
            } catch (e: Exception) {
                throw RuntimeException(e)
            }
        }
        jdwpSocket = null
    }

    fun nextDdmsCommandId(): Int {
        return nextDdmsCommandId.incrementAndGet()
    }

    @Synchronized
    fun clearFeatures() {
        mFeatures.clear()
    }

    @Synchronized
    fun addFeature(value: String) {
        mFeatures.add(value)
    }

    @Synchronized
    fun removeFeature(value: String) {
        mFeatures.remove(value)
    }

    @get:Synchronized
    val features: Set<String>
        get() = HashSet(mFeatures)

    fun requestHgpc() {
        hgpcRequestsCount.incrementAndGet()
    }

    fun getHgpcRequestsCount(): Int {
        return hgpcRequestsCount.get()
    }

    fun setStage(stage: AppStage) {
        this.stage = stage
    }

    companion object {

        /**
         * See [List of VM features](https://cs.android.com/android/platform/superproject/+/android13-release:art/runtime/native/dalvik_system_VMDebug.cc;l=56)
         */
        private val mBuiltinVMFeatures = arrayOf(
            "method-trace-profiling",
            "method-trace-profiling-streaming",
            "method-sample-profiling",
            "hprof-heap-dump",
            "hprof-heap-dump-streaming"
        )

        /**
         * See [Framework features](https://cs.android.com/android/platform/superproject/+/android13-release:frameworks/base/core/java/android/ddm/DdmHandleHello.java;drc=4794e479f4b485be2680e83993e3cf93f0f42d03;l=44)
         */
        private val mBuiltinFrameworkFeatures = arrayOf(
            "opengl-tracing", "view-hierarchy"
        )
    }
}
