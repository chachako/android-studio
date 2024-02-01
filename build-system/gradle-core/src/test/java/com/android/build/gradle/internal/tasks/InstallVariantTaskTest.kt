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
import com.android.build.gradle.internal.fixtures.FakeFileCollection
import com.android.build.gradle.internal.fixtures.FakeGradleDirectory
import com.android.build.gradle.internal.fixtures.FakeGradleDirectoryProperty
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
import org.gradle.api.logging.Logger
import org.junit.Before
import org.junit.Ignore
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import org.mockito.ArgumentMatchers
import org.mockito.Mock
import org.mockito.Mockito
import org.mockito.internal.verification.VerificationModeFactory.times
import org.mockito.junit.MockitoJUnit
import org.mockito.junit.MockitoRule
import java.io.File
import java.util.concurrent.TimeUnit

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

    @Mock
    lateinit var logger: Logger

    @get:Rule
    val fakeAdb = FakeAdbTestRule(deviceVersion)

    private lateinit var deviceConnector: DeviceConnector
    private lateinit var deviceState: DeviceState
    private lateinit var mainOutputFileApk: File

    lateinit var privacySandboxLegacyApkSplitsDirectory: File

    @Before
    fun setUp() {
        deviceState = fakeAdb.connectAndWaitForDevice()
        deviceState.setActivityManager(PackageManager())
        if (deviceVersion.apiLevel >= 34) {
            deviceState.serviceManager.setService("sdk_sandbox") { _, _ -> }
        }
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
    @Ignore("b/303076495") // Won't pass because the privacy sandbox sdk .apks file is not set up correctly.
    fun checkDependencyApkInstallation() {
        createMainApkListingFile()
        val listingFile = createDependencyApkListingFile()
        InstallVariantTask.install(
            "project",
            "variant",
            FakeDeviceProvider(ImmutableList.of(deviceConnector)),
            AndroidVersion.DEFAULT,
            FakeGradleDirectory(temporaryFolder.root),
            ImmutableSet.of(listingFile),
            FakeGradleDirectoryProperty(null),
            FakeGradleDirectoryProperty(FakeGradleDirectory(privacySandboxLegacyApkSplitsDirectory)),
            ImmutableSet.of(),
            ImmutableList.of(),
            4000,
            logger,
            FakeGradleDirectoryProperty(null),
        )
        Mockito.verify(logger, times(3)).quiet("Installed on {} {}.", 1, "device")
        Mockito.verify(deviceConnector, Mockito.atLeastOnce()).name
        Mockito.verify(deviceConnector, Mockito.atLeastOnce()).apiLevel
        Mockito.verify(deviceConnector, Mockito.atLeastOnce()).abis
        Mockito.verify(deviceConnector, Mockito.atLeastOnce()).deviceConfig
        val inOrder = Mockito.inOrder(deviceConnector)

        inOrder.verify(deviceConnector).installPackage(
            ArgumentMatchers.eq(temporaryFolder.newFolder("apks").resolve("dependency1.apk")),
            ArgumentMatchers.any(),
            ArgumentMatchers.anyInt(),
            ArgumentMatchers.any()
        )
        inOrder.verify(deviceConnector).installPackage(
            ArgumentMatchers.eq(temporaryFolder.root.resolve("dependency2.apk")),
            ArgumentMatchers.any(),
            ArgumentMatchers.anyInt(),
            ArgumentMatchers.any()
        )
        inOrder.verify(deviceConnector).installPackage(
            ArgumentMatchers.eq(temporaryFolder.root.resolve("main.apk")),
            ArgumentMatchers.any(),
            ArgumentMatchers.anyInt(),
            ArgumentMatchers.any()
        )
        Mockito.verifyNoMoreInteractions(deviceConnector)
    }

    private fun checkSingleApk(deviceConnector: DeviceConnector) {
        createMainApkListingFile()
        InstallVariantTask.install(
                "project",
                "variant",
                FakeDeviceProvider(ImmutableList.of(deviceConnector)),
                AndroidVersion.DEFAULT,
                FakeGradleDirectory(temporaryFolder.root),
                ImmutableSet.of(),
                FakeGradleDirectoryProperty(null),
                FakeGradleDirectoryProperty(null),
                ImmutableSet.of(),
                ImmutableList.of(),
                4000,
                logger,
                FakeGradleDirectoryProperty(null),
        )
        assert(deviceState.pmLogs.any {
            it.startsWith("install -r -t") && it.contains("main.apk")
        })

        Mockito.verify(logger)
                .lifecycle(
                        "Installing APK '{}' on '{}' for {}:{}",
                        "main.apk",
                        deviceConnector.name,
                        "project",
                        "variant"
                )
        Mockito.verify(logger).quiet("Installed on {} {}.", 1, "device")
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

    private fun createDependencyApkListingFile(): File {
        val dependencyApk1 = temporaryFolder.newFile("dependency1.apk")
        val dependencyApk2 = temporaryFolder.newFile("dependency2.apk")
        return temporaryFolder.newFile("dependencyApkListingFile.txt").also {
            it.writeText("""
[{
  "version": 1,
  "artifactType": {
    "type": "APK",
    "kind": "Directory"
  },
  "applicationId": "com.android.test1",
  "variantName": "debug",
  "elements": [
    {
      "type": "SINGLE",
      "filters": [],
      "versionCode": 123,
      "versionName": "version_name",
      "outputFile": "${dependencyApk1.name}"
    }
  ]
},{
  "version": 1,
  "artifactType": {
    "type": "APK",
    "kind": "Directory"
  },
  "applicationId": "com.android.test2",
  "variantName": "debug",
  "elements": [
    {
      "type": "SINGLE",
      "filters": [],
      "versionCode": 123,
      "versionName": "version_name",
      "outputFile": "${dependencyApk2.name}"
    }
  ]
}]""", Charsets.UTF_8)
        }
    }

}

