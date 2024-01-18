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
import com.android.tools.utp.plugins.host.apkinstaller.proto.AndroidApkInstallerConfigProto.InstallableApk.InstallOption.ForceCompilation
import com.google.testing.platform.api.config.androidSdk
import com.google.testing.platform.api.config.environment
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
import com.google.testing.platform.lib.process.inject.DaggerSubprocessComponent
import com.google.testing.platform.lib.process.inject.SubprocessComponent
import com.google.testing.platform.lib.process.logger.DefaultSubprocessLogger
import com.google.testing.platform.lib.process.logger.SubprocessLogger
import com.google.testing.platform.proto.api.core.TestArtifactProto.Artifact
import com.google.testing.platform.proto.api.core.TestCaseProto
import com.google.testing.platform.proto.api.core.TestResultProto.TestResult
import com.google.testing.platform.proto.api.core.TestSuiteResultProto.TestSuiteResult
import com.google.testing.platform.runtime.android.controller.ext.uninstall
import com.google.testing.platform.runtime.android.device.AndroidDeviceProperties
import java.time.Duration
import java.util.logging.Logger

/**
 * This plugin handles test APKs installation for all instrumented tests.
 * It uninstalls them after test finishes.
 */
class AndroidTestApkInstallerPlugin(
    private val logger: Logger = getLogger(),
    private val subprocessComponentFactory: (Context) -> SubprocessComponent = {
        val subprocessLoggerFactory = object: SubprocessLogger.Factory {
            override fun create() = DefaultSubprocessLogger(
                it.config.environment.outputDirectory,
                flushEagerly = true)
        }
        DaggerSubprocessComponent.builder()
            .subprocessLoggerFactory(subprocessLoggerFactory)
            .build()
    }
) : HostPlugin {

    companion object {
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

        private val INSTALL_MULTIPLE_CMD = listOf("install-multiple")
        private val installErrorSummary = object : ErrorSummary {
            override val errorCode: Int = 2002
            override val errorName: String = "Test APK installation Error"
            override val errorType: Enum<*> = ErrorType.TEST
            override val namespace: String = "AndroidTestApkInstallerPlugin"
        }

        private val packageNameRegex = "package:\\sname='(\\S*)'.*$".toRegex()
    }

    private lateinit var pluginConfig: AndroidApkInstallerConfig
    private lateinit var installables: Set<Artifact>
    private var userId: String? = null

    private lateinit var subprocessComponent: SubprocessComponent
    private lateinit var aaptPath: String

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
     * Obtain current user ID to correctly install test packages
     */
    private fun getUserId(deviceApiLevel: Int, deviceController: DeviceController,
            deviceSerial: String): String? {
        if (deviceApiLevel < 24) return null
        val result = deviceController.execute(listOf("shell", "am", "get-current-user"))
        if (result.statusCode != 0) {
            logger.warning("Failed to execute command to obtain user ID from " +
                    "device $deviceSerial")
            return null
        }
        val userId = result.output.getOrNull(0)
        if (userId?.toIntOrNull() == null) {
            logger.warning("Unexpected output of command get-current-user, " +
                    "expected a user id found the following output: $userId")
            return null
        }
        return userId
    }

    /**
     * Returns corresponding installation command based on installable APK configuration.
     */
    private fun getInstallCmd(
        installableApk: InstallableApk?,
        deviceApiLevel: Int,
        forceReinstall: Boolean = false): List<String> {
        val installCmd: MutableList<String> = INSTALL_MULTIPLE_CMD.toMutableList()
        if (deviceApiLevel >= 34) installCmd.add("--bypass-low-target-sdk-block")
        // Append -t to install apk marked as testOnly.
        installCmd.add("-t")
        if (installableApk != null && installableApk.installOptions.installAsTestService) {
            if (deviceApiLevel >= 23) installCmd.add("-g")
            if (deviceApiLevel >= 30) installCmd.add("--force-queryable")
        }
        if (userId != null) installCmd.addAll(listOfNotNull("--user", userId))
        if (forceReinstall) installCmd.addAll(listOf("-r", "-d"))
        return installCmd
    }

    /**
     * Returns the package name of the given APK file. Returns null if it fails
     * to resolve package name.
     */
    private fun getPackageNameFromApk(apkPath: String): String? {
        var packageName: String? = null
        subprocessComponent.subprocess().executeAsync(
                args = listOf(aaptPath, "dump", "badging", apkPath),
                stdoutProcessor = {
                    if (packageNameRegex.matches(it)) {
                        packageName = packageNameRegex.find(it)?.groupValues?.get(1)
                    }
                }
        ).waitFor()
        return packageName
    }

    override fun configure(context: Context) {
        val config = context.config
        this.pluginConfig = config.parseConfig() ?: AndroidApkInstallerConfig.getDefaultInstance()
        installables = config.setup.installables

        subprocessComponent = subprocessComponentFactory(context)
        aaptPath = config.androidSdk.aaptPath
    }

    private fun apkInstallErrorMessage(
        apkPath: String, deviceSerial: String, additionalOutput: List<String>
    ): String =
        "Failed to install APK: $apkPath on device $deviceSerial." + if (additionalOutput.isEmpty()) ""
        else "\n$additionalOutput"

    /**
     * Installs all test APKs before tests run
     */
    @Throws(UtpException::class)
    override fun beforeAll(deviceController: DeviceController) {
        val deviceApiLevel =
                (deviceController.getDevice().properties as AndroidDeviceProperties)
                        .deviceApiLevel.toInt()
        val deviceSerial = deviceController.getDevice().serial
        userId = getUserId(deviceApiLevel, deviceController, deviceSerial)

        if (deviceApiLevel >= 33) {
            deviceController.execute(listOf("shell", "settings", "put", "global", "verifier_verify_adb_installs", "0"))
        }

        val installablesInstallCmd = getInstallCmd(null, deviceApiLevel)
        installables.forEach { artifact ->
            val apkPath = artifact.sourcePath.path
            if (apkPath.isEmpty()) {
                logger.warning("Installable APK has empty path.")
                return@forEach
            }
            logger.info("Installing installable artifact: $apkPath on device $deviceSerial.")
            deviceController.execute(installablesInstallCmd + listOf(apkPath)).let { result ->
                if (result.statusCode != 0) {
                    throw UtpException(
                        installErrorSummary,
                        apkInstallErrorMessage(
                            artifact.sourcePath.path,
                            deviceSerial,
                            result.output
                        )
                    )
                }
            }
        }

        pluginConfig.apksToInstallList.forEach { installableApk ->
            if (installableApk.apkPathsList.isEmpty()) return@forEach
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
            val installTimeoutDuration: Duration? = installableApk
                    .installOptions
                    .installApkTimeout
                    .takeIf { it > 0 }?.toLong()?.let { Duration.ofSeconds(it) }
            val installCmd =
                getInstallCmd(
                    installableApk,
                    deviceApiLevel,
                    installableApk.forceReinstallBeforeTest
                )
            logger.info("Installing ${installableApk.apkPathsList} on " +
                    "device $deviceSerial.")
            // Perform sequential install when multiple APK is presented and that they are not
            // split APK
            if (!installableApk.installOptions.installAsSplitApk &&
                    installableApk.apkPathsCount > 1) {
                installableApk.apkPathsList.forEach { apkPath ->
                    deviceController.execute(
                                    installCmd +
                                            installableApk.installOptions.commandLineParameterList +
                                            apkPath, installTimeoutDuration, ).let { result ->
                        if(result.statusCode != 0) {
                            throw UtpException(
                                installErrorSummary,
                                apkInstallErrorMessage(apkPath, deviceSerial, result.output)
                            )
                        }
                    }
                }
            } else {
                deviceController.execute(
                                installCmd +
                                        installableApk.installOptions.commandLineParameterList +
                                        installableApk.apkPathsList,
                                        installTimeoutDuration).let { result ->
                    if (result.statusCode != 0) {
                        throw UtpException(
                            installErrorSummary,
                            apkInstallErrorMessage(
                                installableApk.apkPathsList.toString(),
                                deviceSerial,
                                result.output
                            )
                        )
                    }
                }
            }

            if (deviceApiLevel >= 24 &&
                !installableApk.apkPathsList.isEmpty() &&
                installableApk.installOptions.forceCompilation != ForceCompilation.NO_FORCE_COMPILATION) {
                val packageName = getPackageNameFromApk(installableApk.apkPathsList.first())
                if (packageName == null) {
                    logger.warning(
                        "Failed to resolve package name for ${installableApk.apkPathsList.first()}")
                } else {
                    when (installableApk.installOptions.forceCompilation) {
                        ForceCompilation.FULL_COMPILATION -> {
                            deviceController.execute(
                                listOf("shell",
                                       "cmd",
                                       "package",
                                       "compile",
                                       "-m",
                                       "speed",
                                       "-f",
                                       packageName))
                        }

                        ForceCompilation.PROFILE_BASED_COMPILATION -> {
                            deviceController.execute(
                                listOf("shell",
                                       "cmd",
                                       "package",
                                       "compile",
                                       "-m",
                                       "speed-profile",
                                       "-f",
                                       packageName))
                        }

                        else -> {
                            logger.warning("Unknown force compilation option is specified: " +
                                    "${installableApk.installOptions.forceCompilation}")
                        }
                    }
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
    ) = Unit

    override fun afterAll(
        testSuiteResult: TestSuiteResult,
        deviceController: DeviceController,
        cancelled: Boolean
    ) {
        pluginConfig.apksToInstallList.forEach { installableApk ->
            if (installableApk.uninstallAfterTest) {
                installableApk.apksPackageNameList.forEach { apkPackage ->
                    logger.info("Uninstalling $apkPackage for " +
                            "device ${deviceController.getDevice().serial}.")
                    deviceController.uninstall(apkPackage).let { result ->
                        // Status code 0 signifies success
                        if (result.statusCode != 0) {
                            logger.warning("Device ${deviceController.getDevice().serial} " +
                                    "failed to uninstall test APK $apkPackage.\n${result.output}")
                        }
                    }
                }
            }
        }
    }

    override fun canRun(): Boolean = true
}
