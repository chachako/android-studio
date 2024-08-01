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

package com.android.build.gradle.integration.kotlin

import com.android.build.gradle.integration.common.fixture.BaseGradleExecutor
import com.android.build.gradle.integration.common.fixture.GradleTaskExecutor
import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.android.build.gradle.integration.common.fixture.GradleTestProject.Companion.VERSION_CATALOG
import com.android.build.gradle.integration.common.utils.TestFileUtils
import com.android.testutils.TestUtils
import com.android.testutils.apk.Apk
import com.android.testutils.truth.PathSubject.assertThat
import com.google.common.truth.Truth
import org.junit.Assume
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import java.io.File

/**
 * Similar to [TestFixturesTest], but using a project with kotlin test fixtures
 */
@RunWith(Parameterized::class)
class TestFixturesKotlinTest(private val kotlinVersion: String) {

    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "kotlinVersion_{0}")
        fun parameters() = listOf(TestUtils.KOTLIN_VERSION_FOR_TESTS, "1.9.22")
    }

    @get:Rule
    val project: GradleTestProject =
        GradleTestProject.builder().fromTestProject("testFixturesKotlinApp").create()

    @Before
    fun before() {
        TestFileUtils.searchAndReplace(
            project.projectDir.parentFile.resolve(VERSION_CATALOG),
            "version('kotlinVersion', '${TestUtils.KOTLIN_VERSION_FOR_TESTS}')",
            "version('kotlinVersion', '$kotlinVersion' )"
        )
    }

    private fun setUpProject(publishJavaLib: Boolean, publishAndroidLib: Boolean) {
        if (publishJavaLib) {
            TestFileUtils.searchAndReplace(
                project.getSubproject(":app").buildFile,
                "project(\":javaLib\")",
                "'com.example.javaLib:javaLib:1.0'"
            )

            TestFileUtils.searchAndReplace(
                project.getSubproject(":appTests").buildFile,
                "project(\":javaLib\")",
                "'com.example.javaLib:javaLib:1.0'"
            )
        }


        if (publishAndroidLib) {
            TestFileUtils.searchAndReplace(
                project.getSubproject(":app").buildFile,
                "project(\":lib\")",
                "'com.example.lib:lib:1.0'"
            )

            TestFileUtils.searchAndReplace(
                project.getSubproject(":lib2").buildFile,
                "project(\":lib\")",
                "'com.example.lib:lib:1.0'"
            )

            TestFileUtils.searchAndReplace(
                project.getSubproject(":appTests").buildFile,
                "project(\":lib\")",
                "'com.example.lib:lib:1.0'"
            )
        }

        if (publishJavaLib) {
            TestFileUtils.appendToFile(project.getSubproject(":javaLib").buildFile,
                """
                publishing {
                    repositories {
                        maven {
                            url = uri("../testrepo")
                        }
                    }
                    publications {
                        release(MavenPublication) {
                            from components.java
                            groupId = 'com.example.javaLib'
                            artifactId = 'javaLib'
                            version = '1.0'
                        }
                    }
                }

                // required for testFixtures publishing
                group = 'com.example.javaLib'
            """.trimIndent()
            )
        }

        if (publishAndroidLib) {
            TestFileUtils.appendToFile(project.getSubproject(":lib").buildFile,
                """
                android {
                    publishing {
                        singleVariant("release")
                    }
                }

                afterEvaluate {
                    publishing {
                        repositories {
                            maven {
                                url = uri("../testrepo")
                            }
                        }
                        publications {
                            release(MavenPublication) {
                                from components.release
                                groupId = 'com.example.lib'
                                artifactId = 'lib'
                                version = '1.0'
                            }
                        }
                    }
                }

                // required for testFixtures publishing
                group = 'com.example.lib'
            """.trimIndent()
            )
        }

        if (publishJavaLib) {
            executor()
                .run(":javaLib:publish")
        }

        if (publishAndroidLib) {
            executor()
                .run(":lib:publish")
        }
    }

    @Test
    fun `library consumes local test fixtures`() {
        executor()
            .run(":lib:testDebugUnitTest")
    }

    @Test
    fun `verify library test fixtures resources dependency on main resources`() {
        executor()
            .run(":lib:verifyReleaseTestFixturesResources")
    }

    @Test
    fun `verify library resources dependency on test fixtures resources from local project`() {
        setUpProject(
            publishJavaLib = false,
            publishAndroidLib = false
        )
        executor()
            .run(":lib2:verifyReleaseResources")
    }

    @Test
    fun `verify library resources dependency on test fixtures resources from published lib`() {
        setUpProject(
            publishJavaLib = false,
            publishAndroidLib = true
        )
        executor()
            .run(":lib2:verifyReleaseResources")
    }

    @Test
    fun `verify library test dependency on test fixtures resources from local project`() {
        setUpProject(
            publishJavaLib = false,
            publishAndroidLib = false
        )
        executor()
            .run(":lib2:testDebugUnitTest")
    }

    @Test
    fun `verify library test dependency on test fixtures resources from published lib`() {
        setUpProject(
            publishJavaLib = false,
            publishAndroidLib = true
        )
        executor()
            .run(":lib2:testDebugUnitTest")
    }

    @Test
    fun `app consumes local, java and android library test fixtures`() {
        setUpProject(
            publishJavaLib = false,
            publishAndroidLib = false
        )
        executor()
            .run(":app:testDebugUnitTest")
    }

    @Test
    fun `app consumes local, published java and android library test fixtures`() {
        setUpProject(
            publishJavaLib = true,
            publishAndroidLib = true
        )
        executor()
            .run(":app:testDebugUnitTest")
    }

    @Test
    fun `app consumes android library test fixtures published using new publishing dsl`() {
        setUpProject(
            publishJavaLib = false,
            publishAndroidLib = true
        )
        executor()
            .run(":app:testDebugUnitTest")
    }

    @Test
    fun `publish android library main variant without its test fixtures`() {
        TestFileUtils.appendToFile(
            project.getSubproject(":lib").buildFile,
            """

                afterEvaluate {
                    components.release.withVariantsFromConfiguration(
                        configurations.releaseTestFixturesVariantReleaseApiPublication) { skip() }
                    components.release.withVariantsFromConfiguration(
                        configurations.releaseTestFixturesVariantReleaseRuntimePublication) { skip() }
                }

            """.trimIndent()
        )
        setUpProject(
            publishAndroidLib = true,
            publishJavaLib = true
        )
        val testFixtureAar = "testrepo/com/example/lib/lib/1.0/lib-1.0-test-fixtures.aar"
        val mainVariantAar = "testrepo/com/example/lib/lib/1.0/lib-1.0.aar"
        assertThat(project.projectDir.resolve(testFixtureAar)).doesNotExist()
        assertThat(project.projectDir.resolve(mainVariantAar)).exists()
    }

    @Test
    fun `lint analyzes local and library module testFixtures sources`() {
        setUpProjectForLint(
            ignoreTestFixturesSourcesInApp = false
        )
        setUpProject(
            publishAndroidLib = false,
            publishJavaLib = false
        )
        executor().run(":app:lintRelease")
        val reportFile = File(project.getSubproject("app").projectDir, "lint-results.txt")
        assertThat(reportFile).exists()
        assertThat(reportFile).containsAllOf(
            "AppInterfaceTester.kt:21: Error: STOPSHIP comment found;",
            "LibResourcesTester.java:35: Error: Missing permissions required by LibResourcesTester.methodWithUnavailablePermission: android.permission.ACCESS_COARSE_LOCATION [MissingPermission]"
        )
    }

    @Test
    fun `lint ignores local testFixtures sources`() {
        setUpProjectForLint(
            ignoreTestFixturesSourcesInApp = true
        )
        setUpProject(
            publishAndroidLib = false,
            publishJavaLib = false
        )
        executor().run(":app:lintRelease")
        val reportFile = File(project.getSubproject("app").projectDir, "lint-results.txt")
        assertThat(reportFile).exists()
        assertThat(reportFile).containsAllOf(
            "LibResourcesTester.java:35: Error: Missing permissions required by LibResourcesTester.methodWithUnavailablePermission: android.permission.ACCESS_COARSE_LOCATION [MissingPermission]"
        )
    }

    @Test
    fun `test plugin consumes test fixtures`() {
        setUpProject(
            publishAndroidLib = false,
            publishJavaLib = false
        )
        useAndroidX()

        executor().run(":appTests:packageDebug")

        testExclusionInTestApk(
            testApk = project.getSubproject(":appTests").getApk(GradleTestProject.ApkType.DEBUG),
            expectLibAndJavaLibClassesToBeIncluded = true,
        )
    }

    @Test
    fun `test plugin consumes published test fixtures`() {
        setUpProject(
            publishAndroidLib = true,
            publishJavaLib = true
        )
        useAndroidX()

        executor().run(":appTests:packageDebug")

        testExclusionInTestApk(
            testApk = project.getSubproject(":appTests").getApk(GradleTestProject.ApkType.DEBUG),
            expectLibAndJavaLibClassesToBeIncluded = true,
        )
    }

    @Test
    fun `test plugin excludes main lib classes but includes test fixtures`() {
        setUpProject(
            publishAndroidLib = false,
            publishJavaLib = false
        )
        useAndroidX()

        TestFileUtils.searchAndReplace(
            project.getSubproject(":app").buildFile,
            "compileOnly",
            "implementation"
        )

        executor().run(":appTests:packageDebug")

        testExclusionInTestApk(
            testApk = project.getSubproject(":appTests").getApk(GradleTestProject.ApkType.DEBUG),
            expectLibAndJavaLibClassesToBeIncluded = false,
        )
    }

    @Test
    fun `instrumentation tests consume test fixtures`() {
        setUpProject(
            publishAndroidLib = false,
            publishJavaLib = false
        )
        useAndroidX()

        executor().run(":app:packageDebugAndroidTest")

        testExclusionInTestApk(
            testApk = project.getSubproject(":app").getApk(GradleTestProject.ApkType.ANDROIDTEST_DEBUG),
            expectLibAndJavaLibClassesToBeIncluded = true,
        )
    }

    @Test
    fun `instrumentation tests consume published test fixtures`() {
        setUpProject(
            publishAndroidLib = true,
            publishJavaLib = true
        )
        useAndroidX()

        executor().run(":app:packageDebugAndroidTest")

        testExclusionInTestApk(
            testApk = project.getSubproject(":app").getApk(GradleTestProject.ApkType.ANDROIDTEST_DEBUG),
            expectLibAndJavaLibClassesToBeIncluded = true,
        )
    }

    @Test
    fun `instrumentation tests exclude main lib classes but include test fixtures`() {
        setUpProject(
            publishAndroidLib = false,
            publishJavaLib = false
        )
        useAndroidX()

        TestFileUtils.searchAndReplace(
            project.getSubproject(":app").buildFile,
            "compileOnly",
            "implementation"
        )

        executor().run(":app:packageDebugAndroidTest")

        testExclusionInTestApk(
            testApk = project.getSubproject(":app").getApk(GradleTestProject.ApkType.ANDROIDTEST_DEBUG),
            expectLibAndJavaLibClassesToBeIncluded = false,
        )
    }

    @Test
    fun `test kotlin stdlib missing`() {
        TestFileUtils.searchAndReplace(
            project.getSubproject(":app").buildFile,
            "testFixturesImplementation \"org.jetbrains.kotlin:kotlin-stdlib:\${libs.versions.kotlinVersion.get()}\"",
            ""
        )
        val result = executor().expectFailure().run(":app:testDebugUnitTest")
        result.assertErrorContains("Kotlin standard library is missing")
    }

    @Test
    fun `test kotlin version too low`() {
        Assume.assumeTrue(kotlinVersion == TestUtils.KOTLIN_VERSION_FOR_TESTS)
        TestFileUtils.searchAndReplace(
            project.projectDir.parentFile.resolve(VERSION_CATALOG),
            "version('kotlinVersion', '$kotlinVersion' )",
            "version('kotlinVersion', '1.8.10' )"
        )
        val result = executor().expectFailure().run(":app:testDebugUnitTest")
        result.assertErrorContains(
            "The current Kotlin Gradle plugin version (1.8.10) is below the required"
        )
    }

    private fun testExclusionInTestApk(
        testApk: Apk,
        expectTestFixturesClassesToBeIncluded: Boolean = true,
        expectLibAndJavaLibClassesToBeIncluded: Boolean,
        expectAppClassesToBeIncluded: Boolean = false
    ) {
        testApk.use {
            it.mainDexFile.get().classes.keys.let { classes ->
                // test fixtures classes
                listOf(
                    "Lcom/example/app/testFixtures/AppInterfaceTester;",
                    "Lcom/example/javalib/testFixtures/JavaLibInterfaceTester;",
                    "Lcom/example/lib/testFixtures/LibInterfaceTester;",
                    "Lcom/example/lib/testFixtures/LibResourcesTester;"
                ).forEach { clazz ->
                    if (expectTestFixturesClassesToBeIncluded) {
                        Truth.assertThat(classes).contains(clazz)
                    } else {
                        Truth.assertThat(classes).doesNotContain(clazz)
                    }
                }

                // lib and java lib classes
                listOf(
                    "Lcom/example/javalib/JavaLibInterface;",
                    "Lcom/example/lib/LibInterface;",
                ).forEach { clazz ->
                    if (expectLibAndJavaLibClassesToBeIncluded) {
                        Truth.assertThat(classes).contains(clazz)
                    } else {
                        Truth.assertThat(classes).doesNotContain(clazz)
                    }
                }

                // app classes
                listOf(
                    "Lcom/example/app/AppInterface;"
                ).forEach { clazz ->
                    if (expectAppClassesToBeIncluded) {
                        Truth.assertThat(classes).contains(clazz)
                    } else {
                        Truth.assertThat(classes).doesNotContain(clazz)
                    }
                }
            }
        }
    }

    private fun setUpProjectForLint(ignoreTestFixturesSourcesInApp: Boolean) {
        project.getSubproject(":app").buildFile.appendText(
            """
                android {
                    testBuildType "release"
                    lint {
                        abortOnError false
                        enable 'StopShip'
                        textOutput file("lint-results.txt")
                        checkDependencies true
                        ignoreTestFixturesSources $ignoreTestFixturesSourcesInApp
                    }
                }
            """.trimIndent()
        )
        TestFileUtils.searchAndReplace(
            project.getSubproject(":app")
                .file("src/testFixtures/java/com/example/app/testFixtures/AppInterfaceTester.kt"),
            "class AppInterfaceTester(private val name: String) {",
            "// STOPSHIP\n" + "class AppInterfaceTester(private val name: String) {"
        )
        project.getSubproject(":lib").buildFile.appendText(
            """
                android {
                    testBuildType "release"
                }

                dependencies {
                    testFixturesApi 'androidx.annotation:annotation:1.1.0'
                }
            """.trimIndent()
        )
        TestFileUtils.searchAndReplace(
            project.getSubproject(":lib")
                .file("src/testFixtures/java/com/example/lib/testFixtures/LibResourcesTester.java"),
            "public void test() {",
            """
                @androidx.annotation.RequiresPermission(android.Manifest.permission.ACCESS_COARSE_LOCATION)
                public void methodWithUnavailablePermission() {
                }

                public void test() {
                    methodWithUnavailablePermission();
            """.trimIndent()
        )
        useAndroidX()
    }

    private fun useAndroidX() {
        TestFileUtils.appendToFile(project.file("gradle.properties"), "android.useAndroidX=true")
    }

    private fun executor(): GradleTaskExecutor {
        return project.executor()
            .withConfigurationCaching(BaseGradleExecutor.ConfigurationCaching.ON)
    }
}
