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

package com.android.build.gradle.integration.desugar

import com.android.SdkConstants
import com.android.build.gradle.integration.common.fixture.DESUGAR_DEPENDENCY_VERSION
import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.android.build.gradle.integration.common.fixture.app.HelloWorldApp
import com.android.build.gradle.integration.common.fixture.app.MultiModuleTestProject
import com.android.build.gradle.integration.common.utils.TestFileUtils
import com.android.build.gradle.internal.scope.InternalArtifactType
import com.android.tools.profgen.Apk
import com.android.tools.profgen.ArtProfile
import com.android.tools.profgen.ObfuscationMap
import com.android.tools.profgen.dumpProfile
import com.android.utils.FileUtils
import com.google.common.truth.Truth
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.io.ByteArrayInputStream

class L8ArtProfileRewritingTest {

    private val app =
        HelloWorldApp.forPluginWithNamespace("com.android.application", "com.example.app")

    @get:Rule
    val project = GradleTestProject.builder()
        .fromTestApp(
            MultiModuleTestProject.builder()
                .subproject(":app", app)
                .build()
        ).create()

    @Before
    fun setUp() {
        project.getSubproject(":app").also {
            it.buildFile.appendText(
                """
                    android {
                        compileOptions.coreLibraryDesugaringEnabled = true
                        buildFeatures {
                            buildConfig true
                        }
                        buildTypes {
                            release {
                                minifyEnabled false
                                proguardFiles("proguard-rules.pro")
                            }
                        }
                    }
                    dependencies {
                        coreLibraryDesugaring "$DESUGAR_DEPENDENCY"
                    }

                    android.defaultConfig.minSdkVersion = 23
                    android.defaultConfig.multiDexEnabled = true
                    android.compileOptions.sourceCompatibility = JavaVersion.VERSION_1_8
                    android.compileOptions.targetCompatibility = JavaVersion.VERSION_1_8
                """.trimIndent()
            )
        }

        val mainBaselineProfileFileContent =
            """
                HSPLj${'$'}/util/stream/Stream;->**(**)**
                HSPLcom/example/helloworld/Data;->doLambda()V
            """.trimIndent()

        val proguardRulesFileContent =
            """
                -keep class com.example.helloworld.Data { <methods>; }
            """.trimIndent()
        FileUtils.createFile(
            project.file("app/proguard-rules.pro"), proguardRulesFileContent
        )

        FileUtils.createFile(
            project.file("app/src/main/baselineProfiles/file.txt"), mainBaselineProfileFileContent
        )
        FileUtils.createFile(
            project.file("app/src/main/java/com/example/helloworld/Data.java"),
            """
                package com.example.helloworld;

                import java.util.ArrayList;
                import java.util.List;
                import java.util.stream.Collectors;

                class Data {
                    public static void doLambda() {
                        List<Integer> list = new ArrayList<>();
                        for (int i = 0; i < 100; i += 1) {
                          list.add(i);
                        }

                        List<Integer> even = list.stream()
                          .filter(integer -> integer % 2 == 0)
                          .collect(Collectors.toList());

                        System.out.println(even.size());
                    }
                }
            """.trimIndent()
        )
    }

    @Test
    fun testArtProfileRewriting() {
        testL8Rewriting()
    }

    @Test
    fun testArtProfileRewritingWithMinifyEnabled() {
        // Validate the L8 task works with minifyEnabled true
        TestFileUtils.appendToFile(project.getSubproject(":app").buildFile,
            """
                android.buildTypes.release.minifyEnabled = true
            """.trimIndent()
        )

        testL8Rewriting()
    }

    private fun testL8Rewriting() {
        val result = project.executor().run(":app:assembleRelease")

        Truth.assertThat(result.didWorkTasks).contains(":app:expandReleaseL8ArtProfileWildcards")

        val l8Profile = FileUtils.join(
            project.getSubproject(":app").buildDir,
            SdkConstants.FD_INTERMEDIATES,
            InternalArtifactType.L8_ART_PROFILE.getFolderName(),
            "release",
            "l8DexDesugarLibRelease",
            SdkConstants.FN_ART_PROFILE
        )

        Truth.assertThat(l8Profile.readText()).isNotEmpty()
        Truth.assertThat(l8Profile.readText()).contains("HSPLj\$/util/stream/Stream;->")
        Truth.assertThat(l8Profile.readText()).doesNotContain("helloworld")

        // Validate L8 and profgen sources are present in the binary art profile
        val binaryProfile = FileUtils.join(
            project.getSubproject(":app").buildDir,
            SdkConstants.FD_INTERMEDIATES,
            InternalArtifactType.BINARY_ART_PROFILE.getFolderName(),
            "release",
            "compileReleaseArtProfile",
            SdkConstants.FN_BINARY_ART_PROFILE
        )
        val artProfileFromBinary = ArtProfile(ByteArrayInputStream(binaryProfile.readBytes()))
        Truth.assertThat(artProfileFromBinary).isNotNull()
        val profileDump = StringBuilder()
        val apkFile = project.getSubproject(":app")
            .getApk(GradleTestProject.ApkType.RELEASE).file.toFile()
        val profgenApk = Apk(apkFile, apkFile.name)
        if (artProfileFromBinary != null) {
            dumpProfile(profileDump, artProfileFromBinary, profgenApk, ObfuscationMap.Empty)
        }
        Truth.assertThat(profileDump.indexOf("HSPLcom/example/helloworld/Data;->doLambda()V"))
            .isNotEqualTo(-1)
        Truth.assertThat(profileDump.indexOf("HSPLj\$/util/stream/Stream;->"))
            .isNotEqualTo(-1)
    }

    companion object {
        const val DESUGAR_DEPENDENCY = "com.android.tools:desugar_jdk_libs:$DESUGAR_DEPENDENCY_VERSION"
    }
}
