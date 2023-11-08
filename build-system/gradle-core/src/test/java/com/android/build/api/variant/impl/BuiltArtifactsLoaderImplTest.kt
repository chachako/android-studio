/*
 * Copyright (C) 2019 The Android Open Source Project
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

package com.android.build.api.variant.impl

import com.android.build.api.artifact.SingleArtifact
import com.android.build.api.variant.FilterConfiguration
import com.android.build.api.variant.VariantOutputConfiguration
import com.android.build.gradle.internal.fixtures.FakeGradleDirectory
import com.android.build.gradle.internal.scope.InternalArtifactType
import com.android.ide.common.build.BaselineProfileDetails
import com.android.utils.FileUtils
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * Tests for [BuiltArtifactsLoaderImpl]
 */
class BuiltArtifactsLoaderImplTest {

    @get:Rule
    val tmpFolder = TemporaryFolder()

    @get:Rule
    val outFolder = TemporaryFolder()

    @Test
    fun testSingleFileTransformation() {
        createSimpleMetadataFile()

        val builtArtifacts= BuiltArtifactsLoaderImpl().load(
            FakeGradleDirectory(tmpFolder.root))

        assertThat(builtArtifacts).isNotNull()

        val newBuiltArtifacts = BuiltArtifactsImpl(
            artifactType = SingleArtifact.APK,
            applicationId = builtArtifacts!!.applicationId,
            variantName = builtArtifacts.variantName,
            elements = builtArtifacts.elements.map {
                assertThat(File(it.outputFile).readText(Charsets.UTF_8)).isEqualTo(
                    "some manifest")
                it.newOutput(
                    outFolder.newFile("${File(it.outputFile).name}.new").also { file ->
                        file.writeText("updated APK")
                    }.toPath())
            }
        )

        newBuiltArtifacts.save(FakeGradleDirectory(outFolder.root))

        // load the new file
        val updatedBuiltArtifacts = BuiltArtifactsLoaderImpl().load(
            FakeGradleDirectory(outFolder.root))

        assertThat(updatedBuiltArtifacts).isNotNull()

        assertThat(updatedBuiltArtifacts!!.applicationId).isEqualTo(builtArtifacts.applicationId)
        assertThat(updatedBuiltArtifacts.variantName).isEqualTo(builtArtifacts.variantName)
        assertThat(updatedBuiltArtifacts.artifactType).isEqualTo(SingleArtifact.APK)
        assertThat(updatedBuiltArtifacts.elements).hasSize(1)
        val updatedBuiltArtifact = updatedBuiltArtifacts.elements.first()
        assertThat(File(updatedBuiltArtifact.outputFile).name).isEqualTo("file1.xml.new")
        assertThat(updatedBuiltArtifact.versionCode).isEqualTo(123)
        assertThat(updatedBuiltArtifact.versionName).isEqualTo("version_name")
        assertThat(updatedBuiltArtifact.outputType).isEqualTo(VariantOutputConfiguration.OutputType.SINGLE)
        assertThat(updatedBuiltArtifact.filters).isEmpty()
    }

    @Test
    fun testMultipleFileTransformation() {
        tmpFolder.newFile("file1.xml").writeText("xxxhdpi")
        tmpFolder.newFile("file2.xml").writeText("xhdpi")
        tmpFolder.newFile(BuiltArtifactsImpl.METADATA_FILE_NAME).writeText(
            """{
  "version": 1,
  "artifactType": {
    "type": "MERGED_MANIFESTS",
    "kind": "Directory"
  },
  "applicationId": "com.android.test",
  "variantName": "debug",
  "elements": [
    {
      "type": "SINGLE",
      "filters": [
        {
          "filterType": "DENSITY",
          "value": "xxxhdpi"
        }
      ],
      "versionCode": 123,
      "versionName": "version_name",
      "outputFile": "file1.xml"
    },
    {
      "type": "SINGLE",
      "filters": [
        {
          "filterType": "DENSITY",
          "value": "xhdpi"
        }
      ],
      "versionCode": 123,
      "versionName": "version_name",
      "outputFile": "file2.xml"
    }
  ]
}""", Charsets.UTF_8)

        val builtArtifacts= BuiltArtifactsLoaderImpl().load(
            FakeGradleDirectory(tmpFolder.root))

        assertThat(builtArtifacts).isNotNull()
        val newBuiltArtifacts = BuiltArtifactsImpl(
            artifactType = SingleArtifact.APK,
            applicationId = builtArtifacts!!.applicationId,
            variantName = builtArtifacts.variantName,
            elements = builtArtifacts.elements.map {
                assertThat(File(it.outputFile).readText(Charsets.UTF_8)).isEqualTo(
                    it.filters.joinToString())
                it.newOutput(
                    outFolder.newFile("${File(it.outputFile).name}.new").also { file ->
                        file.writeText("updated APK : ${it.filters.joinToString()}")
                    }.toPath())
            }
        )

        newBuiltArtifacts.save(FakeGradleDirectory(outFolder.root))

        // load the new file
        val updatedBuiltArtifacts = BuiltArtifactsLoaderImpl().load(
            FakeGradleDirectory(outFolder.root))

        assertThat(updatedBuiltArtifacts).isNotNull()
        assertThat(updatedBuiltArtifacts!!.applicationId).isEqualTo(builtArtifacts.applicationId)
        assertThat(updatedBuiltArtifacts.variantName).isEqualTo(builtArtifacts.variantName)
        assertThat(updatedBuiltArtifacts.artifactType).isEqualTo(SingleArtifact.APK)
        assertThat(updatedBuiltArtifacts.elements).hasSize(2)
        updatedBuiltArtifacts.elements.forEach { builtArtifact ->
            assertThat(builtArtifact.filters).hasSize(1)
            assertThat(builtArtifact.filters.first().filterType).isEqualTo(
                FilterConfiguration.FilterType.DENSITY)
            val filterValue = builtArtifact.filters.first().identifier
            assertThat(File(builtArtifact.outputFile).readText(Charsets.UTF_8)).isEqualTo(
                "updated APK : $filterValue"
            )
        }
    }

    @Test
    fun testSimpleLoading() {
        createSimpleMetadataFile()

        val builtArtifacts= BuiltArtifactsLoaderImpl().load(
            FakeGradleDirectory(tmpFolder.root))

        assertThat(builtArtifacts).isNotNull()
        assertThat(builtArtifacts!!.artifactType).isEqualTo(InternalArtifactType.PACKAGED_MANIFESTS)
        assertThat(builtArtifacts.applicationId).isEqualTo("com.android.test")
        assertThat(builtArtifacts.variantName).isEqualTo("debug")
        assertThat(builtArtifacts.elements).hasSize(1)
        val builtArtifact = builtArtifacts.elements.first()
        assertThat(builtArtifact.outputFile).isEqualTo(
            FileUtils.toSystemIndependentPath(File(tmpFolder.root, "file1.xml").absolutePath))
        assertThat(builtArtifact.versionCode).isEqualTo(123)
        assertThat(builtArtifact.versionName).isEqualTo("version_name")
        assertThat(builtArtifact.outputType).isEqualTo(VariantOutputConfiguration.OutputType.SINGLE)
    }

    @Test
    fun testLoadingWithBaselineProfiles() {
        createMetadataFileWithBaselineProfiles()

        val builtArtifacts = BuiltArtifactsLoaderImpl().load(FakeGradleDirectory(tmpFolder.root))
        assertThat(builtArtifacts).isNotNull()
        val baselineProfiles = mutableListOf<BaselineProfileDetails>()

        val profileOneDmFiles = mutableSetOf<File>()
        val profileOneDmFile1 = tmpFolder.root.resolve("1/app-release-unsigned-1.dm")
        val profileOneDmFile2 = tmpFolder.root.resolve("1/app-release-unsigned-2.dm")
        profileOneDmFiles.add(profileOneDmFile1)
        profileOneDmFiles.add(profileOneDmFile2)
        baselineProfiles.add(BaselineProfileDetails(28, 30, profileOneDmFiles))

        val profileZeroDmFiles = mutableSetOf<File>()
        val profileZeroDmFile1 = tmpFolder.root.resolve("0/app-release-unsigned.dm")
        profileZeroDmFiles.add(profileZeroDmFile1)
        baselineProfiles.add(BaselineProfileDetails(31, 34, profileZeroDmFiles))

        val profileTwoDmFiles = mutableSetOf<File>()
        val profileTwoDmFile1 = tmpFolder.root.resolve("2/app-release-unsigned.dm")
        profileTwoDmFiles.add(profileTwoDmFile1)
        baselineProfiles.add(BaselineProfileDetails(35, null, profileTwoDmFiles))
        assertThat(builtArtifacts!!.baselineProfiles).isEqualTo(baselineProfiles)

        val baselineProfileFile1 = builtArtifacts!!.baselineProfiles?.first()
            ?.getBaselineProfileFile("app-release-unsigned-1")
        assertThat(baselineProfileFile1).isEqualTo(profileOneDmFile1)
        val baselineProfileFile2 = builtArtifacts!!.baselineProfiles?.first()
            ?.getBaselineProfileFile("app-release-unsigned-2")
        assertThat(baselineProfileFile2).isEqualTo(profileOneDmFile2)

        val baselineProfileFile3 = builtArtifacts!!.baselineProfiles?.get(1)
            ?.getBaselineProfileFile("app-release-unsigned")
        assertThat(baselineProfileFile3).isEqualTo(profileZeroDmFile1)

        val baselineProfileFile4 = builtArtifacts!!.baselineProfiles?.get(2)
            ?.getBaselineProfileFile("app-release-unsigned")
        assertThat(baselineProfileFile4).isEqualTo(profileTwoDmFile1)
    }

    private fun createSimpleMetadataFile() {
        tmpFolder.newFile("file1.xml").writeText("some manifest")
        tmpFolder.newFile(BuiltArtifactsImpl.METADATA_FILE_NAME).writeText(
            """{
  "version": 1,
  "artifactType": {
    "type": "PACKAGED_MANIFESTS",
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
      "outputFile": "file1.xml"
    }
  ]
}""", Charsets.UTF_8)
    }

    private fun createMetadataFileWithBaselineProfiles() {
        tmpFolder.newFile("file1.xml").writeText("some manifest")
        tmpFolder.newFile(BuiltArtifactsImpl.METADATA_FILE_NAME).writeText(
            """
                {
                    "version": 3,
                    "artifactType": {
                        "type": "APK",
                        "kind": "Directory"
                    },
                    "applicationId": "com.example.app",
                    "variantName": "release",
                    "elements": [
                        {
                            "type": "SINGLE",
                            "filters": [],
                            "attributes": [],
                            "versionCode": 1,
                            "versionName": "1.0",
                            "outputFile": "app-release-unsigned.apk"
                        }
                    ],
                    "elementType": "File",
                    "baselineProfiles": [
                        {
                            "minApi": 28,
                            "maxApi": 30,
                            "baselineProfiles": [
                                "1/app-release-unsigned-1.dm",
                                "1/app-release-unsigned-2.dm"
                            ]
                        },
                        {
                            "minApi": 31,
                            "maxApi": 34,
                            "baselineProfiles": [
                                "0/app-release-unsigned.dm"
                            ]
                        },
                        {
                            "minApi": 35,
                            "baselineProfiles": [
                                "2/app-release-unsigned.dm"
                            ]
                        }
                    ]
                }
            """,
            Charsets.UTF_8
        )
    }
}
