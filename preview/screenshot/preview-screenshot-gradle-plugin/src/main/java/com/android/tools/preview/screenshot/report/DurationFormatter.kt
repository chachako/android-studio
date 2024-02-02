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

package com.android.tools.preview.screenshot.report

import java.math.BigDecimal
import java.math.RoundingMode

class DurationFormatter {

    fun format(duration: Long): String {
        if (duration == 0L) {
            return "0s"
        }
        var formattedDuration = duration
        val result = StringBuilder()
        val days: Long =
            formattedDuration / MILLIS_PER_DAY
        formattedDuration %= MILLIS_PER_DAY
        if (days > 0) {
            result.append(days)
            result.append("d")
        }
        val hours: Long =
            duration / MILLIS_PER_HOUR
        formattedDuration %= MILLIS_PER_HOUR
        if (hours > 0 || result.isNotEmpty()) {
            result.append(hours)
            result.append("h")
        }
        val minutes: Long =
            formattedDuration / MILLIS_PER_MINUTE
        formattedDuration %= MILLIS_PER_MINUTE
        if (minutes > 0 || result.isNotEmpty()) {
            result.append(minutes)
            result.append("m")
        }
        val secondsScale = if (result.isNotEmpty()) 2 else 3
        result.append(
            BigDecimal.valueOf(formattedDuration)
                .divide(MILLIS_PER_SECOND.toBigDecimal())
                .setScale(secondsScale, RoundingMode.HALF_UP)
        )
        result.append("s")
        return result.toString()
    }

    companion object {
        const val MILLIS_PER_SECOND = 1000
        val MILLIS_PER_MINUTE: Int =
            60 * MILLIS_PER_SECOND
        val MILLIS_PER_HOUR: Int =
            60 * MILLIS_PER_MINUTE
        val MILLIS_PER_DAY: Int =
            24 * MILLIS_PER_HOUR
    }
}
