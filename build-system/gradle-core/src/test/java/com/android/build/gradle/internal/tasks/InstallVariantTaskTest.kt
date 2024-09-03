/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.build.gradle.internal.tasks

import com.android.build.api.variant.impl.BuiltArtifactsImpl
import com.android.build.gradle.internal.LoggerWrapper
import com.android.build.gradle.internal.fixtures.FakeFileCollection
import com.android.build.gradle.internal.fixtures.FakeGradleDirectory
import com.android.build.gradle.internal.fixtures.FakeGradleDirectoryProperty
import com.android.build.gradle.internal.fixtures.FakeLogger
import com.android.build.gradle.internal.utils.ApkSources
import com.android.build.gradle.internal.utils.DefaultDeviceApkOutput
import com.android.builder.testing.api.DeviceConnector
import com.android.builder.testing.api.DeviceProvider
import com.android.ddmlib.AndroidDebugBridge
import com.android.ddmlib.internal.FakeAdbTestRule
import com.android.fakeadbserver.DeviceState
import com.android.fakeadbserver.services.PackageManager
import com.android.sdklib.AndroidVersion
import com.android.utils.StdLogger
import com.google.common.collect.ImmutableList
import com.google.common.collect.ImmutableSet
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Ignore
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import org.mockito.Mock
import org.mockito.junit.MockitoJUnit
import org.mockito.junit.MockitoRule
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

@RunWith(Parameterized::class)
class InstallVariantTaskTest(private val deviceVersion: AndroidVersion) {

    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "deviceVersion_{0}")
        fun parameters() = listOf(
            AndroidVersion(19),
            AndroidVersion(21),
            AndroidVersion(34)
        )
    }

    @JvmField
    @Rule
    var rule: MockitoRule = MockitoJUnit.rule()

    @JvmField
    @Rule
    var temporaryFolder = TemporaryFolder()

    lateinit var logger: FakeLogger

    @get:Rule
    val fakeAdb = FakeAdbTestRule(deviceVersion)

    @Mock
    private lateinit var deviceConnector: DeviceConnector
    private lateinit var deviceState: DeviceState
    private lateinit var mainOutputFileApk: File

    private var sandboxSupported: Boolean = false

    lateinit var privacySandboxLegacyApkSplitsDirectory: File

    @Before
    fun setUp() {
        deviceState = fakeAdb.connectAndWaitForDevice()
        deviceState.setActivityManager(PackageManager())
        sandboxSupported = deviceVersion.apiLevel >= 34
        if (sandboxSupported) {
            deviceState.serviceManager.setService("sdk_sandbox") { _, _ -> }
        }
        logger = FakeLogger()
        val device = AndroidDebugBridge.getBridge()!!.devices.single()
        deviceConnector = CustomConnectedDevice(
                device,
                StdLogger(StdLogger.Level.VERBOSE),
                10000,
                TimeUnit.MILLISECONDS,
                deviceVersion)

        privacySandboxLegacyApkSplitsDirectory = temporaryFolder.newFolder("privacysandbox-legacy-split-apks")
    }

    @Test
    @Throws(Exception::class)
    fun checkSingleApkInstall() {
        checkSingleApk(deviceConnector)
    }

    @Test
    @Throws(Exception::class)
    @Ignore("b/364301279")
    fun checkDependencyApkInstallation() {
        createMainApkListingFile()
        val splitApk = getSdkSupportSplitApk()
        val deviceApkOutput = DefaultDeviceApkOutput(
            ApkSources(
                FakeGradleDirectoryProperty(FakeGradleDirectory(temporaryFolder.root)),
                FakeFileCollection(getPrivacySandboxSdkApks()),
                FakeGradleDirectoryProperty(FakeGradleDirectory(splitApk)),
                FakeGradleDirectoryProperty(FakeGradleDirectory(privacySandboxLegacyApkSplitsDirectory)),
                FakeGradleDirectoryProperty(null)
            ),
            ImmutableSet.of(), AndroidVersion.DEFAULT, "variant", "project", LoggerWrapper(logger)
        )

        InstallVariantTask.install(
            deviceApkOutput,
            "project",
            "variant",
            FakeDeviceProvider(ImmutableList.of(deviceConnector)),
            ImmutableSet.of(),
            4000,
            logger,
        )


        val packageInstalls =
            deviceState.abbLogs.filter { it.startsWith("package\u0000install-write") }
        if (deviceConnector.supportsPrivacySandbox) {
            assertThat(logger.lifeCycles).containsExactly(
                "Installing Privacy Sandbox APK '{}' on '{}' for {}:{}",
                "Installing Privacy Sandbox APK '{}' on '{}' for {}:{}",
                "Installing APK '{}' on '{}' for {}:{}"
            )
            assertThat(packageInstalls.count()).isEqualTo(2)
        } else {
            assertThat(logger.lifeCycles).containsExactly(
                "Installing APK '{}' on '{}' for {}:{}")
            assertThat(packageInstalls.count()).isEqualTo(0)
        }
        assertThat(deviceState.pmLogs)
            .containsExactly("install -r -t \"/data/local/tmp/main.apk\"")
    }

    private fun getSdkSupportSplitApk(): File {
        val privacySandboxSupportSplit =
            temporaryFolder.newFolder("privacy-sandobox-support-split")
        return File(privacySandboxSupportSplit, "sdk-support.apk")
    }

    private fun checkSingleApk(deviceConnector: DeviceConnector) {
        createMainApkListingFile()
        val deviceApkOutput = DefaultDeviceApkOutput(
            ApkSources(
                FakeGradleDirectoryProperty(FakeGradleDirectory(temporaryFolder.root)),
                FakeFileCollection(ImmutableSet.of<File>()),
                FakeGradleDirectoryProperty(null),
                FakeGradleDirectoryProperty(null),
                FakeGradleDirectoryProperty(null)
            ),
            ImmutableSet.of(), AndroidVersion.DEFAULT, "variant", "project", LoggerWrapper(logger)
        )

        InstallVariantTask.install(
                deviceApkOutput,
            "project",
            "variant",
                FakeDeviceProvider(ImmutableList.of(deviceConnector)),
                ImmutableSet.of(),
                4000,
                logger
        )
        assert(deviceState.pmLogs.any {
            it.startsWith("install -r -t") && it.contains("main.apk")
        })
        assertThat(logger.quiets)
            .containsExactly("Installed on {} {}.")
    }

    internal class FakeDeviceProvider(private val devices: List<DeviceConnector>) : DeviceProvider() {

        private var state = State.NOT_READY

        override fun init() {
            check(state == State.NOT_READY) { "Can only go to READY from NOT_READY. Current state is $state" }
            state = State.READY
        }

        override fun terminate() {
            check(state == State.READY) { "Can only go to TERMINATED from READY. Current state is $state" }
            state = State.TERMINATED
        }

        override fun getName() = "FakeDeviceProvider"
        override fun getDevices() = devices
        override fun getTimeoutInMs() = 4000
        override fun isConfigured() = true

        private enum class State {
            NOT_READY, READY, TERMINATED
        }
    }

    private fun createMainApkListingFile() {
        mainOutputFileApk = temporaryFolder.newFile("main.apk")
        temporaryFolder.newFile(BuiltArtifactsImpl.METADATA_FILE_NAME).writeText("""
{
  "version": 1,
  "artifactType": {
    "type": "APK",
    "kind": "Directory"
  },
  "applicationId": "com.android.test",
  "variantName": "debug",
  "elements": [
    {
      "type": "SINGLE",
      "filters": [],
      "versionCode": 123,
      "versionName": "version_name",
      "outputFile": "${mainOutputFileApk.name}"
    }
  ]
}""", Charsets.UTF_8)
    }

    private fun getPrivacySandboxSdkApks(): Set<File> {
        val sdkApkDir = temporaryFolder.newFolder("sdkApks")
        val sdkApks = listOf(
            File(sdkApkDir, "sdkApk1.zip"),
            File(sdkApkDir, "sdkApk2.zip")
        )
        sdkApks.forEach { zip ->
            ZipOutputStream(BufferedOutputStream(FileOutputStream(zip))).use { instrumentedJar ->
                val standalonesDir = ZipEntry("standalones/standalone.apk")
                instrumentedJar.putNextEntry(standalonesDir)
                instrumentedJar.closeEntry()
            }
        }
        return sdkApks.toSet()
    }

}

