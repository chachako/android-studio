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
package com.android.tools.leakcanarylib.data

import com.android.tools.leakcanarylib.parser.LeakParser

data class Leak(
    var type: LeakType,
    var retainedByteSize: Int,
    var signature: String,
    var leakTraceCount: Int,
    var displayedLeakTrace: List<LeakTrace>
) {

    override fun toString(): String {
        return (if (retainedByteSize >= 0) "$retainedByteSize bytes retained by leaking objects\n" else "") +
                (if (leakTraceCount > 1) "Displaying only 1 leak trace out of $leakTraceCount with the same signature\n" else "") +
                "Signature: $signature\n" +
                displayedLeakTrace.first() // Guaranteed to have at least one element when there is a valid leak. Also, leakTraceCount and
                                           // displayedLeakTrace are inter-related as specified in LeakParser.
    }

    companion object {
        fun fromString(lines: String, type: LeakType): List<Leak> {
            return LeakParser.fromString(lines, type)
        }
    }
}
