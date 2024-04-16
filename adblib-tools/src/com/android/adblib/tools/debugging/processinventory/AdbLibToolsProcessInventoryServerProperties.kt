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
package com.android.adblib.tools.debugging.processinventory

import com.android.adblib.AdbSessionHost
import com.android.adblib.tools.debugging.processinventory.server.ProcessInventoryServer
import java.time.Duration

/**
 * By convention, all [properties][AdbSessionHost.Property] of this module are defined in
 * this singleton.
 */
object AdbLibToolsProcessInventoryServerProperties {

    private const val NAME_PREFIX = "com.android.adblib.tools.debugging.process.inventory.server"

    /**
     * The TCP port (on the local host) the server runs on.
     *
     * Note: The port name contains has the "V1" suffix in case of a breaking change of protocol
     * in the future. At that point a port "V2" should be created to ensure multiple versions of
     * the server can run concurrently (e.g. multiple instance of Android Studio) without
     * interfering with each other.
     *
     * Note: The value `8597` was picked in April 2024, after going through the list at
     * [List_of_TCP_and_UDP_port_numbers](https://en.wikipedia.org/wiki/List_of_TCP_and_UDP_port_numbers),
     * looking for an unused entry.
     *
     * Furthermore, the legacy JDWP proxy server (from ddmlib) was using ports `8598` and `8599`.
     * (See [DdmPreferences.java](https://cs.android.com/android-studio/platform/tools/base/+/3e4497f560c68ab52b635c5d20b913279c051fcd:ddmlib/src/main/java/com/android/ddmlib/DdmPreferences.java;l=78))
     */
    val LOCAL_PORT_V1 = AdbSessionHost.IntProperty(
        name = "$NAME_PREFIX.local.port.v1",
        defaultValue = 8597
    )

    /**
     * The socket connection timeout when trying to connect to a running instance of the
     * [ProcessInventoryServer]
     */
    val CONNECT_TIMEOUT = AdbSessionHost.DurationProperty(
        name = "$NAME_PREFIX.connect.timeout",
        defaultValue = Duration.ofSeconds(5)
    )

    /**
     * The [Duration] to wait between attempts to start a new [ProcessInventoryServer] when
     * attempting to connect to an existing server.
     */
    val START_RETRY_DELAY = AdbSessionHost.DurationProperty(
        name = "$NAME_PREFIX.start.retry.delay",
        defaultValue = Duration.ofSeconds(1)
    )

    /**
     * The amount of time before a device is removed from the [ProcessInventoryServer]
     * inventory if not used.
     */
    val UNUSED_DEVICE_REMOVAL_DELAY = AdbSessionHost.DurationProperty(
        name = "$NAME_PREFIX.unused.device.removal.delay",
        defaultValue = Duration.ofSeconds(10)
    )
}
