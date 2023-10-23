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

package com.android.build.gradle.integration.application

import com.android.build.gradle.integration.common.fixture.GradleTestProjectBuilder
import com.android.utils.FileUtils
import org.junit.Rule
import org.junit.Test
import java.io.File

class UnitTestWithJniLibs {
    @get:Rule
    val testProject = GradleTestProjectBuilder()
        .fromTestProject("unitTesting")
        .create()


    @Test
    fun testJniLibsAccess() {
       testProject.buildFile.appendText(
           """
            abstract class JniProducerTask extends DefaultTask {

                @OutputDirectory
                abstract DirectoryProperty getOutputDir()

                @TaskAction
                void taskAction() {
                    new File(
                        getOutputDir().get().getAsFile(),
                        System.mapLibraryName("someLib")
                    ).write("some native library file")
                }
            }


            androidComponents {
                onVariants(selector().withBuildType("debug")) { variant ->

                    TaskProvider jniLibProducer = tasks.register(variant.name + "JniLibProducerTask", JniProducerTask.class) { task ->
                        File outputDir = new File(getBuildDir(), task.name)
                        task.getOutputDir().set(outputDir)
                    }
                    variant.unitTest.sources.jniLibs.addGeneratedSourceDirectory(
                        jniLibProducer,
                        JniProducerTask::getOutputDir
                    )
                }
            }

           """.trimIndent()
       )

       val testSourceDir = FileUtils.join(
            File(testProject.mainTestDir, "java"),
            "com", "android", "tests")

        File(testSourceDir, "TestWithJniLibs.kt").writeText(
            """
            package com.android.tests

            import java.io.File
            import org.junit.Test
            import org.junit.Assert.*

            class TestWithJniLibs {
                @Test
                fun canFindSoFiles() {
                    println(System.getProperty("java.library.path"))
                    val libraryPath = System.getProperty("java.library.path")
                    if (!libraryPath.contains(
                        "build/generated/jniLibs/debugJniLibProducerTask".replace('/', File.separatorChar)
                        )
                    ) {
                        fail("Cannot find generated so file in java.library.path system property")
                    }
                    if (!libraryPath.contains(
                        "src/testDebug/jniLibs".replace('/', File.separatorChar)
                        )
                    ) {
                        fail("src/testDebug/jniLibs is not in the java.library.path")
                    }
                    if (!libraryPath.contains(
                        "src/test/jniLibs".replace('/', File.separatorChar)
                        )
                    ) {
                        fail("src/test/jniLibs is not in the java.library.path")
                    }
                }
            }
            """.trimIndent()
        )
        testProject.execute("testDebugUnitTest")
    }
}
