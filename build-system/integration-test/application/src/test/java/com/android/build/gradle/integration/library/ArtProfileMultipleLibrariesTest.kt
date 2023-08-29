/*
 * Copyright (C) 2021 The Android Open Source Project
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

package com.android.build.gradle.integration.library

import com.android.SdkConstants
import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.android.build.gradle.integration.common.fixture.app.HelloWorldApp
import com.android.build.gradle.integration.common.fixture.app.MultiModuleTestProject
import com.android.build.gradle.integration.library.ArtProfileSingleLibraryTest.Companion.aabEntryName
import com.android.build.gradle.integration.library.ArtProfileSingleLibraryTest.Companion.aarEntryName
import com.android.build.gradle.integration.library.ArtProfileSingleLibraryTest.Companion.apkEntryName
import com.android.build.gradle.internal.scope.InternalArtifactType
import com.android.tools.profgen.ArtProfile
import com.android.tools.profgen.HumanReadableProfile
import com.android.utils.FileUtils
import com.google.common.truth.Truth
import junit.framework.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import java.io.ByteArrayInputStream
import java.io.File
import java.util.jar.JarFile
import java.util.zip.ZipEntry

@RunWith(Parameterized::class)
class ArtProfileMultipleLibrariesTest(
    private val withArtProfileR8Rewriting: Boolean,
    private val minifyEnabled: Boolean
) {

    companion object {
        @Parameterized.Parameters(
            name = "withArtProfileR8Rewriting_{0}_minifyEnabled_{1}"
        )
        @JvmStatic
        fun setups() =
            listOf(
                arrayOf(true, true),
                arrayOf(false, true),
                arrayOf(false, false),
            )
    }

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val app =
        HelloWorldApp.forPluginWithNamespace("com.android.application", "com.example.app")
    private val lib1 =
        HelloWorldApp.forPluginWithNamespace("com.android.library", "com.example.lib1")
    private val lib2 =
        HelloWorldApp.forPluginWithNamespace("com.android.library", "com.example.lib2")
    private val lib3 =
        HelloWorldApp.forPluginWithNamespace("com.android.library", "com.example.lib3")

    @get:Rule
    val project =
        GradleTestProject.builder()
            .fromTestApp(
                MultiModuleTestProject.builder()
                    .subproject(":app", app)
                    .subproject(":lib1", lib1)
                    .subproject(":lib2", lib2)
                    .subproject(":lib3", lib3)
                    .dependency(app, lib1)
                    .dependency(app, lib2)
                    .dependency(app, lib3)
                    .build()
            ).create()

    @Test
    fun testMultipleLibraryArtProfileMerging() {
        testMultipleLibrariesAndOptionalApplicationArtProfileMerging(false)
    }

    @Test
    fun testMultipleLibraryWithApplicationArtProfileMerging() {
        testMultipleLibrariesAndOptionalApplicationArtProfileMerging(true)
    }

    private fun testMultipleLibrariesAndOptionalApplicationArtProfileMerging(
        addApplicationProfile: Boolean
    ) {

        val app = project.getSubproject(":app").also {
            it.buildFile.appendText(
                """
                        android {
                            defaultConfig {
                                minSdkVersion = 28
                            }
                        }
                        androidComponents {
                            // turn on minification if we want r8 to rewrite the art-profile
                            beforeVariants(selector().withBuildType("release"), { variant ->
                                variant.setMinifyEnabled($minifyEnabled);
                            })
                            // and turn on the feature if necessary
                            onVariants(selector().withName("release"), { variant ->
                                variant.experimentalProperties.put(
                                    "android.experimental.art-profile-r8-rewriting",
                                    $withArtProfileR8Rewriting
                                )
                            })
                        }
                    """.trimIndent()
            )
        }

        val applicationBaselineProfContent =
            """
                    Lcom/example/app/HelloWorld;
                    HSPLcom/example/app/HelloWorld;->onCreate(Landroid/os/Bundle;)V
                """.trimIndent()

        if (addApplicationProfile) {
            val appAndroidAssets = app.mainSrcDir.parentFile
            appAndroidAssets.mkdir()

            File(
                appAndroidAssets,
                SdkConstants.FN_ART_PROFILE
            ).writeText(
                applicationBaselineProfContent
            )
        }

        val libraryBaselineProfContents = mutableListOf<String>()
        var expectedMergedFileContent = ""
        var expectedMergedFileContentBeforeWildcardTask = ""
        var expectedMergedRewrittenFileContent = ""
        for (i in 1..3) {
            val library = project.getSubproject(":lib$i").also {
                it.buildFile.appendText(
                    """
                        android {
                            defaultConfig {
                                consumerProguardFile "consumer-rules.pro"
                            }
                        }
                    """.trimIndent()
                )
            }
            val androidAssets = library.mainSrcDir.parentFile
            androidAssets.mkdir()

            val baselineProfContent =
                """
                    Lcom/example/lib$i/Foo;
                    HSPLcom/example/lib$i/Foo;->m(II)I
                    Lcom/example/lib$i/Bar;
                    HSPLcom/example/lib$i/Bar;->m()V
                    Lcom/example/lib$i/Baz;
                    HSPLcom/example/lib$i/Baz;->m()V
                """.trimIndent()

            val minifyEnabledBaselineProfContent =
                """
                    Lcom/example/lib$i/Bar;
                    HSPLcom/example/lib$i/Bar;->m()V
                    Lcom/example/lib$i/Baz;
                    HSPLcom/example/lib$i/Baz;->m()V
                    Lcom/example/lib$i/Foo;
                    HSPLcom/example/lib$i/Foo;->m(II)I
                """.trimIndent()

            libraryBaselineProfContents.add(baselineProfContent)
            File(androidAssets, SdkConstants.FN_ART_PROFILE).writeText(baselineProfContent)
            expectedMergedFileContent = if (minifyEnabled) {
                expectedMergedFileContent.plus(minifyEnabledBaselineProfContent.plus("\n"))
            } else {
                expectedMergedFileContent.plus(baselineProfContent.plus("\n"))
            }
            expectedMergedFileContentBeforeWildcardTask =
                expectedMergedFileContentBeforeWildcardTask.plus(baselineProfContent.plus("\n"))
            expectedMergedRewrittenFileContent =
                    expectedMergedRewrittenFileContent.plus(
                            """
                                Lcom/example/lib$i/a;
                                HSPLcom/example/lib$i/a;->a(II)I
                            """.trimIndent().plus("\n"))

            File(library.mainSrcDir, "com/example/lib$i/Foo.java").writeText(
                    """
                        package com.example.lib$i;
                        public class Foo {
                            public int m(int i, int j) {
                                return i;
                            }
                        }
                    """.trimIndent()
            )
            File(library.mainSrcDir, "com/example/lib$i/Bar.java").writeText(
                    """
                        package com.example.lib$i;
                        public class Bar {
                            public void m() {}
                        }
                    """.trimIndent()
            )
            File(library.projectDir, "consumer-rules.pro").writeText(
                    """
                        -keep,allowobfuscation class com.example.lib$i.Foo {
                            int m(int, int);
                        }
                        -keeppackagenames com.example.lib$i
                        -checkdiscard class com.example.lib$i.Bar
                    """.trimIndent()
            )
        }
        if (addApplicationProfile) {
            if (minifyEnabled) {
                expectedMergedFileContent =
                    applicationBaselineProfContent.plus("\n$expectedMergedFileContent")
                expectedMergedRewrittenFileContent =
                    applicationBaselineProfContent.plus("\n$expectedMergedRewrittenFileContent")
            } else {
                expectedMergedFileContent =
                    expectedMergedFileContent.plus(applicationBaselineProfContent.plus("\n"))
                expectedMergedRewrittenFileContent =
                    expectedMergedRewrittenFileContent.plus(
                        applicationBaselineProfContent.plus("\n"))
            }
            expectedMergedFileContentBeforeWildcardTask =
                expectedMergedFileContentBeforeWildcardTask.plus(applicationBaselineProfContent.plus("\n"))
        }
        expectedMergedFileContent = expectedMergedFileContent.trimEnd()
        expectedMergedRewrittenFileContent = expectedMergedRewrittenFileContent.trimEnd()
        expectedMergedFileContentBeforeWildcardTask = expectedMergedFileContentBeforeWildcardTask.trimEnd()

        val result = project.executor()
                .run(
                    ":lib1:bundleReleaseAar",
                    ":lib2:bundleReleaseAar",
                    ":lib3:bundleReleaseAar",
                    ":app:assembleRelease",
                    ":app:bundleRelease",
                )
        Truth.assertThat(result.failedTasks).isEmpty()

        for (i in 1..3) {
            val libFile = FileUtils.join(
                    project.getSubproject(":lib$i").buildDir,
                    SdkConstants.FD_INTERMEDIATES,
                    InternalArtifactType.LIBRARY_ART_PROFILE.getFolderName(),
                    "release",
                    SdkConstants.FN_ART_PROFILE,
            )
            val expectedBaselineProfContent = libraryBaselineProfContents.get(i - 1)
            Truth.assertThat(libFile.readText()).isEqualTo(expectedBaselineProfContent)

            // check packaging.
            project.getSubproject(":lib$i").getAar("release") {
                ArtProfileSingleLibraryTest.checkAndroidArtifact(tempFolder, it, aarEntryName) { fileContent ->
                    Truth.assertThat(fileContent).isEqualTo(
                            expectedBaselineProfContent.toByteArray()
                    )
                }
            }
        }

        // if minifyEnabled is true, check that the merged art-profile file exists in a separate
        // folder (as it is the input to the R8 Task)
        val mergedFilePreR8 = FileUtils.join(
            project.getSubproject(":app").buildDir,
            SdkConstants.FD_INTERMEDIATES,
            InternalArtifactType.MERGED_ART_PROFILE.getFolderName(),
            "release",
            SdkConstants.FN_ART_PROFILE
        )
        Truth.assertThat(mergedFilePreR8.readText())
            .isEqualTo(expectedMergedFileContentBeforeWildcardTask)

        if (minifyEnabled) {
            val mergedFile = FileUtils.join(
                project.getSubproject(":app").buildDir,
                SdkConstants.FD_INTERMEDIATES,
                InternalArtifactType.R8_ART_PROFILE.getFolderName(),
                "release",
                SdkConstants.FN_ART_PROFILE
            )
            Truth.assertThat(
                mergedFile.readText().trimEnd() // R8 seems to add a newline at the end
            ).isEqualTo(
                if (withArtProfileR8Rewriting) expectedMergedRewrittenFileContent
                else expectedMergedFileContent
            )
            Truth.assertThat(
                    HumanReadableProfile(mergedFile) {
                        fail(it)
                    }
            ).isNotNull()
        } else {
            Truth.assertThat(
                HumanReadableProfile(mergedFilePreR8) {
                    fail(it)
                }
            ).isNotNull()
        }

        val binaryProfile = FileUtils.join(
                project.getSubproject(":app").buildDir,
                SdkConstants.FD_INTERMEDIATES,
                InternalArtifactType.BINARY_ART_PROFILE.getFolderName(),
                "release",
                SdkConstants.FN_BINARY_ART_PROFILE,
        )
        Truth.assertThat(
                ArtProfile(ByteArrayInputStream(binaryProfile.readBytes()))
        ).isNotNull()

        val binaryProfileMetadata = FileUtils.join(
            project.getSubproject(":app").buildDir,
            SdkConstants.FD_INTERMEDIATES,
            InternalArtifactType.BINARY_ART_PROFILE_METADATA.getFolderName(),
            "release",
            SdkConstants.FN_BINARY_ART_PROFILE_METADATA,
        )

        Truth.assertThat(binaryProfileMetadata.exists())

        // check APK packaging.
        project.getSubproject(":app").getApk(GradleTestProject.ApkType.RELEASE).also {
            ArtProfileSingleLibraryTest.checkAndroidArtifact(tempFolder, it, apkEntryName) { fileContent ->
                Truth.assertThat(ArtProfile(ByteArrayInputStream(fileContent))).isNotNull()
            }
            JarFile(it.file.toFile()).use { jarFile ->
                val artProfileEntry = jarFile.getEntry(
                    "${SdkConstants.FN_BINART_ART_PROFILE_FOLDER_IN_APK}/${SdkConstants.FN_BINARY_ART_PROFILE}")
                Truth.assertThat(artProfileEntry.method).isEqualTo(ZipEntry.STORED)

                val artProfileMetadataEntry = jarFile.getEntry(
                    "${SdkConstants.FN_BINART_ART_PROFILE_FOLDER_IN_APK}/${SdkConstants.FN_BINARY_ART_PROFILE_METADATA}")
                Truth.assertThat(artProfileMetadataEntry.method).isEqualTo(ZipEntry.STORED)
            }
        }

        // check Bundle packaging.
        project.getSubproject(":app").getBundle(GradleTestProject.ApkType.RELEASE).also {
            ArtProfileSingleLibraryTest.checkAndroidArtifact(tempFolder, it, aabEntryName) { fileContent ->
                Truth.assertThat(ArtProfile(ByteArrayInputStream(fileContent))).isNotNull()
            }
        }
    }
}
