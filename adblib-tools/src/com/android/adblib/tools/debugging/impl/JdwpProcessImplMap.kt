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
import com.android.adblib.CoroutineScopeCache
import com.android.adblib.tools.debugging.utils.ReferenceCounted

/**
 * A thread-safe collection of [JdwpProcessImpl] for a given [ConnectedDevice],
 * with O(1) access by process ID.
 */
internal class JdwpProcessImplMap(private val device: ConnectedDevice) : AutoCloseable {

    private val processMapLock = Any()

    private val processMap = hashMapOf<Int, ReferenceCounted<JdwpProcessImpl>>()

    /**
     * Returns the [JdwpProcessImpl] instance corresponding to [pid], after creating it
     * and adding a reference count to it. [release] should be called when the
     * [JdwpProcessImpl] instance is not needed anymore.
     */
    fun retain(pid: Int): JdwpProcessImpl {
        return synchronized(processMapLock) {
            processMap.getOrPut(pid) {
                ReferenceCounted(JdwpProcessImpl(device.session, device, pid))
            }.retain()
        }
    }

    /**
     * Releases the reference count for the given [pid] that was previously [retained][retain].
     */
    fun release(pid: Int) {
        synchronized(processMapLock) {
            processMap[pid]?.also { refCounted ->
                // Remove entry if new reference count is zero
                if (refCounted.release() == 0) {
                    processMap.remove(pid)
                }
            }
        }
    }

    override fun close() {
        synchronized(processMapLock) {
            val toClose = processMap.values.toList()
            processMap.clear()
            toClose
        }.forEach { it.close() }
    }
}

private val jdwpProcessImplMapKey =
    CoroutineScopeCache.Key<JdwpProcessImplMap>("JdwpProcessImplMap")

/**
 * Accessor for the [JdwpProcessImplMap] associated to this [ConnectedDevice]
 */
internal fun ConnectedDevice.jdwpProcessImplMap(): JdwpProcessImplMap {
    return cache.getOrPut(jdwpProcessImplMapKey) {
        JdwpProcessImplMap(this)
    }
}
