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
package com.android.adblib.tools.debugging.impl

import com.android.adblib.tools.debugging.JdwpProcess
import com.android.adblib.tools.debugging.SharedJdwpSession

/**
 * Base class of internal implementations of [JdwpProcess], defining additional functions
 * to ensure proper behavior required for sharing [JdwpProcess] instances.
 *
 * Concrete instances should be obtained through [JdwpProcessFactory.create].
 */
internal abstract class AbstractJdwpProcess : JdwpProcess, AutoCloseable {

    /**
     * (**testing only**) Whether the [SharedJdwpSession] is currently in use
     */
    abstract val isJdwpSessionRetained: Boolean

    /**
     * Starts monitoring the JDWP process (in a separate coroutine) and emitting
     * values to [propertiesFlow]
     */
    abstract fun startMonitoring()

}

