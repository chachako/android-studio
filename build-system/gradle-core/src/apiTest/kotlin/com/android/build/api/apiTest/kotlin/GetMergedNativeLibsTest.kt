/*
 * Copyright (C) 2020 The Android Open Source Project
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

package com.android.build.api.apiTest.kotlin

import com.android.build.api.apiTest.VariantApiBaseTest
import com.google.common.truth.Truth
import org.junit.Test
import kotlin.test.assertNotNull

class GetMergedNativeLibsTest: VariantApiBaseTest(TestType.Script) {

    @Test
    fun getMergedNativeLibsTest() {
        given {
            tasksToInvoke.add(":lib:checkDebugNativeLibs")
            addModule(":lib") {
                buildFile =
                        // language=kotlin
                    """
            plugins {
                    id("com.android.library")
                    kotlin("android")
            }
            import org.gradle.api.DefaultTask
            import org.gradle.api.file.DirectoryProperty
            import org.gradle.api.tasks.InputFiles
            import org.gradle.api.tasks.TaskAction

            import com.android.build.api.variant.BuiltArtifactsLoader
            import com.android.build.api.artifact.SingleArtifact
            import org.gradle.api.provider.Property
            import org.gradle.api.tasks.Internal

            abstract class CheckNativeLibsTask: DefaultTask() {

                @get:InputFiles
                abstract val nativeLibsDir: DirectoryProperty

                @TaskAction
                fun taskAction() {
                    println("Checking native libs in ${'$'}{nativeLibsDir.get().asFile.absolutePath}")
                }
            }
            android {
                ${testingElements.addCommonAndroidBuildLogic()}
            }
            androidComponents {
                onVariants { variant ->
                    project.tasks.register<CheckNativeLibsTask>("check${'$'}{variant.name}NativeLibs") {
                        nativeLibsDir.set(
                            variant.artifacts.get(SingleArtifact.MERGED_NATIVE_LIBS)
                        )
                    }
                }
            }
        """.trimIndent()
                testingElements.addManifest(this)
                addSource("src/main/jniLibs/x86/foo.so", "foo")
            }
        }
        withDocs {
            index =
                    // language=markdown
                """
# artifacts.get in Kotlin

This sample shows how to obtain the merged native libraries folder from the AGP.

The [onVariants] block will wire the [CheckNativeLibsTask] input property (nativeLibsDir) by using
the [Artifacts.get] call with the right [SingleArtifact]:
`nativeLibsDir.set(artifacts.get(SingleArtifact.MERGED_NATIVE_LIBS))`
## To Run
./gradlew checkDebugNativeLibs

expected result : "Checking native libs in .... " message.
            """.trimIndent()
        }
        check {
            assertNotNull(this)
            Truth.assertThat(output).contains("Checking native libs")
            Truth.assertThat(output).contains("BUILD SUCCESSFUL")
        }
    }
}
