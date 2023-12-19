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

package com.android.tools.render.configuration

import com.android.ide.common.resources.Locale
import com.android.sdklib.IAndroidTarget
import com.android.sdklib.devices.DefaultDevices
import com.android.sdklib.devices.Device
import com.android.sdklib.devices.VendorDevices
import com.android.sdklib.internal.avd.AvdInfo
import com.android.tools.configurations.ConfigurationModelModule
import com.android.tools.configurations.ConfigurationSettings
import com.android.tools.configurations.ResourceResolverCache
import com.android.utils.NullLogger
import com.google.common.collect.ImmutableList
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project

/**
 * Mostly stub [ConfigurationSettings] essentially used to pass [ConfigurationModelModule] to
 * [Configuration] during creation.
 */
internal class StandaloneConfigurationSettings(
    override val configModule: ConfigurationModelModule,
    override val project: Project,
    private val androidTarget: IAndroidTarget,
) : ConfigurationSettings {

    private val defaultDevices = DefaultDevices(NullLogger.getLogger()).also { it.init() }
    private val vendorDevices = VendorDevices(NullLogger.getLogger()).also { it.init() }
    override val defaultDevice = defaultDevices.getDevice("medium_phone", "Generic")

    override fun selectDevice(device: Device) { }
    override var locale: Locale = Locale.ANY
    override var target: IAndroidTarget? = androidTarget
    override fun getTarget(minVersion: Int): IAndroidTarget = androidTarget
    override val stateVersion: Int = 0 // State does not change
    override val resolverCache: ResourceResolverCache = ResourceResolverCache(this)
    override val module: Module
        get() = throw UnsupportedOperationException("Should not be called in standalone rendering")
    override val localesInProject: ImmutableList<Locale> = ImmutableList.of()
    override val devices: ImmutableList<Device> =
        ImmutableList
            .builder<Device>()
            .addAll(defaultDevices.devices!!.values())
            .addAll(vendorDevices.devices!!.values())
            .build()
    override val projectTarget: IAndroidTarget = androidTarget
    override fun createDeviceForAvd(avd: AvdInfo): Device? = null

    override val highestApiTarget: IAndroidTarget = androidTarget
    override val targets: Array<IAndroidTarget> = emptyArray()
    override fun getDeviceById(id: String): Device? = null
    override val recentDevices: List<Device> = emptyList()
    override val avdDevices: List<Device> = emptyList()
}
