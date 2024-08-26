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
package com.android.adblib.impl

import com.android.adblib.AdbActivityManagerServices
import com.android.adblib.AdbSession
import com.android.adblib.DeviceSelector
import com.android.adblib.adbLogger
import com.android.adblib.shellCommand
import com.android.adblib.withTextCollector
import java.io.IOException

class AdbActivityManagerServicesImpl(private val session: AdbSession) : AdbActivityManagerServices {
    private val logger = adbLogger(session.host)

    override suspend fun forceStop(device: DeviceSelector, packageName: String) {
        validatePackageName(packageName)
        session.deviceServices
            .shellCommand(device, "am force-stop $packageName")
            .withTextCollector()
            .executeAsSingleOutput { result ->
                if (result.stderr.isNotEmpty()) {
                    val shortenedMessage = result.stderr.substring(0, 100)
                    logger.warn("`am force-stop $packageName`: stderr: $shortenedMessage")
                    throw IOException(result.stderr)
                }
            }
    }

    override suspend fun crash(device: DeviceSelector, packageName: String) {
        validatePackageName(packageName)
        session.deviceServices
            .shellCommand(device, "am crash $packageName")
            .withTextCollector()
            .executeAsSingleOutput { result ->
                if (result.stderr.isNotEmpty()) {
                    // stderr is not empty, e.g. for unsupported API level
                    val shortenedMessage = result.stderr.substring(0, 100)
                    logger.warn("`am crash $packageName`: stderr: $shortenedMessage")
                    throw IOException(result.stderr)
                }
            }
    }

    /**
     * Ensures a package name contains only valid characters.
     */
    private fun validatePackageName(packageName: String) {
        if (!packageName.matches(PACKAGE_NAME_REGEX)) {
            throw IllegalArgumentException("packageName `$packageName` contains illegal characters")
        }
    }
}

private val PACKAGE_NAME_REGEX: Regex = Regex("[a-zA-Z0-9._]+")
