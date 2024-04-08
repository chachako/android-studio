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
package com.android.adblib.tools.debugging.processinventory.server

/**
 * Run-time configuration parameters of a [ProcessInventoryServer] instance.
 */
interface ProcessInventoryServerConfiguration {

    /**
     * An arbitrary value attached to each request sent by the client to the
     * [process inventory server][ProcessInventoryServer], used for diagnostics and logging.
     */
    val clientDescription: String

    /**
     * An arbitrary value attach to each response sent from the
     * [process inventory server][ProcessInventoryServer], used for diagnostics and logging.
     */
    val serverDescription: String
}
