/*
 * Copyright (C) 2024 The Android Open Source Project
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

import com.android.build.gradle.integration.common.fixture.BaseGradleExecutor
import com.android.build.gradle.integration.common.fixture.GradleTaskExecutor
import com.android.build.gradle.integration.common.fixture.GradleTestProjectBuilder
import com.android.build.gradle.integration.common.utils.TestFileUtils
import com.android.build.gradle.internal.scope.InternalArtifactType
import com.android.testutils.apk.Aar
import com.android.testutils.truth.PathSubject
import com.android.testutils.truth.ZipFileSubject
import com.android.utils.FileUtils
import com.google.common.truth.Truth
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import kotlin.io.path.readText

class KotlinMultiplatformResourcesTest {

    @get:Rule
    val project = GradleTestProjectBuilder()
        .fromTestProject("kotlinMultiplatform")
        .create()

    @Before
    fun setUp() {
        TestFileUtils.appendToFile(
            project.getSubproject("kmpFirstLib").ktsBuildFile,
            """
                kotlin.androidLibrary {
                    experimentalProperties["android.experimental.kmp.enableAndroidResources"] = true
                }
            """.trimIndent()
        )

        FileUtils.writeToFile(
            project.getSubproject("kmpFirstLib").file("src/androidMain/res/values/strings.xml"),
            """
                <resources>
                    <string name="kmp_lib_string">lib string</string>
                </resources>
            """.trimIndent()
        )

        FileUtils.writeToFile(
            project.getSubproject("kmpFirstLib")
                .file("src/androidMain/kotlin/com/example/kmpfirstlib/UseR.kt"),
            """
                package com.example.kmpfirstlib;

                class UseR {
                    fun getStringResourceValue() = R.string.kmp_lib_string
                }
            """.trimIndent()
        )
    }

    @Test
    fun testKmpLibraryResourceTasksExecuted() {
        val result = executor().run(":kmpFirstLib:assemble")
        Truth.assertThat(result.didWorkTasks).containsAtLeastElementsIn(
            listOf(
                ":kmpFirstLib:packageAndroidMainResources",
                ":kmpFirstLib:parseAndroidMainLocalResources",
                ":kmpFirstLib:generateAndroidMainRFile"
            )
        )
    }

    @Test
    fun testLibraryAarContents() {
        executor().run(":kmpFirstLib:assemble")

        Aar(
            project.getSubproject("kmpFirstLib")
                .getOutputFile("aar", "kmpFirstLib.aar")
        ).use { aar ->
            PathSubject.assertThat(aar.getEntry("R.txt")).isNotNull()
            Truth.assertThat(
                aar.getEntry("R.txt").readText()
            ).contains("int string kmp_lib_string 0x0")

            val values = aar.getEntry("res/values/values.xml")
            Truth.assertThat(values.readText()).isEqualTo(
            "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
                    "<resources>\n" +
                    "    <string name=\"kmp_lib_string\">lib string</string>\n" +
                    "</resources>"
            )
        }
    }

    @Test
    fun checkResourcesArtifacts() {
        executor().run(":kmpFirstLib:assemble")

        val lib = project.getSubproject(":kmpFirstLib")

        val rDef = lib.getIntermediateFile("local_only_symbol_list", "androidMain", "parseAndroidMainLocalResources", "R-def.txt")
        PathSubject.assertThat(rDef).contains("string kmp_lib_string")

        val compileR = lib.getIntermediateFile("compile_r_class_jar", "androidMain", "generateAndroidMainRFile", "R.jar")
        PathSubject.assertThat(compileR).exists()

        val compileRTxt = lib.getIntermediateFile("compile_symbol_list", "androidMain", "generateAndroidMainRFile", "R.txt")
        PathSubject.assertThat(compileRTxt).exists()
        PathSubject.assertThat(compileRTxt).contains("int string kmp_lib_string 0x0")
    }

    @Test
    fun testRclassPackagedInCompileClassesJar() {
        executor().run(":kmpFirstLib:bundleAndroidMainClassesToCompileJar")

        val classesJar = project.getSubproject(":kmpFirstLib")
            .getIntermediateFile(
                InternalArtifactType.COMPILE_LIBRARY_CLASSES_JAR.getFolderName()
                        + "/androidMain/bundleAndroidMainClassesToCompileJar/classes.jar"
            )
        ZipFileSubject.assertThat(
            classesJar
        ) { it: ZipFileSubject ->
            it.contains("/com/example/kmpfirstlib/UseR.class")
            it.contains("/com/example/kmpfirstlib/R.class")
            it.contains("/com/example/kmpfirstlib/R\$string.class")
        }
    }

    @Test
    fun testAppConsumeKmpResource() {
        FileUtils.writeToFile(
            project.getSubproject("app")
                .file("src/main/java/com/example/app/UseKmpResource.java"),
            """
                package com.example.app;

                public class UseKmpResource {
                    public static int getKmpLibStringValue() {
                        return com.example.kmpfirstlib.R.string.kmp_lib_string;
                    }
                }
            """.trimIndent()
        )

        executor().run(":app:assembleDebug")
    }

    private fun executor(): GradleTaskExecutor {
        return project.executor()
            .withConfigurationCaching(BaseGradleExecutor.ConfigurationCaching.ON)
    }
}
