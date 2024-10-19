/*
 * Copyright (C) 2018 The Android Open Source Project
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

import com.android.build.gradle.internal.LoggerWrapper
import com.android.build.gradle.internal.fixtures.FakeGradleProperty
import com.android.build.gradle.internal.fixtures.FakeNoOpAnalyticsService
import com.android.build.gradle.internal.profile.AnalyticsService
import com.android.builder.testing.api.DeviceConnector
import com.android.builder.testing.api.DeviceProvider
import com.android.bundle.Devices
import com.android.sdklib.AndroidVersion
import com.android.utils.ILogger
import com.google.common.collect.ImmutableList
import com.google.common.truth.Truth.assertThat
import org.gradle.api.Project
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.testfixtures.ProjectBuilder
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import org.mockito.ArgumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.mockito.junit.MockitoJUnit
import org.mockito.junit.MockitoRule
import java.io.File
import java.nio.file.Files
import java.nio.file.Path

@RunWith(Parameterized::class)
class InstallVariantViaBundleTaskTest(private val sdkVersion: AndroidVersion) {

    @JvmField
    @Rule
    var tmp = TemporaryFolder()

    companion object {

        @JvmStatic
        @Parameterized.Parameters
        fun sdkVersion() = arrayOf(
                AndroidVersion(21),
                AndroidVersion(34)
        )
    }

    private lateinit var project: Project

    private val deviceConnector: DeviceConnector = mock()

    @Before
    fun setUp() {
        project = ProjectBuilder.builder().withProjectDir(tmp.newFolder()).build()
        whenever(deviceConnector.name).thenReturn("Test Device")
        whenever(deviceConnector.apiLevel).thenReturn(sdkVersion.apiLevel)
        whenever(deviceConnector.apiCodeName).thenReturn(sdkVersion.codename)
        whenever(deviceConnector.abis).thenReturn(listOf("x86_64"))
        whenever(deviceConnector.density).thenReturn(-1)
        whenever(deviceConnector.supportsPrivacySandbox).thenReturn(sdkVersion.apiLevel >= 33)
    }

    private fun getParams(privacySandboxSdkApksFiles: List<File> = emptyList()) =
        object : InstallVariantViaBundleTask.Params() {
            override val adbExe: RegularFileProperty
                get() = project.objects.fileProperty().fileValue(File("adb.exe"))
            override val apkBundle: RegularFileProperty
                get() = project.objects.fileProperty().fileValue(File("bundle.aab"))
            override val timeOutInMs: Property<Int>
                get() = project.objects.property(Int::class.java).value(0)
            override val installOptions: ListProperty<String>
                get() = project.objects.listProperty(String::class.java)
            override val variantName: Property<String>
                get() = project.objects.property(String::class.java).value("variantName")
            override val minApiCodeName: Property<String?>
                get() = project.objects.property(String::class.java)
            override val minSdkVersion: Property<Int>
                get() = project.objects.property(Int::class.java).value(21)
            override val projectPath: Property<String>
                get() = project.objects.property(String::class.java).value("projectName")
            override val taskOwner: Property<String>
                get() = project.objects.property(String::class.java).value("taskOwner")
            override val workerKey: Property<String>
                get() = project.objects.property(String::class.java).value("workerKey")
            override val analyticsService: Property<AnalyticsService>
                get() = FakeGradleProperty(FakeNoOpAnalyticsService())
            override val privacySandboxSdkApksFiles: ConfigurableFileCollection
                get() = project.objects.fileCollection().from(privacySandboxSdkApksFiles)
        }

    @Test
    fun installSingle() {

        val outputPath = Files.createTempFile(
            "extract-apk",
            ""
        )
        val runnable = TestInstallRunnable(getParams(), deviceConnector, listOf(outputPath))
        runnable.run()
        var apkArgumentCaptor = argumentCaptor<File>()
        var timeoutArgumentCaptor = argumentCaptor<Int>()
        var optionsArgumentCaptor = argumentCaptor<Collection<String>>()
        var loggerArgumentCaptor = argumentCaptor<LoggerWrapper>()
        verify(deviceConnector, times(1)).installPackage(apkArgumentCaptor.capture(), optionsArgumentCaptor.capture(), timeoutArgumentCaptor.capture(), loggerArgumentCaptor.capture())
        assertThat(apkArgumentCaptor.value.name).contains("extract-apk")
        assertThat(timeoutArgumentCaptor.value).isEqualTo(0)
        assertThat(optionsArgumentCaptor.value).isEqualTo(emptyList<String>())
    }

    @Test
    fun installMultiple() {
        val outputPath = Files.createTempFile(
            "extract-apk",
            ""
        )
        val outputPath2 = Files.createTempFile(
            "extract-apk",
            ""
        )

        val runnable = TestInstallRunnable(getParams(), deviceConnector, listOf(outputPath, outputPath2))
        runnable.run()
        var apkArgumentCaptor = argumentCaptor<List<File>>()
        var timeoutArgumentCaptor = argumentCaptor<Int>()
        var optionsArgumentCaptor = argumentCaptor<Collection<String>>()
        var loggerArgumentCaptor = argumentCaptor<LoggerWrapper>()

        verify(deviceConnector, times(1)).installPackages(apkArgumentCaptor.capture(), optionsArgumentCaptor.capture(), timeoutArgumentCaptor.capture(), loggerArgumentCaptor.capture())
        assertThat(apkArgumentCaptor.value.size).isEqualTo(2)
        assertThat(apkArgumentCaptor.value).containsExactly(outputPath.toFile(), outputPath2.toFile())
        assertThat(timeoutArgumentCaptor.value).isEqualTo(0)
        assertThat(optionsArgumentCaptor.value).isEqualTo(emptyList<String>())
    }

    @Test
    fun installPrivacySandboxSdkApks_oneToOne() {
        val outputPath = Files.createTempFile(
            "extract-apk",
            ""
        )
        val sdk1Apks = File("sdk1.apks")
        val sdk1ExtractedApk = tmp.newFile("sdk1-extracted.apk")
        val runnable = TestInstallRunnable(
            getParams(listOf(sdk1Apks)),
            deviceConnector,
            listOf(outputPath),
            privacySandboxSdkApkMapping = mapOf(
                sdk1Apks to listOf(sdk1ExtractedApk)
            )
        )
        runnable.run()

        var apkArgumentCaptor = argumentCaptor<File>()
        var timeoutArgumentCaptor = argumentCaptor<Int>()
        var optionsArgumentCaptor = argumentCaptor<Collection<String>>()
        var loggerArgumentCaptor = argumentCaptor<LoggerWrapper>()

        if (deviceConnector.supportsPrivacySandbox) {
            verify(deviceConnector, times(2)).installPackage(apkArgumentCaptor.capture(), optionsArgumentCaptor.capture(), timeoutArgumentCaptor.capture(), loggerArgumentCaptor.capture())
            assertThat(apkArgumentCaptor.allValues).containsExactly(sdk1ExtractedApk, outputPath.toFile())
        } else {
            verify(deviceConnector, times(1)).installPackage(
                apkArgumentCaptor.capture(),
                optionsArgumentCaptor.capture(),
                timeoutArgumentCaptor.capture(),
                loggerArgumentCaptor.capture()
            )
            assertThat(apkArgumentCaptor.value).isEqualTo(outputPath.toFile())
        }
    }

    @Test
    fun installPrivacySandboxSdkApks_manyToMany() {
        val outputPath = Files.createTempFile(
            "extract-apk",
            ""
        )
        val sdk1Apks = File("sdk1.apks")
        val sdk1ExtractedApk1 = tmp.newFile("sdk1-extracted1.apk")
        val sdk1ExtractedApk2 = tmp.newFile("sdk1-extracted2.apk")

        val sdk2Apks = File("sdk2.apks")
        val sdk2ExtractedApk1 = tmp.newFile("sdk2-extracted1.apk")
        val sdk2ExtractedApk2 = tmp.newFile("sdk2-extracted2.apk")
        val runnable = TestInstallRunnable(
            getParams(listOf(sdk1Apks, sdk2Apks)),
            deviceConnector,
            listOf(outputPath),
            privacySandboxSdkApkMapping = mapOf(
                sdk1Apks to listOf(sdk1ExtractedApk1, sdk1ExtractedApk2),
                sdk2Apks to listOf(sdk2ExtractedApk1, sdk2ExtractedApk2)
            )
        )

        runnable.run()
        var apksArgumentCaptor = argumentCaptor<List<File>>()
        var apkArgumentCaptor = argumentCaptor<File>()
        var timeoutArgumentCaptor = argumentCaptor<Int>()
        var optionsArgumentCaptor = argumentCaptor<Collection<String>>()
        var loggerArgumentCaptor = argumentCaptor<LoggerWrapper>()

        if (deviceConnector.supportsPrivacySandbox) {
            verify(deviceConnector, times(2)).installPackages(apksArgumentCaptor.capture(), optionsArgumentCaptor.capture(), timeoutArgumentCaptor.capture(), loggerArgumentCaptor.capture())
            assert(apksArgumentCaptor.allValues.size == 2)
            assert(apksArgumentCaptor.allValues[0].size == 2)
            assert(apksArgumentCaptor.allValues[1].size == 2)
            assertThat(apksArgumentCaptor.allValues[0].all { it.name.contains("sdk1-extracted") }).isTrue()
            assertThat(apksArgumentCaptor.allValues[1].all { it.name.contains("sdk2-extracted") }).isTrue()
            assertThat(timeoutArgumentCaptor.value).isEqualTo(0)
            assertThat(optionsArgumentCaptor.value).isEqualTo(emptyList<String>())
        }
        verify(deviceConnector, times(1)).installPackage(apkArgumentCaptor.capture(), optionsArgumentCaptor.capture(), timeoutArgumentCaptor.capture(), loggerArgumentCaptor.capture())
        assertThat(apkArgumentCaptor.value).isEqualTo(outputPath.toFile())
        assertThat(timeoutArgumentCaptor.value).isEqualTo(0)
        assertThat(optionsArgumentCaptor.value).isEqualTo(emptyList<String>())
    }

    private class TestInstallRunnable(
        val params: InstallVariantViaBundleTask.Params,
        private val deviceConnector: DeviceConnector,
        private val outputPaths: List<Path>,
        private val privacySandboxSdkApkMapping: Map<File, List<File>> = mapOf(),
    ) : InstallVariantViaBundleTask.InstallRunnable() {

        override fun createDeviceProvider(iLogger: ILogger): DeviceProvider =
            InstallVariantTaskTest.FakeDeviceProvider(ImmutableList.of(deviceConnector))

        override fun getApkFiles(apkBundles: Collection<Path>, deviceSpec: Devices.DeviceSpec)
                : List<Path> {
            return ImmutableList.copyOf(outputPaths)
        }

        override fun getPrivacySandboxSdkApkFiles(apk: Path) =
            privacySandboxSdkApkMapping[apk.fileName.toFile()]!!

        override fun getParameters(): InstallVariantViaBundleTask.Params {
            return params
        }
    }
}

internal inline fun <reified T : Any> argumentCaptor(): ArgumentCaptor<T> {
    return ArgumentCaptor.forClass(T::class.java)
}
