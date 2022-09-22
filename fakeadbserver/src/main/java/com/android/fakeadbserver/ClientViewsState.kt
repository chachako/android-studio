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
package com.android.fakeadbserver

import java.nio.ByteBuffer

class ClientViewsState {

    private val lock = Any()
    private val viewRoots = mutableListOf<String>()
    private val viewCaptures = mutableMapOf<ViewCaptureId, ByteBuffer>()
    private val viewHierarchies = mutableMapOf<ViewHierarchyId, ByteBuffer>()

    fun addViewRoot(viewRoot: String) {
        synchronized(lock) {
            viewRoots.add(viewRoot)
        }
    }

    fun viewRoots(): List<String> {
        return synchronized(lock) {
            viewRoots.toList()
        }
    }

    fun addViewCapture(viewRoot: String, view: String, viewData: ByteBuffer) {
        synchronized(lock) {
            viewCaptures[ViewCaptureId(viewRoot, view)] = viewData
        }
    }

    fun captureViewData(viewRoot: String, view: String): ByteBuffer? {
        return synchronized(lock) {
            viewCaptures[ViewCaptureId(viewRoot, view)]
        }
    }

    fun addViewHierarchy(
        viewRoot: String,
        skipChildren: Boolean,
        includeProperties: Boolean,
        useV2: Boolean,
        data: ByteBuffer
    ) {
        synchronized(lock) {
            val id = ViewHierarchyId(viewRoot, skipChildren, includeProperties, useV2)
            viewHierarchies[id] = data
        }
    }

    fun viewHierarchyData(
        viewRoot: String,
        skipChildren: Boolean,
        includeProperties: Boolean,
        useV2: Boolean
    ): ByteBuffer? {
        return synchronized(lock) {
            val id = ViewHierarchyId(viewRoot, skipChildren, includeProperties, useV2)
            viewHierarchies[id]
        }
    }

    data class ViewCaptureId(val viewRoot: String, val view: String)

    data class ViewHierarchyId(
        val viewRoot: String,
        val skipChildren: Boolean,
        val includeProperties: Boolean,
        val useV2: Boolean
    )
}
