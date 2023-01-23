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

import com.android.adblib.thisLogger
import com.android.adblib.tools.debugging.JdwpProcess
import com.android.adblib.tools.debugging.SharedJdwpSession
import com.android.adblib.tools.debugging.handleDdmsCaptureView
import com.android.adblib.tools.debugging.handleDdmsDumpViewHierarchy
import com.android.adblib.tools.debugging.handleDdmsListViewRoots
import com.android.adblib.tools.debugging.properties
import com.android.adblib.tools.debugging.sendDdmsExit
import com.android.adblib.tools.debugging.toByteBuffer
import com.android.adblib.withErrorTimeout
import com.android.adblib.withPrefix
import com.android.ddmlib.Client
import com.android.ddmlib.ClientData
import com.android.ddmlib.DebugViewDumpHandler
import com.android.ddmlib.IDevice
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.isActive
import kotlinx.coroutines.runBlocking
import java.time.Duration
import java.util.concurrent.CancellationException
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

/**
 * Implementation of the ddmlib [Client] interface based on a [JdwpProcess] instance.
 */
internal class AdblibClientWrapper(
    private val deviceClientManager: AdbLibDeviceClientManager,
    private val iDevice: IDevice,
    val jdwpProcess: JdwpProcess
) : Client {

    private val logger =
        thisLogger(deviceClientManager.session).withPrefix("pid: ${jdwpProcess.pid}: ")

    private val clientDataWrapper = ClientData(this, jdwpProcess.pid)

    private var featuresAdded = false

    fun addFeatures(features: List<String>) {
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
        return iDevice
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
     * Returns `true' if there is an "external" debugger (i.e. IntelliJ or Android Studio)
     * currently attached to the process via a JDWP session.
     */
    override fun isDebuggerAttached(): Boolean {
        return jdwpProcess.properties.jdwpSessionProxyStatus.isExternalDebuggerAttached
    }

    override fun executeGarbageCollector() {
        legacyNotImplemented("executeGarbageCollector")
    }

    override fun startMethodTracer() {
        legacyNotImplemented("startMethodTracer")
    }

    override fun stopMethodTracer() {
        legacyNotImplemented("stopMethodTracer")
    }

    override fun startSamplingProfiler(samplingInterval: Int, timeUnit: TimeUnit?) {
        legacyNotImplemented("startSamplingProfiler")
    }

    override fun stopSamplingProfiler() {
        legacyNotImplemented("stopSamplingProfiler")
    }

    override fun requestAllocationDetails() {
        legacyNotImplemented("requestAllocationDetails")
    }

    override fun enableAllocationTracker(enabled: Boolean) {
        legacyNotImplemented("enableAllocationTracker")
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
                chunkReply.payload.toByteBuffer(chunkReply.length)
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
                chunkReply.payload.toByteBuffer(chunkReply.length)
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
                chunkReply.payload.toByteBuffer(chunkReply.length)
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
        timeout: Duration = RUN_BLOCKING_LEGACY_DEFAULT_TIMEOUT,
        block: suspend CoroutineScope.() -> R
    ): R {
        return runBlocking {
            deviceClientManager.session.withErrorTimeout(timeout) {
                block()
            }
        }
    }

    private fun launchLegacyWithJdwpSession(
        operation: String,
        block: suspend SharedJdwpSession.() -> Unit
    ) {
        // Note: We use `async` here to make sure exceptions are not propagated to
        // the parent context.
        val deferred = jdwpProcess.scope.async {
            jdwpProcess.withJdwpSession {
                block()
            }
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

    private fun legacyNotImplemented(operation: String) {
        val message =
            "Operation '$operation' is not implemented because it is deprecated. It should never be called."
        logger.info { message }
        throw NotImplementedError(message)
    }

    companion object {

        private val RUN_BLOCKING_LEGACY_DEFAULT_TIMEOUT: Duration = Duration.ofMillis(5_000)
    }
}
