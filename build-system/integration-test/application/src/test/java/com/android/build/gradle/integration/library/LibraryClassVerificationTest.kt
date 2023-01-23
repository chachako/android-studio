/*
 * Copyright (C) 2017 The Android Open Source Project
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

import com.android.build.gradle.integration.common.fixture.testprojects.PluginType
import com.android.build.gradle.integration.common.fixture.testprojects.createGradleProject
import com.android.build.gradle.integration.common.truth.ScannerSubject.Companion.assertThat
import com.android.build.gradle.integration.common.utils.TestFileUtils
import com.android.build.gradle.options.BooleanOption
import com.android.testutils.MavenRepoGenerator
import com.android.testutils.TestInputsGenerator.jarWithClasses
import org.junit.Rule
import org.junit.Test

/** Tests for libraries with resources.  */
class LibraryClassVerificationTest {
    @get:Rule
    val project = createGradleProject {
        subProject("lib") {
            useNewPluginsDsl = true
            plugins.add(PluginType.ANDROID_LIB)
            plugins.add(PluginType.KOTLIN_ANDROID)
            android {
                defaultCompileSdk()
                namespace = "com.example.lib"
                minSdk = 21
            }
            dependencies {
                implementation(project(":otherlib"))
                implementation(MavenRepoGenerator.Library("com.example.base:base:0.1",
                        jarWithClasses(listOf(BaseClass::class.java)),
                ))
                implementation(localJar {
                    name = "embedded.jar"
                    addClass("com/example/EmbeddedJarClass") })
                compileOnly(localJar {
                    name = "compileOnly.jar"
                    addClass("com/example/CompileOnlyJarClass") })
            }
            addFile("src/main/java/com/example/lib/Use.kt",
                    //language=kotlin
                    """
                        package com.example.lib
                        import com.android.build.gradle.integration.library.BaseClass
                        import com.example.EmbeddedJarClass
                        import com.example.otherlib.OtherLibClass
                        import com.example.otherlib.R as OtherLibR

                        class Use : EmbeddedJarClass() {
                            fun useBaseClass(): Int {
                                return BaseClass().x
                            }
                            fun useOtherLibClass(otherLibClass: OtherLibClass): Int {
                                return otherLibClass.y
                            }
                            fun useMyString(): Int {
                                return R.string.my_string
                            }
                           fun useOtherLibString(): Int {
                                return OtherLibR.string.otherlib_string
                            }
                        }
                    """.trimIndent())
            addFile("src/main/res/values/strings.xml",
                    //language=xml
                    """
                        <?xml version="1.0" encoding="utf-8"?>
                        <resources><string name="my_string">My String</string></resources>
                    """.trimIndent())
        }
        subProject("otherlib"){
            useNewPluginsDsl = true
            plugins.add(PluginType.ANDROID_LIB)
            plugins.add(PluginType.KOTLIN_ANDROID)
            android {
                defaultCompileSdk()
                namespace = "com.example.otherlib"
                minSdk = 21
            }
            addFile("src/main/java/com/example/otherlib/OtherLibClass.kt",
                    //language=kotlin
                    """
                        package com.example.otherlib

                        class OtherLibClass(val y: Int = R.string.otherlib_string)
                    """.trimIndent())
            addFile("src/main/res/values/strings.xml",
                    //language=xml
                    """
                        <?xml version="1.0" encoding="utf-8"?>
                        <resources><string name="otherlib_string">Other Lib String</string></resources>
                    """.trimIndent())
        }
        withKotlinPlugin = true
        gradleProperties {
            set(BooleanOption.VERIFY_AAR_CLASSES, true)
        }
    }
    @Test
    fun checkInvalidClasses() {
        // Check the debug and release builds pass
        project.executor().run(":lib:assembleDebug")
        project.executor().run(":lib:assembleRelease")
        // Add reference to compile only jar, which should fail the release build
        TestFileUtils.searchAndReplace(project.file("lib/src/main/java/com/example/lib/Use.kt"),
                "EmbeddedJarClass",
                "CompileOnlyJarClass")
        // Verify debug build still passes, as verification only affects release builds
        project.executor().run(":lib:assembleDebug")
        // Verify release build fails with a useful error message
        val result = project.executor().expectFailure().run(":lib:assembleRelease")
        assertThat(result.stderr).contains("Error: Missing class com.example.CompileOnlyJarClass (referenced from: void com.example.lib.Use.<init>() and 1 other context)")

        // override to disable in that particular project
        TestFileUtils.appendToFile(project.getSubproject("lib").buildFile, """
            android.experimentalProperties["android.experimental.verifyLibraryClasses"] = false
        """.trimIndent())
        project.executor().run(":lib:assembleRelease")
    }
}

open class BaseClass {
    var x: Int = 2
}
