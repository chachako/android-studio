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
import com.android.adblib.adbLogger
import com.android.adblib.connectedDevicesTracker
import com.android.adblib.getOrPutSynchronized
import com.android.adblib.property
import com.android.adblib.withScopeContext
import com.android.adblib.scope
import com.android.adblib.serialNumber
import com.android.adblib.tools.AdbLibToolsProperties
import com.android.adblib.tools.AdbLibToolsProperties.JDWP_PROCESS_MANAGER_REFRESH_DELAY
import com.android.adblib.tools.AdbLibToolsProperties.JDWP_PROCESS_TRACKER_RETRY_DELAY
import com.android.adblib.tools.debugging.JdwpPacketReceiver
import com.android.adblib.tools.debugging.JdwpProcess
import com.android.adblib.tools.debugging.JdwpProcessProperties
import com.android.adblib.tools.debugging.SharedJdwpSession
import com.android.adblib.tools.debugging.isTrackAppSupported
import com.android.adblib.tools.debugging.packets.JdwpPacketView
import com.android.adblib.tools.debugging.trackAppStateFlow
import com.android.adblib.tools.debugging.trackJdwpStateFlow
import com.android.adblib.tools.debugging.utils.JobTracker
import com.android.adblib.tools.debugging.utils.logIOCompletionErrors
import com.android.adblib.utils.createChildScope
import com.android.adblib.waitForDevice
import com.android.adblib.withPrefix
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.time.Duration
import java.util.TreeMap

/**
 * Maintains a list of active [JdwpProcess] instances of a given [ConnectedDevice].
 *
 * The [addProcesses] method allows callers to retrieve [JdwpProcess] instances, which are
 * automatically closed when the corresponding processes exit on the device.
 *
 * A [JdwpProcessManager] instance, as well as all [JdwpProcess] instances, are closed when
 * the [ConnectedDevice.scope] is cancelled (i.e. when the device is disconnected).
 *
 * Note: All methods of this class are thread-safe.
 */
internal interface JdwpProcessManager {

    /**
     * The [device] this manager is tied to.
     */
    val device: ConnectedDevice

    /**
     * Add [processIds] to the list of active processes and returns a [Map]
     * of these process IDs to [JdwpProcess] instances. This is an atomic
     * operation to ensure thread-safety, i.e. it is guaranteed that [Map.keys]
     * of the returned [Map] is the same [Set] as [processIds].
     *
     * Note that calling this method multiple times with the same process ID
     * may result in identical [JdwpProcess] instances returned.
     *
     * Note it is valid to call this method with process IDS of processes that
     * do not (yet) exist on the device, the returned [JdwpProcess] instances will
     * simply remain active for a little while before being closed. This behavior is
     * needed to ensure smooth behavior due to the asynchronous nature of process
     * creation and termination.
     *
     * The lifetime of the returned [JdwpProcess] instances is managed by this
     * [JdwpProcessManager], i.e. [JdwpProcess.scope] is valid until the process
     * has terminated on the device. Given the asynchronous behavior of process
     * termination, there may be a short delay between the process termination
     * and the [JdwpProcess.scope] being [cancelled][CoroutineScope.cancel].
     */
    fun addProcesses(processIds: Set<Int>): Map<Int, JdwpProcess>
}

/**
 * Returns the [JdwpProcessManager] for this [ConnectedDevice].
 */
internal val ConnectedDevice.jdwpProcessManager: JdwpProcessManager
    get() = jdwpProcessManagerImpl

private val jdwpProcessManagerKey =
    CoroutineScopeCache.Key<JdwpProcessManagerImpl>(JdwpProcessManagerImpl::class.java.simpleName)

/**
 * Returns the [JdwpProcessManagerImpl] for this [ConnectedDevice].
 */
private val ConnectedDevice.jdwpProcessManagerImpl: JdwpProcessManagerImpl
    get() {
        return cache.getOrPutSynchronized(jdwpProcessManagerKey) {
            JdwpProcessManagerImpl(device = this)
        }
    }

/**
 * Implementation of [JdwpProcessManager], automatically closed when stored in the
 * [ConnectedDevice.cache] of a [device].
 */
private class JdwpProcessManagerImpl(
    override val device: ConnectedDevice,
): JdwpProcessManager, AutoCloseable {

    private val logger = adbLogger(device.session).withPrefix("$device - ")

    private val scope = device.scope.createChildScope(isSupervisor = true)

    /**
     * The factory of [AbstractJdwpProcess] instance for [jdwpProcessMap]
     */
    private val processOrDelegateFactory: (ConnectedDevice, Int) -> AbstractJdwpProcess =
        { device, pid ->
            val localDelegateSession = delegateSession
            if (localDelegateSession == null) {
                JdwpProcessImpl(device, pid)
            } else {
                JdwpProcessDelegate(device, pid, localDelegateSession)
            }
        }

    /**
     * Lock for [jdwpProcessMap]
     */
    private val lock = Any()

    /**
     * The map of processes added by [addProcesses]. but not yet removed by [setActiveProcessIds]
     */
    private val jdwpProcessMap = AbstractJdwpProcessMap(device, processOrDelegateFactory)

    /**
     * [Duration] between receiving a new set of active process ids, and updating the
     * [jdwpProcessMap]. This delay is used to prevent processes added with
     * [addProcesses] being [closed][AbstractJdwpProcess.close] right away in case
     * our [process id tracker][processIdsStateFlowUpdaterJob] is slightly lagging
     * or ahead of external callers.
     */
    private val jdwpProcessMapRefreshDelay: Duration
        get() = device.session.property(JDWP_PROCESS_MANAGER_REFRESH_DELAY)

    /**
     * The flow of process ids as tracked by the [processIdsStateFlowUpdaterJob] job
     */
    private val processIdsStateFlow: StateFlow<Set<Int>>
        get() = device.jdwpProcessIdStateFlow

    /**
     * Starts a [Job] to track the list of active JDWP process ids and call [setActiveProcessIds]
     * periodically to remove and close processes that have exited.
     */
    private val processIdsStateFlowUpdaterJob: Job by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        scope.launch {
            runCatching {
                JobTracker(this).use { jobTracker ->
                    device.jdwpProcessIdStateFlow.collect { processIds ->
                        jobTracker.cancelPreviousAndLaunch {
                            // Delay for a little bit so that we get cancelled if another
                            // set of process IDs is emitted in the meantime.
                            delay(jdwpProcessMapRefreshDelay.toMillis())
                            setActiveProcessIds(processIds)
                        }
                    }
                }
            }.onFailure { throwable ->
                logger.logIOCompletionErrors(throwable)
            }
        }
    }

    /**
     * The delegate [AdbSession] used to create [AbstractJdwpProcess] instances or `null`
     * if we don't have a delegate session.
     *
     * Note: The delegate session is computed once, and it can't change over time. The
     * assumption is that if there is a need to expose a different delegate session, some
     * higher level component would close all devices of the adb session, and reconnect them,
     * which would result in invalidating all our caches.
     */
    private val delegateSession: AdbSession? by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        // Find delegate session (or null)
        val deviceSession = device.session
        device.session.jdwpProcessSessionFinderList
            .fold(deviceSession) { session, finder ->
                finder.findDelegateSession(session)
            }.let {
                if (it === deviceSession) null else it
            }
    }

    override fun addProcesses(processIds: Set<Int>): Map<Int, AbstractJdwpProcess> {
        // Ensure our coroutine updating flow of active JDWP process IDS is launched
        processIdsStateFlowUpdaterJob

        return synchronized(lock) {
            scope.ensureActive()

            // Update our map given the process IDs we received as parameter
            jdwpProcessMap.addProcesses(processIds)
            processIds.map { pid ->
                jdwpProcessMap.getProcessOrNull(pid)?.also {
                    it.startMonitoring()
                } ?: run {
                    // A `null` result should never happen, given we called `addProcesses` above
                    throwProcessNotFoundInternalError(pid)
                }
            }.associateBy { process -> process.pid }
        }
    }

    /**
     * Wait for process id [pid] to be active, and return the corresponding
     * [AbstractJdwpProcess] instance. Due to asynchronous behavior, the process may
     * never show up, it is up to the caller to cancel the coroutine if the process
     * exits.
     */
    suspend fun waitForProcess(pid: Int): AbstractJdwpProcess {
        check(delegateSession == null) {
            "Nested delegate sessions are not supported yet"
        }

        // Ensure our coroutine updating flow of active JDWP process IDS is launched
        processIdsStateFlowUpdaterJob

        // Wait until the PID shows up (it may never succeed if the process has already
        // exited, that's fine, the caller will cancel this coroutine if that is the case
        val processIds = processIdsStateFlow.first { processIds -> processIds.contains(pid) }

        // Synchronize our internal list of processes to the list of process IDs we got.
        // * If our process id flow is faster than the external tracker, we may create processes
        // not seen by our external tracker, but that is OK, as the external tracker will
        // eventually catch up by calling `update` and get the expected process.
        // * If our external tracker is faster, we may close processes from the last call
        // to `update`, but that is OK, processes can exit at any time and our external tracker
        // should already be able to handle that situation.
        return synchronized(lock) {
            scope.ensureActive()
            assert(processIds.contains(pid))
            jdwpProcessMap.addProcesses(processIds)
            jdwpProcessMap.getProcessOrNull(pid) ?: run {
                // A `null` result should never happen, given we called `addProcesses` above
                throwProcessNotFoundInternalError(pid)
            }
        }
    }

    /**
     * Close this [JdwpProcessManager], cancelling its [scope], and closing all
     * remaining [AbstractJdwpProcess] instances.
     */
    override fun close() {
        synchronized(lock) {
            scope.cancel("${this::class.java.simpleName} has been closed")
            jdwpProcessMap.clear()
        }
    }

    private fun setActiveProcessIds(processIds: Set<Int>) {
        return synchronized(lock) {
            scope.ensureActive()
            jdwpProcessMap.setActiveProcessIds(processIds)
        }
    }

    private fun throwProcessNotFoundInternalError(pid: Int): Nothing {
        val message = "Internal error: A process (pid=$pid) was not found in the process map"
        logger.error(message)
        throw IllegalStateException(message)
    }

    companion object {

        private val JdwpProcessIdTrackerKey =
            CoroutineScopeCache.Key<JdwpProcessIdTracker>(JdwpProcessIdTracker::class.simpleName!!)

        private val ConnectedDevice.jdwpProcessIdStateFlow: StateFlow<Set<Int>>
            get() {
                return cache.getOrPutSynchronized(JdwpProcessIdTrackerKey) {
                    JdwpProcessIdTracker(this)
                }.stateFlow
            }

        private class JdwpProcessIdTracker(private val device: ConnectedDevice) {
            private val logger = adbLogger(device.session).withPrefix("device=$device - ")

            private val mutableFlow = MutableStateFlow<Set<Int>>(emptySet())

            private val trackingJob: Job by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
                device.scope.launch {
                    runCatching {
                        trackProcesses()
                    }.onFailure { throwable ->
                        logger.logIOCompletionErrors(throwable)
                    }
                }
            }

            val stateFlow: StateFlow<Set<Int>> = mutableFlow.asStateFlow()
                get() {
                    // Note: We rely on "lazy" to ensure the tracking coroutine is launched only once
                    trackingJob
                    return field
                }

            private suspend fun trackProcesses() {
                device.withScopeContext {
                    logger.debug { "Starting process ID tracking" }
                    // Use the best available tracking mechanism
                    if (device.isTrackAppSupported()) {
                        device.trackAppStateFlow().map { trackAppItem ->
                            trackAppItem.entries.filter { it.debuggable }.map { it.pid }.toSet()
                        }.collect { processIds ->
                            mutableFlow.value = processIds
                        }
                    } else {
                        device.trackJdwpStateFlow().map { trackJdwpItem ->
                            trackJdwpItem.processIds.toSet()
                        }.collect { processIds ->
                            mutableFlow.value = processIds
                        }
                    }
                }.withRetry { throwable ->
                    // Retry as long as device scope is active
                    logger.logIOCompletionErrors(throwable)
                    val delay = device.session.property(JDWP_PROCESS_TRACKER_RETRY_DELAY)
                    delay(delay.toMillis())
                    true
                }.withFinally {
                    logger.debug { "Exiting process ID tracking" }
                }.execute()
            }
        }
    }
}

/**
 * A map from [pid][Int] to [AbstractJdwpProcess] for a given [ConnectedDevice].
 * [AbstractJdwpProcess] instances are created on-demand and [closed][AbstractJdwpProcess.close]
 * when the process is removed from the map.
 *
 * The map should be [cleared][clear] when [device] is disconnected.
 */
private class AbstractJdwpProcessMap(
    private val device: ConnectedDevice,
    private val factory: (ConnectedDevice, Int) -> AbstractJdwpProcess
) {

    private val logger = adbLogger(device.session)
        .withPrefix("${device.session} - $device - ")

    /**
     * The [MutableMap] of active processes.
     * * [Map.keys] is the list of currently known process IDs.
     * * [Map.values] contains either `null` if the process has not been used yet,
     * or an [AbstractJdwpProcess] instance created by [factory].
     *
     * Note: We use a [TreeMap] to keep entries sorted by process ID
     */
    private val map: MutableMap<Int, AbstractJdwpProcess?> = TreeMap()

    /**
     * Returns the [AbstractJdwpProcess] instance for the process [pid] of this [device], or
     * `null` if the [pid] is not the pid of an active process set by the last call to
     * [setActiveProcessIds].
     */
    fun getProcessOrNull(pid: Int): AbstractJdwpProcess? {
        // Ensure pid is known
        if (!map.contains(pid)) {
            return null
        }

        // Return existing instance or create one if needed
        return map.getOrPut(pid) {
            factory(device, pid).also { newProcess ->
                logger.verbose { "Adding new process to map: '$newProcess' " }
            }
        }
    }

    fun addProcesses(processIds: Set<Int>) {
        val newSet = map.keys + processIds
        setActiveProcessIds(newSet)
    }

    /**
     * Updates the list of active processes given their process ids.
     * * Previously existing entries remain associated to the same [AbstractJdwpProcess] instances
     * * New entries are initialized to `null` and created on demand with [getProcessOrNull]
     * * Deleted entries are removed and the associated [AbstractJdwpProcess] instances are
     * [closed][AbstractJdwpProcess.close]
     */
    fun setActiveProcessIds(processIds: Set<Int>) {
        logger.verbose { "Setting list of active process ids: $processIds" }
        val toClose = mutableListOf<AbstractJdwpProcess>()
        val added = processIds - map.keys
        val removed = map.keys - processIds
        removed.forEach { pid ->
            map.remove(pid)?.also {
                toClose.add(it)
            }
        }
        added.forEach { pid ->
            logger.verbose { "Adding `null` entry for pid=$pid" }
            map[pid] = null
        }
        assert(processIds == map.keys)
        closeJdwpProcessList(toClose)
    }

    /**
     * Clears the map, [closing][AbstractJdwpProcess.close] all existing
     * [AbstractJdwpProcess] entries.
     */
    fun clear() {
        logger.debug { "clear(): # of entries=${map.size}" }
        val toClose = map.values.filterNotNull()
        map.clear()
        closeJdwpProcessList(toClose)
    }

    private fun closeJdwpProcessList(toClose: List<AbstractJdwpProcess>) {
        toClose.forEach { jdwpProcess ->
            logger.verbose { "Removing process from map: $jdwpProcess" }
            closeJdwpProcess(jdwpProcess)
        }
    }

    private fun closeJdwpProcess(jdwpProcess: AbstractJdwpProcess) {
        val delayMillis = device.session.property(AdbLibToolsProperties.JDWP_PROCESS_TRACKER_CLOSE_NOTIFICATION_DELAY).toMillis()
        if (delayMillis > 0) {
            device.scope.launch {
                withTimeoutOrNull(delayMillis) {
                    jdwpProcess.awaitReadyToClose()
                } ?: run {
                    logger.info { "JDWP process was not ready to close within $delayMillis milliseconds" }
                }
            }.invokeOnCompletion {
                jdwpProcess.close()
            }
        } else {
            jdwpProcess.close()
        }
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
    private val delegateSession: AdbSession
) : AbstractJdwpProcess() {

    private val logger = adbLogger(device.session)
        .withPrefix("${device.session} - $device - pid=$pid - ")

    private val propertiesMutableFlow = MutableStateFlow(JdwpProcessProperties(pid))

    private val withJdwpSessionTracker = BlockActivationTracker()

    override val jdwpSessionActivationCount: StateFlow<Int>
        get() = withJdwpSessionTracker.activationCount

    override val cache = CoroutineScopeCache.create(device.scope)

    override val propertiesFlow = propertiesMutableFlow.asStateFlow()

    private val deferredDelegateProcess: Deferred<AbstractJdwpProcess> =
        cache.scope.async {
            // Note: Waiting for the equivalent device in "delegateSession" may seem brittle,
            // but is actually reliable:
            // * If the delegate device has not been discovered yet, waiting for it is good
            // enough
            // * If the delegate device has been removed, it implies this device will also
            // eventually be seen as disconnected, and its scope will be cancelled, so
            // the "wait" operation will be cancelled.
            val delegateDevice = delegateSession.connectedDevicesTracker.waitForDevice(device.serialNumber)

            // Note: Waiting for the equivalent process in "delegateDevice" may also seem
            // brittle, but is actually reliable:
            // * If the delegate process has not been discovered yet, waiting for it is good
            // enough
            // * If the delegate process is already gone, it implies this process will also
            // eventually be closed, meaning our scope will be cancelled, so the "wait"
            // operation will be cancelled.
            delegateDevice.jdwpProcessManagerImpl.waitForProcess(pid)
        }

    override fun startMonitoring() {
        scope.launch {
            runCatching {
                deferredDelegateProcess.await().also { delegateProcess ->
                    logger.debug { "Acquired delegate process, starting monitoring" }
                    delegateProcess.startMonitoring()
                    delegateProcess.propertiesFlow.collect { properties ->
                        logger.debug { "Updating properties flow from delegate process: $properties" }
                        propertiesMutableFlow.value = properties
                    }
                }
            }.onFailure { throwable ->
                logger.logIOCompletionErrors(throwable)
            }
        }
    }

    override suspend fun <T> withJdwpSession(block: suspend SharedJdwpSession.() -> T): T {
        return withJdwpSessionTracker.track {
            // Get the SharedJdwpSession of the delegate process, then wrap it to call "block"
            deferredDelegateProcess.await().withJdwpSession {
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
        deferredDelegateProcess.cancel()
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

