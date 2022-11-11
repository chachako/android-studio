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

import com.android.tools.utp.plugins.host.apkinstaller.proto.AndroidApkInstallerConfigProto.AndroidApkInstallerConfig
import com.android.tools.utp.plugins.host.apkinstaller.proto.AndroidApkInstallerConfigProto.InstallableApk.InstallOption
import com.google.testing.platform.api.config.ProtoConfig
import com.google.testing.platform.api.context.Context
import com.google.testing.platform.api.device.DeviceController
import com.google.testing.platform.api.plugin.HostPlugin
import com.google.testing.platform.lib.logging.jvm.getLogger
import com.google.testing.platform.proto.api.core.TestCaseProto
import com.google.testing.platform.proto.api.core.TestResultProto.TestResult
import com.google.testing.platform.proto.api.core.TestSuiteResultProto.TestSuiteResult
import com.google.testing.platform.runtime.android.controller.ext.uninstall
import com.google.testing.platform.runtime.android.device.AndroidDeviceProperties
import java.util.logging.Logger

/**
 * This plugin handles test APKs installation for all instrumented tests.
 * It uninstalls them after test finishes.
 */
class AndroidTestApkInstallerPlugin(private val logger: Logger = getLogger()) : HostPlugin {
    companion object {
        // Prepend -t to install apk marked as testOnly.
        private val SPLIT_APK_INSTALL_OPTIONS = listOf("install-multiple", "-t")
        private val NORMAL_APK_INSTALL_OPTIONS = listOf("install", "-t")

        /**
         * Encapsulates information about minimum API level for test APK installation per the
         * features used in the test APK.
         * If there's no restriction, NONE is used
         * Split APK is supported in API 21 and higher
         */
        private enum class MinFeatureApiLevel(val apiLevel: Int) {
            NONE(Int.MIN_VALUE),
            SPLIT_APK(21)
        }
    }

    lateinit var config: AndroidApkInstallerConfig

    /**
     * Return minimum API level given install options
     * Returns MinApiLevel.NONE if there's no such restriction
     */
    private fun getFeatureMinApiLevel(installOption: InstallOption): MinFeatureApiLevel {
        var minApiLevel = MinFeatureApiLevel.NONE
        if (installOption.installAsSplitApk) {
            minApiLevel = MinFeatureApiLevel.SPLIT_APK
        }
        return minApiLevel
    }

    override fun configure(context: Context) {
        val config = context[Context.CONFIG_KEY] as ProtoConfig
        this.config = AndroidApkInstallerConfig.parseFrom(config.configProto!!.value)
    }

    @Throws(InstantiationError::class)
    override fun beforeAll(deviceController: DeviceController) {
        val deviceApiLevel =
                (deviceController.getDevice().properties as AndroidDeviceProperties)
                        .deviceApiLevel.toInt()

        config.apksToInstallList.forEach { installableApk ->
            // Check if device API level is above the min API level required to install certain
            // features
            val featureMinApi = getFeatureMinApiLevel(installableApk.installOptions)
            if (featureMinApi.apiLevel > deviceApiLevel) {
                throw InstantiationError("Minimum API level for installing $featureMinApi " +
                        "feature is ${featureMinApi.apiLevel} but device " +
                        "${deviceController.getDevice().serial} is API level $deviceApiLevel.")
            }

            if(installableApk.installOptions.installAsSplitApk) {
                deviceController.execute(
                        SPLIT_APK_INSTALL_OPTIONS +
                                installableApk.installOptions.commandLineParameterList +
                                installableApk.apkPathsList)
            } else {
                installableApk.apkPathsList.forEach { singleApk ->
                    deviceController.execute(
                            NORMAL_APK_INSTALL_OPTIONS +
                                    installableApk.installOptions.commandLineParameterList +
                                    singleApk)
                }
            }
        }
    }

    override fun beforeEach(
            testCase: TestCaseProto.TestCase?,
            deviceController: DeviceController
    ) = Unit

    override fun afterEach(
        testResult: TestResult,
        deviceController: DeviceController,
        cancelled: Boolean
    ): TestResult = testResult

    override fun afterAll(
        testSuiteResult: TestSuiteResult,
        deviceController: DeviceController,
        cancelled: Boolean
    ): TestSuiteResult {
        config.apksToInstallList.forEach { installableApk ->
            if(installableApk.uninstallAfterTest) {
                installableApk.apksPackageNameList.forEach {
                    val uninstallResult = deviceController.uninstall(it)
                    // Status code 0 signifies success
                    if (uninstallResult.statusCode != 0) {
                        logger.warning("Device ${deviceController.getDevice().serial} " +
                                "failed to uninstall test APK $it.")
                    }
                }
            }
        }
        return testSuiteResult
    }

    override fun canRun(): Boolean = true
}
