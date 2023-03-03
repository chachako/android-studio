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
package com.android.processmonitor.utils

import com.android.adblib.AdbLogger

/**
 * An [AdbLogger] that prepends every log entry with a prefix.
 *
 * Useful when logging per device etc.
 */
internal class PrefixLogger(
    private val delegate: AdbLogger,
    private val prefix: String
) : AdbLogger() {

    override val minLevel = delegate.minLevel

    override fun log(level: Level, message: String) {
        delegate.log(level, "$prefix: $message")
    }

    override fun log(level: Level, exception: Throwable?, message: String) {
        delegate.log(level, exception, "$prefix: $message")
    }
}
