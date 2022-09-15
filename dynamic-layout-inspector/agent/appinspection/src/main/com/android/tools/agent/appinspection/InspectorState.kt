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
import com.android.tools.idea.layoutinspector.view.inspection.LayoutInspectorViewProtocol
import com.android.tools.idea.layoutinspector.view.inspection.LayoutInspectorViewProtocol.AppContext
import com.android.tools.idea.layoutinspector.view.inspection.LayoutInspectorViewProtocol.Configuration
import com.android.tools.idea.layoutinspector.view.inspection.LayoutInspectorViewProtocol.Screenshot
import kotlinx.coroutines.CompletableDeferred
import java.io.OutputStream
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executor
import java.util.concurrent.ExecutorService
import java.util.concurrent.atomic.AtomicReference

class InspectorState {

    /**
     * Lock used to guard an instance of [InspectorState].
     */
    val lock = Any()

    /**
     * A mapping of root view IDs to screen capture data that should be accessed across multiple threads.
     */
    val captureContextMap = mutableMapOf<Long, CaptureContext>()

    /**
     * When true, the inspector will keep generating layout events as the screen changes.
     * Otherwise, it will only return a single layout snapshot before going back to waiting.
     */
    var fetchContinuously: Boolean = false

    /**
     * Settings that determine the format of screenshots taken when doing a layout capture.
     */
    var screenshotSettings = ScreenshotSettings(Screenshot.Type.BITMAP)

    /**
     * When a snapshot is requested an entry will be added to this map for each window. Then
     * when content for that window is processed it will be set into the Deferred rather than
     * sent back as a normal Event.
     */
    var snapshotRequests: MutableMap<Long, SnapshotRequest> = ConcurrentHashMap()
}

/**
 * Context data associated with a capture of a single layout tree.
 */
data class CaptureContext(
    /**
     * A handle returned by a system that does continuous capturing, which, when closed, tells
     * the system to stop as soon as possible.
     */
    val callbackHandle: AutoCloseable,
    val screenshotType: Screenshot.Type,
    val rootView: View,
    /**
     * An [Executor] used to execute the code that does the screen capturing.
     * It runs some code before and after executing the screen capture logic.
     */
    val captureExecutor: CaptureExecutor,
    /**
     * The output stream on which the screen capture is written.
     */
    val captureOutputStream: OutputStream,
    /**
     * Executor used during capture
     */
    val executorService: ExecutorService,
    /**
     * When true, indicates we should stop capturing after the next one
     */
    var isLastCapture: Boolean = false,
) {
    fun shutdown() {
        callbackHandle.close()
        executorService.shutdown()
    }
}

data class ScreenshotSettings(
    val type: Screenshot.Type,
    val scale: Float = 1.0f
)

class SnapshotRequest {
    enum class State { NEW, PROCESSING }
    val result = CompletableDeferred<LayoutInspectorViewProtocol.CaptureSnapshotResponse.WindowSnapshot>()
    val state = AtomicReference(State.NEW)
}

/**
 * Wrapper class to hold information about the device.
 * @param appContext holds things like the theme of the app and the screen size.
 * @param configuration holds other device configurations like screen density, keyboard state etc.
 */
data class DeviceInfo(var appContext: AppContext, var configuration: Configuration)
