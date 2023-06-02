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
package com.android.declarative.internal

import com.android.utils.ILogger

/**
 * Temporary class to handle logging in the declarative world.
 * This can be used from the Open Source Gradle Plugin as well as the Android Studio plugin.
 *
 * @param lenient true if errors should just be logged, false to raise exceptions
 * @param logger [ILogger] instance used to do the actual logging.
 */
open class IssueLogger(
    private val lenient: Boolean = false,
    val logger: ILogger,
){

    /**
     * An error condition has occurred, raise it.
     *
     * @param error the error reason
     */
    fun raiseError(error: String) {
        if (lenient) {
            logger.error(null, "ERROR: $error")
        } else {
            throw RuntimeException(error)
        }
    }

    /**
     * Expect a condition to be true, logging error messages if not.
     * If the [expectation] is true, the [value] will be null checked.
     * If the check fail, [raiseError] will be called with the [nullValueFailureMessage]
     * otherwise the [then] block will be invoked with the non-null reference.
     */
    fun <T> expect(
        expectation: Boolean = true,
        expectationFailureMessage: () -> String = { "" },
        value: T?,
        nullValueFailureMessage: () -> String,
        then: (T) -> Unit
    ) {
        if (!expectation) {
            raiseError(expectationFailureMessage())
        } else {
            value ?: raiseError(nullValueFailureMessage())
            value?.let { then(it) }
        }
    }
}

fun ILogger.error(block: () -> String) {
    error(block())
}
