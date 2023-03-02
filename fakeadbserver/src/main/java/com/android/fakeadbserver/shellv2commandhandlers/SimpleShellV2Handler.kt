/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.fakeadbserver.shellv2commandhandlers

import com.android.fakeadbserver.DeviceState
import com.android.fakeadbserver.FakeAdbServer
import com.android.fakeadbserver.ShellProtocolType
import com.android.fakeadbserver.ShellV2Protocol
import com.android.fakeadbserver.services.ServiceOutput
import com.android.fakeadbserver.services.ShellProtocolServiceOutput
import com.google.common.base.Charsets
import java.net.Socket

/**
 * A specialized version of shell handlers that assumes the command are of the form "exe arg1 arg2".
 * For more complex handlers extend [ShellV2Handler] directly.
 */
abstract class SimpleShellV2Handler(
    shellProtocolType: ShellProtocolType,
    private val executable: String
) : ShellV2Handler(shellProtocolType) {

    override fun shouldExecute(shellCommand: String, shellCommandArgs: String?): Boolean {
        return executable == shellCommand
    }
}
