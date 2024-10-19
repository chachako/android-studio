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

package com.android.build.gradle.internal.testing.utp

import com.android.tools.utp.plugins.deviceprovider.profile.proto.DeviceProviderProfileProto
import com.android.tools.utp.plugins.result.listener.gradle.proto.GradleAndroidTestResultListenerProto.TestResultEvent
import com.google.common.truth.Truth.assertThat
import com.google.testing.platform.proto.api.core.TestSuiteResultProto
import com.google.wireless.android.sdk.stats.DeviceTestSpanProfile
import com.google.wireless.android.sdk.stats.TestRun

import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.time.Clock
import java.time.Instant

class UtpRunProfileTest {
    @get:Rule
    var temporaryFolder = TemporaryFolder()

    private val mockClock: Clock = mock()

    private val testRunResult: UtpTestRunResult = mock()

    private val resultsProto: TestSuiteResultProto.TestSuiteResult = mock()

    @Before
    fun setup() {
        whenever(testRunResult.resultsProto).thenReturn(resultsProto)
    }

    private fun setupClock(vararg time: Long) {
        val iterator = time.toList().iterator()
        whenever(mockClock.instant()).thenAnswer {
            Instant.ofEpochMilli(iterator.next())
        }
    }

    @Test
    fun testToProtoUsingDeviceProviderProto() {

        val deviceProfileFolder = temporaryFolder.newFolder()
        val deviceProfileFile = deviceProfileFolder.resolve("profiling/device1_profile.pb").also {
            it.parentFile.mkdirs()
        }


        deviceProfileFile.outputStream().use { writer ->
            DeviceProviderProfileProto.DeviceProviderProfile.newBuilder().apply {
                deviceProvision = DeviceProviderProfileProto.TimeSpan.newBuilder().apply {
                    spanBeginMs = 450L
                    spanEndMs = 550L
                }.build()
                deviceRelease = DeviceProviderProfileProto.TimeSpan.newBuilder().apply {
                    spanBeginMs = 950L
                    spanEndMs = 1050L
                }.build()
            }.build().writeTo(writer)
        }

        setupClock(
            100L,
            250L,
            350L,
            650L,
            850L
        )

        val profileManager = UtpRunProfileManager(mockClock).also {
            it.recordDeviceLockStart()
            it.recordDeviceLockEnd()
        }

        val profileProto = profileManager.createTestRunProfile (
            deviceProfileFolder,
            DeviceTestSpanProfile.DeviceType.VIRTUAL_MANAGED_DEVICE,
            "device1"
        ).apply {
            recordSetupStart()

            listener().also {
                it.onTestResultEvent(
                    TestResultEvent.newBuilder().apply {
                        testSuiteStarted = TestResultEvent.TestSuiteStarted.newBuilder().build()
                    }.build()
                )
                it.onTestResultEvent(
                    TestResultEvent.newBuilder().apply {
                        testSuiteFinished = TestResultEvent.TestSuiteFinished.newBuilder().build()
                    }.build()
                )
            }

            recordUtpRunFinished(testRunResult)
        }.toDeviceTestSpanProfileProto()

        assertThat(profileProto.deviceType).isEqualTo(
            DeviceTestSpanProfile.DeviceType.VIRTUAL_MANAGED_DEVICE
        )
        assertThat(profileProto.testKind).isEqualTo(
            TestRun.TestKind.INSTRUMENTATION_TEST
        )
        assertThat(profileProto.processType).isEqualTo(
            DeviceTestSpanProfile.ProcessType.EXTERNAL_UTP_PROCESS
        )

        assertThat(profileProto.deviceLockWaitStartTimeMs).isEqualTo(100L)
        assertThat(profileProto.deviceLockWaitDurationMs).isEqualTo(150L)

        // We do not own setup in UTP so we need to approximate from when we started the UTP process
        // til the device is requested.
        assertThat(profileProto.utpSetupStartTimeMs).isEqualTo(350L)
        assertThat(profileProto.utpSetupDurationMs).isEqualTo(100L)

        assertThat(profileProto.utpProvideDeviceStartTimeMs).isEqualTo(450L)
        assertThat(profileProto.utpProvideDeviceDurationMs).isEqualTo(100L)

        // We don't own the test setup in UTP so we need to approximate from when the device ended
        // to when the test is started.
        assertThat(profileProto.utpTestSetupStartTimeMs).isEqualTo(550L)
        assertThat(profileProto.utpTestSetupDurationMs).isEqualTo(100L)

        assertThat(profileProto.utpTestRunStartTimeMs).isEqualTo(650L)
        assertThat(profileProto.utpTestRunDurationMs).isEqualTo(200L)

        assertThat(profileProto.utpTearDownStartTimeMs).isEqualTo(950L)
        assertThat(profileProto.utpTearDownDurationMs).isEqualTo(100L)

        assertThat(profileProto.progressResult).isEqualTo(
            DeviceTestSpanProfile.TestProgressResult.TESTS_COMPLETED
        )
    }
}
