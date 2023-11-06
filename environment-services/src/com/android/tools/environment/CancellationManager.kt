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
package com.android.tools.environment

import java.util.ServiceLoader

interface CancellationManager {
    /** Checks if the currently running code should be cancelled and if so throws an exception. */
    fun doThrowIfCancelled()

    companion object {
        /**
         * If there is a registered [CancellationManager] performs [doThrowIfCancelled] check on
         * it otherwise does nothing.
         */
        @JvmStatic
        fun throwIfCancelled() {
            val serviceLoader =
                ServiceLoader.load(CancellationManager::class.java, this::class.java.classLoader)
            serviceLoader.findFirst().ifPresent {
                it.doThrowIfCancelled()
            }
        }
    }
}
