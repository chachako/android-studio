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

package com.android.tools.render.environment

import com.android.tools.analytics.crash.CrashReport
import com.android.tools.analytics.crash.CrashReporter
import org.apache.http.HttpEntity
import java.util.concurrent.CompletableFuture

/** [CrashReporter] that prints the crashes information to the console. */
internal class ConsoleCrashReporter : CrashReporter {
    override fun submit(crashReport: CrashReport): CompletableFuture<String> =
        submit(crashReport, false)

    override fun submit(
        crashReport: CrashReport,
        userReported: Boolean
    ): CompletableFuture<String> {
        (crashReport as? ThrowableCrashReport)?.report()
        return CompletableFuture.completedFuture("")
    }

    override fun submit(kv: MutableMap<String, String>): CompletableFuture<String> {
        return CompletableFuture.completedFuture("")
    }

    override fun submit(entity: HttpEntity): CompletableFuture<String> {
        return CompletableFuture.completedFuture("")
    }
}
