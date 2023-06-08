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
package com.android.adblib.ddmlibcompatibility.debugging

import com.android.adblib.AdbSession
import com.android.adblib.ddmlibcompatibility.AdbLibDdmlibCompatibilityProperties.RUN_BLOCKING_LEGACY_DEFAULT_TIMEOUT
import com.android.adblib.property
import com.android.adblib.thisLogger
import com.android.adblib.tools.debugging.DdmsCommandException
import com.android.adblib.tools.debugging.JdwpCommandProgress
import com.android.adblib.tools.debugging.JdwpProcess
import com.android.adblib.tools.debugging.JdwpProcessProperties
import com.android.adblib.tools.debugging.ProfilerStatus
import com.android.adblib.tools.debugging.SharedJdwpSession
import com.android.adblib.tools.debugging.allocationTracker
import com.android.adblib.tools.debugging.executeGarbageCollector
import com.android.adblib.tools.debugging.handleDdmsCaptureView
import com.android.adblib.tools.debugging.handleDdmsDumpViewHierarchy
import com.android.adblib.tools.debugging.handleDdmsListViewRoots
import com.android.adblib.tools.debugging.packets.JdwpPacketView
import com.android.adblib.tools.debugging.packets.ddms.withPayload
import com.android.adblib.tools.debugging.profiler
import com.android.adblib.tools.debugging.properties
import com.android.adblib.tools.debugging.sendDdmsExit
import com.android.adblib.tools.debugging.toByteArray
import com.android.adblib.tools.debugging.toByteBuffer
import com.android.adblib.utils.createChildScope
import com.android.adblib.withErrorTimeout
import com.android.adblib.withPrefix
import com.android.ddmlib.AndroidDebugBridge.IClientChangeListener
import com.android.ddmlib.Client
import com.android.ddmlib.ClientData
import com.android.ddmlib.ClientData.MethodProfilingStatus
import com.android.ddmlib.DdmPreferences
import com.android.ddmlib.DebugViewDumpHandler
import com.android.ddmlib.IDevice
import com.google.common.annotations.VisibleForTesting
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.isActive
import kotlinx.coroutines.job
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.time.Duration
import java.util.concurrent.CancellationException
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

/**
 * Implementation of the ddmlib [Client] interface based on a [JdwpProcess] instance.
 */
internal class AdblibClientWrapper(
    private val trackerHost: ProcessTrackerHost,
    val jdwpProcess: JdwpProcess
) : Client {
    private val session: AdbSession
        get() = trackerHost.device.session

    private val logger = thisLogger(session).withPrefix("pid: ${jdwpProcess.pid}: ")

    private val clientDataWrapper = ClientData(this, jdwpProcess.pid)

    private var featuresAdded = false

    private val legacyOperationsScope = jdwpProcess.scope.createChildScope(isSupervisor = true)

    fun startTracking() {
        // Track process changes as long as process coroutine scope is active
        jdwpProcess.scope.launch {
            trackJdwpProcessInfo()
        }
    }

    private suspend fun trackJdwpProcessInfo() {
        var lastProcessInfo = jdwpProcess.propertiesFlow.value
        jdwpProcess.propertiesFlow.collect { processInfo ->
            try {
                updateJdwpProcessInfo(lastProcessInfo, processInfo)
            } finally {
                lastProcessInfo = processInfo
            }
        }
    }

    private suspend fun updateJdwpProcessInfo(
        previousProcessInfo: JdwpProcessProperties,
        newProcessInfo: JdwpProcessProperties
    ) {
        fun <T> hasChanged(x: T?, y: T?): Boolean {
            return x != y
        }

        // Always update "Client" wrapper data
        val previousDebuggerStatus = this.clientData.debuggerConnectionStatus
        updateClientWrapper(this, newProcessInfo)
        val newDebuggerStatus = this.clientData.debuggerConnectionStatus

        // Check if anything related to process info has changed
        with(previousProcessInfo) {
            if (hasChanged(processName, newProcessInfo.processName) ||
                hasChanged(userId, newProcessInfo.userId) ||
                hasChanged(packageName, newProcessInfo.packageName) ||
                hasChanged(vmIdentifier, newProcessInfo.vmIdentifier) ||
                hasChanged(abi, newProcessInfo.abi) ||
                hasChanged(jvmFlags, newProcessInfo.jvmFlags) ||
                hasChanged(isWaitingForDebugger, newProcessInfo.isWaitingForDebugger) ||
                hasChanged(isNativeDebuggable, newProcessInfo.isNativeDebuggable)
            ) {
                @Suppress("DeferredResultUnused")
                trackerHost.postClientUpdated(
                    this@AdblibClientWrapper,
                    ProcessTrackerHost.ClientUpdateKind.NameOrProperties
                )
            }
        }

        // Debugger status change is handled through its own callback
        if (hasChanged(previousDebuggerStatus, newDebuggerStatus)) {
            this.clientData.debuggerConnectionStatus = newDebuggerStatus
            @Suppress("DeferredResultUnused")
            trackerHost.postClientUpdated(
                this,
                ProcessTrackerHost.ClientUpdateKind.DebuggerConnectionStatus
            )
        }
    }

    private fun updateClientWrapper(
        clientWrapper: AdblibClientWrapper,
        newProperties: JdwpProcessProperties
    ) {
        val names = ClientData.Names(
            newProperties.processName ?: "",
            newProperties.userId,
            newProperties.packageName
        )
        clientWrapper.clientData.setNames(names)
        clientWrapper.clientData.vmIdentifier = newProperties.vmIdentifier
        clientWrapper.clientData.abi = newProperties.abi
        clientWrapper.clientData.jvmFlags = newProperties.jvmFlags
        clientWrapper.clientData.isNativeDebuggable = newProperties.isNativeDebuggable
        if (newProperties.features.isNotEmpty()) {
            clientWrapper.addFeatures(newProperties.features)
        }

        // "DebuggerStatus" is trickier: order is important
        clientWrapper.clientData.debuggerConnectionStatus = when {
            // This comes from the JDWP connection proxy, when a JDWP connection is started
            newProperties.jdwpSessionProxyStatus.isExternalDebuggerAttached -> ClientData.DebuggerStatus.ATTACHED

            // This comes from seeing a DDMS_WAIT packet on the JDWP connection
            newProperties.isWaitingForDebugger -> ClientData.DebuggerStatus.WAITING

            // This comes from any error during process properties polling
            newProperties.exception != null -> ClientData.DebuggerStatus.ERROR

            // This happens when process properties have been collected and also
            // when there is no active jdwp debugger connection
            else -> ClientData.DebuggerStatus.DEFAULT
        }
    }


    private fun addFeatures(features: List<String>) {
        // Add features only once to avoid duplicates (and we can assume they don't
        // change during the lifetime of a process).
        if (!featuresAdded) {
            synchronized(this) {
                if (!featuresAdded) {
                    features.forEach {
                        clientData.addFeature(it)
                    }
                    featuresAdded = true
                }
            }
        }
    }

    override fun getDevice(): IDevice {
        return trackerHost.iDevice
    }

    override fun isDdmAware(): Boolean {
        // Note: This should return `true` when there has been DDMS packet seen
        //  on the JDWP connection to the process. This is a signal the process
        //  is a process running on an Android VM.
        // We use vmIdentifier as a proxy for checking a DDM HELO packet has
        // been received.
        return jdwpProcess.properties.vmIdentifier != null
    }

    override fun getClientData(): ClientData {
        return clientDataWrapper
    }

    override fun kill() {
        // Sends a DDMS EXIT packet to the VM
        runBlockingLegacy {
            jdwpProcess.withJdwpSession {
                sendDdmsExit(1)
            }
        }
    }

    /**
     * In ddmlib case, this method would return `true` when ddmlib had an active JDWP socket
     * connection with the process on the device.
     * Since we use "on-demand" JDWP connection, we return `true` when 1) the process is still
     * active and 2) we were able to retrieve all process properties during the initial JDWP
     * connection.
     */
    override fun isValid(): Boolean {
        return jdwpProcess.scope.isActive &&
                jdwpProcess.properties.vmIdentifier != null
    }

    /**
     * Returns the TCP port (on "localhost") that an "external" debugger (i.e. IntelliJ or
     * Android Studio) can connect to open a JDWP session with the process.
     */
    override fun getDebuggerListenPort(): Int {
        return jdwpProcess.properties.jdwpSessionProxyStatus.socketAddress?.port ?: -1
    }

    /**
     * Returns `true` if there is an "external" debugger (i.e. IntelliJ or Android Studio)
     * currently attached to the process via a JDWP session.
     */
    override fun isDebuggerAttached(): Boolean {
        return jdwpProcess.properties.jdwpSessionProxyStatus.isExternalDebuggerAttached
    }

    override fun executeGarbageCollector() {
        // Note: To maintain strict ddmlib behavior, we block this method until the
        // DDMS command is sent to the AndroidVM, but we don't wait for the reply.
        runHalfBlockingLegacy("executeGarbageCollector") { progress ->
            jdwpProcess.executeGarbageCollector(progress)
        }
    }

    override fun startMethodTracer() {
        val canStream: Boolean = clientData.hasFeature(ClientData.FEATURE_PROFILING_STREAMING)
        if (!canStream) {
            throw DdmsCommandException("Profiling to a device file is not supported, use a device with API >= 10")
        }
        val bufferSize = getProfileBufferSize()

        runHalfBlockingLegacy("startMethodTracer") { progress ->
            jdwpProcess.profiler.startInstrumentationProfiling(bufferSize, progress)

            // Sends a (async) status query. This ensures that the status is properly updated
            // if for some reason starting the tracing failed.
            queryProfilerStatus()
        }
    }

    override fun stopMethodTracer() {
        val canStream: Boolean = clientData.hasFeature(ClientData.FEATURE_PROFILING_STREAMING)
        if (!canStream) {
            throw DdmsCommandException("Profiling to a device file is not supported, use a device with API >= 10")
        }

        // Sends a DDMS MPSE packet to the VM
        runHalfBlockingLegacy("stopMethodTracer") { progress ->
            val profilingData =
                jdwpProcess.profiler.stopInstrumentationProfiling(progress) { data, dataLength ->
                    // Note: At this point, the ddms chunk payload points directly
                    // to the socket of the underlying JDWP session.
                    // We clone it into an in-memory ByteBuffer (which is wasteful)
                    // only because the ddmlib API requires it.
                    data.toByteArray(dataLength)
                }
            // Notify handler
            val handler = ClientData.getMethodProfilingHandler()
            handler?.onSuccess(profilingData, this)
            notifyProfilingOff()
        }
    }

    private suspend fun queryProfilerStatus() {
        when (jdwpProcess.profiler.queryStatus()) {
            ProfilerStatus.Off -> {
                clientData.methodProfilingStatus = MethodProfilingStatus.OFF
                logger.debug { "Method profiling is not running" }
            }

            ProfilerStatus.InstrumentationProfilerRunning -> {
                clientData.methodProfilingStatus = MethodProfilingStatus.TRACER_ON
                logger.debug { "Method tracing is active" }
            }

            ProfilerStatus.SamplingProfilerRunning -> {
                clientData.methodProfilingStatus = MethodProfilingStatus.SAMPLER_ON
                logger.debug { "Sampler based profiling is active" }
            }
        }

        @Suppress("DeferredResultUnused")
        trackerHost.postClientUpdated(this, ProcessTrackerHost.ClientUpdateKind.ProfilingStatus)
    }

    private suspend fun notifyProfilingOff() {
        // Update status and notify listeners
        clientData.methodProfilingStatus = MethodProfilingStatus.OFF
        @Suppress("DeferredResultUnused")
        trackerHost.postClientUpdated(this, ProcessTrackerHost.ClientUpdateKind.ProfilingStatus)
    }

    override fun startSamplingProfiler(samplingInterval: Int, timeUnit: TimeUnit) {
        val bufferSize = getProfileBufferSize()
        runHalfBlockingLegacy("startSamplingProfiler") { progress ->
            jdwpProcess.profiler.startSampleProfiling(
                samplingInterval.toLong(),
                timeUnit,
                bufferSize,
                progress
            )

            // Send a status query. This ensures that the status is properly updated if for some
            // reason starting the tracing failed.
            queryProfilerStatus()
        }
    }

    override fun stopSamplingProfiler() {
        runHalfBlockingLegacy("stopSamplingProfiler") { progress ->
            val profilingData =
                jdwpProcess.profiler.stopSampleProfiling(progress) { data, dataLength ->
                    // Note: At this point, the ddms chunk payload points directly
                    // to the socket of the underlying JDWP session.
                    // We clone it into an in-memory ByteBuffer (which is wasteful)
                    // only because the ddmlib API requires it.
                    data.toByteArray(dataLength)
                }

            // Notify handler
            val handler = ClientData.getMethodProfilingHandler()
            handler?.onSuccess(profilingData, this)

            notifyProfilingOff()
        }
    }

    /**
     * Requests allocation details (asynchronously) and invokes the
     * [IClientChangeListener.clientChanged] callback, with [Client.CHANGE_HEAP_ALLOCATIONS] as
     * flags. The allocation data (an array of bytes) is available in
     * [ClientData.getAllocationsData] during the callback invocation.
     *
     * Note that allocation tracking should be enabled (see [enableAllocationTracker])
     * for this method to collect meaningful data.
     */
    override fun requestAllocationDetails() {
        // Note: Implementation is subtle here: We have to be asynchronous, but we also
        // don't want to return before we have sent the DDMS "REAL" query, because the caller
        // of this `Client` interface may call `enableAllocationTracker(false)` just after
        // calling this method.
        //
        // Consumers typically do this:
        //    client.enableAllocationTracker(true)
        //    (wait some time, maybe very short)
        //    client.requestAllocationDetails()
        //    client.enableAllocationTracker(false)
        //    (ddmlib callback from requestAllocationDetails is invoked some time later)
        runHalfBlockingLegacy("requestAllocationDetails") { progress ->
            val allocationData =
                jdwpProcess.allocationTracker.fetchAllocationDetails(progress) { data, length ->
                    // Note: At this point, the ddms chunk payload points directly
                    // to the socket of the underlying JDWP session.
                    // We clone it into an in-memory ByteBuffer (which is wasteful)
                    // only because the ddmlib API requires it.
                    data.toByteArray(length)
                }

            // Work with legacy global handler.
            @Suppress("DEPRECATION")
            val handler = ClientData.getAllocationTrackingHandler()
            if (handler != null) {
                logger.debug { "requestAllocationDetails: Allocation data is ${allocationData.size} bytes" }
                handler.onSuccess(allocationData, this@AdblibClientWrapper)
            }

            //
            // Set allocation data, call listeners, then clear allocation data
            //
            clientData.allocationsData = allocationData

            // Notify listeners *and* wait until even has been dispatched to listeners
            trackerHost.postClientUpdated(this, ProcessTrackerHost.ClientUpdateKind.HeapAllocations).await()

            // Clean up after everything has been notified (synchronously).
            clientData.allocationsData = null
        }
    }

    override fun enableAllocationTracker(enabled: Boolean) {
        // Note: This implementation is tricky: we must block until the DDMS packet
        // is sent to the JDWP session to ensure a call to `requestAllocationDetails` does
        // not come too early.
        //
        // Consumers typically do this:
        //    client.enableAllocationTracker(true)
        //    (wait some time, maybe very short)
        //    client.requestAllocationDetails()
        //    client.enableAllocationTracker(false)
        //    (ddmlib callback from requestAllocationDetails is invoked some time later)
        runHalfBlockingLegacy("enableAllocationTracker") { progress ->
            jdwpProcess.allocationTracker.enable(enabled, progress)
        }
    }

    /**
     * The [notifyVmMirrorExited] method was originally added due to an implementation defect
     * of ddmlib: there was a race condition during process disconnect where ddmlib would
     * non-deterministically lose track of some client processes when the debugger ends
     * its debugging session.
     * See [bug 37104675](https://issuetracker.google.com/issues/37104675) for more information.
     *
     * Adblib keeps track of client processes in a different way, so this method
     * can be a "no-op".
     */
    override fun notifyVmMirrorExited() {
        logger.verbose { "'notifyVmMirrorExited' invoked, doing nothing" }
    }

    override fun listViewRoots(replyHandler: DebugViewDumpHandler) {
        launchLegacyWithJdwpSession("listViewRoots") {
            val buffer = handleDdmsListViewRoots { chunkReply ->
                // Note: At this point, the ddms chunk payload points directly
                // to the socket of the underlying JDWP session.
                // We clone it into an in-memory ByteBuffer (which is wasteful)
                // only because the ddmlib API requires it.
                chunkReply.withPayload { it.toByteBuffer(chunkReply.length) }
            }

            // Invoke the handler with the packet result payload
            replyHandler.handleChunkData(buffer)
        }
    }

    override fun captureView(
        viewRoot: String,
        view: String,
        replyHandler: DebugViewDumpHandler
    ) {
        launchLegacyWithJdwpSession("captureView($viewRoot, $view)") {
            val buffer = handleDdmsCaptureView(viewRoot, view) { chunkReply ->
                // Note: At this point, the ddms chunk payload points directly
                // to the socket of the underlying JDWP session.
                // We clone it into an in-memory ByteBuffer (which is wasteful)
                // only because the ddmlib API requires it.
                chunkReply.withPayload { it.toByteBuffer(chunkReply.length) }
            }

            // Invoke the handler with the packet result payload
            replyHandler.handleChunkData(buffer)
        }
    }

    override fun dumpViewHierarchy(
        viewRoot: String,
        skipChildren: Boolean,
        includeProperties: Boolean,
        useV2: Boolean,
        handler: DebugViewDumpHandler
    ) {
        launchLegacyWithJdwpSession("dumpViewHierarchy($viewRoot, $skipChildren, $includeProperties, $useV2)") {
            val buffer = handleDdmsDumpViewHierarchy(
                viewRoot = viewRoot,
                skipChildren = skipChildren,
                includeProperties = includeProperties,
                useV2 = useV2
            ) { chunkReply ->
                // Note: At this point, the ddms chunk payload points directly
                // to the socket of the underlying JDWP session.
                // We clone it into an in-memory ByteBuffer (which is wasteful)
                // only because the ddmlib API requires it.
                chunkReply.withPayload { it.toByteBuffer(chunkReply.length) }
            }

            // Invoke the handler with the packet result payload
            handler.handleChunkData(buffer)
        }
    }

    override fun dumpDisplayList(viewRoot: String, view: String) {
        legacyNotImplemented("dumpDisplayList")
    }

    /**
     * Similar to [runBlocking] but with a custom [timeout]
     *
     * @throws TimeoutException if [block] take more than [timeout] to execute
     */
    private fun <R> runBlockingLegacy(
        timeout: Duration = session.property(RUN_BLOCKING_LEGACY_DEFAULT_TIMEOUT),
        block: suspend CoroutineScope.() -> R
    ): R {
        return runBlocking {
            session.withErrorTimeout(timeout) {
                block()
            }
        }
    }

    /**
     * A mix of [launchLegacy] and [runBlockingLegacy]: The [block] invoked by [launchLegacy]
     * completes a [CompletableDeferred] to unblock a wait inside [runBlockingLegacy].
     */
    private fun runHalfBlockingLegacy(
        operation: String,
        timeout: Duration = session.property(RUN_BLOCKING_LEGACY_DEFAULT_TIMEOUT),
        block: suspend (JdwpCommandProgress) -> Unit
    ) {
        val deferred = CompletableDeferred<Unit>(jdwpProcess.scope.coroutineContext.job)
        val progress = object : JdwpCommandProgress {
            override suspend fun afterSend(packet: JdwpPacketView) {
                deferred.complete(Unit)
            }
        }
        launchLegacy(operation) {
            block(progress)
        }
        return runBlockingLegacy(timeout = timeout) {
            deferred.await()
        }
    }

    private fun launchLegacy(
        operation: String,
        block: suspend CoroutineScope.() -> Unit
    ) {
        // Note: We use `async` here to make sure exceptions are not propagated to
        // the parent context.
        val deferred = legacyOperationsScope.async {
            block()
        }
        // We log errors here because legacy ddmlib APIs wrapped by this method don't
        // have a way to report errors to their callers.
        deferred.invokeOnCompletion { cause: Throwable? ->
            cause?.also {
                if (cause !is CancellationException) {
                    logger.warn(
                        cause,
                        "A legacy ddmlib operation ('$operation') failed with an error ${cause.message}"
                    )
                }
            }
        }
    }

    @VisibleForTesting
    suspend fun awaitLegacyOperations() {
        legacyOperationsScope.coroutineContext.job.children.toList().joinAll()
    }

    private fun launchLegacyWithJdwpSession(
        operation: String,
        block: suspend SharedJdwpSession.() -> Unit
    ) {
        launchLegacy(operation) {
            jdwpProcess.withJdwpSession {
                block()
            }
        }
    }

    private fun legacyNotImplemented(@Suppress("SameParameterValue") operation: String) {
        val message =
            "Operation '$operation' is not implemented because it is deprecated. It should never be called."
        logger.info { message }
        throw NotImplementedError(message)
    }

    private fun getProfileBufferSize(): Int {
        return DdmPreferences.getProfilerBufferSizeMb() * 1024 * 1024
    }
}
