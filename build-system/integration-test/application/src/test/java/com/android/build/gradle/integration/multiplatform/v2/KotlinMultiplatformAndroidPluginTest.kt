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

package com.android.build.gradle.integration.multiplatform.v2

import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.android.build.gradle.integration.common.fixture.GradleTestProjectBuilder
import com.android.build.gradle.integration.common.truth.ApkSubject
import com.android.build.gradle.integration.common.truth.TruthHelper.assertThatApk
import com.android.build.gradle.integration.common.utils.TestFileUtils
import com.android.build.gradle.internal.scope.InternalArtifactType
import com.android.testutils.apk.Aar
import com.android.testutils.apk.Apk
import com.android.utils.FileUtils
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import org.junit.Assume
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import kotlin.io.path.pathString
import kotlin.io.path.readText

@RunWith(Parameterized::class)
class KotlinMultiplatformAndroidPluginTest(private val publishLibs: Boolean) {

    companion object {

        @JvmStatic
        @Parameterized.Parameters(name = "publishLibs={0}")
        fun getOptions() = listOf(false, true)
    }

    @get:Rule
    val project = GradleTestProjectBuilder()
        .fromTestProject("kotlinMultiplatform")
        .create()

    @Before
    fun setUpProject() {
        if (publishLibs) {
            project.publishLibs()
        }
    }

    @Test
    fun testKmpLibraryTestApkContentsWithBuildTypeSelection() {
        TestFileUtils.appendToFile(
            project.getSubproject("kmpFirstLib").ktsBuildFile,
            """
                kotlin.androidLibrary {
                    dependencyVariantSelection {
                      buildTypes.add("release")
                    }
                }
            """.trimIndent()
        )

        // TODO (b/293964676): remove withFailOnWarning(false) once KMP bug is fixed
        project.executor()
            .withFailOnWarning(false)
            .run(":kmpFirstLib:mergeAndroidInstrumentedTestJavaResource")

        val androidTestMergedRes = project.getSubproject("kmpFirstLib").getIntermediateFile(
            InternalArtifactType.MERGED_JAVA_RES.getFolderName() + "/androidInstrumentedTest/feature-kmpFirstLib.jar"
        )

        assertThat(androidTestMergedRes.exists()).isTrue()

        Apk(androidTestMergedRes).use { apk ->
            assertThat(apk.getEntry("android_lib_resource.txt").readText()).isEqualTo(
                "android lib resource\n"
            )
        }
    }

    @Test
    fun testChangingTheSourceSetTreeForAndroidUnitTests() {
        Assume.assumeFalse(publishLibs)
        TestFileUtils.searchAndReplace(
            project.getSubproject("kmpFirstLib").ktsBuildFile,
            """
                defaultSourceSetName = "androidUnitTest"
            """.trimIndent(),
            """
                defaultSourceSetName = "androidUnitTest"
                sourceSetTreeName = "unitTest"
            """.trimIndent()
        )

        TestFileUtils.appendToFile(
            project.getSubproject("kmpFirstLib").ktsBuildFile,
            """
                kotlin.androidLibrary.compilations.withType(
                    com.android.build.api.dsl.KotlinMultiplatformAndroidTestOnJvmCompilation::class.java
                ) {
                    enableCoverage = true
                }
            """.trimIndent()
        )

        TestFileUtils.appendToFile(
            project.getSubproject("kmpFirstLib").ktsBuildFile,
            """
                kotlin.sourceSets.getByName("androidUnitTest") {
                    dependencies {
                        implementation("junit:junit:4.13.2")
                    }
                }
            """.trimIndent()
        )

        project.executor()
            .run(":kmpFirstLib:createAndroidUnitTestCoverageReport")

        assertWithMessage(
            "Running android unit tests should not run common tests because they are not part of the" +
                    " same source set tree"
        ).that(
            FileUtils.join(
                project.getSubproject("kmpFirstLib").buildDir,
                "reports",
                "tests",
                "testAndroidUnitTest",
                "classes"
            ).listFiles()!!.map { it.name }
        ).containsExactly(
            "com.example.kmpfirstlib.KmpAndroidFirstLibClassTest.html",
        )
    }

    @Test
    fun testRunningUnitTests() {
        Assume.assumeFalse(publishLibs)
        TestFileUtils.appendToFile(
            project.getSubproject("kmpFirstLib").ktsBuildFile,
            """
                kotlin.androidLibrary.compilations.withType(
                    com.android.build.api.dsl.KotlinMultiplatformAndroidTestOnJvmCompilation::class.java
                ) {
                    enableCoverage = true
                }
            """.trimIndent()
        )

        project.executor()
            .run(":kmpFirstLib:createAndroidUnitTestCoverageReport")

        assertWithMessage(
            "Running kmp unit tests should run common tests as well"
        ).that(
            FileUtils.join(
                project.getSubproject("kmpFirstLib").buildDir,
                "reports",
                "tests",
                "testAndroidUnitTest",
                "classes"
            ).listFiles()!!.map { it.name }
        ).containsExactly(
            "com.example.kmpfirstlib.KmpAndroidFirstLibClassTest.html",
            "com.example.kmpfirstlib.KmpCommonFirstLibClassTest.html"
        )

        val coveragePackageFolder = FileUtils.join(
            project.getSubproject("kmpFirstLib").buildDir,
            "reports", "coverage", "test", "main", "com.example.kmpfirstlib"
        )
        assertThat(coveragePackageFolder.exists()).isTrue()

        assertThat(coveragePackageFolder.listFiles()!!.map { it.name }).containsExactly(
            "index.html",
            "index.source.html",

            "KmpCommonFirstLibClass.html",
            "KmpCommonFirstLibClass.kt.html",

            "KmpAndroidActivity.html",
            "KmpAndroidActivity.kt.html",

            "KmpAndroidFirstLibClass.html",
            "KmpAndroidFirstLibClass.kt.html",

            "KmpAndroidFirstLibJavaClass.html",
            "KmpAndroidFirstLibJavaClass.java.html",
        )

        val packageCoverageReport = FileUtils.join(
            coveragePackageFolder,
            "index.html"
        )

        val generatedCoverageReportHTML = packageCoverageReport.readLines().joinToString("\n")

        val totalCoverageMetricsContents = Regex("<tfoot>(.*?)</tfoot>")
            .find(generatedCoverageReportHTML)
        val totalCoverageInfo = Regex("<td class=\"ctr2\">(.*?)</td>")
            .find(totalCoverageMetricsContents?.groups?.first()!!.value)

        val packageCoveragePercentage = totalCoverageInfo!!.groups[1]!!.value

        assertThat(packageCoveragePercentage.trimEnd('%').toInt() > 0).isTrue()

        project.executor().run(":app:testDebugUnitTest")
    }

    @Test
    fun testAppApkContents() {
        project.executor()
            .run(":app:assembleDebug")

        project.getSubproject("app").getApk(GradleTestProject.ApkType.DEBUG).use { apk ->
            // classes from commonMain are packaged
            assertThatApk(apk).hasClass("Lcom/example/kmpfirstlib/KmpCommonFirstLibClass;")
            assertThatApk(apk).hasClass("Lcom/example/kmpsecondlib/KmpCommonSecondLibClass;")

            // classes from androidMain are packaged
            assertThatApk(apk).hasClass("Lcom/example/kmpfirstlib/KmpAndroidFirstLibClass;")
            assertThatApk(apk).hasClass("Lcom/example/kmpfirstlib/KmpAndroidFirstLibJavaClass;")
            assertThatApk(apk).hasClass("Lcom/example/kmpsecondlib/KmpAndroidSecondLibClass;")

            // transitive deps are packaged
            assertThatApk(apk).hasClass("Lcom/example/androidlib/AndroidLib;")

            assertThatApk(apk).hasClass("Lcom/example/kmpjvmonly/KmpJvmOnlyLibClass;")
            assertThatApk(apk).hasClass("Lcom/example/kmpjvmonly/KmpCommonJvmOnlyLibClass;")

            assertThatApk(apk).hasClass("Lcom/example/kmplibraryplugin/KmpLibraryPluginAndroidClass;")
            assertThatApk(apk).hasClass("Lcom/example/kmplibraryplugin/KmpLibraryPluginCommonClass;")

            assertThatApk(apk).hasClass("Lcom/example/app/AndroidApp;")

            val manifestContents = ApkSubject.getManifestContent(apk.file).joinToString("\n")
            assertThat(manifestContents).contains(
                "com.example.kmpfirstlib.KmpAndroidActivity"
            )

            assertThat(apk.getEntry("kmp_resource.txt").readText()).isEqualTo(
                "kmp resource\n"
            )

            assertThat(apk.getEntry("android_lib_resource.txt").readText()).isEqualTo(
                "android lib debug resource\n"
            )
        }
    }

    @Test
    fun testKmpLibraryAarContents() {
        project.executor()
            .run(":kmpFirstLib:assemble")

        Aar(
            project.getSubproject("kmpFirstLib").getOutputFile(
                "aar",
                "kmpFirstLib.aar"
            )
        ).use { aar ->

            assertThat(aar.getEntry("R.txt")).isNotNull()

            aar.getEntryAsZip("classes.jar").use { classesJar ->
                assertThat(classesJar.entries.map { it.pathString }).containsExactlyElementsIn(
                    listOf(
                        "/kmp_resource.txt",
                        "/com/example/kmpfirstlib/KmpCommonFirstLibClass.class",
                        "/com/example/kmpfirstlib/KmpAndroidFirstLibClass.class",
                        "/com/example/kmpfirstlib/KmpAndroidFirstLibJavaClass.class",
                        "/com/example/kmpfirstlib/KmpAndroidActivity.class",
                    )
                )

                assertThat(classesJar.getEntry("kmp_resource.txt").readText()).isEqualTo(
                    "kmp resource\n"
                )
            }

            assertThat(aar.androidManifestContentsAsString).contains("uses-sdk android:minSdkVersion=\"22\"")
            assertThat(aar.androidManifestContentsAsString).contains("package=\"com.example.kmpfirstlib\"")

            assertThat(
                aar.getEntry("META-INF/com/android/build/gradle/aar-metadata.properties").readText()
            ).contains("minAndroidGradlePluginVersion=7.2.0")
         }
    }

    @Test
    fun testKmpLibraryTestApkContents() {
        TestFileUtils.appendToFile(
            project.getSubproject("kmpFirstLib").ktsBuildFile,
            """
                kotlin.androidLibrary {
                    packaging.resources.excludes.addAll(listOf(
                        "**/*.java",
                        "junit/**",
                        "LICENSE-junit.txt"
                    ))
                }
            """.trimIndent()
        )

        project.executor()
            .run(":kmpFirstLib:assembleInstrumentedTest")

        val testApk = project.getSubproject("kmpFirstLib").getOutputFile(
            "apk", "androidTest", "main", "kmpFirstLib-androidTest.apk"
        )

        assertThat(testApk.exists()).isTrue()

        Apk(testApk).use { apk ->
            // Test apk should be signed by debug signing config
            assertThatApk(apk).containsApkSigningBlock()

            assertThatApk(apk).hasApplicationId("com.example.kmpfirstlib.test")

            // classes from commonMain are packaged
            assertThatApk(apk).hasClass("Lcom/example/kmpfirstlib/KmpCommonFirstLibClass;")
            assertThatApk(apk).hasClass("Lcom/example/kmpsecondlib/KmpCommonSecondLibClass;")

            // instrumented test classes are packaged
            assertThatApk(apk).containsClass("Lcom/example/kmpfirstlib/test/KmpAndroidFirstLibActivityTest;")

            // classes from common tests and unit tests are not packaged
            assertThatApk(apk).doesNotContainClass("Lcom/example/kmpfirstlib/KmpCommonFirstLibClassTest;")
            assertThatApk(apk).doesNotContainClass("Lcom/example/kmpfirstlib/KmpAndroidFirstLibClassTest;")

            // classes from androidMain are packaged
            assertThatApk(apk).hasClass("Lcom/example/kmpfirstlib/KmpAndroidFirstLibClass;")
            assertThatApk(apk).hasClass("Lcom/example/kmpfirstlib/KmpAndroidFirstLibJavaClass;")
            assertThatApk(apk).hasClass("Lcom/example/kmpsecondlib/KmpAndroidSecondLibClass;")

            // classes from library dependencies are packaged
            assertThatApk(apk).hasClass("Lcom/example/androidlib/AndroidLib;")
            assertThatApk(apk).hasClass("Landroidx/test/core/app/ActivityScenario;")

            // classes from jvm only project are packaged
            assertThatApk(apk).hasClass("Lcom/example/kmpjvmonly/KmpJvmOnlyLibClass;")
            assertThatApk(apk).hasClass("Lcom/example/kmpjvmonly/KmpCommonJvmOnlyLibClass;")

            // classes from kmp + library plugin are packaged
            assertThatApk(apk).hasClass("Lcom/example/kmplibraryplugin/KmpLibraryPluginAndroidClass;")
            assertThatApk(apk).hasClass("Lcom/example/kmplibraryplugin/KmpLibraryPluginCommonClass;")

            // resources from dependencies are packaged
            assertThatApk(apk).contains("resources.arsc")

            val manifestContents = ApkSubject.getManifestContent(apk.file).joinToString("\n")
            assertThat(manifestContents).contains(
                "com.example.kmpfirstlib.KmpAndroidActivity"
            )

            assertThat(apk.getEntry("kmp_resource.txt").readText()).isEqualTo(
                "kmp resource\n"
            )

            assertThat(apk.getEntry("android_lib_resource.txt").readText()).isEqualTo(
                "android lib debug resource\n"
            )

            // all contents
            assertThat(
                apk.entries.map { it.pathString }.filterNot {
                    it.startsWith("/res") || it.endsWith(".kotlin_builtins") ||
                            it.startsWith("/META-INF") ||
                            (it.startsWith("/classes") && it.endsWith(".dex"))
                }
            ).containsExactlyElementsIn(
                listOf(
                    "/AndroidManifest.xml",
                    "/kmp_resource.txt",
                    "/android_lib_resource.txt",
                )
            )
        }

        val apkIdeRedirectFile = FileUtils.join(
            project.getSubproject("kmpFirstLib").intermediatesDir,
            "apk_ide_redirect_file",
            "androidInstrumentedTest",
            "redirect.txt"
        )
        assertThat(apkIdeRedirectFile.exists()).isTrue()
        assertThat(apkIdeRedirectFile.readText())
            .contains("listingFile=../../../outputs/apk/androidTest/main/output-metadata.json")
    }
}
