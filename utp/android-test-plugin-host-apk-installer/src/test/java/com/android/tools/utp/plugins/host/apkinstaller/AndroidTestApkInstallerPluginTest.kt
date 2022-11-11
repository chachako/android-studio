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

import com.android.testutils.MockitoKt
import com.android.tools.utp.plugins.host.apkinstaller.proto.AndroidApkInstallerConfigProto.AndroidApkInstallerConfig
import com.android.tools.utp.plugins.host.apkinstaller.proto.AndroidApkInstallerConfigProto.InstallableApk
import com.android.tools.utp.plugins.host.apkinstaller.proto.AndroidApkInstallerConfigProto.InstallableApk.InstallOption
import com.google.common.truth.Truth
import com.google.protobuf.Any
import com.google.testing.platform.api.config.ProtoConfig
import com.google.testing.platform.api.context.Context
import com.google.testing.platform.api.device.CommandResult
import com.google.testing.platform.api.device.DeviceController
import com.google.testing.platform.proto.api.core.ExtensionProto
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
import org.mockito.Mockito
import org.mockito.Mockito.anyList
import org.mockito.Mockito.anyLong
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.mockito.junit.MockitoJUnit
import org.mockito.junit.MockitoRule
import org.mockito.quality.Strictness
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

    private val testApkPaths = listOf("base.apk", "feature.apk")
    private val additionalInstallOptions = listOf("-unit", "-test")
    private val apkPackageNames = listOf("com.test.base", "com.test.test")
    private val mockDeviceApiLevel = "21"
    private val mockDeviceSerial = "mock-4445"

    @Before
    fun setup() {
        androidTestApkInstallerPlugin = AndroidTestApkInstallerPlugin()
        mockDeviceProperties =
                AndroidDeviceProperties(mapOf(DEVICE_API_LEVEL to mockDeviceApiLevel))
        `when`(mockDeviceController.getDevice().properties).thenReturn(mockDeviceProperties)
    }

    private fun createPlugin(config: AndroidApkInstallerConfig)
            : AndroidTestApkInstallerPlugin {
        val packedConfig = Any.pack(config)
        val protoConfig = object : ProtoConfig {
            override val configProto: Any
                get() = packedConfig
            override val configResource: ExtensionProto.ConfigResource?
                get() = null
        }
        val context = MockitoKt.mock<Context>()
        Mockito.`when`(context[Context.CONFIG_KEY]).thenReturn(protoConfig)
        return AndroidTestApkInstallerPlugin(mockLogger).apply {
            configure(context)
        }
    }

    @Test
    fun emptyAPKInstallListTest() {
        createPlugin(AndroidApkInstallerConfig.newBuilder().apply {
            clearApksToInstall()
        }.build()).apply {
            beforeAll(mockDeviceController)
            beforeEach(TestCase.getDefaultInstance(), mockDeviceController)
            afterEach(TestResult.getDefaultInstance(), mockDeviceController, false)
        }
        verify(mockDeviceController, never()).execute(anyList(), anyLong())
    }

    @Test
    fun splitAPKWithAdditionalInstallOptionTest() {
        createPlugin(AndroidApkInstallerConfig.newBuilder().apply {
            addApksToInstall(
                    InstallableApk.newBuilder().apply {
                        addAllApkPaths(testApkPaths)
                        installOptions = InstallOption.newBuilder().apply {
                            addAllCommandLineParameter(additionalInstallOptions)
                            installAsSplitApk = true
                        }.build()
                    }.build()
            )
        }.build()).apply {
            beforeAll(mockDeviceController)
        }
        verify(mockDeviceController).execute(
                listOf("install-multiple", "-t") +
                        additionalInstallOptions +
                        testApkPaths)
    }

    @Test
    fun nonSplitAPKTest() {
        createPlugin(AndroidApkInstallerConfig.newBuilder().apply {
            addApksToInstall(
                    InstallableApk.newBuilder().apply {
                        addAllApkPaths(testApkPaths)
                        installOptions = InstallOption.newBuilder().apply {
                            addAllCommandLineParameter(additionalInstallOptions)
                            installAsSplitApk = false
                        }.build()
                        uninstallAfterTest = false
                    }.build()
            )
        }.build()).apply {
            beforeAll(mockDeviceController)
        }
        testApkPaths.forEach {
            verify(mockDeviceController).execute(
                    listOf("install", "-t") +
                            additionalInstallOptions +
                            it)
        }
    }

    @Test
    fun minApiLevelHigherThanDeviceApi() {
        `when`(mockDeviceController.getDevice().serial).thenReturn(mockDeviceSerial)
        // Minimum API that supports split APK is 21
        val tempDeviceProperties = AndroidDeviceProperties(mapOf(DEVICE_API_LEVEL to "20"))
        `when`(mockDeviceController.getDevice().properties).thenReturn(tempDeviceProperties)

        val exception = assertThrows(InstantiationError::class.java) {
            createPlugin(AndroidApkInstallerConfig.newBuilder().apply {
                addApksToInstall(
                        InstallableApk.newBuilder().apply {
                            addAllApkPaths(testApkPaths)
                            installOptions = InstallOption.newBuilder().apply {
                                addAllCommandLineParameter(additionalInstallOptions)
                                installAsSplitApk = true
                            }.build()
                            uninstallAfterTest = false
                        }.build()
                )
            }.build()).apply {
                beforeAll(mockDeviceController)
            }
        }
        assertEquals("Minimum API level for installing SPLIT_APK " +
                "feature is 21 but device $mockDeviceSerial is API level 20.", exception.message)
        verify(mockDeviceController, never()).execute(anyList(), anyLong())
    }

    @Test
    fun uninstallSuccessTest() {
        apkPackageNames.forEach {
            `when`(mockDeviceController.uninstall(it)).thenReturn(CommandResult(0, listOf("test")))
        }
        createPlugin(AndroidApkInstallerConfig.newBuilder().apply {
            addApksToInstall(
                    InstallableApk.newBuilder().apply {
                        uninstallAfterTest = true
                        addAllApksPackageName(apkPackageNames)
                    }.build()
            )
        }.build()).apply {
            afterAll(TestSuiteResultProto.TestSuiteResult.getDefaultInstance(),
                    mockDeviceController)
        }
        apkPackageNames.forEach {
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
            addApksToInstall(
                    InstallableApk.newBuilder().apply {
                        uninstallAfterTest = true
                        addAllApksPackageName(apkPackageNames)
                    }.build()
            )
        }.build()).apply {
            afterAll(TestSuiteResultProto.TestSuiteResult.getDefaultInstance(),
                    mockDeviceController)
        }
        apkPackageNames.forEach {
            verify(mockLogger).warning("Device $mockDeviceSerial " +
                    "failed to uninstall test APK $it.")
        }
    }

    @Test
    fun noUninstallTest() {
        createPlugin(AndroidApkInstallerConfig.newBuilder().apply {
            addApksToInstall(
                    InstallableApk.newBuilder().apply {
                        uninstallAfterTest = false
                        addAllApksPackageName(apkPackageNames)
                    }.build()
            )
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
