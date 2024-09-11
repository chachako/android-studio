/*
 * Copyright (C) 2020 The Android Open Source Project
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

package com.android.build.gradle.integration.application

import com.android.SdkConstants
import com.android.build.gradle.integration.common.fixture.BaseGradleExecutor
import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.android.build.gradle.integration.common.fixture.GradleTestProject.ApkType.Companion.DEBUG
import com.android.build.gradle.integration.common.fixture.GradleTestProject.ApkType.Companion.RELEASE
import com.android.build.gradle.integration.common.fixture.GradleTestProject.Companion.builder
import com.android.build.gradle.internal.scope.InternalArtifactType
import com.android.build.gradle.internal.scope.getOutputDir
import com.android.build.gradle.options.BooleanOption
import com.android.build.shrinker.DummyContent.TINY_PNG
import com.android.build.shrinker.DummyContent.TINY_PROTO_CONVERTED_TO_BINARY_XML
import com.android.testutils.TestUtils
import com.android.testutils.truth.PathSubject.assertThat
import com.android.testutils.truth.ZipFileSubject.assertThat
import com.android.tools.apk.analyzer.AaptInvoker
import com.android.utils.FileUtils
import com.android.utils.StdLogger
import com.google.common.io.ByteStreams
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import java.io.File
import java.util.stream.Collectors
import java.util.zip.ZipEntry
import java.util.zip.ZipFile

@RunWith(Parameterized::class)
class ResourceShrinkerTest(private val r8IntegratedResourceShrinking: Boolean) {

    companion object {

        @Parameterized.Parameters(name = "r8IntegratedResourceShrinking_{0}")
        @JvmStatic
        fun parameters() = listOf(true, false)
    }

    @get:Rule
    var project = builder().fromTestProject("shrink")
        .addGradleProperty(BooleanOption.R8_INTEGRATED_RESOURCE_SHRINKING, r8IntegratedResourceShrinking)
        .create()

    @get:Rule
    var projectWithDynamicFeatureModules = builder().fromTestProject("shrinkDynamicFeatureModules")
        .addGradleProperty(BooleanOption.R8_INTEGRATED_RESOURCE_SHRINKING, r8IntegratedResourceShrinking)
        .create()

    private val testAapt2 = TestUtils.getAapt2().toFile().absoluteFile

    @Test
    fun `shrink resources for APKs`() {
        project.executor()
            .withConfigurationCaching(BaseGradleExecutor.ConfigurationCaching.ON)
            .run(
                ":assembleRelease",
                ":webview:assembleRelease",
                ":keep:assembleRelease",
                ":assembleDebug"
            )

        // Check that unused resources are replaced in shrunk apk.
        val removedFiles = listOf(
                "res/drawable-hdpi-v4/notification_bg_normal.9.png",
                "res/drawable-hdpi-v4/notification_bg_low_pressed.9.png",
                "res/drawable-hdpi-v4/notification_bg_low_normal.9.png",
                "res/drawable-hdpi-v4/notify_panel_notification_icon_bg.png",
                "res/drawable-hdpi-v4/notification_bg_normal_pressed.9.png",
                "res/drawable-mdpi-v4/notification_bg_normal.9.png",
                "res/drawable-mdpi-v4/notification_bg_low_pressed.9.png",
                "res/drawable-mdpi-v4/notification_bg_low_normal.9.png",
                "res/drawable-mdpi-v4/notify_panel_notification_icon_bg.png",
                "res/drawable-mdpi-v4/notification_bg_normal_pressed.9.png",
                "res/drawable-xhdpi-v4/notification_bg_normal.9.png",
                "res/drawable-xhdpi-v4/notification_bg_low_pressed.9.png",
                "res/drawable-xhdpi-v4/notification_bg_low_normal.9.png",
                "res/drawable-xhdpi-v4/notify_panel_notification_icon_bg.png",
                "res/drawable-xhdpi-v4/notification_bg_normal_pressed.9.png",
                "res/drawable-v21/notification_action_background.xml",
                "res/drawable/force_remove.xml",
                "res/drawable/notification_bg.xml",
                "res/drawable/notification_bg_low.xml",
                "res/drawable/notification_icon_background.xml",
                "res/drawable/notification_tile_bg.xml",
                "res/drawable/unused9.xml",
                "res/drawable/unused10.xml",
                "res/drawable/unused11.xml",
                "res/layout-v16/notification_template_custom_big.xml",
                "res/layout-v17/notification_template_lines_media.xml",
                "res/layout-v17/notification_action_tombstone.xml",
                "res/layout-v17/notification_template_media.xml",
                "res/layout-v17/notification_template_big_media_custom.xml",
                "res/layout-v17/notification_template_custom_big.xml",
                "res/layout-v17/notification_template_big_media_narrow_custom.xml",
                "res/layout-v17/notification_template_big_media_narrow.xml",
                "res/layout-v17/notification_template_big_media.xml",
                "res/layout-v17/notification_action.xml",
                "res/layout-v17/notification_template_media_custom.xml",
                "res/layout-v21/notification_template_custom_big.xml",
                "res/layout-v21/notification_action.xml",
                "res/layout-v21/notification_action_tombstone.xml",
                "res/layout-v21/notification_template_icon_group.xml",
                "res/layout/lib_unused.xml",
                "res/layout/marked_as_used_by_old.xml",
                "res/layout/notification_action.xml",
                "res/layout/notification_action_tombstone.xml",
                "res/layout/notification_media_action.xml",
                "res/layout/notification_media_cancel_action.xml",
                "res/layout/notification_template_big_media.xml",
                "res/layout/notification_template_big_media_custom.xml",
                "res/layout/notification_template_big_media_narrow.xml",
                "res/layout/notification_template_big_media_narrow_custom.xml",
                "res/layout/notification_template_icon_group.xml",
                "res/layout/notification_template_lines_media.xml",
                "res/layout/notification_template_media.xml",
                "res/layout/notification_template_media_custom.xml",
                "res/layout/notification_template_part_chronometer.xml",
                "res/layout/notification_template_part_time.xml",
                "res/layout/unused1.xml",
                "res/layout/unused2.xml",
                "res/layout/unused14.xml",
                "res/layout/unused13.xml",
                "res/menu/unused12.xml",
                "res/raw/keep.xml"
        )
        checkUnusedResourcesAreReplacedInApk(project, removedFiles)

        val debugApk = project.getApk(DEBUG)
        val releaseApk = project.getApk(RELEASE)
        val debugResourcePaths = getZipPaths(debugApk.file.toFile())
        val releaseResourcePaths = getZipPaths(releaseApk.file.toFile())
        val numberOfDebugApkEntries = 119
        val debugMetaFiles =
                listOf("META-INF/CERT.RSA", "META-INF/CERT.SF", "META-INF/MANIFEST.MF")
        assertThat(debugResourcePaths.size)
                .isEqualTo(numberOfDebugApkEntries)
        assertThat(debugResourcePaths).containsAtLeastElementsIn(debugMetaFiles)
        val numberOfReleaseApkEntries = 120 // include the version-control-metadata.properties. file
        assertThat(releaseResourcePaths.size)
                .isEqualTo(numberOfReleaseApkEntries -
                                   (debugMetaFiles.size + removedFiles.size))

        // Check that unused resources are removed in project with web views and all web view
        // resources are marked as used.
        checkUnusedResourcesAreReplacedInApk(
            project.getSubproject("webview"), listOf(
                "res/raw/unused_icon.png",
                "res/raw/unused_index.html",
                "res/xml/my_xml.xml"
            )
        )
        // Check that replaced files has proper dummy content.
        assertThat(project.getSubproject("webview").getApk(RELEASE).file.toFile()) {
            it.doesNotContain("res/0t.png")
            it.doesNotContain("res/VT.html")
            it.doesNotContain("res/jd.xml")
        }
        // Check that zip entities have proper methods.
        assertThat(getZipPathsWithMethod(
                project.getSubproject("webview").getShrunkBinaryResources()))
                .containsExactly(
                        "  stored  resources.arsc",
                        "deflated  AndroidManifest.xml",
                        "deflated  res/raw/unknown",
                        "deflated  res/drawable/used1.xml",
                        "  stored  res/raw/used_icon.png",
                        "  stored  res/raw/used_icon2.png",
                        "deflated  res/raw/used_index.html",
                        "deflated  res/raw/used_index2.html",
                        "deflated  res/raw/used_index3.html",
                        "deflated  res/layout/used_layout1.xml",
                        "deflated  res/layout/used_layout2.xml",
                        "deflated  res/layout/used_layout3.xml",
                        "deflated  res/raw/used_script.js",
                        "deflated  res/raw/used_styles.css",
                        "deflated  res/layout/webview.xml"
                )

        // Check that unused resources that are referenced with Resources.getIdentifier are removed
        // in case shrinker mode is set to 'strict'.
        checkUnusedResourcesAreReplacedInApk(
            project.getSubproject("keep"),
            listOf(
                "res/raw/keep.xml",
                "res/layout/unused1.xml",
                "res/layout/unused2.xml"
            )
        )
        // Ensure that report file is created and near mapping file
        assertThat(project.file("build/outputs/mapping/release/mapping.txt")).exists()
        assertThat(project.file("build/outputs/mapping/release/resources.txt").readLines()).hasSize(570)
    }

    private fun checkUnusedResourcesAreReplacedInApk(
        project: GradleTestProject,
        unusedResources: List<String>,
        multiApkSplitName: String? = null
    ) {
        val linkedResources = project.getLinkedProtoResources(multiApkSplitName)
        val shrunkResources = project.getShrunkProtoResources(multiApkSplitName)
        val releaseApk = project.getApk(multiApkSplitName, RELEASE).file.toFile()

        assertThat(getZipPaths(linkedResources)).containsAtLeastElementsIn(unusedResources)
        assertThat(getZipPaths(shrunkResources)).containsNoneIn(unusedResources)
        assertThat(getZipPaths(releaseApk)).containsNoneIn(unusedResources)
    }

    private fun checkUnusedResourcesAreReplacedInBundle(
        project: GradleTestProject,
        unusedResources: List<String>
    ) {
        assertThat(getZipPaths(project.getOriginalBundle())).containsAtLeastElementsIn(unusedResources)
        assertThat(getZipPaths(project.getShrunkBundle())).containsNoneIn(unusedResources)
    }

    @Test
    fun `optimize shrunk resources`() {
        project.executor().run(":webview:assembleRelease")

        val releaseApk = project.getSubproject("webview").getApk(RELEASE).file.toFile()

        val expectedOptimizeApkContents = listOf(
                "classes.dex",
                "resources.arsc",
                "AndroidManifest.xml",
                "res/0g.js",
                "res/1B.png",
                "res/95.html",
                "res/Fr.xml",
                "res/GM",
                "res/Hv.xml",
                "res/Ta.css",
                "res/_M.xml",
                "res/_S.xml",
                "res/g0.xml",
                "res/jL.html",
                "res/mZ.html",
                "res/vy.png",
                "META-INF/com/android/build/gradle/app-metadata.properties",
                "META-INF/version-control-info.textproto"
        )

        // As AAPT optimize shortens file paths including shrunk resource file names,
        // the shrunk apk must include the obfuscated files.
        val optimizeApkFileNames = getZipPaths(releaseApk)
        assertThat(optimizeApkFileNames).containsExactlyElementsIn(expectedOptimizeApkContents)

        assertThat(getZipPathsWithMethod(releaseApk))
                .containsAtLeast(
                        "deflated  AndroidManifest.xml",
                        "  stored  resources.arsc",
                        "deflated  classes.dex"
                )

        assertThat(getZipEntriesWithContent(releaseApk, TINY_PROTO_CONVERTED_TO_BINARY_XML))
                .hasSize(0)
        assertThat(getZipEntriesWithContent(releaseApk, ByteArray(0))).hasSize(0)
        assertThat(getZipEntriesWithContent(releaseApk, TINY_PNG)).hasSize(0)
    }

    @Test
    fun `shrink resources for bundles`() {
        project.executor()
            .withConfigurationCaching(BaseGradleExecutor.ConfigurationCaching.ON)
            .run(
                ":bundleRelease",
                ":packageDebugUniversalApk",
                ":packageReleaseUniversalApk",
                ":webview:bundleRelease",
                ":keep:bundleRelease"
            )

        // Check that unused resources are replaced in shrunk bundle.
        val replacedFiles = listOf(
                "res/drawable-hdpi-v4/notification_bg_normal.9.png",
                "res/drawable-hdpi-v4/notification_bg_low_pressed.9.png",
                "res/drawable-hdpi-v4/notification_bg_low_normal.9.png",
                "res/drawable-hdpi-v4/notify_panel_notification_icon_bg.png",
                "res/drawable-hdpi-v4/notification_bg_normal_pressed.9.png",
                "res/drawable-mdpi-v4/notification_bg_normal.9.png",
                "res/drawable-mdpi-v4/notification_bg_low_pressed.9.png",
                "res/drawable-mdpi-v4/notification_bg_low_normal.9.png",
                "res/drawable-mdpi-v4/notify_panel_notification_icon_bg.png",
                "res/drawable-mdpi-v4/notification_bg_normal_pressed.9.png",
                "res/drawable-xhdpi-v4/notification_bg_normal.9.png",
                "res/drawable-xhdpi-v4/notification_bg_low_pressed.9.png",
                "res/drawable-xhdpi-v4/notification_bg_low_normal.9.png",
                "res/drawable-xhdpi-v4/notify_panel_notification_icon_bg.png",
                "res/drawable-xhdpi-v4/notification_bg_normal_pressed.9.png",
                "res/drawable-v21/notification_action_background.xml",
                "res/drawable/force_remove.xml",
                "res/drawable/notification_bg.xml",
                "res/drawable/notification_bg_low.xml",
                "res/drawable/notification_icon_background.xml",
                "res/drawable/notification_tile_bg.xml",
                "res/drawable/unused9.xml",
                "res/drawable/unused10.xml",
                "res/drawable/unused11.xml",
                "res/layout-v16/notification_template_custom_big.xml",
                "res/layout-v17/notification_template_lines_media.xml",
                "res/layout-v17/notification_action_tombstone.xml",
                "res/layout-v17/notification_template_media.xml",
                "res/layout-v17/notification_template_big_media_custom.xml",
                "res/layout-v17/notification_template_custom_big.xml",
                "res/layout-v17/notification_template_big_media_narrow_custom.xml",
                "res/layout-v17/notification_template_big_media_narrow.xml",
                "res/layout-v17/notification_template_big_media.xml",
                "res/layout-v17/notification_action.xml",
                "res/layout-v17/notification_template_media_custom.xml",
                "res/layout-v21/notification_template_custom_big.xml",
                "res/layout-v21/notification_action.xml",
                "res/layout-v21/notification_action_tombstone.xml",
                "res/layout-v21/notification_template_icon_group.xml",
                "res/layout/lib_unused.xml",
                "res/layout/marked_as_used_by_old.xml",
                "res/layout/notification_action.xml",
                "res/layout/notification_action_tombstone.xml",
                "res/layout/notification_media_action.xml",
                "res/layout/notification_media_cancel_action.xml",
                "res/layout/notification_template_big_media.xml",
                "res/layout/notification_template_big_media_custom.xml",
                "res/layout/notification_template_big_media_narrow.xml",
                "res/layout/notification_template_big_media_narrow_custom.xml",
                "res/layout/notification_template_icon_group.xml",
                "res/layout/notification_template_lines_media.xml",
                "res/layout/notification_template_media.xml",
                "res/layout/notification_template_media_custom.xml",
                "res/layout/notification_template_part_chronometer.xml",
                "res/layout/notification_template_part_time.xml",
                "res/layout/unused1.xml",
                "res/layout/unused2.xml",
                "res/layout/unused14.xml",
                "res/layout/unused13.xml",
                "res/menu/unused12.xml",
                "res/raw/keep.xml"
        )
        checkUnusedResourcesAreReplacedInBundle(project, replacedFiles.map { "base/$it" })

        // Check that unused resources are removed in release APK and leave as is in debug one.
        val unusedResources = listOf(
            "META-INF/BNDLTOOL.RSA",
            "META-INF/BNDLTOOL.SF",
            "META-INF/MANIFEST.MF"
        )
        assertThat(getZipPaths(project.getBundleUniversalApk(DEBUG).file.toFile()))
            .containsAtLeastElementsIn(unusedResources)
        assertThat(getZipPaths(project.getBundleUniversalApk(RELEASE).file.toFile()))
            .containsNoneIn(unusedResources)

        // Check that unused resources are removed in project with web views and all web view
        // resources are marked as used.
        checkUnusedResourcesAreReplacedInBundle(
            project.getSubproject("webview"),
            listOf(
                "base/res/raw/unused_icon.png",
                "base/res/raw/unused_index.html",
                "base/res/xml/my_xml.xml"
            )
        )

        // Check that unused resources that are referenced with Resources.getIdentifier are removed
        // in case shrinker mode is set to 'strict'.
        checkUnusedResourcesAreReplacedInBundle(
            project.getSubproject("keep"),
            listOf(
                "base/res/raw/keep.xml",
                "base/res/layout/unused1.xml",
                "base/res/layout/unused2.xml"
            )
        )
    }

    @Test
    fun `shrink resources for bundles with dynamic features`() {
        projectWithDynamicFeatureModules.executor().run(":base:bundleRelease")

        // Check that unused resources are replaced in shrunk bundle.
        checkUnusedResourcesAreReplacedInBundle(
            projectWithDynamicFeatureModules.getSubproject("base"), listOf(
                "feature/res/drawable/feat_unused.png",
                "feature/res/drawable/discard_from_feature_1.xml",
                "feature/res/layout/feat_unused_layout.xml",
                "feature/res/raw/feat_keep.xml",
                "feature/res/raw/webpage.html",
                "base/res/drawable/discard_from_feature_2.xml",
                "base/res/drawable/force_remove.xml",
                "base/res/drawable/unused5.9.png",
                "base/res/drawable/unused9.xml",
                "base/res/drawable/unused10.xml",
                "base/res/drawable/unused11.xml",
                "base/res/layout/unused1.xml",
                "base/res/layout/unused2.xml",
                "base/res/layout/unused13.xml",
                "base/res/layout/unused14.xml",
                "base/res/menu/unused12.xml",
                "base/res/raw/keep.xml"
            )
        )

        // Check that replaced files release bundle have proper dummy content.
        val releaseBundle = projectWithDynamicFeatureModules.getSubproject("base")
                .getOutputFile("bundle", "release", "base-release.aab")

        assertThat(releaseBundle) {
            it.doesNotContain("feature/res/drawable/feat_unused.png")
            it.doesNotContain("feature/res/layout/feat_unused_layout.xml")
            it.doesNotContain("feature/res/raw/webpage.html")
            it.doesNotContain("base/res/layout/unused1.xml")
            it.doesNotContain("base/res/raw/keep.xml")
            it.doesNotContain("base/res/drawable/unused5.9.png")
        }

        // Ensure that report file is created and near mapping file
        assertThat(
            projectWithDynamicFeatureModules.getSubproject("base")
                .file("build/outputs/mapping/release/mapping.txt")
        ).exists()
        assertThat(
            projectWithDynamicFeatureModules.getSubproject("base")
                .file("build/outputs/mapping/release/resources.txt").readLines()
        ).hasSize(173)
    }

    @Test
    fun `shrink resources for APKs with dynamic features`() {
        projectWithDynamicFeatureModules.executor().run(":base:assembleRelease")

        // Check that unused resources are replaced in shrunk bundle.
        checkUnusedResourcesAreReplacedInApk(
            projectWithDynamicFeatureModules.getSubproject("base"),
            unusedResources = listOf(
                "res/drawable/discard_from_feature_2.xml",
                "res/drawable/force_remove.xml",
                "res/drawable/from_raw_feat.xml",
                "res/drawable/unused10.xml",
                "res/drawable/unused11.xml",
                "res/drawable/unused9.xml",
                "res/layout/unused1.xml",
                "res/layout/unused13.xml",
                "res/layout/unused14.xml",
                "res/layout/unused2.xml",
                "res/layout/used_from_feature_2.xml",
                "res/menu/unused12.xml",
            ) + if (r8IntegratedResourceShrinking) {
                emptyList()
            } else {
                // This resource is used by a feature module, so the fact that it appears in this
                // list of unusedResources is unexpected. This is a limitation of the legacy
                // resource shrinking pipeline (r8IntegratedResourceShrinking = false).
                listOf("res/layout/used_from_feature_1.xml")
            }
        )
    }

    @Test
    fun `shrink resources for multi-APKs`() {
        val abiSplitsSubproject = project.getSubproject("abisplits")
        FileUtils.createFile(
                FileUtils.join(
                        abiSplitsSubproject.mainResDir,
                        SdkConstants.FD_RES_XML,
                        "signing_certificates.xml"),
                """<?xml version="1.0" encoding="utf-8"?>
                <parent_tag>
                    <signing_certificate
                        name="Resource 1">
                        Line One
                        Line Two
                        Line Three
                    </signing_certificate>
                </parent_tag>
                """
        )

        // Use the signing configuration xml resource, to replacing with empty contents
        // during resource shrinking.
        val usedActivity = FileUtils.join(
                abiSplitsSubproject.mainSrcDir,
                "com", "android", "tests", "shrink", "UsedActivity.java"
        )
        val usedActivtyContentsWithXmlUsage =
                usedActivity.readText().replace("setContentView(R.layout.used);",
                        "setContentView(R.layout.used);\n" +
                                "getApplicationContext().getResources().getXml(R.xml.signing_certificates);"
                )
        usedActivity.writeText(usedActivtyContentsWithXmlUsage)

        abiSplitsSubproject.buildFile.appendText(
                "android {\n" +
                        "    splits {\n" +
                        "        abi {\n" +
                        "            enable true\n" +
                        "            reset()\n" +
                        "            include \"x86\", \"x86_64\", \"armeabi-v7a\", \"arm64-v8a\"\n" +
                        "            universalApk true\n" +
                        "        }\n" +
                        "    }\n" +
                        "  }\n"
        )

        project.executor()
            .withConfigurationCaching(BaseGradleExecutor.ConfigurationCaching.ON)
            .run(":abisplits:assembleRelease")

        // Check that unused resources are removed from all split APKs, including universal APK
        for (split in listOf("universal", "arm64-v8a", "armeabi-v7a", "x86", "x86_64")) {
            checkUnusedResourcesAreReplacedInApk(
                project.getSubproject("abisplits"),
                listOf("res/layout/unused.xml"),
                split
            )
        }

        // Regression test for b/22833352 (Check signing cert content is correctly compiled)
        val shrunkUniversalApk =
                FileUtils.join(abiSplitsSubproject.outputDir,
                        "apk",
                        "release",
                        "abisplits-universal-release-unsigned.apk")
        val logger = StdLogger(StdLogger.Level.VERBOSE)
        val result = AaptInvoker(testAapt2.toPath(), logger)
                .getXmlStrings(shrunkUniversalApk, "res/V7.xml")
        assertThat(result.map(String::trim)).containsExactly(
                "String pool of 5 unique UTF-8 non-sorted strings, 5 entries and 0 styles using 184 bytes:",
                "String #0 :  Line One",
                "Line Two",
                "Line Three",
                "String #1 : Resource 1",
                "String #2 : name",
                "String #3 : parent_tag",
                "String #4 : signing_certificate"
        )
    }

    private fun getZipPaths(zipFile: File, transform: (path: ZipEntry) -> String = { it.name }) =
            ZipFile(zipFile).use { zip ->
                zip.stream().map(transform).collect(Collectors.toList())
            }

    private fun getZipPathsWithMethod(zipFile: File) = getZipPaths(zipFile) {
        val method = when (it.method) {
            ZipEntry.STORED -> "  stored"
            ZipEntry.DEFLATED -> "deflated"
            else -> " unknown"
        }
        "$method  ${it.name}"
    }

    private fun getZipEntriesWithContent(zipFile: File, content: ByteArray) =
            ZipFile(zipFile).use { zip ->
                zip.stream()
                        .filter {
                            ByteStreams.toByteArray(zip.getInputStream(it))
                                    .contentEquals(content)
                        }
                        .map { it.name }
                        .collect(Collectors.toList())
            }

    private fun GradleTestProject.getOriginalBundle() =
            getIntermediateFile(
                    "intermediary_bundle",
                    "release",
                    "packageReleaseBundle",
                    "intermediary-bundle.aab"
            )

    private fun GradleTestProject.getShrunkBundle() =
            getIntermediateFile(
                    "intermediary_bundle",
                    "release",
                    "shrinkBundleReleaseResources",
                    "intermediary-bundle.aab"
            )

    private fun GradleTestProject.getLinkedProtoResources(splitName: String? = null) =
        InternalArtifactType.LINKED_RESOURCES_PROTO_FORMAT.getOutputDir(buildDir)
            .resolve("release/convertLinkedResourcesToProtoRelease")
            .resolve(listOfNotNull("linked-resources-proto-format", splitName, "release.ap_").joinToString("-"))

    private fun GradleTestProject.getShrunkProtoResources(splitName: String? = null): File {
        val task = if (r8IntegratedResourceShrinking) {
            "minifyReleaseWithR8"
        } else {
            "shrinkReleaseRes"
        }
        return InternalArtifactType.SHRUNK_RESOURCES_PROTO_FORMAT.getOutputDir(buildDir)
            .resolve("release/$task")
            .resolve(listOfNotNull("shrunk-resources-proto-format", splitName, "release.ap_").joinToString("-"))
    }

    private fun GradleTestProject.getShrunkBinaryResources(splitName: String? = null): File =
        InternalArtifactType.SHRUNK_RESOURCES_BINARY_FORMAT.getOutputDir(buildDir)
            .resolve("release/convertShrunkResourcesToBinaryRelease")
            .resolve(listOfNotNull("shrunk-resources-binary-format", splitName, "release.ap_").joinToString("-"))
}
