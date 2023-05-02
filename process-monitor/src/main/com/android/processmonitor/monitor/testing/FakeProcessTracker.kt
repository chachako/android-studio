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
package com.android.processmonitor.monitor.testing

import com.android.processmonitor.common.ProcessEvent
import com.android.processmonitor.common.ProcessTracker
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.consumeAsFlow
import java.io.Closeable

/** A fake [ProcessTracker] for tests */
class FakeProcessTracker : ProcessTracker, Closeable {

    private val channel = Channel<ProcessEvent>(10)

    suspend fun send(vararg events: ProcessEvent) {
        events.forEach { channel.send(it) }
    }

    override fun trackProcesses(): Flow<ProcessEvent> = channel.consumeAsFlow()

    override fun close() {
        channel.close()
    }
}
