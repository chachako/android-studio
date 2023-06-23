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

import com.android.adblib.utils.ResizableBuffer

/**
 * An object implements the [SupportsOffline] interface to indicate that it allows creating an
 * in-memory copy of itself that can be used after the object has been disconnected from the
 * underlying ephemeral resource the object is bound to (e.g. a network connection or a file).
 */
internal interface SupportsOffline<out T> {

    /**
     * Returns a copy of this [SupportsOffline] object that can be used after the object
     * has been disconnected from the underlying resource it is bound to.
     */
    suspend fun toOffline(workBuffer: ResizableBuffer = ResizableBuffer()): T
}

/**
 * Returns a copy of this object that can be used after the object has been disconnected
 * from the underlying resource it is bound to, or `null` if this object does not implement
 * the [SupportsOffline] interface.
 */
internal suspend inline fun <reified T> T.toOfflineOrNull(
    workBuffer: ResizableBuffer
): T? {
    return if (this is SupportsOffline<*>) {
        return this.toOffline(workBuffer) as T
    } else {
        null
    }
}
