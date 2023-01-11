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

package com.android.tools.agent.appinspection

import android.view.View
import androidx.annotation.GuardedBy
import androidx.inspection.Connection
import com.android.tools.agent.appinspection.framework.flatten
import com.android.tools.agent.appinspection.proto.StringTable
import com.android.tools.agent.appinspection.proto.createAppContext
import com.android.tools.agent.appinspection.proto.createConfiguration
import com.android.tools.agent.appinspection.proto.createPropertyGroup
import com.android.tools.agent.appinspection.proto.toNode
import com.android.tools.agent.appinspection.util.ThreadUtils
import com.android.tools.idea.layoutinspector.view.inspection.LayoutInspectorViewProtocol
import com.android.tools.idea.layoutinspector.view.inspection.LayoutInspectorViewProtocol.ProgressEvent.ProgressCheckpoint
import com.android.tools.idea.protobuf.ByteString
import java.io.ByteArrayOutputStream
import java.util.concurrent.Executor
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicLong

/**
 * An [Executor] responsible for capturing the View information visible on the screen.
 */
class CaptureExecutor(
    private val captureOutputStream: ByteArrayOutputStream,
    @GuardedBy("state.lock")
    private val state: InspectorState,
    private val root: View,
    private val rootsDetector: RootsDetector,
    private val foldSupport: FoldSupport?,
    private val updateState: (ProgressCheckpoint) -> Unit,
    private val connection: Connection,
    private val previousDeviceInfo: DeviceInfo,
) : Executor {

    /**
     * A function executed before [execute]
     */
    var doBeforeRun: (() -> Unit)? = null

    private val currentRequestId = AtomicLong()

    override fun execute(command: Runnable) {
        doBeforeRun?.invoke()

        val requestId = currentRequestId.incrementAndGet()
        updateState(ProgressCheckpoint.VIEW_INVALIDATION_CALLBACK)
        val executorService = state.captureContextMap[root.uniqueDrawingId]?.executorService ?: return

        try {
            executorService.execute {
                if (requestId != currentRequestId.get()) {
                    // This request is obsolete, just return
                    command.run()
                    captureOutputStream.reset()
                }
                else {
                    executeCapture(command, captureOutputStream, root)
                }
            }
        } catch (exception: RejectedExecutionException) {
            // this can happen if we stop capture and then start again immediately: "executor"
            // above can be shutdown in between when it's retrieved and when the execution
            // starts on the next line.
            connection.sendEvent {
                errorEventBuilder.message = "ViewLayoutInspector got RejectedExecutionException during capture: ${exception.message}"
            }
        }
    }

    /**
     * @param doCapture Triggers a capture, the output of which is written into [captureOutputStream].
     */
    private fun executeCapture(doCapture: Runnable, captureOutputStream: ByteArrayOutputStream, rootView: View) {
        var snapshotRequest: SnapshotRequest?
        var context: CaptureContext
        var screenshotSettings: ScreenshotSettings
        synchronized(state.lock) {
            snapshotRequest = state.snapshotRequests[rootView.uniqueDrawingId]
            if (snapshotRequest?.state?.compareAndSet(
                    SnapshotRequest.State.NEW, SnapshotRequest.State.PROCESSING
                ) != true
            ) {
                snapshotRequest = null
            }
            screenshotSettings = if (snapshotRequest != null) {
                ScreenshotSettings(LayoutInspectorViewProtocol.Screenshot.Type.SKP)
            } else {
                state.screenshotSettings
            }
            // We might get some lingering captures even though we already finished
            // listening earlier (this would be indicated by no context). Just abort
            // early in that case.
            // Note: We copy the context instead of returning it directly, to avoid rare
            // but potential threading issues as other threads can modify the context, e.g.
            // handling the stop fetch command.
            context = state.captureContextMap[rootView.uniqueDrawingId]?.copy() ?: return
            if (snapshotRequest == null && context.isLastCapture) {
                state.captureContextMap.remove(rootView.uniqueDrawingId)
            }
        }

        // Just in case, always check roots before sending a layout event, as this may send
        // out a roots event. We always want layout events to follow up-to-date root events.
        rootsDetector.checkRoots()

        val snapshotResponse = if (snapshotRequest != null) {
            LayoutInspectorViewProtocol.CaptureSnapshotResponse.WindowSnapshot.newBuilder()
        }
        else null
        run {
            // Prepare and send LayoutEvent
            // Triggers image fetch into `os`
            // We always have to do this even if we don't use the bytes it gives us,
            // because otherwise an internal queue backs up
            doCapture.run()

            // If we have a snapshot request, we can remove it from the request map now that
            // it's no longer needed to indicate that SKPs need to be collected.
            if (snapshotRequest != null) {
                state.snapshotRequests.remove(rootView.uniqueDrawingId)
            }

            // This root is no longer visible. Ignore this update.
            if (!rootsDetector.lastRootIds.contains(rootView.uniqueDrawingId)) {
                return@run
            }
            sendLayoutEvent(rootView, context, screenshotSettings, captureOutputStream, snapshotResponse)
        }
        if (snapshotResponse != null || context.isLastCapture) {
            sendAllPropertiesEvent(rootView, snapshotResponse)

            // Send the updated fold state, in case we haven't been sending it continuously.
            foldSupport?.sendFoldStateEventNow()
        }
        snapshotResponse?.let { snapshotRequest?.result?.complete(it.build()) }
        return
    }

    private fun sendLayoutEvent(
        rootView: View,
        context: CaptureContext,
        screenshotSettings: ScreenshotSettings,
        os: ByteArrayOutputStream,
        snapshotResponse: LayoutInspectorViewProtocol.CaptureSnapshotResponse.WindowSnapshot.Builder?
    ) {
        val screenshot = ByteString.copyFrom(os.toByteArray())

        os.reset() // Clear stream, ready for next frame
        updateState(ProgressCheckpoint.SCREENSHOT_CAPTURED)
        if (context.isLastCapture) {
            context.shutdown()
        }

        val stringTable = StringTable()
        val appContext = rootView.createAppContext(stringTable)
        val configuration = rootView.createConfiguration(stringTable)

        val (rootViewNode, rootOffset) = ThreadUtils.runOnMainThread {
            val rootViewNode = rootView.toNode(stringTable)
            val rootOffset = IntArray(2)
            rootView.getLocationInSurface(rootOffset)

            (rootViewNode to rootOffset)
        }.get()

        updateState(ProgressCheckpoint.VIEW_HIERARCHY_CAPTURED)
        val layout = createLayoutMessage(
            stringTable,
            appContext,
            configuration,
            rootViewNode,
            rootOffset,
            screenshotSettings,
            screenshot
        )
        if (snapshotResponse != null) {
            snapshotResponse.layout = layout
        } else {
            updateState(ProgressCheckpoint.RESPONSE_SENT)
            connection.sendEvent {
                layoutEvent = layout
            }
        }
    }

    private fun sendAllPropertiesEvent(
        root: View,
        snapshotResponse: LayoutInspectorViewProtocol.CaptureSnapshotResponse.WindowSnapshot.Builder?
    ) {
        // Prepare and send PropertiesEvent
        // We get here either if the client requested a one-time snapshot of the layout
        // or if the client just stopped an in-progress fetch. Collect and send all
        // properties, so that the user can continue to explore all values in the UI and
        // they will match exactly the layout at this moment in time.

        val allViews = ThreadUtils.runOnMainThread {
            root.flatten().toList()
        }.get(10, TimeUnit.SECONDS) ?: throw TimeoutException()
        val stringTable = StringTable()
        val propertyGroups = allViews.map { it.createPropertyGroup(stringTable) }
        val properties = LayoutInspectorViewProtocol.PropertiesEvent.newBuilder().apply {
            rootId = root.uniqueDrawingId
            addAllPropertyGroups(propertyGroups)
            addAllStrings(stringTable.toStringEntries())
        }.build()
        if (snapshotResponse != null) {
            snapshotResponse.properties = properties
        } else {
            connection.sendEvent {
                propertiesEvent = properties
            }
        }
    }

    private fun createLayoutMessage(
        stringTable: StringTable,
        appContext: LayoutInspectorViewProtocol.AppContext,
        configuration: LayoutInspectorViewProtocol.Configuration,
        rootView: LayoutInspectorViewProtocol.ViewNode,
        rootOffset: IntArray,
        screenshotSettings: ScreenshotSettings,
        screenshot: ByteString?
    ) = LayoutInspectorViewProtocol.LayoutEvent.newBuilder().apply {
        addAllStrings(stringTable.toStringEntries())
        if (appContext != previousDeviceInfo.appContext || configuration != previousDeviceInfo.configuration) {
            previousDeviceInfo.configuration = configuration
            previousDeviceInfo.appContext = appContext
            this.configuration = configuration
            this.appContext = appContext
        }
        this.rootView = rootView
        this.rootOffset = LayoutInspectorViewProtocol.Point.newBuilder().apply {
            x = rootOffset[0]
            y = rootOffset[1]
        }.build()

        // only send a screenshot if bitmaps are not disabled or if the current screenshot type is SKP
        if (!state.disableBitmapScreenshot || screenshotSettings.type == LayoutInspectorViewProtocol.Screenshot.Type.SKP) {
            this.screenshot = LayoutInspectorViewProtocol.Screenshot.newBuilder().apply {
                type = screenshotSettings.type
                bytes = screenshot
            }.build()
        }
    }.build()
}
