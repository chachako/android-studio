/*
 * Copyright (C) 2020 The Android Open Source Project
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

package com.android.build.gradle.internal.testing.utp.emulatorcontrol

import com.android.build.gradle.internal.SdkComponentsBuildService
import com.android.build.gradle.internal.dsl.EmulatorControl
import com.android.build.gradle.internal.fixtures.FakeGradleProvider
import com.android.build.gradle.internal.fixtures.FakeProviderFactory
import com.android.build.gradle.internal.services.DslServices
import com.android.build.gradle.internal.services.createDslServices
import com.android.build.gradle.internal.testing.utp.createEmulatorControlConfig
import com.android.build.gradle.internal.testing.utp.createEmulatorControlConfig
import com.android.build.gradle.options.IntegerOption
import com.android.build.gradle.options.OptionalBooleanOption
import com.android.build.gradle.options.ProjectOptions
import com.google.common.collect.ImmutableMap
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito

class EmulatorControlConfigTest {
    private lateinit var dslServices: DslServices
    private lateinit var emulatorControl: EmulatorControl

    private val emptyProjectOptions = ProjectOptions(
        ImmutableMap.of(),
        FakeProviderFactory(
            FakeProviderFactory.factory, ImmutableMap.of()
        )
    )

    @Before
    fun setUp() {
        val sdkComponents = Mockito.mock(SdkComponentsBuildService::class.java)
        dslServices = createDslServices(sdkComponents = FakeGradleProvider(sdkComponents))
        emulatorControl = dslServices.newDecoratedInstance(EmulatorControl::class.java, dslServices)
    }

    @Test
    fun disableByDefault() {
        val emulatorControlConfig = createEmulatorControlConfig(emptyProjectOptions, emulatorControl)
        assertThat(emulatorControlConfig.enabled).isFalse()
    }

    @Test
    fun enableByDslWithDefaultSetup() {
        emulatorControl.enable = true
        val emulatorControlConfig = createEmulatorControlConfig(emptyProjectOptions, emulatorControl)
        assertThat(emulatorControlConfig.enabled).isTrue()
        assertThat(emulatorControlConfig.secondsValid).isEqualTo(3600)
        assertThat(emulatorControlConfig.allowedEndpoints).isEmpty()
    }

    @Test
    fun setSecondsValidByDsl() {
        emulatorControl.enable = true
        emulatorControl.secondsValid = 30
        val emulatorControlConfig = createEmulatorControlConfig(emptyProjectOptions, emulatorControl)
        assertThat(emulatorControlConfig.enabled).isTrue()
        assertThat(emulatorControlConfig.secondsValid).isEqualTo(30)
        assertThat(emulatorControlConfig.allowedEndpoints).isEmpty()
    }

    @Test
    fun setAllowEndpointsByDsl() {
        emulatorControl.enable = true
        emulatorControl.allowedEndpoints.add("a")
        val emulatorControlConfig = createEmulatorControlConfig(emptyProjectOptions, emulatorControl)
        assertThat(emulatorControlConfig.secondsValid).isEqualTo(3600)
        assertThat(emulatorControlConfig.allowedEndpoints).contains("a")
        assertThat(emulatorControlConfig.allowedEndpoints).hasSize(1)
    }
}
