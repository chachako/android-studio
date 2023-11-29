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

package com.android.build.gradle.integration.manageddevice.utils

import com.android.SdkConstants
import com.android.build.gradle.integration.common.fixture.GradleTaskExecutor
import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.android.build.gradle.integration.common.utils.SdkHelper
import com.android.build.gradle.integration.common.utils.TestFileUtils
import com.android.build.gradle.options.BooleanOption
import com.android.build.gradle.options.IntegerOption
import com.android.build.gradle.options.StringOption
import com.android.sdklib.repository.AndroidSdkHandler
import com.android.utils.FileUtils
import com.google.common.base.Splitter
import com.google.common.hash.Funnels
import com.google.common.hash.Hashing
import com.google.common.io.ByteStreams
import org.junit.rules.ExternalResource
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.io.path.fileSize

class CustomAndroidSdkRule : ExternalResource() {

    private val testTmpDir = File(System.getenv("TEST_TMPDIR"), "CustomAndroidSdkRule")
    private val customSdkDir = File(testTmpDir, "CustomAndroidSdk")
    private val customSdkRepo = File(
            testTmpDir,
            FileUtils.toSystemDependentPath("SysImgSource/dl.google.com/android/repository"))
    private val customUserHomeDir = File(testTmpDir, "CustomUserLocal")
    private val customAndroidPrefDir = File(customUserHomeDir, ".android")

    private val systemImageFiles = Splitter.on(' ')
            .split(System.getProperty("sdk.repo.sysimage.android29.files"))
            .map { File(it) }
            .toList()

    private val emulatorZip = File(System.getProperty("sdk.repo.emulator.zip"))

    override fun before() {
        setupSdk()
        setupSdkRepo()
    }

    /**
     * Returns a valid executor for running managed device tasks.
     *
     * Running managed devices require a custom location for the avds to be created. Needs to be
     * able to download the system-image from a local repo dir. Runs on the canary channel. Runs the
     * emulator in software-rendering mode.
     */
    fun GradleTestProject.executorWithCustomAndroidSdk(): GradleTaskExecutor {
        val sdkDirProp = "${SdkConstants.SDK_DIR_PROPERTY} = ${customSdkDir.absolutePath.replace("\\", "\\\\")}"
        if (!localProp.readText().contains(sdkDirProp)) {
            TestFileUtils.appendToFile(localProp, System.lineSeparator() + sdkDirProp)
        }
        if (!customUserHomeDir.exists()) {
            FileUtils.mkdirs(customUserHomeDir)
        }
        if (!customAndroidPrefDir.exists()) {
            FileUtils.mkdirs(customAndroidPrefDir)
        }
        return executor()
            .withLocalPrefsRoot()
            .withEnvironmentVariables(mapOf(
                "HOME" to customUserHomeDir.absolutePath,
                "ANDROID_USER_HOME" to customAndroidPrefDir.absolutePath
            ))
            .withoutOfflineFlag()
            .withSdkAutoDownload()
            .with(IntegerOption.ANDROID_SDK_CHANNEL, 3)
            .with(StringOption.GRADLE_MANAGED_DEVICE_EMULATOR_GPU_MODE, "swiftshader_indirect")
            .with(BooleanOption.GRADLE_MANAGED_DEVICE_EMULATOR_SHOW_KERNEL_LOGGING, true)
            .withArgument("-D${AndroidSdkHandler.SDK_TEST_BASE_URL_PROPERTY}=file:///${customSdkRepo.absolutePath}/")
    }

    /**
     * Sets the given file as the sdkDir for the project, as well as sets up the directory to
     * be ready for download.
     */
    private fun setupSdk() {
        if (customSdkDir.exists()) {
            return
        }
        FileUtils.mkdirs(customSdkDir)
        SdkHelper.findSdkDir().copyRecursively(customSdkDir)
        Path.of(customSdkDir.absolutePath, "platform-tools", "adb").toFile().setExecutable(true)
        setupLicenses()
    }

    /**
     * Sets up the licenses to allow for auto-download of Sdk components
     */
    private fun setupLicenses() {
        val licensesFolder = File(customSdkDir, "licenses")
        FileUtils.mkdirs(licensesFolder)
        val licenseFile = File(licensesFolder, "android-sdk-license")
        val previewLicenseFile = File(licensesFolder, "android-sdk-preview-license")

        // noinspection SpellCheckingInspection SHAs.
        val licensesHash =
                String.format(
                        "8933bad161af4178b1185d1a37fbf41ea5269c55%n"
                                + "d56f5187479451eabf01fb78af6dfcb131a6481e%n"
                                + "24333f8a63b6825ea9c5514f83c2829b004d1fee%n"
                                + "85435445a95c234340d05367a999a69d7b46701c%n")

        val previewLicenseHash =
                String.format("84831b9409646a918e30573bab4c9c91346d8abd")

        Files.write(licenseFile.toPath(), licensesHash.toByteArray(StandardCharsets.UTF_8))
        Files.write(
                previewLicenseFile.toPath(), previewLicenseHash.toByteArray(StandardCharsets.UTF_8))
    }

    /**
     * Sets up a facsimile of the online android repository for the purpose of downloading the
     * system image for managed devices locally.
     */
    private fun setupSdkRepo() {
        if (customSdkRepo.exists()) {
            return
        }
        FileUtils.mkdirs(customSdkRepo)

        // Setup toplevel components first
        setupTopLevelRepository()

        // Setup manifest for all other necessary components
        setupManifestXml()

        // Set up the system image repository
        setupAOSPImageRepository()
    }

    /**
     * Sets up the toplevel repository directory, which contains the dependencies of system images,
     * most notably the emulator.
     *
     * This is grouped into two steps.
     * 1. The repository XMLs, which specify the versions, urls, and channel of sdk components needed
     * by the system image.
     * 2. The actual tools located at these relative urls.
     *
     * The repository XMLs are set up manually to ensure that the dependency versions are stable. As the
     * real XML file may update the available versions for download (and therefore require the
     * underlying bazel rules to change what target is available).
     */
    private fun setupTopLevelRepository() {
        val repositoryXml = File(customSdkRepo, "repository2-3.xml")
        val repositoryXmlContents = """
            <?xml version="1.0" ?>
            <sdk:sdk-repository xmlns:common="http://schemas.android.com/repository/android/common/02" xmlns:generic="http://schemas.android.com/repository/android/generic/02" xmlns:sdk="http://schemas.android.com/sdk/android/repo/repository2/02" xmlns:sdk-common="http://schemas.android.com/sdk/android/repo/common/02" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
                <license id="android-sdk-license" type="text">A TOTALLY VALID LICENSE</license>
                <channel id="channel-0">stable</channel>
                <remotePackage path="emulator">
                    <!-- Generated from:ab bid:11078245 branch:aosp-emu-33-release -->
                    <type-details xsi:type="generic:genericDetailsType"/>
                    <revision>
                        <major>33</major>
                        <minor>1</minor>
                        <micro>22</micro>
                    </revision>
                    <display-name>Android Emulator</display-name>
                    <uses-license ref="android-sdk-license"/>
                    <channelRef ref="channel-0"/>
                    <archives>
                        <archive>
                            <!-- Built on: Sat Nov 11 23:06:51 2023. -->
                            <complete>
                                <size>252615367</size>
                                <checksum type="sha1">2b8d488442abcddd3864400436e16feb5fd8567f</checksum>
                                <url>emulator-linux_x64-11078245.zip</url>
                            </complete>
                            <host-os>linux</host-os>
                            <host-arch>x64</host-arch>
                        </archive>
                    </archives>
                </remotePackage>
            </sdk:sdk-repository>
        """.trimIndent()
        Files.write(
                repositoryXml.toPath(), repositoryXmlContents.toByteArray(StandardCharsets.UTF_8))
        val repositoryV2Xml = File(customSdkRepo, "repository2-2.xml")
        val repositoryV2XmlContents = """
            <?xml version="1.0" ?>
            <sdk:sdk-repository xmlns:common="http://schemas.android.com/repository/android/common/02" xmlns:generic="http://schemas.android.com/repository/android/generic/02" xmlns:sdk="http://schemas.android.com/sdk/android/repo/repository2/03" xmlns:sdk-common="http://schemas.android.com/sdk/android/repo/common/03" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
            </sdk:sdk-repository>
        """.trimIndent()
        Files.write(
                repositoryV2Xml.toPath(),
                repositoryV2XmlContents.toByteArray(StandardCharsets.UTF_8))

        FileUtils.copyFileToDirectory(emulatorZip, customSdkRepo)
    }

    /**
     * Creates the addon list XML, which is a manifest of all other repository XML locations.
     *
     * Without the manifest, the [RepoManager] will not be able to find the system image urls.
     *
     * For downloading system-images, we only need the sys-img XML for AOSP images at present. We do
     * not want to download the full addons list from online with a bazel rule. There is no need to
     * include other XMLs, as this drowns the gradle output with useless XML parsing errors.
     */
    private fun setupManifestXml() {
        val sourceList = File(customSdkRepo, "addons_list-5.xml")
        val sourceListContents = """
            <?xml version="1.0" ?>
            <common:site-list xmlns:common="http://schemas.android.com/repository/android/sites-common/1" xmlns:sdk="http://schemas.android.com/sdk/android/addons-list/5" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
                <site xsi:type="sdk:sysImgSiteType">
                    <displayName>Automated Test Device System Images</displayName>
                   <url>sys-img/android/sys-img2-3.xml</url>
                </site>
            </common:site-list>
        """.trimIndent()
        Files.write(sourceList.toPath(), sourceListContents.toByteArray(StandardCharsets.UTF_8))
    }

    /**
     * Sets up the repository in the sys-img/android subdirectory of the android repository.
     *
     * This is done in 2 steps like the toplevel repository:
     * 1. create the XML to specify where the system images are.
     * 2. insert the system-images into those locations.
     *
     * The repository XML is set up manually to ensure that the revision # of the system image does not
     * change, because the system image that is downloaded by bazel is contingent on the version.
     */
    private fun setupAOSPImageRepository() {
        val sysImgRepoFolder = FileUtils.join(customSdkRepo, "sys-img", "android")
        FileUtils.mkdirs(sysImgRepoFolder)

        val sysImgCommonPathLength = systemImageFiles
                .map(File::getAbsolutePath)
                .reduce(String::commonPrefixWith)
                .length

        val sysImgZipFile = File(sysImgRepoFolder, "x86_64-29_r06.zip")
        ZipOutputStream(BufferedOutputStream(sysImgZipFile.outputStream())).use { output ->
            systemImageFiles.forEach { file ->
                output.putNextEntry(ZipEntry(file.absolutePath.drop(sysImgCommonPathLength)))
                BufferedInputStream(file.inputStream()).use { content ->
                    content.copyTo(output, bufferSize = 1024)
                }
            }
        }

        val sysImgZipSha256 = Hashing.sha256().newHasher().let {
            BufferedInputStream(sysImgZipFile.inputStream()).use { content ->
                ByteStreams.copy(content, Funnels.asOutputStream(it))
            }
            it.hash().toString()
        }

        val sysImgXml = File(sysImgRepoFolder, "sys-img2-3.xml")
        val sysImgXmlContents = """
            <?xml version="1.0" ?>
            <sys-img:sdk-sys-img xmlns:sys-img="http://schemas.android.com/sdk/android/repo/sys-img2/03" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
                <license id="android-sdk-license" type="text">A TOTALLY VALID LICENSE</license>
                <channel id="channel-0">stable</channel>
                <remotePackage path="system-images;android-29;default;x86_64">
                    <type-details xsi:type="sys-img:sysImgDetailsType">
                        <api-level>29</api-level>
                        <tag>
                            <id>default</id>
                            <display>Default Android System Image</display>
                        </tag>
                        <abi>x86_64</abi>
                    </type-details>
                    <revision>
                        <major>6</major>
                    </revision>
                    <display-name>Intel x86_64 Atom System Image</display-name>
                    <uses-license ref="android-sdk-license"/>
                    <dependencies>
                        <dependency path="emulator">
                            <min-revision>
                                <major>28</major>
                                <minor>1</minor>
                                <micro>9</micro>
                            </min-revision>
                        </dependency>
                    </dependencies>
                    <channelRef ref="channel-0"/>
                    <archives>
                        <archive>
                            <complete>
                                <size>${sysImgZipFile.toPath().fileSize()}</size>
                                <checksum>${sysImgZipSha256}</checksum>
                                <url>${sysImgZipFile.name}</url>
                            </complete>
                            <host-os>linux</host-os>
                        </archive>
                    </archives>
                </remotePackage>
            </sys-img:sdk-sys-img>
        """.trimIndent()
        Files.write(sysImgXml.toPath(), sysImgXmlContents.toByteArray(StandardCharsets.UTF_8))
    }
}
