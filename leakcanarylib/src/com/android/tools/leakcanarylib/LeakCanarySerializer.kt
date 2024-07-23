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
package com.android.tools.leakcanarylib

import com.android.tools.leakcanarylib.data.Analysis

/**
 * This class provides a method to parse LeakCanary logcat messages into structured `Analysis` object.
 */
class LeakCanarySerializer {

    /**
     * Parses a LeakCanary logcat message into an `Analysis` object.
     *
     * @param message The LeakCanary logcat message to parse.
     * @return An `Analysis` object representing the parsed analysis result, which can be either an `AnalysisSuccess` or an
     * `AnalysisFailure`.
     */
    fun parseLogcatMessage(message: String): Analysis {
        return Analysis.fromString(message)
    }
}
