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
package com.android.adblib.tools

import com.android.adblib.AdbDeviceServices
import com.android.adblib.AdbSessionHost
import com.android.adblib.tools.debugging.JdwpProcess
import java.time.Duration

/**
 * By convention, all [properties][AdbSessionHost.Property] of this module are defined in
 * this singleton.
 */
object AdbLibToolsProperties {

    private const val NAME_PREFIX = "com.android.adblib.tools"

    /**
     * If the [AdbDeviceServices.trackApp] call fails with an error while the device is
     * still connected, we want to retry. This defines the [Duration] to wait before retrying.
     */
    val TRACK_APP_RETRY_DELAY = AdbSessionHost.DurationProperty(
        name = "$NAME_PREFIX.track.app.retry.delay",
        defaultValue = Duration.ofSeconds(2)
    )

    /**
     * If the [AdbDeviceServices.trackJdwp] call fails with an error while the device is
     * still connected, we want to retry. This defines the [Duration] to wait before retrying.
     */
    val TRACK_JDWP_RETRY_DELAY = AdbSessionHost.DurationProperty(
        name = "$NAME_PREFIX.track.jdwp.retry.delay",
        defaultValue = Duration.ofSeconds(2)
    )

    val JDWP_PROCESS_TRACKER_RETRY_DELAY = AdbSessionHost.DurationProperty(
        name = "$NAME_PREFIX.jdwp.process.tracker.retry.delay",
        defaultValue = Duration.ofSeconds(2)
    )

    val APP_PROCESS_TRACKER_RETRY_DELAY = AdbSessionHost.DurationProperty(
        name = "$NAME_PREFIX.app.process.tracker.retry.delay",
        defaultValue = Duration.ofSeconds(2)
    )

    /**
     * This value is from DDMLIB, using it should help avoid potential backward compatibility
     * issues if someone depends on this somehow.
     */
    val JDWP_SESSION_FIRST_PACKET_ID = AdbSessionHost.IntProperty(
        name = "$NAME_PREFIX.jdwp.session.first.packet.id",
        defaultValue = 0x40000000
    )

    /**
     * Amount of time to wait before collecting the properties of a [JdwpProcess] after the
     * process has been discovered, when [PROCESS_PROPERTIES_COLLECTOR_DELAY_USE_SHORT] is `false`.
     *
     * See [b/271572555](https://issuetracker.google.com/issues/271572555) for more context.
     */
    val PROCESS_PROPERTIES_COLLECTOR_DELAY_DEFAULT = AdbSessionHost.DurationProperty(
        name = "$NAME_PREFIX.process.properties.collector.delay.default",
        defaultValue = Duration.ofMillis(500)
    )

    /**
     * Amount of time to wait before collecting the properties of a [JdwpProcess] after the
     * process has been discovered, when [PROCESS_PROPERTIES_COLLECTOR_DELAY_USE_SHORT] is `true`.
     *
     * See [b/271572555](https://issuetracker.google.com/issues/271572555) for more context.
     */
    val PROCESS_PROPERTIES_COLLECTOR_DELAY_SHORT = AdbSessionHost.DurationProperty(
        name = "$NAME_PREFIX.process.properties.collector.delay.short",
        defaultValue = Duration.ofMillis(0)
    )

    /**
     * Where to use [PROCESS_PROPERTIES_COLLECTOR_DELAY_SHORT] or
     * [PROCESS_PROPERTIES_COLLECTOR_DELAY_DEFAULT] when collecting process properties.
     *
     * See [b/271572555](https://issuetracker.google.com/issues/271572555) for more context.
     */
    val PROCESS_PROPERTIES_COLLECTOR_DELAY_USE_SHORT = AdbSessionHost.BooleanProperty(
        name = "$NAME_PREFIX.process.properties.collector.delay.use.short",
        defaultValue = false,
        isVolatile = true
    )

    /**
     * Maximum amount of time a JDWP connection is open while waiting for the JDWP "handshake"
     * and various DDMS packets related to the process state.
     *
     * Note: The current value (15 seconds) matches the time Android Studio waits for a process
     * to show up as "waiting for debugger" after deploying and starting an application on a
     * device.
     */
    val PROCESS_PROPERTIES_READ_TIMEOUT = AdbSessionHost.DurationProperty(
        name = "$NAME_PREFIX.process.properties.read.timeout",
        defaultValue = Duration.ofSeconds(15)
    )

    /**
     * Amount of time to wait before retrying a JDWP session to retrieve process properties
     */
    val PROCESS_PROPERTIES_RETRY_DURATION = AdbSessionHost.DurationProperty(
        name = "$NAME_PREFIX.process.properties.retry.duration",
        defaultValue = Duration.ofSeconds(2)
    )

    val APP_PROCESS_RETRIEVE_PROCESS_NAME_RETRY_COUNT = AdbSessionHost.IntProperty(
        name = "$NAME_PREFIX.app.process.retrieve.name.retry.count",
        defaultValue = 5
    )

    val APP_PROCESS_RETRIEVE_PROCESS_NAME_RETRY_DELAY = AdbSessionHost.DurationProperty(
        name = "$NAME_PREFIX.app.process.retrieve.name.retry.delay",
        defaultValue = Duration.ofMillis(200)
    )

    val DDMS_REPLY_WAIT_TIMEOUT = AdbSessionHost.DurationProperty(
        name = "$NAME_PREFIX.ddms.reply.wait.timeout",
        defaultValue = Duration.ofSeconds(2)
    )

    /**
     * If true then we use app boot stage signal when available (i.e. in API 34+)
     */
    val SUPPORT_STAG_PACKETS = AdbSessionHost.BooleanProperty(
        name = "$NAME_PREFIX.support.stag.packets",
        defaultValue = false
    )

}
