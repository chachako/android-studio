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

import java.util.ServiceConfigurationError
import java.util.ServiceLoader

/** Interface to log messages with different severity and optional [Throwable]. */
interface Logger {
    fun warn(message: String, throwable: Throwable?)

    fun warn(message: String) = warn(message, null)

    fun error(message: String, throwable: Throwable?)

    fun error(message: String) = error(message, null)

    fun error(throwable: Throwable) = error(throwable.message ?: "", throwable)

    fun debug(message: String, throwable: Throwable?)

    fun debug(message: String) = debug(message, null)

    fun debug(throwable: Throwable) = debug(throwable.message ?: "", throwable)

    val isDebugEnabled: Boolean

    /** Interface for the service providing functionality to construct a logger. */
    interface LoggerProvider {

        /**
         * Creates a logger with the [name]. The name will be part of the output the message,
         * helping to better locate the origin of the it. It is recommended to use the FQCN of the
         * class logging the message to easier locate the origin.
         */
        fun createLogger(name: String): Logger

        /**
         * One can replace the current logging by adding a [LoggerProvider] with a higher priority
         * to the classpath. The [LoggerProvider] with the highest priority wins.
         */
        val priority: Int
    }

    companion object {
        @JvmStatic
        fun getInstance(name: String): Logger {
            val serviceLoader = ServiceLoader.load(LoggerProvider::class.java, this::class.java.classLoader)
            val loggerProvider =
                serviceLoader.maxByOrNull { it.priority } ?:
                throw ServiceConfigurationError("Could not find any LoggerProviders")
            return loggerProvider.createLogger(name)
        }

        @JvmStatic
        fun <T> getInstance(clazz: Class<T>) = getInstance(clazz.name)
    }
}
