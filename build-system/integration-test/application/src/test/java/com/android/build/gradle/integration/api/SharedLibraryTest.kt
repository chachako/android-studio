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

package com.android.build.gradle.integration.api

import com.android.build.gradle.integration.common.fixture.GradleBuildResult
import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.android.build.gradle.integration.common.fixture.testprojects.PluginType
import com.android.build.gradle.integration.common.fixture.testprojects.createGradleProject
import com.android.build.gradle.integration.common.fixture.testprojects.prebuilts.setUpHelloWorld
import com.android.build.gradle.integration.common.truth.TruthHelper.assertThat
import com.android.build.gradle.integration.common.utils.TestFileUtils
import com.android.build.gradle.internal.publishing.AndroidArtifacts.PATH_SHARED_LIBRARY_RESOURCES_APK
import com.android.build.gradle.options.BooleanOption
import com.android.builder.internal.aapt.v2.Aapt2Exception
import com.android.testutils.MavenRepoGenerator
import com.android.testutils.generateAarWithContent
import com.google.common.truth.Truth
import junit.framework.TestCase.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.Description
import org.junit.runners.model.Statement
import java.io.File
import kotlin.io.path.readBytes
import kotlin.reflect.KClass

class SharedLibraryTest {

    @JvmField
    @Rule
    val sharedTokenProject = createGradleProject(name = "shared_token_project") {
        rootProject {
            plugins.add(PluginType.ANDROID_APP)
            android {
                compileSdk = 34
                applicationId = "com.android_token_test_lib"
                addFile(
                    "src/main/res/values/strings.xml",
                    "<resources>\n" +
                            "<string name=\"oem_token_demo\">TOKEN_DEMO</string>\n" +
                            "</resources>"
                )
                addFile(
                    "src/main/res/values/values.xml",
                    "<resources></resources>"
                )
            }
        }
    }

    private val sharedLibAar: MavenRepoGenerator.Library by lazy {
        MavenRepoGenerator.Library(
            mavenCoordinate = "test:name:0.1",
            packaging = "aar",
            artifact = generateAarWithContent(
                packageName = "com.android.tokens_test_lib",
                extraFiles =
                listOf(
                    PATH_SHARED_LIBRARY_RESOURCES_APK to sharedTokenProject.getApk(GradleTestProject.ApkType.DEBUG).file.readBytes()
                )
            )
        )
    }

    @JvmField
    @Rule
    val consumerProject = createGradleProject("consumer_project_1") {
        rootProject {
            plugins.add(PluginType.ANDROID_APP)
            android {
                applicationId = "com.android.token_test"
                setUpHelloWorld()
                addFile(
                    "src/main/res/values/strings.xml",
                    "<resources>\n" +
                            "<string name=\"app_name\">Name</string>\n" +
                            "<string name=\"oem_token_demo_test\">@*com.android_token_test_lib:string/oem_token_demo</string>\n" +
                            "</resources>"
                )
            }
        }
    }

    @Before
    fun setup() {
        sharedTokenProject.execute("assembleDebug")
    }

    @Test
    fun `token string resource reference is resolved`() {
        consumerProject.useRepoWithLibrary(sharedLibAar)
        consumerProject.addDependency(sharedLibAar)
        consumerProject.setGradleProperty(BooleanOption.SUPPORT_OEM_TOKEN_LIBRARIES, true)
        val result = consumerProject.executor().run("assembleDebug")
        assertNull(result.exception)
    }

    @Test
    fun `token resolution fails when shared library support not enabled`() {
        consumerProject.useRepoWithLibrary(sharedLibAar)
        consumerProject.addDependency(sharedLibAar)
        val result = consumerProject.executor().expectFailure().run("assembleDebug")
        result.assertExceptionCause(
            Aapt2Exception::class,
            """
                Android resource linking failed
                pkg.name-mergeDebugResources-2:/values/values.xml:4: error: resource com.android_token_test_lib:string/oem_token_demo not found.
                error: failed linking references.
            """.trimIndent()
        )
    }

    @Test
    fun `token resolution fails when dependency not included`() {
        consumerProject.setGradleProperty(BooleanOption.SUPPORT_OEM_TOKEN_LIBRARIES, true)
        val result = consumerProject.executor().expectFailure().run("assembleDebug")
        result.assertExceptionCause(
            Aapt2Exception::class,
            """
                Android resource linking failed
                pkg.name-mergeDebugResources-2:/values/values.xml:4: error: resource com.android_token_test_lib:string/oem_token_demo not found.
                error: failed linking references.
            """.trimIndent()
        )
        consumerProject.useRepoWithLibrary(sharedLibAar)
        consumerProject.addDependency(sharedLibAar)
        val resultAfter = consumerProject.executor().run("assembleDebug")
        assertThat(resultAfter.exception).isNull()
    }

    private fun GradleTestProject.setGradleProperty(booleanOption: BooleanOption, state: Boolean) {
        TestFileUtils.appendToFile(gradlePropertiesFile, "${booleanOption.propertyName}=${state}")
    }

    private fun GradleTestProject.addDependency(library: MavenRepoGenerator.Library) {
        TestFileUtils.searchAndReplace(
            buildFile,
            "dependencies {",
            "dependencies {\n implementation(\"${library.mavenCoordinate}\")"
        )
    }

    private fun GradleTestProject.useRepoWithLibrary(repo: MavenRepoGenerator.Library) {
        val file = File(buildFile.parent, "maven_repo")
        MavenRepoGenerator(libraries = listOf(repo)).generate(file.toPath())
        TestFileUtils.searchAndReplace(
            settingsFile, "repositories {",
            "repositories {\n" +
                    "maven {\n" +
                    "  url = uri(\"${file.toURI()}\")\n" +
                    "  metadataSources {\n" +
                    "    mavenPom()\n" +
                    "    artifact()\n" +
                    "  }\n" +
                    " }\n"
        )
    }

    private fun GradleBuildResult.assertExceptionCause(kClass: KClass<*>, message: String) {
        var cause = exception?.cause
        while (cause != null) {
            if (cause.javaClass.canonicalName == kClass.java.canonicalName) {
                Truth.assertThat(cause.message?.trimMargin()).isEqualTo(message)
                return
            } else if (cause.cause != null) {
                cause = cause.cause
            } else {
                throw AssertionError("Cannot assert for exception type ${kClass}.")
            }
        }
    }
}
