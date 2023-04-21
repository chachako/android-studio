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

package com.android.tools.utp.plugins.host.emulatorcontrol

import com.android.testutils.MockitoKt.any
import com.android.tools.utp.plugins.host.emulatorcontrol.proto.EmulatorControlPluginProto
import com.google.common.truth.Truth.assertThat
import com.google.protobuf.Any
import com.google.testing.platform.api.context.Context
import com.google.testing.platform.api.device.Device
import com.google.testing.platform.api.device.DeviceController
import com.google.testing.platform.api.plugins.PluginConfigImpl
import com.google.testing.platform.proto.api.config.AndroidSdkProto
import com.google.testing.platform.proto.api.config.EnvironmentProto.Environment
import com.google.testing.platform.proto.api.config.SetupProto
import com.google.testing.platform.proto.api.core.TestCaseProto.TestCase
import com.google.testing.platform.proto.api.core.TestResultProto.TestResult
import com.google.testing.platform.proto.api.core.TestStatusProto.TestStatus
import com.google.testing.platform.proto.api.core.TestSuiteResultProto.TestSuiteResult
import com.google.testing.platform.runtime.android.device.AndroidDevice
import com.google.testing.platform.runtime.android.device.AndroidDeviceProperties
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import org.mockito.ArgumentMatchers.isNull
import org.mockito.Mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoInteractions
import org.mockito.Mockito.`when`
import org.mockito.MockitoAnnotations.openMocks
import java.io.File
import java.util.UUID
import java.util.concurrent.TimeUnit

/**
 * Unit tests for [EmulatorAccess]
 */
@RunWith(JUnit4::class)
class EmulatorControlPluginTest {

    private val appPackage = "dummyAppPackage"

    @get:Rule
    var tempFolder = TemporaryFolder()

    @Mock
    private lateinit var mockDeviceController: DeviceController

    @Mock
    private lateinit var mockContext: Context

    @Mock
    private lateinit var mockTestCase: TestCase

    private lateinit var emulatorControlPlugin: EmulatorControlPlugin
    private lateinit var emulatorControlPluginConfig: EmulatorControlPluginProto.EmulatorControlPlugin
    private lateinit var config: PluginConfigImpl
    private lateinit var testResult: TestResult
    private lateinit var failingTestResult: TestResult
    private lateinit var testSuiteResult: TestSuiteResult
    private val grpcPort = 8554

    private fun buildemulatorControlConfig(
        configGrpcPort: Int = grpcPort,
        jwtToken: String = "abc",
        clientPrivateKeyFilePath: String = "",
        clientCaFilePath: String = "",
        trustedCa: String = "",
        tlsPrefix: String = "/data/data/abc",
        jwkPath: String = ""
    ): EmulatorControlPluginProto.EmulatorControlPlugin {
        return EmulatorControlPluginProto.EmulatorControlPlugin.newBuilder().apply {
            emulatorGrpcPort = configGrpcPort
            token = jwtToken
            emulatorClientPrivateKeyFilePath = clientPrivateKeyFilePath
            emulatorClientCaFilePath = clientCaFilePath
            trustedCollectionRootPath = trustedCa
            tlsCfgPrefix = tlsPrefix
            jwkFile = jwkPath
        }.build()
    }

    private fun buildConfig(emulatorControlPluginConfig: EmulatorControlPluginProto.EmulatorControlPlugin): PluginConfigImpl {
        return PluginConfigImpl(
            environmentProto = Environment.newBuilder().apply {
                outputDirBuilder.path = tempFolder.root.path
            }.build(),
            testSetupProto = SetupProto.TestSetup.getDefaultInstance(),
            androidSdkProto = AndroidSdkProto.AndroidSdk.getDefaultInstance(),
            configProto = Any.pack(emulatorControlPluginConfig)
        )
    }

    @Before
    fun setup() {
        openMocks(this)
        `when`(mockDeviceController.getDevice()).thenReturn(
            AndroidDevice(
                serial = "emulator-5554",
                type = Device.DeviceType.VIRTUAL,
                port = 5555,
                emulatorPort = 5554,
                serverPort = 5037,
                properties = AndroidDeviceProperties()
            )
        )
        emulatorControlPluginConfig = buildemulatorControlConfig(0)
        config = buildConfig(emulatorControlPluginConfig)
        `when`(mockContext[Context.CONFIG_KEY]).thenReturn(config)
        testResult = TestResult.getDefaultInstance()
        failingTestResult = TestResult.newBuilder().setTestStatus(
            TestStatus.FAILED
        ).build()
        testSuiteResult = TestSuiteResult.getDefaultInstance()
        emulatorControlPlugin = EmulatorControlPlugin()
    }

    @Test
    fun configure_ok() {
        emulatorControlPlugin.configure(mockContext)
        assertThat(emulatorControlPlugin.emulatorControlPluginConfig).isEqualTo(
            emulatorControlPluginConfig
        )
    }

    @Test
    fun beforeAll_pushes_no_secrets_when_not_present() {
        emulatorControlPlugin.configure(mockContext)
        emulatorControlPlugin.beforeAll(mockDeviceController)
        verifyNoInteractions(mockDeviceController)
    }

    @Test
    fun beforeAll_pushes_secrets_when_present() {
        val fakeClientCa = tempFolder.root.path + File.separator + UUID.randomUUID().toString()
        File(fakeClientCa).bufferedWriter().use { out -> out.write("fake-cert") }
        val localEmulatorControlPluginConfig =
            buildemulatorControlConfig(clientCaFilePath = fakeClientCa)
        val localConfig = buildConfig(localEmulatorControlPluginConfig)
        `when`(mockContext[Context.CONFIG_KEY]).thenReturn(localConfig)
        emulatorControlPlugin.configure(mockContext)
        emulatorControlPlugin.beforeAll(mockDeviceController)

        // Only one secret was present, so only one secret was pushed.
        verify(mockDeviceController).push(any()).times(1)
    }

    @Test
    fun beforeEach_touches_jwk() {
        val fakeJwk = tempFolder.root.path + File.separator + UUID.randomUUID().toString()
        File(fakeJwk).bufferedWriter().use { out -> out.write("fake-cert") }
        val modified = File(fakeJwk).lastModified()

        // Wait a bit so we are sure we have a different timestamp.
        TimeUnit.MILLISECONDS.sleep(10)
        val localEmulatorControlPluginConfig = buildemulatorControlConfig(
            jwkPath = fakeJwk
        )
        val localConfig = buildConfig(localEmulatorControlPluginConfig)
        `when`(mockContext[Context.CONFIG_KEY]).thenReturn(localConfig)
        emulatorControlPlugin.configure(mockContext)
        emulatorControlPlugin.beforeEach(mockTestCase, mockDeviceController)

        // The fakejwk's timestamp should have been updated.
        assertThat(File(fakeJwk).lastModified()).isGreaterThan(modified)
    }

    @Test
    fun afterAll_cleans_up_jwk() {
        val fakeJwk = tempFolder.root.path + File.separator + UUID.randomUUID().toString()
        File(fakeJwk).bufferedWriter().use { out -> out.write("fake-cert") }

        assert(File(fakeJwk).exists())
        val localEmulatorControlPluginConfig = buildemulatorControlConfig(
            jwkPath = fakeJwk
        )
        val localConfig = buildConfig(localEmulatorControlPluginConfig)
        `when`(mockContext[Context.CONFIG_KEY]).thenReturn(localConfig)
        emulatorControlPlugin.configure(mockContext)
        emulatorControlPlugin.afterAll(testSuiteResult, mockDeviceController)

        // The fake jwk should have been deleted from disk.
        assertThat(File(fakeJwk).exists()).isFalse()
    }

    @Test
    fun afterAll_cleans_up_pushed_files() {
        val fakeJwk = tempFolder.root.path + File.separator + UUID.randomUUID().toString()
        File(fakeJwk).bufferedWriter().use { out -> out.write("fake-cert") }

        assert(File(fakeJwk).exists())
        val localEmulatorControlPluginConfig = buildemulatorControlConfig(
            jwkPath = fakeJwk
        )
        val localConfig = buildConfig(localEmulatorControlPluginConfig)
        `when`(mockContext[Context.CONFIG_KEY]).thenReturn(localConfig)
        emulatorControlPlugin.configure(mockContext)
        emulatorControlPlugin.afterAll(testSuiteResult, mockDeviceController)

        // The fake certificates should have been deleted from disk.
        // i.e. listOf<String>("shell", "rm", "/data/data/abc.cer", "/data/data/abc.cer",
        //         "/data/data/abc.ca")
        verify(mockDeviceController).execute(any(), isNull())
    }
}
