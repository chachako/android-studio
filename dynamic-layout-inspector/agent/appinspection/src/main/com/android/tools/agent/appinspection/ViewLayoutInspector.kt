/*
 * Copyright (C) 2020 The Android Open Source Project
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

package com.android.tools.agent.appinspection

import android.os.Build
import android.util.Log
import android.view.View
import android.view.ViewDebug
import android.view.WindowManager
import androidx.annotation.GuardedBy
import androidx.annotation.VisibleForTesting
import androidx.inspection.Connection
import androidx.inspection.Inspector
import androidx.inspection.InspectorEnvironment
import androidx.inspection.InspectorFactory
import com.android.tools.agent.appinspection.framework.SkiaQWorkaround
import com.android.tools.agent.appinspection.framework.SynchronousPixelCopy
import com.android.tools.agent.appinspection.framework.flatten
import com.android.tools.agent.appinspection.framework.takeScreenshot
import com.android.tools.agent.appinspection.framework.toByteArray
import com.android.tools.agent.appinspection.proto.createGetPropertiesResponse
import com.android.tools.agent.appinspection.util.ThreadUtils
import com.android.tools.agent.appinspection.util.compress
import com.android.tools.layoutinspector.BitmapType
import com.android.tools.layoutinspector.errors.errorCode
import com.android.tools.layoutinspector.errors.noHardwareAcceleration
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import com.android.tools.idea.layoutinspector.view.inspection.LayoutInspectorViewProtocol
import com.android.tools.idea.layoutinspector.view.inspection.LayoutInspectorViewProtocol.Command
import com.android.tools.idea.layoutinspector.view.inspection.LayoutInspectorViewProtocol.DisableBitmapScreenshotCommand
import com.android.tools.idea.layoutinspector.view.inspection.LayoutInspectorViewProtocol.DisableBitmapScreenshotResponse
import com.android.tools.idea.layoutinspector.view.inspection.LayoutInspectorViewProtocol.Event
import com.android.tools.idea.layoutinspector.view.inspection.LayoutInspectorViewProtocol.GetPropertiesCommand
import com.android.tools.idea.layoutinspector.view.inspection.LayoutInspectorViewProtocol.GetPropertiesResponse
import com.android.tools.idea.layoutinspector.view.inspection.LayoutInspectorViewProtocol.LayoutEvent
import com.android.tools.idea.layoutinspector.view.inspection.LayoutInspectorViewProtocol.ProgressEvent
import com.android.tools.idea.layoutinspector.view.inspection.LayoutInspectorViewProtocol.ProgressEvent.ProgressCheckpoint
import com.android.tools.idea.layoutinspector.view.inspection.LayoutInspectorViewProtocol.Response
import com.android.tools.idea.layoutinspector.view.inspection.LayoutInspectorViewProtocol.Screenshot
import com.android.tools.idea.layoutinspector.view.inspection.LayoutInspectorViewProtocol.StartFetchCommand
import com.android.tools.idea.layoutinspector.view.inspection.LayoutInspectorViewProtocol.StartFetchResponse
import com.android.tools.idea.layoutinspector.view.inspection.LayoutInspectorViewProtocol.StopFetchResponse
import com.android.tools.idea.layoutinspector.view.inspection.LayoutInspectorViewProtocol.UpdateScreenshotTypeCommand
import com.android.tools.idea.layoutinspector.view.inspection.LayoutInspectorViewProtocol.UpdateScreenshotTypeResponse
import com.android.tools.idea.layoutinspector.view.inspection.LayoutInspectorViewProtocol.WindowRootsEvent
import java.io.ByteArrayOutputStream
import java.io.OutputStream
import java.io.PrintStream
import java.util.Timer
import java.util.TimerTask
import java.util.concurrent.Callable
import java.util.concurrent.Executor
import java.util.concurrent.Executors
import kotlin.concurrent.timerTask

private const val LAYOUT_INSPECTION_ID = "layoutinspector.view.inspection"

// created by java.util.ServiceLoader
class ViewLayoutInspectorFactory : InspectorFactory<ViewLayoutInspector>(LAYOUT_INSPECTION_ID) {
    override fun createInspector(
        connection: Connection,
        environment: InspectorEnvironment
    ) = ViewLayoutInspector(connection, environment)
}

private const val MAX_START_FETCH_RETRIES = 10

class ViewLayoutInspector(connection: Connection, private val environment: InspectorEnvironment) :
    Inspector(connection) {

    /**
     * This exists only for testing purposes.
     */
    @VisibleForTesting
    var doBeforeCapture: (() -> Unit)? = null

    private var checkpoint: ProgressCheckpoint = ProgressCheckpoint.NOT_STARTED
        set(value) {
            if (value <= field){
                return
            }
            field = value
            if (value != ProgressCheckpoint.NOT_STARTED) {
                connection.sendEvent {
                    progressEvent = ProgressEvent.newBuilder().apply {
                        checkpoint = field
                    }.build()
                }
            }
        }

    private val scope =
        CoroutineScope(SupervisorJob() + environment.executors().primary().asCoroutineDispatcher())

    @GuardedBy("state.lock")
    private val state = InspectorState()

    private val foldSupport = createFoldSupport(connection, { state.fetchContinuously })
        get() = foldSupportOverrideForTests ?: field

    @property:VisibleForTesting
    var foldSupportOverrideForTests: FoldSupport? = null

    private val rootsDetector = RootsDetector(connection, ::onRootsChanged) { checkpoint = it }

    private val deviceInfo = DeviceInfo()

    override fun onReceiveCommand(data: ByteArray, callback: CommandCallback) {
        val command = Command.parseFrom(data)
        when (command.specializedCase) {
            Command.SpecializedCase.START_FETCH_COMMAND -> handleStartFetchCommand(
                command.startFetchCommand,
                callback
            )
            Command.SpecializedCase.STOP_FETCH_COMMAND -> handleStopFetchCommand(callback)
            Command.SpecializedCase.GET_PROPERTIES_COMMAND -> handleGetProperties(
                command.getPropertiesCommand,
                callback
            )
            Command.SpecializedCase.UPDATE_SCREENSHOT_TYPE_COMMAND -> handleUpdateScreenshotType(
                command.updateScreenshotTypeCommand,
                callback
            )
            Command.SpecializedCase.CAPTURE_SNAPSHOT_COMMAND -> handleCaptureSnapshotCommand(
                command.captureSnapshotCommand,
                callback
            )
            Command.SpecializedCase.DISABLE_BITMAP_SCREENSHOT_COMMAND -> handleDisableBitmapScreenshotCommand(
                command.disableBitmapScreenshotCommand,
                callback
            )
            else -> error("Unexpected view inspector command case: ${command.specializedCase}")
        }
    }

    override fun onDispose() {
        forceStopAllCaptures()
        foldSupport?.shutdown()
        scope.cancel("ViewLayoutInspector has been disposed")
        SynchronousPixelCopy.stopHandler()
    }

    /**
     * Stop any stale roots from capturing and, depending on [InspectorState.fetchContinuously],
     * may start capturing new roots.
     */
    private fun onRootsChanged(added: List<Long>, removed: List<Long>, roots: Map<Long, View>) {
        synchronized(state.lock) {
            for (toRemove in removed) {
                state.captureContextMap.remove(toRemove)?.shutdown()
            }
            added.mapNotNull { roots[it] }.forEach { foldSupport?.start(it) }
            removed.mapNotNull { roots[it] }.forEach { foldSupport?.stop(it) }

            if (state.fetchContinuously) {
                if (added.isNotEmpty()) {
                    // The first time we call this method, `lastRootIds` gets initialized
                    // with views already being captured, so we don't need to start
                    // capturing them again.
                    val actuallyAdded = added.toMutableList().apply {
                        removeAll { id -> state.captureContextMap.containsKey(id) }
                    }
                    if (actuallyAdded.isNotEmpty()) {
                        ThreadUtils.runOnMainThread {
                            for (toAdd in added) {
                                try {
                                    startCapturing(roots.getValue(toAdd))
                                } catch (t: Throwable) {
                                    Log.w("layinsp", t)
                                    connection.sendEvent {
                                        errorEvent =
                                            LayoutInspectorViewProtocol.ErrorEvent.newBuilder()
                                                .apply {
                                                    message = t.stackTraceToString()
                                                }.build()
                                    }
                                }
                            }
                        }
                    }
                }
                else if (removed.isNotEmpty()) {
                    ThreadUtils.runOnMainThread {
                        // When a window goes away, we expect remaining views to send a
                        // signal causing the client to refresh, but this doesn't always
                        // happen, so to be safe, we force it ourselves.
                        roots.values.forEach { view -> view.invalidate() }
                    }
                }
            }
        }
    }

    private fun forceStopAllCaptures() {
        rootsDetector.stop()
        synchronized(state.lock) {
            for (context in state.captureContextMap.values) {
                context.shutdown()
            }
            state.captureContextMap.clear()
        }
    }

    private fun startCapturing(root: View) {
        if (!root.isHardwareAccelerated() && !root.hasHardwareFlagSetInLayoutParams()) {
            rootsDetector.stop()
            throw noHardwareAcceleration()
        }

        val captureOutputStream = ByteArrayOutputStream()

        val captureExecutor = CaptureExecutor(
            captureOutputStream,
            state,
            root,
            rootsDetector,
            foldSupport,
            updateState = { checkpoint = it },
            connection,
            deviceInfo,
        )
        captureExecutor.doBeforeRun = doBeforeCapture

        updateCapturingCallback(root, captureExecutor, captureOutputStream)
        checkpoint = ProgressCheckpoint.STARTED
        root.invalidate() // Force a re-render so we send the current screen
    }

    /**
     * Return true if the layout params have the hardware accelerated flag.
     *
     * Warning: The flag is known to return the wrong result for several OEM devices including
     *          Samsung, Honor, ... See b/244120504
     *          The flag is often missing on these devices when it should be present.
     *
     * Currently we call this because we fear that View.isHardwareAccelerated() may return false
     * when the app is hardware accelerated. This could happen when View.mAttachInfo is null.
     * We do not want to complain about something that is incorrect.
     */
    private fun View.hasHardwareFlagSetInLayoutParams(): Boolean {
        val params = layoutParams
        if (params is WindowManager.LayoutParams) {
            if (params.flags and WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED != 0) {
                return true
            }
        }
        return false
    }

    private fun updateAllCapturingCallbacks() {
        for ((_, _, root, captureExecutor, captureOutputStream, _, _) in state.captureContextMap.values) {
            updateCapturingCallback(root, captureExecutor, captureOutputStream)
        }
    }

    /**
     * Creates or updates the capturing callback associated with [rootView].
     * The capturing callback can be to capture SKP or BITMAP.
     * The function creates a new [CaptureContext] associated with [rootView].
     */
    private fun updateCapturingCallback(rootView: View, captureExecutor: CaptureExecutor, captureOutputStream: OutputStream) {
        // Starting rendering captures must be called on the View thread or else it throws
        ThreadUtils.runOnMainThread {
            synchronized(state.lock) {
                var screenshotType = if (state.snapshotRequests.isNotEmpty()) {
                    // snapshots only support SKP. If there is a snapshot request, use SKP.
                    Screenshot.Type.SKP
                }
                else {
                    state.screenshotSettings.type
                }

                if (screenshotType == state.captureContextMap[rootView.uniqueDrawingId]?.screenshotType) {
                    // There is already a callback registered for this view and capture type.
                    return@runOnMainThread
                }

                // Stop the existing callback.
                // The AutoClose implementation in ViewDebug will set the picture capture callback
                // to null in the renderer. Do not call this after creating a new callback.
                state.captureContextMap[rootView.uniqueDrawingId]?.callbackHandle?.close()

                var capturingCallbackHandle = if (screenshotType == Screenshot.Type.SKP) {
                    try {
                        // If we get null, it means the view is gone. It will be removed by the roots detector later.
                        registerSkpCallback(rootView, captureExecutor, captureOutputStream) ?: return@runOnMainThread
                    }
                    catch (exception: Exception) {
                        connection.sendEvent {
                            errorEventBuilder.message = "Unable to register listener for 3d mode images: ${exception.message}"
                        }
                        state.screenshotSettings = ScreenshotSettings(Screenshot.Type.BITMAP, state.screenshotSettings.scale)
                        null
                    }
                }
                else {
                    null
                }

                if (capturingCallbackHandle == null) {
                    capturingCallbackHandle = registerScreenshotCallback(rootView, captureExecutor, captureOutputStream)
                    screenshotType = Screenshot.Type.BITMAP
                }

                // We might get multiple callbacks for the same view while still processing an earlier
                // one. Let's process them sequentially to avoid confusion.
                val sequentialExecutor =
                    Executors.newSingleThreadExecutor { r -> ThreadUtils.newThread(r) }

                state.captureContextMap[rootView.uniqueDrawingId] =
                    CaptureContext(
                        capturingCallbackHandle,
                        screenshotType,
                        rootView,
                        captureExecutor,
                        captureOutputStream,
                        sequentialExecutor,
                        isLastCapture = (!state.fetchContinuously)
                    )
            }
        }
    }

    private fun registerSkpCallback(
        rootView: View,
        captureExecutor: Executor,
        os: OutputStream
    ): AutoCloseable? {
        return if (Build.VERSION.SDK_INT > 32 || (Build.VERSION.SDK_INT == 32  && Build.VERSION.PREVIEW_SDK_INT > 0)) {
            // This method is only accessible on T+ (or Q, but there it's broken).
            ViewDebug::class.java.getDeclaredMethod(
                "startRenderingCommandsCapture",
                View::class.java,
                Executor::class.java,
                Callable::class.java
            ).invoke(null, rootView, captureExecutor, Callable { os }) as AutoCloseable
        } else {
            SkiaQWorkaround.startRenderingCommandsCapture(rootView, captureExecutor) { os }
        }
    }

    private fun registerScreenshotCallback(
        rootView: View,
        captureExecutor: CaptureExecutor,
        captureOutputStream: OutputStream
    ): AutoCloseable {
        val timer = Timer("ViewLayoutInspectorTimer")
        var stop = false
        val doCapture = {
            if (!stop) {
                captureExecutor.execute {
                    // even if the screenshot is not taken, CaptureExecutor#execute runs some
                    // critical code, necessary for the inspector to function.
                    // TODO in the future we might want to refactor this, to be able to execute
                    //  a number of indipendent actions when registerFrameCommitCallback is called.
                    if (!state.disableBitmapScreenshot) {
                        captureBitmapScreenshot(rootView, captureOutputStream)
                    }
                }
            }
        }
        var task: TimerTask? = null
        lateinit var callback: Runnable
        callback = Runnable {
            task?.cancel()
            task = null
            doCapture()
            task = timerTask {
                if (!stop) {
                    doCapture()
                }
            }
            // If another frame comes in while the listener is running it seems we won't be able to
            // re-register the listener in time to capture it. In order to be sure we capture the
            // end state of an animation, schedule another update for a little bit in the future.
            timer.schedule(task, 500L)

            if (!stop) {
                rootView.viewTreeObserver.registerFrameCommitCallback(callback)
            }
        }
        rootView.viewTreeObserver.registerFrameCommitCallback(callback)
        return AutoCloseable {
            rootView.viewTreeObserver.unregisterFrameCommitCallback(callback)
            stop = true
        }

    }

    private fun captureBitmapScreenshot(rootView: View, captureOutputStream: OutputStream) {
        // If this is the lowest z-index window (the normal case) we can be more
        // efficient because we don't need alpha information.
        val bitmapType = if (rootView.uniqueDrawingId == rootsDetector.lastRootIds.firstOrNull()) {
            BitmapType.RGB_565
        }
        else {
            BitmapType.ABGR_8888
        }
        rootView.takeScreenshot(state.screenshotSettings.scale, bitmapType)
            ?.toByteArray()
            ?.compress()
            ?.let { captureOutputStream.write(it) }
    }

    private fun sendEmptyLayoutEvent() {
        connection.sendEvent {
            layoutEvent = LayoutEvent.getDefaultInstance()
        }
    }

    private fun handleStartFetchCommand(
        startFetchCommand: StartFetchCommand,
        callback: CommandCallback
    ) {
        checkpoint = ProgressCheckpoint.START_RECEIVED
        forceStopAllCaptures()

        synchronized(state.lock) {
            state.fetchContinuously = startFetchCommand.continuous
            if (!startFetchCommand.continuous) {
                state.screenshotSettings =
                    ScreenshotSettings(Screenshot.Type.SKP, state.screenshotSettings.scale)
            }
        }

        if (startFetchCommand.continuous) {
            rootsDetector.start()
        }
        else {
            // We may be getting here after a previous start / stop flow
            rootsDetector.reset()
        }
        try {
            // Since the start command is sent right after we set the debug system properties, which
            // cause an activity restart, it's possible that the activity will still be restarting
            // at this point and we won't find any root views. Retry a few times until we do.
            var tries = 0
            while (tries++ < MAX_START_FETCH_RETRIES) {
                val result = ThreadUtils.runOnMainThread {
                    val rootViews = getRootViews()
                    if (rootViews.isEmpty()) {
                        false
                    } else {
                        for (root in rootViews) {
                            startCapturing(root)
                        }
                        foldSupport?.initialize(rootViews.first().context)
                        true
                    }
                }.get()
                when {
                    result -> break
                    tries == MAX_START_FETCH_RETRIES -> sendEmptyLayoutEvent()
                    else -> Thread.sleep(300)
                }
            }
        }
        catch (exception: Exception) {
            Log.w("layinsp", "Error during startCapturing", exception)
            callback.reply {
                startFetchResponse = StartFetchResponse.newBuilder().apply {
                    error = exception.cause?.message ?: "Unknown error"
                    code = exception.cause?.errorCode
                }.build()
            }
            return
        }
        callback.reply {
            startFetchResponse = StartFetchResponse.getDefaultInstance()
        }
    }

    private fun handleUpdateScreenshotType(
        updateScreenshotTypeCommand: UpdateScreenshotTypeCommand,
        callback: CommandCallback
    ) {
        var changed: Boolean
        synchronized(state.lock) {
            val oldSettings = state.screenshotSettings
            val newSettings = updateScreenshotTypeCommand.let {
                ScreenshotSettings(
                    it.type.takeIf { type -> type != Screenshot.Type.UNKNOWN } ?: oldSettings.type,
                    it.scale.takeIf { scale -> scale > 0f } ?: oldSettings.scale)
            }
            changed = (oldSettings != newSettings)
            state.screenshotSettings = newSettings
        }
        callback.reply {
            updateScreenshotTypeResponse = UpdateScreenshotTypeResponse.getDefaultInstance()
        }

        if (changed) {
            updateAllCapturingCallbacks()
            ThreadUtils.runOnMainThread {
                for (rootView in getRootViews()) {
                    rootView.invalidate()
                }
            }
        }
    }

    private fun handleDisableBitmapScreenshotCommand(
        disableBitmapScreenshotCommand: DisableBitmapScreenshotCommand,
        callback: CommandCallback
    ) {
        synchronized(state.lock) {
            state.disableBitmapScreenshot = disableBitmapScreenshotCommand.disable
        }
        callback.reply {
            disableBitmapScreenshotResponse = DisableBitmapScreenshotResponse.getDefaultInstance()
        }
    }

    private fun handleStopFetchCommand(callback: CommandCallback) {
        state.fetchContinuously = false
        callback.reply {
            stopFetchResponse = StopFetchResponse.getDefaultInstance()
        }

        rootsDetector.stop()
        synchronized(state.lock) {
            val contextMap = state.captureContextMap
            for (context in contextMap.values) {
                context.isLastCapture = true
            }
            ThreadUtils.runOnMainThread {
                getRootViews()
                    .filter { view -> contextMap.containsKey(view.uniqueDrawingId) }
                    .forEach { view -> view.invalidate() }
            }
        }
    }

    private fun handleGetProperties(
        propertiesCommand: GetPropertiesCommand,
        callback: CommandCallback
    ) {

        ThreadUtils.runOnMainThread {
            val foundView = getRootViews()
                .asSequence()
                .filter { it.uniqueDrawingId == propertiesCommand.rootViewId }
                .flatMap { rootView -> rootView.flatten() }
                .filter { view -> view.uniqueDrawingId == propertiesCommand.viewId }
                .firstOrNull()

            environment.executors().primary().execute {
                val response =
                    foundView?.createGetPropertiesResponse()
                        ?: GetPropertiesResponse.getDefaultInstance()
                callback.reply { getPropertiesResponse = response }
            }
        }
    }

    private fun handleCaptureSnapshotCommand(
        @Suppress("UNUSED_PARAMETER") // TODO: support bitmap
        captureSnapshotCommand: LayoutInspectorViewProtocol.CaptureSnapshotCommand,
        callback: CommandCallback
    ) {
        rootsDetector.checkRoots()
        state.snapshotRequests.clear()

        scope.launch {
            val roots = ThreadUtils.runOnMainThreadAsync { getRootViews() }.await()
            val windowSnapshots = roots.map { view ->
                SnapshotRequest().also {
                    state.snapshotRequests[view.uniqueDrawingId] = it
                }.result
            }
            // At this point we need to switch to capturing SKPs if we aren't already.
            updateAllCapturingCallbacks()
            ThreadUtils.runOnMainThread { roots.forEach { it.invalidate() } }
            val reply = LayoutInspectorViewProtocol.CaptureSnapshotResponse.newBuilder().apply {
                windowRoots = WindowRootsEvent.newBuilder().apply {
                    addAllIds(roots.map { it.uniqueDrawingId })
                }.build()
                addAllWindowSnapshots(windowSnapshots.awaitAll())
            }.build()
            // And now we need to switch back to bitmaps, if we were before.
            updateAllCapturingCallbacks()
            callback.reply {
                captureSnapshotResponse = reply
            }
        }
    }
}

private fun Inspector.CommandCallback.reply(initResponse: Response.Builder.() -> Unit) {
    val response = Response.newBuilder()
    response.initResponse()
    reply(response.build().toByteArray())
}

fun Connection.sendEvent(init: Event.Builder.() -> Unit) {
    sendEvent(
        Event.newBuilder()
            .apply { init() }
            .build()
            .toByteArray()
    )
}

private fun Throwable.stackTraceToString(): String {
    val error = ByteArrayOutputStream()
    printStackTrace(PrintStream(error))
    return error.toString()
}
