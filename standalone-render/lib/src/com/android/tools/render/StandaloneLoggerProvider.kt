/*
 * Copyright (C) 2024 The Android Open Source Project
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

package com.android.tools.render

import com.android.tools.environment.Logger
import java.lang.AssertionError

/** A mostly no-op logger with an exception that it throws if an error is reported. */
class StandaloneLoggerProvider : Logger.LoggerProvider {
    override fun createLogger(name: String): Logger {
        return object : Logger {
            override fun warn(message: String, throwable: Throwable?) { }
            override fun error(message: String, throwable: Throwable?) {
                throw AssertionError(message, throwable)
            }
            override fun debug(message: String, throwable: Throwable?) { }
            override fun info(message: String, throwable: Throwable?) { }
            override val isDebugEnabled: Boolean = false
        }
    }

    override val priority: Int = 100
}
