/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.fakeadbserver

/**
 * Base class for JDWP processes [ClientState] and profileable processes [ProfileableProcessState]
 */
abstract class ProcessState(val pid: Int) {

    abstract val debuggable: Boolean

    abstract val profileable: Boolean

    open val architecture: String
        get() = ""
}

/**
 * An [ProcessState] that is [profileable] but not [debuggable]
 */
class ProfileableProcessState(pid: Int, override val architecture: String) : ProcessState(pid) {

    override val debuggable: Boolean
        get() = false

    override val profileable: Boolean
        get() = true
}
