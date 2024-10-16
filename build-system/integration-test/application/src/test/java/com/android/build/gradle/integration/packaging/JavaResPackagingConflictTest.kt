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

package com.android.build.gradle.integration.packaging

import com.android.build.gradle.integration.common.fixture.BaseGradleExecutor
import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.android.build.gradle.integration.common.fixture.testprojects.PluginType
import com.android.build.gradle.integration.common.fixture.testprojects.TestProjectBuilder
import com.android.build.gradle.integration.common.fixture.testprojects.createGradleProject
import com.android.build.gradle.integration.common.fixture.testprojects.createGradleProjectBuilder
import com.android.build.gradle.integration.common.fixture.testprojects.prebuilts.setUpHelloWorld
import com.android.builder.merge.DuplicateRelativeFileException
import com.android.testutils.MavenRepoGenerator
import com.android.testutils.TestInputsGenerator
import com.android.testutils.TestInputsGenerator.jarWithEmptyClasses
import com.google.common.truth.Truth
import org.junit.Rule
import org.junit.Test
import java.nio.file.Path

private val basicSetupAction: TestProjectBuilder.() -> Unit = {
    subProject(":app") {
        plugins.add(PluginType.ANDROID_APP)
        android {
            setUpHelloWorld()
            defaultCompileSdk()
        }

        dependencies {
            implementation(project(":library"))
            implementation(project(":library2"))
        }
    }
    subProject(":library") {
        plugins.add(PluginType.ANDROID_LIB)
        android {
            defaultCompileSdk()
        }
    }
    subProject(":library2") {
        plugins.add(PluginType.ANDROID_LIB)
        android {
            defaultCompileSdk()
        }
    }
}

class JavaResPackagingConflictTest {
    @JvmField
    @Rule
    val testBuild = createGradleProject(null, basicSetupAction)

    @Test
    fun testConflictBetweenLibraries() {
        val library = testBuild.getSubproject(":library")
        val library2 = testBuild.getSubproject(":library2")

        library.addFile("src/main/resources/foo.txt", "lib_content")
        library2.addFile("src/main/resources/foo.txt", "lib2_content")

        val result = testBuild.executor()
            .expectFailure()
            .run(":app:mergeDebugJavaResource")

        val originCause = findCause(result.exception!!)
        Truth.assertThat(originCause).named("Cause as DuplicateRelativeFileException").isNotNull()
        Truth.assertThat(originCause?.message).isEqualTo("""
2 files found with path 'foo.txt' from inputs:
 - project(":library")
 - project(":library2")
Adding a packaging block may help, please refer to
https://developer.android.com/reference/tools/gradle-api/com/android/build/api/dsl/Packaging
for more information
            """.trimIndent())
    }
}

class JavaResPackagingConflictWithIncludedBuildTest {

    @JvmField
    @Rule
    val testBuild = createGradleProject {
        basicSetupAction()
        subProject(":app") {
            dependencies {
                implementation("included.build:anotherLib:1.0")
            }
        }
        includedBuild("includedBuild") {
            subProject(":anotherLib") {
                plugins.add(PluginType.ANDROID_LIB)
                group = "included.build"
                version = "1.0"
                android {
                    defaultCompileSdk()
                }
                // FIXME can't query for this subproject via GradleTestProject.
                addFile("src/main/resources/foo.txt", "lib3_content")
            }
        }
    }

    @Test
    fun testConflictBetweenLibraries() {
        val library = testBuild.getSubproject(":library")
        val library2 = testBuild.getSubproject(":library2")

        library.addFile("src/main/resources/foo.txt", "lib_content")
        library2.addFile("src/main/resources/foo.txt", "lib2_content")

        val result = testBuild.executor()
            // PROJECT_ISOLATION mode is not supported with includedBuilds
            .withConfigurationCaching(BaseGradleExecutor.ConfigurationCaching.ON)
            .expectFailure()
            .run(":app:mergeDebugJavaResource")

        val originCause = findCause(result.exception!!)
        Truth.assertThat(originCause).named("Cause as DuplicateRelativeFileException").isNotNull()
        Truth.assertThat(originCause?.message).isEqualTo("""
3 files found with path 'foo.txt' from inputs:
 - project(":library") - Build: :
 - project(":library2") - Build: :
 - project(":anotherLib") - Build: :includedBuild
Adding a packaging block may help, please refer to
https://developer.android.com/reference/tools/gradle-api/com/android/build/api/dsl/Packaging
for more information
            """.trimIndent())
    }
}

class JavaResPackagingConflictWithExternalLibrariesTest {

    @JvmField
    @Rule
    val testBuild = createGradleProjectBuilder {
        basicSetupAction()
        subProject(":app") {
            dependencies {
                implementation("com.example:jar:1")
            }
        }
    }.withAdditionalMavenRepo(
        MavenRepoGenerator(libraries = listOf(
            MavenRepoGenerator.Library(
                "com.example:jar:1",
                TestInputsGenerator.jarWithTextEntries("foo.txt" to "blah")
            )
        ))
    ).create()

    @Test
    fun testConflictBetweenLibraries() {
        val library = testBuild.getSubproject(":library")
        val library2 = testBuild.getSubproject(":library2")

        library.addFile("src/main/resources/foo.txt", "lib_content")
        library2.addFile("src/main/resources/foo.txt", "lib2_content")

        val result = testBuild.executor()
            .expectFailure()
            .run(":app:mergeDebugJavaResource")

        val originCause = findCause(result.exception!!)
        Truth.assertThat(originCause).named("Cause as DuplicateRelativeFileException").isNotNull()
        Truth.assertThat(originCause?.message).isEqualTo("""
3 files found with path 'foo.txt' from inputs:
 - project(":library")
 - project(":library2")
 - com.example:jar:1
Adding a packaging block may help, please refer to
https://developer.android.com/reference/tools/gradle-api/com/android/build/api/dsl/Packaging
for more information
            """.trimIndent())
    }
}

private fun findCause(e: Throwable): Throwable? {
    var cause: Throwable? = e
    while (cause != null && cause.cause != null) {
        cause = cause.cause
        if (cause?.javaClass?.canonicalName == DuplicateRelativeFileException::class.qualifiedName) {
            return cause
        }
    }

    return null
}


private fun GradleTestProject.addFile(relativePath: String, content: String) {
    val file = this.file(relativePath)
    file.parentFile.mkdirs()
    file.writeText(content)
}
