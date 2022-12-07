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
import com.android.tools.utp.plugins.host.apkinstaller.proto.AndroidApkInstallerConfigProto.InstallableApk
import com.android.tools.utp.plugins.host.apkinstaller.proto.AndroidApkInstallerConfigProto.InstallableApk.InstallOption
import com.google.testing.platform.api.config.parseConfig
import com.google.testing.platform.api.config.setup
import com.google.testing.platform.api.context.Context
import com.google.testing.platform.api.context.config
import com.google.testing.platform.api.device.DeviceController
import com.google.testing.platform.api.error.ErrorSummary
import com.google.testing.platform.api.plugin.HostPlugin
import com.google.testing.platform.core.error.ErrorType
import com.google.testing.platform.core.error.UtpException
import com.google.testing.platform.lib.logging.jvm.getLogger
import com.google.testing.platform.proto.api.core.TestArtifactProto.Artifact
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

        private val BASE_INSTALL_CMD = listOf("install", "-t")
        private val SPLIT_APK_INSTALL_CMD = listOf("install-multiple", "-t")
        private val installErrorSummary = object : ErrorSummary {
            override val errorCode: Int = 2002
            override val errorName: String = "Test APK installation Error"
            override val errorType: Enum<*> = ErrorType.TEST
            override val namespace: String = "AndroidTestApkInstallerPlugin"
        }
    }

    private lateinit var pluginConfig: AndroidApkInstallerConfig
    private lateinit var installables: Set<Artifact>

    /**
     * Returns minimum API level given install options
     * Returns MinApiLevel.NONE if there's no such restriction
     */
    private fun getFeatureMinApiLevel(installOption: InstallOption): MinFeatureApiLevel {
        var minApiLevel = MinFeatureApiLevel.NONE
        if (installOption.installAsSplitApk) {
            minApiLevel = MinFeatureApiLevel.SPLIT_APK
        }
        return minApiLevel
    }

    /**
     * Returns corresponding installation command based on installable APK configuration.
     */
    private fun getInstallCmd(installableApk: InstallableApk, deviceApiLevel: Int): List<String> {
        var installCmd: MutableList<String> = BASE_INSTALL_CMD.toMutableList()
        if (installableApk.installOptions.installAsSplitApk) {
            installCmd = SPLIT_APK_INSTALL_CMD.toMutableList()
        }
        if (installableApk.installOptions.installAsTestService) {
            if (deviceApiLevel >= 23) installCmd.add("-g")
            if (deviceApiLevel >= 30) installCmd.add("--force-queryable")
        }
        return installCmd
    }

    override fun configure(context: Context) {
        val config = context.config
        this.pluginConfig = config.parseConfig() ?: AndroidApkInstallerConfig.getDefaultInstance()
        installables = config.setup.installables
        if (installables.isEmpty()) {
            logger.info("No installables found in test fixture. Nothing to install.")
        }
    }

    /**
     * Installs all test APKs before tests run
     */
    @Throws(UtpException::class)
    override fun beforeAll(deviceController: DeviceController) {
        val deviceApiLevel =
                (deviceController.getDevice().properties as AndroidDeviceProperties)
                        .deviceApiLevel.toInt()
        val deviceSerial = deviceController.getDevice().serial

        installables.forEach { artifact ->
            val apkPath = artifact.sourcePath.path
            logger.info("Installing APK: $apkPath on device $deviceSerial.")
            if (deviceController.execute(BASE_INSTALL_CMD + listOf(apkPath)).statusCode != 0) {
                throw UtpException(
                        installErrorSummary,
                        "Failed to install APK: ${artifact.sourcePath.path} on device " +
                                "$deviceSerial.")
            }
        }

        pluginConfig.apksToInstallList.forEach { installableApk ->
            // Check if device API level is above the min API level required to install certain
            // features
            val featureMinApi = getFeatureMinApiLevel(installableApk.installOptions)
            if (featureMinApi.apiLevel > deviceApiLevel) {
                throw UtpException(
                        installErrorSummary,
                        "Minimum API level for installing $featureMinApi " +
                        "feature is ${featureMinApi.apiLevel} but device " +
                        "$deviceSerial is API level $deviceApiLevel.")
            }

            // If timeout is 0, we treat it as if there's no timeout and set
            // installTimeout to null. 0 is the default value if it's not set.
            // Setting timeout to 0 means that it will immediately timeout without doing anything,
            // probably not something the user intend to do.
            val installTimeout = installableApk
                    .installOptions
                    .installApkTimeout
                    .takeIf { it > 0 }?.toLong()
            val installCmd = getInstallCmd(installableApk, deviceApiLevel)
            logger.info("Installing ${installableApk.apkPathsList} on " +
                    "device $deviceSerial.")
            // Perform sequential install when multiple APK is presented and that they are not
            // split APK
            if (!installableApk.installOptions.installAsSplitApk &&
                    installableApk.apkPathsCount > 1) {
                installableApk.apkPathsList.forEach { apkPath ->
                    if (deviceController.execute(
                                    installCmd +
                                            installableApk.installOptions.commandLineParameterList +
                                            apkPath, installTimeout).statusCode != 0) {
                        throw UtpException(
                                installErrorSummary,
                                "Failed to install APK: $apkPath on device $deviceSerial.")
                    }
                }
            } else {
                if (deviceController.execute(
                                installCmd +
                                        installableApk.installOptions.commandLineParameterList +
                                        installableApk.apkPathsList,
                                installTimeout).statusCode != 0) {
                    throw UtpException(
                            installErrorSummary,
                            "Failed to install APK: ${installableApk.apkPathsList} on device " +
                                    "$deviceSerial.")
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
        pluginConfig.apksToInstallList.forEach { installableApk ->
            if (installableApk.uninstallAfterTest) {
                installableApk.apksPackageNameList.forEach { apkPackage ->
                    logger.info("Uninstalling $apkPackage for " +
                            "device ${deviceController.getDevice().serial}.")
                    val uninstallResult = deviceController.uninstall(apkPackage)
                    // Status code 0 signifies success
                    if (uninstallResult.statusCode != 0) {
                        logger.warning("Device ${deviceController.getDevice().serial} " +
                                "failed to uninstall test APK $apkPackage.")
                    }
                }
            }
        }
        return testSuiteResult
    }

    override fun canRun(): Boolean = true
}
