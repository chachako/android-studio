/*
 * Copyright (C) 2015 The Android Open Source Project
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

import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.android.build.gradle.integration.common.fixture.GradleTestProject.Companion.builder
import com.android.build.gradle.integration.common.fixture.TemporaryProjectModification
import com.android.build.gradle.integration.common.truth.AarSubject
import com.android.build.gradle.integration.common.truth.AbstractAndroidSubject
import com.android.build.gradle.integration.common.truth.TruthHelper.assertThat
import com.android.build.gradle.integration.common.utils.TestFileUtils
import com.android.utils.FileUtils
import com.google.common.base.Charsets
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.Timeout
import java.io.File
import java.io.IOException
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.util.concurrent.TimeUnit

/**
 * test for packaging of android asset files.
 *
 * This only uses raw files. This is not about running aapt tests, this is only about
 * everything around it, so raw files are easier to test in isolation.
 */
class ResPackagingTest {
    // Add a timeout so there's a trace dump on Windows when the test hangs (b/178233111).
    @get:Rule
    val timeout = Timeout.builder()
        .withTimeout(180, TimeUnit.SECONDS)
        .withLookingForStuckThread(true)
        .build()

    @get:Rule
    val project = builder()
        .fromTestProject("projectWithModules")
        .create()

    private val appProject by lazy { project.getSubproject("app") }
    private val libProject by lazy { project.getSubproject("library") }
    private val libProject2 by lazy { project.getSubproject("library2") }
    private val testProject by lazy { project.getSubproject("test") }

    private fun execute(vararg tasks: String) {
        project.executor().run(*tasks)
    }

    @Before
    fun setUp() {

        // rewrite settings.gradle to remove un-needed modules
        project.setIncludedProjects("app", "library", "library2", "test")

        // setup dependencies.
        TestFileUtils.appendToFile(
            appProject.buildFile,
            """android {
                        publishNonDefault true
                    }

                    dependencies {
                        api project(':library')
                    }
                    """
        )

        libProject.buildFile.appendText(
            """dependencies {
                        api project(':library2')
                    }
                """)

        testProject.buildFile.appendText(
            """android {
                targetProjectPath ':app'
                targetVariant 'debug'
            }
            """)

        // put some default files in the 4 projects, to check non incremental packaging as well,
        // and to provide files to change to test incremental support.
        val appDir = appProject.projectDir
        createOriginalResFile(appDir, "main", "file.txt", "app:abcd")
        createOriginalResFile(appDir, "androidTest", "filetest.txt", "appTest:abcd")

        val testDir = testProject.projectDir
        createOriginalResFile(testDir, "main", "file.txt", "test:abcd")

        val libDir = libProject.projectDir
        createOriginalResFile(libDir, "main", "filelib.txt", "library:abcd")
        createOriginalResFile(libDir, "androidTest", "filelibtest.txt", "libraryTest:abcd")

        val lib2Dir = libProject2!!.projectDir
        createOriginalResFile(lib2Dir, "main", "filelib2.txt", "library2:abcd")
        createOriginalResFile(lib2Dir, "androidTest", "filelib2test.txt", "library2Test:abcd")
    }

    @Test
    fun testNonIncrementalPackaging() {
        execute("clean", "assembleDebug", "assembleAndroidTest")

        // chek the files are there. Start from the bottom of the dependency graph
        checkAar(libProject2!!, "filelib2.txt", "library2:abcd")
        checkTestApk(libProject2!!, "filelib2.txt", "library2:abcd")
        checkTestApk(libProject2!!, "filelib2test.txt", "library2Test:abcd")

        checkAar(libProject, "filelib.txt", "library:abcd")
        // aar does not contain dependency's assets
        checkAar(libProject, "filelib2.txt", null)
        // test apk contains both test-ony assets, lib assets, and dependency assets.
        checkTestApk(libProject, "filelib.txt", "library:abcd")
        checkTestApk(libProject, "filelib2.txt", "library2:abcd")
        checkTestApk(libProject, "filelibtest.txt", "libraryTest:abcd")
        // but not the assets of the dependency's own test
        checkTestApk(libProject, "filelib2test.txt", null)

        // app contain own assets + all dependencies' assets.
        checkApk(appProject, "file.txt", "app:abcd")
        checkApk(appProject, "filelib.txt", "library:abcd")
        checkApk(appProject, "filelib2.txt", "library2:abcd")
        checkTestApk(appProject, "filetest.txt", "appTest:abcd")
        // app test does not contain dependencies' own test assets.
        checkTestApk(appProject, "filelibtest.txt", null)
        checkTestApk(appProject, "filelib2test.txt", null)
    }

    // ---- APP DEFAULT ---
    @Test
    fun testAppProjectWithNewResFile() {
        execute("app:clean", "app:assembleDebug")

        TemporaryProjectModification.doTest(appProject) { project: TemporaryProjectModification ->
            project.addFile("src/main/res/raw/newfile.txt", "newfile content")
            execute("app:assembleDebug")
            checkApk(appProject, "newfile.txt", "newfile content")
        }
    }

    @Test
    fun testAppProjectWithRemovedResFile() {
        execute("app:clean", "app:assembleDebug")

        TemporaryProjectModification.doTest(appProject) { project: TemporaryProjectModification ->
            project.removeFile("src/main/res/raw/file.txt")
            execute("app:assembleDebug")
            checkApk(appProject, "file.txt", null)
        }
    }

    @Test
    fun testAppProjectWithModifiedResFile() {
        execute("app:clean", "app:assembleDebug")

        TemporaryProjectModification.doTest(appProject) { project: TemporaryProjectModification ->
            project.replaceFile("src/main/res/raw/file.txt", "new content")
            execute("app:assembleDebug")
            checkApk(appProject, "file.txt", "new content")
        }
    }

    @Test
    fun testAppProjectWithNewDebugResFileOverridingMain() {
        execute("app:clean", "app:assembleDebug")

        TemporaryProjectModification.doTest(appProject) { project: TemporaryProjectModification ->
            project.addFile("src/debug/res/raw/file.txt", "new content")
            execute("app:assembleDebug")
            checkApk(appProject, "file.txt", "new content")
        }

        // file's been removed, checking in the other direction.
        execute("app:assembleDebug")
        checkApk(appProject, "file.txt", "app:abcd")
    }

    @Test
    fun testAppProjectWithnewResFileOverridingDependency() {
        execute("app:clean", "app:assembleDebug")

        TemporaryProjectModification.doTest(appProject) { project: TemporaryProjectModification ->
            project.addFile("src/main/res/raw/filelib.txt", "new content")
            execute("app:assembleDebug")
            checkApk(appProject, "filelib.txt", "new content")
        }

        // file's been removed, checking in the other direction.
        execute("app:assembleDebug")
        checkApk(appProject, "filelib.txt", "library:abcd")
    }

    @Test
    fun testAppProjectWithnewResFileInDebugSourceSet() {
        execute("app:clean", "app:assembleDebug")

        TemporaryProjectModification.doTest(appProject) { project: TemporaryProjectModification ->
            project.addFile("src/debug/res/raw/file.txt", "new content")
            execute("app:assembleDebug")
            checkApk(appProject, "file.txt", "new content")
        }

        // file's been removed, checking in the other direction.
        execute("app:assembleDebug")
        checkApk(appProject, "file.txt", "app:abcd")
    }

    @Test
    fun testAppProjectWithModifiedResInDependency() {
        execute("app:clean", "library:clean", "app:assembleDebug")

        TemporaryProjectModification.doTest(libProject) { project: TemporaryProjectModification ->
            project.replaceFile("src/main/res/raw/filelib.txt", "new content")
            execute("app:assembleDebug")
            checkApk(appProject, "filelib.txt", "new content")
        }
    }

    @Test
    fun testAppProjectWithAddedResInDependency() {
        execute("app:clean", "library:clean", "app:assembleDebug")

        TemporaryProjectModification.doTest(libProject) { project: TemporaryProjectModification ->
            project.addFile("src/main/res/raw/new_lib_file.txt", "new content")
            execute("app:assembleDebug")
            checkApk(appProject, "new_lib_file.txt", "new content")
        }
    }

    @Test
    fun testAppProjectWithRemovedResInDependency() {
        execute("app:clean", "library:clean", "app:assembleDebug")

        TemporaryProjectModification.doTest(libProject) { project: TemporaryProjectModification ->
            project.removeFile("src/main/res/raw/filelib.txt")
            execute("app:assembleDebug")
            checkApk(appProject, "filelib.txt", null)
        }
    }

    @Test
    fun testAppResourcesAreFilteredByMinSdkFull() {
        testAppResourcesAreFilteredByMinSdk(false)
    }

    @Test
    fun testAppResourcesAreFilteredByMinSdkIncremental() {
        // Note: this test is very similar to the previous one but, instead of trying all 3
        // versions independently, we start with min SDK 26, then change to <26 and set
        // min SDK to 27. The outputs should be the same as in the previous test.
        testAppResourcesAreFilteredByMinSdk(true)
    }

    private fun testAppResourcesAreFilteredByMinSdk(incremental: Boolean) {
        // Here are which files go into where:
        //  (none)  v26     v27
        //  f1
        //  f2      f2
        //  f3      f3      f3
        //          f4      f4
        //                  f5
        //
        // If we build with minSdkVersion < 26, we should get everything exactly as shown.
        //
        // If we build with minSdkVersion = 26 we should end up with:
        // (none)   v26     v27
        //  f1
        //          f2
        //          f3      f3
        //          f4      f4
        //                  f5
        //
        // If we build with minSdkVersion = 27 we should end up with:
        // (none)   v26     v27
        //  f1
        //          f2
        //                  f3
        //                  f4
        //                  f5
        val raw = appProject.file("src/main/res/raw")
        Files.createDirectories(raw.toPath())

        val raw26 = appProject.file("src/main/res/raw-v26")
        Files.createDirectories(raw26.toPath())

        val raw27 = appProject.file("src/main/res/raw-v27")
        Files.createDirectories(raw27.toPath())

        val f1NoneC = byteArrayOf(0)
        val f2NoneC = byteArrayOf(1)
        val f2v26C = byteArrayOf(2)
        val f3NoneC = byteArrayOf(3)
        val f3v26C = byteArrayOf(4)
        val f3v27C = byteArrayOf(5)
        val f4v26C = byteArrayOf(6)
        val f4v27C = byteArrayOf(7)
        val f5v27C = byteArrayOf(8)

        val f1None = File(raw, "f1")
        com.google.common.io.Files.write(f1NoneC, f1None)

        val f2None = File(raw, "f2")
        com.google.common.io.Files.write(f2NoneC, f2None)

        val f2v26 = File(raw26, "f2")
        com.google.common.io.Files.write(f2v26C, f2v26)

        val f3None = File(raw, "f3")
        com.google.common.io.Files.write(f3NoneC, f3None)

        val f3v26 = File(raw26, "f3")
        com.google.common.io.Files.write(f3v26C, f3v26)

        val f3v27 = File(raw27, "f3")
        com.google.common.io.Files.write(f3v27C, f3v27)

        val f4v26 = File(raw26, "f4")
        com.google.common.io.Files.write(f4v26C, f4v26)

        val f4v27 = File(raw27, "f4")
        com.google.common.io.Files.write(f4v27C, f4v27)

        val f5v27 = File(raw27, "f5")
        com.google.common.io.Files.write(f5v27C, f5v27)


        val appGradleFile = appProject.file("build.gradle")
        val appGradleFileContents =
            com.google.common.io.Files.asCharSource(appGradleFile, StandardCharsets.UTF_8).read()

        // Set min SDK version 26 and generate the APK.
        var newBuild =
            appGradleFileContents.replace("minSdkVersion .*".toRegex(), "minSdkVersion 26 // Updated")
        assertThat(newBuild).isNotEqualTo(appGradleFileContents)
        com.google.common.io.Files.asCharSink(appGradleFile, Charset.defaultCharset()).write(newBuild)
        execute("clean", ":app:assembleDebug")

        assertThat(appProject.getApk("debug")).containsFileWithContent("res/raw/f1", f1NoneC)
        assertThat(appProject.getApk("debug")).doesNotContain("res/raw/f2")
        assertThat(appProject.getApk("debug")).doesNotContain("res/raw/f3")
        assertThat(appProject.getApk("debug")).containsFileWithContent("res/raw-v26/f2", f2v26C)
        assertThat(appProject.getApk("debug")).containsFileWithContent("res/raw-v26/f3", f3v26C)
        assertThat(appProject.getApk("debug")).containsFileWithContent("res/raw-v26/f4", f4v26C)
        assertThat(appProject.getApk("debug")).containsFileWithContent("res/raw-v27/f3", f3v27C)
        assertThat(appProject.getApk("debug")).containsFileWithContent("res/raw-v27/f4", f4v27C)
        assertThat(appProject.getApk("debug")).containsFileWithContent("res/raw-v27/f5", f5v27C)

        // Set lower min SDK version and generate the APK. Incremental update!
        newBuild = appGradleFileContents.replace("minSdkVersion".toRegex(), "minSdkVersion 25 //")
        assertThat(newBuild).isNotEqualTo(appGradleFileContents)
        com.google.common.io.Files.asCharSink(appGradleFile, StandardCharsets.UTF_8).write(newBuild)
        if (incremental) {
            execute(":app:assembleDebug")
        } else {
            execute("clean", ":app:assembleDebug")
        }

        assertThat(appProject.getApk("debug"))
            .containsFileWithContent("res/raw/f1", f1NoneC)
        assertThat(appProject.getApk("debug")).containsFileWithContent("res/raw/f2", f2NoneC)
        assertThat(appProject.getApk("debug")).containsFileWithContent("res/raw/f3", f3NoneC)
        assertThat(appProject.getApk("debug")).containsFileWithContent("res/raw-v26/f2", f2v26C)
        assertThat(appProject.getApk("debug")).containsFileWithContent("res/raw-v26/f3", f3v26C)
        assertThat(appProject.getApk("debug")).containsFileWithContent("res/raw-v26/f4", f4v26C)
        assertThat(appProject.getApk("debug")).containsFileWithContent("res/raw-v27/f3", f3v27C)
        assertThat(appProject.getApk("debug")).containsFileWithContent("res/raw-v27/f4", f4v27C)
        assertThat(appProject.getApk("debug")).containsFileWithContent("res/raw-v27/f5", f5v27C)

        // Set min SDK version 27 and generate the APK. Incremental update!
        newBuild = appGradleFileContents.replace("minSdkVersion".toRegex(), "minSdkVersion 27 //")
        assertThat(newBuild).isNotEqualTo(appGradleFileContents)
        com.google.common.io.Files.asCharSink(appGradleFile, StandardCharsets.UTF_8).write(newBuild)
        if (incremental) {
            execute(":app:assembleDebug")
        } else {
            execute("clean", ":app:assembleDebug")
        }

        assertThat(appProject.getApk("debug")).containsFileWithContent("res/raw/f1", f1NoneC)
        assertThat(appProject.getApk("debug")).doesNotContain("res/raw/f2")
        assertThat(appProject.getApk("debug")).doesNotContain("res/raw/f3")
        assertThat(appProject.getApk("debug")).containsFileWithContent("res/raw-v26/f2", f2v26C)
        assertThat(appProject.getApk("debug")).doesNotContain("res/raw-v26/f3")
        assertThat(appProject.getApk("debug")).doesNotContain("res/raw-v26/f4")
        assertThat(appProject.getApk("debug")).containsFileWithContent("res/raw-v27/f3", f3v27C)
        assertThat(appProject.getApk("debug")).containsFileWithContent("res/raw-v27/f4", f4v27C)
        assertThat(appProject.getApk("debug")).containsFileWithContent("res/raw-v27/f5", f5v27C)
    }

    // ---- APP TEST ---
    @Test
    fun testAppProjectTestWithNewResFile() {
        execute("app:clean", "app:assembleAT")

        TemporaryProjectModification.doTest(appProject) { project: TemporaryProjectModification ->
            project.addFile("src/androidTest/res/raw/newfile.txt", "new file content")
            execute("app:assembleAT")
            checkTestApk(appProject, "newfile.txt", "new file content")
        }
    }

    @Test
    fun testAppProjectTestWithRemovedResFile() {
        execute("app:clean", "app:assembleAT")

        TemporaryProjectModification.doTest(appProject) { project: TemporaryProjectModification ->
            project.removeFile("src/androidTest/res/raw/filetest.txt")
            execute("app:assembleAT")
            checkTestApk(appProject, "filetest.txt", null)
        }
    }

    @Test
    fun testAppProjectTestWithModifiedResFile() {
        execute("app:clean", "app:assembleAT")

        TemporaryProjectModification.doTest(appProject) { project: TemporaryProjectModification ->
            project.replaceFile("src/androidTest/res/raw/filetest.txt", "new content")
            execute("app:assembleAT")
            checkTestApk(appProject, "filetest.txt", "new content")
        }
    }

    @Test
    fun testAppProjectWithMultipleFlavors() {
        appProject.buildFile.appendText(
            """
                android {
                    flavorDimensions = ["color"]
                    productFlavors {
                        red {
                            dimension = "color"
                        }
                        blue {
                            dimension = "color"
                        }
                    }
                }
            """.trimIndent()
        )

        File(appProject.projectDir, "src/red/res/raw").mkdirs()
        File(appProject.projectDir, "src/red/res/raw/red.txt").writeText("Red Text")
        File(appProject.projectDir, "src/blue/res/raw").mkdirs()
        File(appProject.projectDir, "src/blue/res/raw/blue.txt").writeText("Blue Text")

        execute("app:assembleDebug")

        check(assertThat(appProject.getApk("red", "debug")), "red.txt", "Red Text")
        check(assertThat(appProject.getApk("red", "debug")), "blue.txt", null)
        check(assertThat(appProject.getApk("blue", "debug")), "blue.txt", "Blue Text")
        check(assertThat(appProject.getApk("blue", "debug")), "red.txt", null)
    }

    // ---- LIB DEFAULT ---
    @Test
    fun testLibProjectWithNewResFile() {
        execute("library:clean", "library:assembleDebug")

        TemporaryProjectModification.doTest(libProject) { project: TemporaryProjectModification ->
            project.addFile("src/main/res/raw/newfile.txt", "newfile content")
            execute("library:assembleDebug")
            checkAar(libProject, "newfile.txt", "newfile content")
        }
    }

    @Test
    fun testLibProjectWithRemovedResFile() {
        execute("library:clean", "library:assembleDebug")

        TemporaryProjectModification.doTest(libProject) { project: TemporaryProjectModification ->
            project.removeFile("src/main/res/raw/filelib.txt")
            execute("library:assembleDebug")
            checkAar(libProject, "filelib.txt", null)
        }
    }

    @Test
    fun testLibProjectWithModifiedResFile() {
        execute("library:clean", "library:assembleDebug")

        TemporaryProjectModification.doTest(libProject) { project: TemporaryProjectModification ->
            project.replaceFile("src/main/res/raw/filelib.txt", "new content")
            execute("library:assembleDebug")
            checkAar(libProject, "filelib.txt", "new content")
        }
    }

    @Test
    fun testLibProjectWithnewResFileInDebugSourceSet() {
        execute("library:clean", "library:assembleDebug")

        TemporaryProjectModification.doTest(libProject) { project: TemporaryProjectModification ->
            project.addFile("src/debug/res/raw/filelib.txt", "new content")
            execute("library:assembleDebug")
            checkAar(libProject, "filelib.txt", "new content")
        }

        // file's been removed, checking in the other direction.
        execute("library:assembleDebug")
        checkAar(libProject, "filelib.txt", "library:abcd")
    }

    // ---- LIB TEST ---
    @Test
    fun testLibProjectTestWithNewResFile() {
        execute("library:clean", "library:assembleAT")

        TemporaryProjectModification.doTest(libProject) { project: TemporaryProjectModification ->
            project.addFile("src/androidTest/res/raw/newfile.txt", "new file content")
            execute("library:assembleAT")
            checkTestApk(libProject, "newfile.txt", "new file content")
        }
    }

    @Test
    fun testLibProjectTestWithRemovedResFile() {
        execute("library:clean", "library:assembleAT")

        TemporaryProjectModification.doTest(libProject) { project: TemporaryProjectModification ->
            project.removeFile("src/androidTest/res/raw/filelibtest.txt")
            execute("library:assembleAT")
            checkTestApk(libProject, "filelibtest.txt", null)
        }
    }

    @Test
    fun testLibProjectTestWithModifiedResFile() {
        execute("library:clean", "library:assembleAT")

        TemporaryProjectModification.doTest(libProject) { project: TemporaryProjectModification ->
            project.replaceFile("src/androidTest/res/raw/filelibtest.txt", "new content")
            execute("library:assembleAT")
            checkTestApk(libProject, "filelibtest.txt", "new content")
        }
    }

    @Test
    fun testLibProjectTestWithnewResFileOverridingTestedLib() {
        execute("library:clean", "library:assembleAT")

        TemporaryProjectModification.doTest(libProject) { project: TemporaryProjectModification ->
            project.addFile("src/androidTest/res/raw/filelib.txt", "new content")
            execute("library:assembleAT")
            checkTestApk(libProject, "filelib.txt", "new content")
        }

        // files been removed, checking in the other direction.
        execute("library:assembleAT")
        checkTestApk(libProject, "filelib.txt", "library:abcd")
    }

    @Test
    fun testLibProjectTestWithnewResFileOverridingDependency() {
        execute("library:clean", "library:assembleAT")

        TemporaryProjectModification.doTest(libProject) { project: TemporaryProjectModification ->
            project.addFile("src/androidTest/res/raw/filelib2.txt", "new content")
            execute("library:assembleAT")
            checkTestApk(libProject, "filelib2.txt", "new content")
        }

        // file's been removed, checking in the other direction.
        execute("library:assembleAT")
        checkTestApk(libProject, "filelib2.txt", "library2:abcd")
    }

    // ---- TEST DEFAULT ---
    @Test
    fun testTestProjectWithNewResFile() {
        execute("test:clean", "test:assembleDebug")

        TemporaryProjectModification.doTest(testProject) { project: TemporaryProjectModification ->
            project.addFile("src/main/res/raw/newfile.txt", "newfile content")
            execute("test:assembleDebug")
            checkApk(testProject, "newfile.txt", "newfile content")
        }
    }

    @Test
    fun testTestProjectWithRemovedResFile() {
        execute("test:clean", "test:assembleDebug")

        TemporaryProjectModification.doTest(testProject) { project: TemporaryProjectModification ->
            project.removeFile("src/main/res/raw/file.txt")
            execute("test:assembleDebug")
            checkApk(testProject, "file.txt", null)
        }
    }

    @Test
    fun testTestProjectWithModifiedResFile() {
        execute("test:clean", "test:assembleDebug")

        TemporaryProjectModification.doTest(testProject) { project: TemporaryProjectModification ->
            project.replaceFile("src/main/res/raw/file.txt", "new content")
            execute("test:assembleDebug")
            checkApk(testProject, "file.txt", "new content")
        }
    }

    companion object {
        private fun createOriginalResFile(
            projectFolder: File,
            dimension: String,
            filename: String,
            content: String
        ) {
            val assetFolder = FileUtils.join(projectFolder, "src", dimension, "res", "raw")
            FileUtils.mkdirs(assetFolder)
            com.google.common.io.Files.asCharSink(File(assetFolder, filename), Charsets.UTF_8).write(content)
        }

        // -----------------------
        /**
         * check an apk has (or not) the given res file name.
         *
         *
         * If the content is non-null the file is expected to be there with the same content. If the
         * content is null the file is not expected to be there.
         *
         * @param project the project
         * @param filename the filename
         * @param content the content
         */
        private fun checkApk(
            project: GradleTestProject, filename: String, content: String?
        ) {
            check(assertThat(project.getApk("debug")), filename, content)
        }

        /**
         * check a test apk has (or not) the given res file name.
         *
         *
         * If the content is non-null the file is expected to be there with the same content. If the
         * content is null the file is not expected to be there.
         *
         * @param project the project
         * @param filename the filename
         * @param content the content
         */
        private fun checkTestApk(
            project: GradleTestProject, filename: String, content: String?
        ) {
            check(assertThat(project.testApk), filename, content)
        }

        /**
         * check an aat has (or not) the given res file name.
         *
         *
         * If the content is non-null the file is expected to be there with the same content. If the
         * content is null the file is not expected to be there.
         *
         * @param project the project
         * @param filename the filename
         * @param content the content
         */
        private fun checkAar(
            project: GradleTestProject, filename: String, content: String?
        ) {
            project.testAar(
                "debug"
            ) { it: AarSubject -> check(it, filename, content) }
        }

        private fun check(
            subject: AbstractAndroidSubject<*, *>,
            filename: String,
            content: String?
        ) {
            if (content != null) {
                subject.containsFileWithContent("res/raw/$filename", content)
            } else {
                subject.doesNotContainResource("raw/$filename")
            }
        }
    }
}
