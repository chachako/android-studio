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

import com.android.adblib.AdbSession
import com.android.adblib.ConnectedDevice
import com.android.adblib.CoroutineScopeCache
import com.android.adblib.connectedDevicesTracker
import com.android.adblib.getOrPutSynchronized
import com.android.adblib.scope
import com.android.adblib.serialNumber
import com.android.adblib.thisLogger
import com.android.adblib.tools.debugging.JdwpPacketReceiver
import com.android.adblib.tools.debugging.JdwpProcessProperties
import com.android.adblib.tools.debugging.SharedJdwpSession
import com.android.adblib.tools.debugging.packets.JdwpPacketView
import com.android.adblib.waitForDevice
import com.android.adblib.withPrefix
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap
import java.util.function.Function

internal object JdwpProcessFactory {

    /**
     * Creates an instance of a [AbstractJdwpProcess] for the given [device] and [pid],
     * delegating to the [AdbSession] dedicated to the device if needed.
     */
    internal fun create(device: ConnectedDevice, pid: Int): AbstractJdwpProcess {
        val deviceSession = device.session
        val delegateSession = device.session.jdwpProcessSessionFinderList
            .fold(deviceSession) { session, finder ->
                finder.findDelegateSession(session)
            }
        return if (delegateSession === deviceSession) {
            // There is no delegate session, use the device session
            device.jdwpProcessMap.computeIfAbsent(pid)
        } else {
            // Delegate to the equivalent process in the delegate session
            val deferredDelegateJdwpProcess = device.scope.async {
                // Note: Waiting for the equivalent device in "delegateSession" may seem brittle,
                // but is actually reliable:
                // * If the delegate device has not been discovered yet, waiting for it is good
                // enough
                // * If the delegate device has been removed, it implies this device will also
                // eventually be seen as disconnected, and its scope will be cancelled, so
                // the "wait" operation will be cancelled.
                val delegateDevice =
                    delegateSession.connectedDevicesTracker.waitForDevice(device.serialNumber)
                delegateDevice.jdwpProcessMap.computeIfAbsent(pid)
            }
            JdwpProcessDelegate(device, pid, deferredDelegateJdwpProcess)
        }
    }

}

private val JdwpProcessMapKey =
    CoroutineScopeCache.Key<JdwpProcessMap>(JdwpProcessMap::class.simpleName!!)

private val ConnectedDevice.jdwpProcessMap: JdwpProcessMap
    get() {
        return cache.getOrPutSynchronized(JdwpProcessMapKey) {
            JdwpProcessMap(this)
        }
    }

/**
 * A map from [pid][Int] to [JdwpProcessImpl] for a given [ConnectedDevice]
 *
 * The map is thread-safe and should be [closed][close] when [device] is
 * disconnected.
 */
private class JdwpProcessMap(private val device: ConnectedDevice): AutoCloseable {

    private val logger = thisLogger(device.session)
        .withPrefix("${device.session} - $device - ")

    private val map = ConcurrentHashMap<Int, JdwpProcessImpl>()

    private var closed = false

    /**
     * Callback to [ConcurrentHashMap.computeIfAbsent], stored in a field to avoid heap
     * allocation on every call.
     */
    private val createProcess = Function<Int, JdwpProcessImpl> { pid ->
        JdwpProcessImpl(device, pid, this::onProcessClosed).also {
            logger.debug { "Created ${it::class.simpleName} for pid=$pid: $it" }
        }
    }

    /**
     * Returns the [JdwpProcessImpl] instance for the process [pid] of this
     * [device].
     */
    fun computeIfAbsent(pid: Int): JdwpProcessImpl {
        check(!closed) { "${this::class.simpleName} is closed" }

        return map.computeIfAbsent(pid, createProcess).also {
            logger.verbose { "# of entries=${map.size}" }
        }
    }

    /**
     * Called when the [CoroutineScope] of the corresponding [device] is closed
     */
    override fun close() {
        closed = true
        logger.debug { "close(): # of entries=${map.size}" }
        val toClose = map.values.toList()
        map.clear()
        toClose.forEach { jdwpProcess -> jdwpProcess.close() }
    }

    private fun remove(pid: Int) {
        map.remove(pid).also { process ->
            logger.debug { "removing pid=$pid, process=$process (# of entries left=${map.size})" }
        }
    }

    /**
     * Called by a [JdwpProcessImpl] when it is closed
     */
    private fun onProcessClosed(process: JdwpProcessImpl) {
        remove(process.pid)
    }
}

/**
 * A [AbstractJdwpProcess] that uses a [device] from one [AdbSession], but delegates
 * the rest of the implementation to a [AbstractJdwpProcess] from another [AdbSession], so
 * that the underlying JDWP connection can be shared across [AdbSession].
 *
 * The [close] method should be called by the owner (the [JdwpProcessTrackerImpl] or the
 * [AppProcessTrackerImpl]) when the process terminates.
 */
private class JdwpProcessDelegate(
    override val device: ConnectedDevice,
    override val pid: Int,
    private val deferredDelegate: Deferred<AbstractJdwpProcess>
) : AbstractJdwpProcess() {

    private val logger = thisLogger(device.session)
        .withPrefix("${device.session} - $device - pid=$pid - ")

    private val propertiesMutableFlow = MutableStateFlow(JdwpProcessProperties(pid))

    private val withJdwpSessionTracker = BlockActivationTracker()

    override val isJdwpSessionRetained: Boolean
        get() = withJdwpSessionTracker.activationCount.value > 1

    override val cache = CoroutineScopeCache.create(device.scope)

    override val propertiesFlow = propertiesMutableFlow.asStateFlow()

    override fun startMonitoring() {
        scope.launch {
            deferredDelegate.await().also { delegateProcess ->
                logger.debug { "Acquired delegate process, starting monitoring" }
                delegateProcess.startMonitoring()
                delegateProcess.propertiesFlow.collect { properties ->
                    logger.debug { "Updating properties flow from delegate process: $properties" }
                    propertiesMutableFlow.value = properties
                }
            }
        }
    }

    override suspend fun <T> withJdwpSession(block: suspend SharedJdwpSession.() -> T): T {
        // Get the SharedJdwpSession of the delegate process, then wrap it to call "block"
        return withJdwpSessionTracker.track {
            // Get the SharedJdwpSession of the delegate process, then wrap it to call "block"
            deferredDelegate.await().withJdwpSession {
                logger.debug { "Acquired delegate process JDWP session, calling 'block'" }
                SharedJdwpSessionDelegate(device, this).block()
            }
        }
    }

    override suspend fun awaitReadyToClose() {
        // Wait until no active JDWP session
        withJdwpSessionTracker.waitWhileActive()
        logger.debug { "Ready to close" }
    }

    override fun close() {
        logger.debug { "close()" }
        cache.close()
        deferredDelegate.cancel()
    }

    override fun toString(): String {
        return "${this::class.simpleName}(session=${device.session}, device=$device, pid=$pid)"
    }

    /**
     * Delegates [SharedJdwpSession] methods while exposing a custom [device] property passed
     * as constructor parameter.
     */
    private class SharedJdwpSessionDelegate(
        override val device: ConnectedDevice,
        private val delegate: SharedJdwpSession,
    ) : SharedJdwpSession {

        override val pid: Int
            get() = delegate.pid

        override suspend fun sendPacket(packet: JdwpPacketView) {
            delegate.sendPacket(packet)
        }

        override suspend fun newPacketReceiver(): JdwpPacketReceiver {
            return delegate.newPacketReceiver()
        }

        override fun nextPacketId(): Int {
            return delegate.nextPacketId()
        }

        override suspend fun addReplayPacket(packet: JdwpPacketView) {
            delegate.addReplayPacket(packet)
        }
    }
}
