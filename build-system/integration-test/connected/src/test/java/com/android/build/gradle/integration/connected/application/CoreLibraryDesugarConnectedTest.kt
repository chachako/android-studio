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

package com.android.build.gradle.integration.connected.application

import com.android.build.gradle.integration.common.fixture.DESUGAR_DEPENDENCY_VERSION
import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.android.build.gradle.integration.common.fixture.app.HelloWorldApp
import com.android.build.gradle.integration.common.utils.TestFileUtils
import com.android.build.gradle.integration.connected.utils.getEmulator
import com.android.build.gradle.options.BooleanOption
import com.android.utils.FileUtils
import org.junit.Before
import org.junit.ClassRule
import org.junit.Rule
import org.junit.Test

class CoreLibraryDesugarConnectedTest {

    @get:Rule
    val project = GradleTestProject.builder()
            .fromTestApp(
                    HelloWorldApp.forPluginWithMinSdkVersion("com.android.application", 21))
            .create()

    @Before
    fun setUp() {
        TestFileUtils.appendToFile(
                project.buildFile,
                """
                    android {
                        compileOptions {
                            coreLibraryDesugaringEnabled true
                        }
                        buildTypes {
                            release {
                                minifyEnabled true
                                signingConfig signingConfigs.debug
                                testProguardFiles 'proguard-rules.pro'
                            }
                        }
                    }
                    dependencies {
                        coreLibraryDesugaring "$DESUGAR_DEPENDENCY"
                    }
                """.trimIndent()
        )

        // Add a function using desugar library api which is called from app
        TestFileUtils.addMethod(
                FileUtils.join(project.mainSrcDir,"com/example/helloworld/HelloWorld.java"),
                """
                public static java.time.LocalTime getTime() {
                    return java.time.LocalTime.MIDNIGHT;
                }
            """.trimIndent())

        TestFileUtils.searchAndReplace(
                FileUtils.join(project.mainSrcDir, "com/example/helloworld/HelloWorld.java"),
                "// onCreate",
                "getTime();"
        )
        // Add a keep rule for android test to work around b/126429384
        TestFileUtils.appendToFile(
                project.projectDir.resolve("proguard-rules.pro"),
                "-keep class j\$.time.LocalTime { *;}\n"
        )

        // R.id can get shrunk away now, remove the test reference to it
        TestFileUtils.searchAndReplace(
                FileUtils.join(project.projectDir, "src/androidTest/java/com/example/helloworld/HelloWorldTest.java"),
                "mTextView = (TextView) a.findViewById(R.id.text)",
                "// R.id will get shrunk away // mTextView = (TextView) a.findViewById(R.id.text)")
        TestFileUtils.searchAndReplace(
                FileUtils.join(project.projectDir, "src/androidTest/java/com/example/helloworld/HelloWorldTest.java"),
                "Assert.assertNotNull(mTextView)",
                "// R.id will get shrunk away // Assert.assertNotNull(mTextView)")

        project.addAdbTimeout()
        project.execute("uninstallAll")
    }

    // Regression test for b/266687543
    @Test
    fun testMinifiedRelease() {
        TestFileUtils.appendToFile(
                project.buildFile,
                """
                    android.testBuildType "release"
                """.trimIndent()
        )
        project.executor()
                .with(BooleanOption.USE_NON_FINAL_RES_IDS, true)
                .run("connectedReleaseAndroidTest")
    }

    companion object {
        @ClassRule
        @JvmField
        val emulator = getEmulator()

        private const val DESUGAR_DEPENDENCY =
                "com.android.tools:desugar_jdk_libs:$DESUGAR_DEPENDENCY_VERSION"
    }
}
