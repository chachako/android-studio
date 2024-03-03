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
package com.android.fakeadbserver.hostcommandhandlers

import com.android.fakeadbserver.DeviceState
import com.android.fakeadbserver.FakeAdbServer
import java.net.Socket
import kotlin.time.Duration
import kotlin.time.DurationUnit

/** host:features returns list of features supported by both the device and the HOST.  */
class FeaturesCommandHandler : SimpleHostCommandHandler("features") {

    override fun invoke(
        fakeAdbServer: FakeAdbServer,
        responseSocket: Socket,
        device: DeviceState?,
        args: String
    ): Boolean {
        device?.delayStdout?.let {
            if (it != Duration.ZERO) {
                Thread.sleep(it.toLong(DurationUnit.MILLISECONDS))
            }
        }
        if (device == null) {
            writeFailMissingDevice(responseSocket.getOutputStream(), command)
            return false
        }
        val out = responseSocket.getOutputStream()
        // This is a features request. It should contain only the features supported by
        // both the server and the device.
        val deviceFeatures = device.features
        val hostFeatures = fakeAdbServer.features
        val commonFeatures = HashSet(deviceFeatures)
        commonFeatures.retainAll(hostFeatures)
        writeOkayResponse(out, java.lang.String.join(",", commonFeatures))
        return false
    }

}
