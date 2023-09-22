/*
 * Copyright (C) 2022 The Android Open Source Project
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

package com.android.tools.utp.plugins.host.apkinstaller

import com.android.testutils.MockitoKt.any
import com.android.testutils.MockitoKt.eq
import com.android.tools.utp.plugins.host.apkinstaller.proto.AndroidApkInstallerConfigProto.AndroidApkInstallerConfig
import com.google.common.truth.Truth
import com.google.protobuf.Any
import com.google.testing.platform.api.config.ConfigBase
import com.google.testing.platform.api.context.Context
import com.google.testing.platform.api.device.CommandResult
import com.google.testing.platform.api.device.DeviceController
import com.google.testing.platform.api.error.ErrorSummary
import com.google.testing.platform.core.error.ErrorType
import com.google.testing.platform.core.error.UtpException
import com.google.testing.platform.proto.api.config.AndroidSdkProto
import com.google.testing.platform.proto.api.config.FixtureProto.TestFixture
import com.google.testing.platform.proto.api.config.FixtureProto.TestFixtureId
import com.google.testing.platform.proto.api.config.RunnerConfigProto.RunnerConfig
import com.google.testing.platform.proto.api.config.SetupProto
import com.google.testing.platform.proto.api.core.TestCaseProto.TestCase
import com.google.testing.platform.proto.api.core.TestResultProto.TestResult
import com.google.testing.platform.proto.api.core.TestSuiteResultProto
import com.google.testing.platform.runtime.android.controller.ext.uninstall
import com.google.testing.platform.runtime.android.device.AndroidDeviceProperties
import com.google.testing.platform.runtime.android.device.AndroidDeviceProperties.Companion.DEVICE_API_LEVEL
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import org.mockito.Answers
import org.mockito.Mock
import org.mockito.Mockito.anyList
import org.mockito.Mockito.anyLong
import org.mockito.Mockito.inOrder
import org.mockito.Mockito.never
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoMoreInteractions
import org.mockito.Mockito.`when`
import org.mockito.junit.MockitoJUnit
import org.mockito.junit.MockitoRule
import org.mockito.quality.Strictness
import java.time.Duration
import java.util.logging.Logger

/**
 * Unit tests for [AndroidTestApkInstallerPlugin].
 */
@RunWith(JUnit4::class)
class AndroidTestApkInstallerPluginTest {

    @get:Rule
    var mockitoJUnitRule: MockitoRule =
            MockitoJUnit.rule().strictness(Strictness.STRICT_STUBS)

    @Mock(answer = Answers.RETURNS_DEEP_STUBS)
    private lateinit var mockDeviceController: DeviceController

    @Mock
    private lateinit var mockLogger: Logger
    private lateinit var mockDeviceProperties: AndroidDeviceProperties
    private lateinit var androidTestApkInstallerPlugin: AndroidTestApkInstallerPlugin

    companion object {

        private val createTestFixture: (TestFixtureId, List<String>?) -> TestFixture = {
            fixtureId, installablePath ->
            TestFixture.newBuilder()
                .apply {
                    testFixtureId = fixtureId
                    setup = createTestSetup(installablePath)
                    environmentBuilder.apply {
                        androidEnvironmentBuilder.apply {
                            androidSdk = AndroidSdkProto.AndroidSdk.newBuilder().build()
                        }
                    }
                }
                .build()
        }

        private val createTestSetup: (List<String>?) -> SetupProto.TestSetup = { installablePath ->
            SetupProto.TestSetup.newBuilder()
                .apply {
                    installablePath?.forEach {
                        addInstallableBuilder().sourcePathBuilder.path = it
                    }
                }
                .build()
        }

        private val createTestFixtureId: (String) -> TestFixtureId = { fixtureId ->
            TestFixtureId.newBuilder().apply { id = fixtureId }.build()
        }

        private val createPluginConfig: (AndroidApkInstallerConfig?, List<String>?) -> ConfigBase =
                { pluginConfig, installablePath ->
                val fixture1 = createTestFixture(fixtureId1, installablePath)
                val runnerConfig = RunnerConfig.newBuilder().apply { addTestFixture(fixture1) }.build()
                ConfigBase(
                    environmentProto = runnerConfig.testFixtureList.first().environment,
                    testSetupProto = runnerConfig.testFixtureList[0].setup,
                    androidSdkProto = runnerConfig.testFixtureList.first().environment.androidEnvironment.androidSdk,
                    configProto = Any.pack(pluginConfig ?: AndroidApkInstallerConfig.getDefaultInstance())
            )
        }

        private val fixtureId1 = createTestFixtureId("1")
        private val testApkPaths = listOf("base.apk", "feature.apk")
        private val additionalInstallOptions = listOf("-unit", "-test")
        private val apkPackageNames = listOf("com.test.base", "com.test.test")
        private val mockDeviceApiLevel = "21"
        private val mockDeviceSerial = "mock-4445"
        private val installTimeout = 1
        private val testArtifactsPath = listOf("/data/test.apk", "/data/app.apk")
        private val mockUserId = "1"
        private val installErrorSummary = object : ErrorSummary {
            override val errorCode: Int = 2002
            override val errorName: String = "Test APK installation Error"
            override val errorType: Enum<*> = ErrorType.TEST
            override val namespace: String = "AndroidTestApkInstallerPlugin"
        }
    }

    private fun createPlugin(
            config: AndroidApkInstallerConfig,
            installablePath: List<String>? = null): AndroidTestApkInstallerPlugin {
        return AndroidTestApkInstallerPlugin(mockLogger).apply {
            configure(object : Context {
                override fun get(key: String) =
                    when (key) {
                        Context.CONFIG_KEY -> createPluginConfig(config, installablePath)
                        else -> null
                    }
            })
        }
    }

    @Before
    fun setup() {
        androidTestApkInstallerPlugin = AndroidTestApkInstallerPlugin()
        mockDeviceProperties =
                AndroidDeviceProperties(mapOf(DEVICE_API_LEVEL to mockDeviceApiLevel))
        `when`(mockDeviceController.getDevice().properties).thenReturn(mockDeviceProperties)
    }

    @Test
    fun configureWithNoInstallable() {
        createPlugin(AndroidApkInstallerConfig.getDefaultInstance())
        verify(mockLogger).info("No installables found in test fixture. Nothing to install.")
    }

    @Test
    fun emptyAPKInstallListTest() {
        createPlugin(AndroidApkInstallerConfig.newBuilder().apply {
            clearApksToInstall()
        }.build(), null).apply {
            beforeAll(mockDeviceController)
            beforeEach(TestCase.getDefaultInstance(), mockDeviceController)
            afterEach(TestResult.getDefaultInstance(), mockDeviceController, false)
        }
        verify(mockLogger).info("No installables found in test fixture. Nothing to install.")
        verify(mockDeviceController, never()).execute(anyList(), any(Duration::class.java))
        verify(mockDeviceController, times(3)).getDevice()
    }

    @Test
    fun splitAPKWithAdditionalInstallOptionTestWithInstallable() {
        `when`(mockDeviceController.getDevice().serial).thenReturn(mockDeviceSerial)
        `when`(mockDeviceController.execute(anyList(), eq(null))).thenReturn(CommandResult(0, listOf()))
        createPlugin(AndroidApkInstallerConfig.newBuilder().apply {
            addApksToInstallBuilder().apply {
                addAllApkPaths(testApkPaths)
                installOptionsBuilder.apply {
                    addAllCommandLineParameter(additionalInstallOptions)
                    installAsSplitApk = true
                }.build()
            }.build()
        }.build(), testArtifactsPath).apply {
            beforeAll(mockDeviceController)
        }
        val inOrder = inOrder(mockDeviceController, mockLogger)
        inOrder.verify(mockDeviceController, times(4)).getDevice()
        testArtifactsPath.forEach {
            inOrder.verify(mockLogger).info("Installing APK: $it on device $mockDeviceSerial.")
            inOrder.verify(mockDeviceController).execute(listOf("install", "-t", it))
        }
        inOrder.verify(mockDeviceController).execute(
                eq(listOf("install-multiple", "-t") +
                        additionalInstallOptions +
                        testApkPaths), eq(null))
        verify(mockLogger).info("Installing $testApkPaths on device $mockDeviceSerial.")
    }

    @Test
    fun splitAPKInstallFail() {
        `when`(mockDeviceController.getDevice().serial).thenReturn(mockDeviceSerial)
        `when`(mockDeviceController.execute(anyList(), eq(null))).thenReturn(CommandResult(1, listOf()))
        val exception = assertThrows(UtpException::class.java) {
            createPlugin(AndroidApkInstallerConfig.newBuilder().apply {
                addApksToInstallBuilder().apply {
                    addAllApkPaths(testApkPaths)
                    installOptionsBuilder.apply {
                        addAllCommandLineParameter(additionalInstallOptions)
                        installAsSplitApk = true
                    }.build()
                }.build()
            }.build()).apply {
                beforeAll(mockDeviceController)
            }
        }
        val inOrder = inOrder(mockDeviceController, mockLogger)
        inOrder.verify(mockDeviceController, times(4)).getDevice()
        inOrder.verify(mockDeviceController).execute(
                eq(listOf("install-multiple", "-t") +
                        additionalInstallOptions +
                        testApkPaths), eq(null))
        assertEquals(UtpException(installErrorSummary,
                "Failed to install APK: $testApkPaths on device $mockDeviceSerial.").message,
                exception.message)
    }

    @Test
    fun installTestServiceApi23() {
        `when`(mockDeviceController.getDevice().serial).thenReturn(mockDeviceSerial)
        `when`(mockDeviceController.execute(anyList(), eq(null))).thenReturn(CommandResult(0, listOf()))
        mockDeviceProperties =
                AndroidDeviceProperties(mapOf(DEVICE_API_LEVEL to "23"))
        `when`(mockDeviceController.getDevice().properties).thenReturn(mockDeviceProperties)
        createPlugin(AndroidApkInstallerConfig.newBuilder().apply {
            addApksToInstallBuilder().apply {
                addAllApkPaths(testApkPaths)
                installOptionsBuilder.apply {
                    addAllCommandLineParameter(additionalInstallOptions)
                    installAsTestService = true
                }.build()
            }.build()
        }.build()).apply {
            beforeAll(mockDeviceController)
        }

        verify(mockLogger).info("Installing $testApkPaths on device $mockDeviceSerial.")
        verify(mockDeviceController, times(5)).getDevice()
        testApkPaths.forEach {
            verify(mockDeviceController).execute(
                    listOf("install", "-t", "-g") +
                            additionalInstallOptions +
                            it, null)
        }
    }

    @Test
    fun installTestServiceApi24() {
        `when`(mockDeviceController.execute(anyList(), eq(null))).thenReturn(CommandResult(0, listOf()))
        `when`(mockDeviceController.execute(eq(listOf("shell", "am", "get-current-user")), eq(null)))
                .thenReturn(CommandResult(0, listOf(mockUserId)))
        `when`(mockDeviceController.getDevice().serial).thenReturn(mockDeviceSerial)
        mockDeviceProperties =
                AndroidDeviceProperties(mapOf(DEVICE_API_LEVEL to "24"))
        `when`(mockDeviceController.getDevice().properties).thenReturn(mockDeviceProperties)
        createPlugin(AndroidApkInstallerConfig.newBuilder().apply {
            addApksToInstallBuilder().apply {
                addAllApkPaths(testApkPaths)
                installOptionsBuilder.apply {
                    addAllCommandLineParameter(additionalInstallOptions)
                    installAsTestService = true
                }.build()
            }.build()
        }.build()).apply {
            beforeAll(mockDeviceController)
        }

        verify(mockLogger).info("Installing $testApkPaths on device $mockDeviceSerial.")
        verify(mockDeviceController, times(5)).getDevice()
        testApkPaths.forEach {
            verify(mockDeviceController).execute(
                    listOf("install", "-t", "-g", "--user", mockUserId) +
                            additionalInstallOptions +
                            it, null)
        }
    }

    @Test
    fun installTestServiceApi30() {
        `when`(mockDeviceController.execute(anyList(), eq(null))).thenReturn(CommandResult(0, listOf()))
        `when`(mockDeviceController.execute(eq(listOf("shell", "am", "get-current-user")), eq(null)))
                .thenReturn(CommandResult(0, listOf(mockUserId)))
        `when`(mockDeviceController.getDevice().serial).thenReturn(mockDeviceSerial)
        mockDeviceProperties =
                AndroidDeviceProperties(mapOf(DEVICE_API_LEVEL to "30"))
        `when`(mockDeviceController.getDevice().properties).thenReturn(mockDeviceProperties)
        createPlugin(AndroidApkInstallerConfig.newBuilder().apply {
            addApksToInstallBuilder().apply {
                addAllApkPaths(testApkPaths)
                installOptionsBuilder.apply {
                    addAllCommandLineParameter(additionalInstallOptions)
                    installAsTestService = true
                }.build()
            }.build()
        }.build()).apply {
            beforeAll(mockDeviceController)
        }

        verify(mockLogger).info("Installing $testApkPaths on device $mockDeviceSerial.")
        verify(mockDeviceController, times(5)).getDevice()
        testApkPaths.forEach {
            verify(mockDeviceController).execute(
                    listOf("install", "-t", "-g", "--force-queryable", "--user", mockUserId) +
                            additionalInstallOptions +
                            it, null)
        }
    }

    @Test
    fun invalidUserIdFormat() {
        `when`(mockDeviceController.execute(anyList(), eq(null))).thenReturn(CommandResult(0, listOf()))
        val invalidUserId = "invalid user id format"
        `when`(mockDeviceController.execute(listOf("shell", "am", "get-current-user")))
                .thenReturn(CommandResult(0, listOf(invalidUserId)))
        mockDeviceProperties =
                AndroidDeviceProperties(mapOf(DEVICE_API_LEVEL to "24"))
        `when`(mockDeviceController.getDevice().properties).thenReturn(mockDeviceProperties)
        createPlugin(AndroidApkInstallerConfig.getDefaultInstance(), testArtifactsPath).apply {
                beforeAll(mockDeviceController)
            }
        verify(mockDeviceController, times(4)).getDevice()
        verify(mockLogger).warning("Unexpected output of command get-current-user, " +
                "expected a user id found the following output: $invalidUserId")
        testArtifactsPath.forEach {
            verify(mockDeviceController).execute(
                    listOf("install", "-t") + it, null)
        }
    }

    @Test
    fun getUserIdExecutionFailed() {
        `when`(mockDeviceController.execute(listOf("shell", "am", "get-current-user")))
                .thenReturn(CommandResult(1, listOf()))
        mockDeviceProperties =
                AndroidDeviceProperties(mapOf(DEVICE_API_LEVEL to "24"))
        `when`(mockDeviceController.getDevice().properties).thenReturn(mockDeviceProperties)
        `when`(mockDeviceController.getDevice().serial).thenReturn(mockDeviceSerial)

        createPlugin(AndroidApkInstallerConfig.getDefaultInstance()).apply {
            beforeAll(mockDeviceController)
        }
        verify(mockDeviceController, times(5)).getDevice()
        verifyNoMoreInteractions(mockDeviceController)
        verify(mockLogger).warning("Failed to execute command to obtain user ID from " +
                "device $mockDeviceSerial")
    }


    @Test
    fun failToInstallInstallable() {
        `when`(mockDeviceController.getDevice().serial).thenReturn(mockDeviceSerial)
        `when`(mockDeviceController.execute(anyList(), eq(null))).thenReturn(CommandResult(1, listOf()))
        val exception = assertThrows(UtpException::class.java) {
            createPlugin(AndroidApkInstallerConfig.getDefaultInstance(), testArtifactsPath).apply {
                beforeAll(mockDeviceController)
            }
        }
        val testArtifactPath1 = testArtifactsPath[0]
        verify(mockDeviceController, times(4)).getDevice()
        verify(mockLogger).info("Installing APK: $testArtifactPath1 on device $mockDeviceSerial.")
        verify(mockDeviceController).execute(listOf("install", "-t", testArtifactPath1))
        assertEquals(UtpException(installErrorSummary,
                "Failed to install APK: $testArtifactPath1 on device " +
                        "$mockDeviceSerial.").message, exception.message)
    }

    @Test
    fun nonSplitAPKTest() {
        `when`(mockDeviceController.getDevice().serial).thenReturn(mockDeviceSerial)
        `when`(mockDeviceController.execute(anyList(), eq(null))).thenReturn(CommandResult(0, listOf()))
        createPlugin(AndroidApkInstallerConfig.newBuilder().apply {
            addApksToInstallBuilder().apply {
                addAllApkPaths(testApkPaths)
                installOptionsBuilder.apply {
                    addAllCommandLineParameter(additionalInstallOptions)
                }.build()
            }.build()
        }.build()).apply {
            beforeAll(mockDeviceController)
        }

        verify(mockLogger).info("Installing $testApkPaths on device $mockDeviceSerial.")
        verify(mockDeviceController, times(4)).getDevice()
        testApkPaths.forEach {
            verify(mockDeviceController).execute(
                    listOf("install", "-t") +
                            additionalInstallOptions +
                            it, null)
        }
    }

    @Test
    fun nonSplitAPKFailTest() {
        `when`(mockDeviceController.getDevice().serial).thenReturn(mockDeviceSerial)
        `when`(mockDeviceController.execute(anyList(), eq(null))).thenReturn(CommandResult(1, listOf()))
        val exception = assertThrows(UtpException::class.java) {
            createPlugin(AndroidApkInstallerConfig.newBuilder().apply {
                addApksToInstallBuilder().apply {
                    addAllApkPaths(testApkPaths)
                    installOptionsBuilder.apply {
                        addAllCommandLineParameter(additionalInstallOptions)
                    }.build()
                }.build()
            }.build()).apply {
                beforeAll(mockDeviceController)
            }
        }

        verify(mockLogger).info("Installing $testApkPaths on device $mockDeviceSerial.")
        verify(mockDeviceController, times(4)).getDevice()
        verify(mockDeviceController).execute(
                listOf("install", "-t") +
                        additionalInstallOptions +
                        testApkPaths.first(), null)
        assertEquals(UtpException(installErrorSummary,
                "Failed to install APK: ${testApkPaths.first()} on " +
                    "device $mockDeviceSerial.").message,
                exception.message)
    }

    @Test
    fun installableWithEmptyPath() {
        val apkPath = "test.apk"
        `when`(mockDeviceController.getDevice().serial).thenReturn(mockDeviceSerial)
        `when`(mockDeviceController.execute(anyList(), eq(null))).thenReturn(CommandResult(0, listOf()))
        createPlugin(AndroidApkInstallerConfig.getDefaultInstance(), listOf(apkPath, "")).apply {
            beforeAll(mockDeviceController)
        }
        verify(mockDeviceController, times(4)).getDevice()
        verify(mockLogger).info("Installing APK: $apkPath on device $mockDeviceSerial.")
        verify(mockDeviceController).execute(
                listOf("install", "-t") + apkPath, null)
        verify(mockLogger).warning("Installable APK has empty path.")
        verifyNoMoreInteractions(mockDeviceController)
    }

    @Test
    fun emptyTestApkPath() {
        `when`(mockDeviceController.getDevice().serial).thenReturn(mockDeviceSerial)
        createPlugin(AndroidApkInstallerConfig.newBuilder().apply {
            addApksToInstallBuilder().defaultInstanceForType
        }.build()).apply {
            beforeAll(mockDeviceController)
        }
        verify(mockDeviceController, times(4)).getDevice()
        verifyNoMoreInteractions(mockDeviceController)
    }

    @Test
    fun nonSplitAPKTestWithTimeout() {
        `when`(mockDeviceController.getDevice().serial).thenReturn(mockDeviceSerial)
        `when`(mockDeviceController.execute(anyList(), any(Duration::class.java))).thenReturn(CommandResult(0, listOf()))
        createPlugin(AndroidApkInstallerConfig.newBuilder().apply {
            addApksToInstallBuilder().apply {
                addAllApkPaths(testApkPaths)
                installOptionsBuilder.apply {
                    addAllCommandLineParameter(additionalInstallOptions)
                    installApkTimeout = installTimeout
                }.build()
            }.build()
        }.build()).apply {
            beforeAll(mockDeviceController)
        }
        verify(mockDeviceController, times(4)).getDevice()
        verify(mockLogger).info("Installing $testApkPaths on device $mockDeviceSerial.")
        testApkPaths.forEach {
            verify(mockDeviceController).execute(
                    listOf("install", "-t") +
                            additionalInstallOptions +
                            it, Duration.ofSeconds(installTimeout.toLong()))
        }
    }

    @Test
    fun minApiLevelHigherThanDeviceApi() {
        `when`(mockDeviceController.getDevice().serial).thenReturn(mockDeviceSerial)
        // Minimum API that supports split APK is 21
        val tempDeviceProperties = AndroidDeviceProperties(mapOf(DEVICE_API_LEVEL to "20"))
        `when`(mockDeviceController.getDevice().properties).thenReturn(tempDeviceProperties)

        val exception = assertThrows(UtpException::class.java) {
            createPlugin(AndroidApkInstallerConfig.newBuilder().apply {
                addApksToInstallBuilder().apply {
                    addAllApkPaths(testApkPaths)
                    installOptionsBuilder.apply {
                        addAllCommandLineParameter(additionalInstallOptions)
                        installAsSplitApk = true
                    }.build()
                }.build()
            }.build()).apply {
                beforeAll(mockDeviceController)
            }
        }
        assertEquals(UtpException(installErrorSummary,
                "Minimum API level for installing SPLIT_APK " +
                "feature is 21 but device $mockDeviceSerial is API level 20.").message,
                exception.message)
        verify(mockDeviceController, never()).execute(anyList(), any(Duration::class.java))
    }

    @Test
    fun uninstallSuccessTest() {
        apkPackageNames.forEach {
            `when`(mockDeviceController.uninstall(it)).thenReturn(CommandResult(0, listOf("test")))
        }
        `when`(mockDeviceController.getDevice().serial).thenReturn(mockDeviceSerial)
        createPlugin(AndroidApkInstallerConfig.newBuilder().apply {
            addApksToInstallBuilder().apply {
                uninstallAfterTest = true
                addAllApksPackageName(apkPackageNames)
            }.build()
        }.build(), testArtifactsPath).apply {
            afterAll(TestSuiteResultProto.TestSuiteResult.getDefaultInstance(),
                    mockDeviceController)
        }
        apkPackageNames.forEach {
            verify(mockLogger).info("Uninstalling $it for " +
                    "device $mockDeviceSerial.")
            verify(mockDeviceController).uninstall(it)
        }
    }
    @Test
    fun uninstallFailTest() {
        `when`(mockDeviceController.getDevice().serial).thenReturn(mockDeviceSerial)
        apkPackageNames.forEach {
            `when`(mockDeviceController.uninstall(it)).thenReturn(CommandResult(1, listOf("test")))
        }
        createPlugin(AndroidApkInstallerConfig.newBuilder().apply {
            addApksToInstallBuilder().apply {
                uninstallAfterTest = true
                addAllApksPackageName(apkPackageNames)
            }.build()
        }.build(), testArtifactsPath).apply {
            afterAll(TestSuiteResultProto.TestSuiteResult.getDefaultInstance(),
                    mockDeviceController)
        }
        apkPackageNames.forEach {
            verify(mockLogger).info("Uninstalling $it for " +
                    "device $mockDeviceSerial.")
            verify(mockLogger).warning("Device $mockDeviceSerial " +
                    "failed to uninstall test APK $it.")
        }
    }

    @Test
    fun noUninstallTest() {
        createPlugin(AndroidApkInstallerConfig.newBuilder().apply {
            addApksToInstallBuilder().apply {
                addAllApksPackageName(apkPackageNames)
            }.build()
        }.build()).apply {
            afterAll(TestSuiteResultProto.TestSuiteResult.getDefaultInstance(),
                    mockDeviceController)
        }

        apkPackageNames.forEach {
            verify(mockDeviceController, never()).uninstall(it)
        }
    }

    @Test
    fun canRun_IsTrue() {
        Truth.assertThat(androidTestApkInstallerPlugin.canRun()).isTrue()
    }
}
