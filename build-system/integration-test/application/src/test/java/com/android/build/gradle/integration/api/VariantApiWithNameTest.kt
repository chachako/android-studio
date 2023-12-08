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

package com.android.build.gradle.integration.api

import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.android.build.gradle.integration.common.fixture.app.HelloWorldLibraryApp
import com.android.build.gradle.integration.common.fixture.app.KotlinHelloWorldApp
import com.google.common.truth.Truth
import org.junit.Rule
import org.junit.Test
import java.io.File

/**
 * The same transformation does not work for library as library does not do global resource merge,
 * so it does not produce ASSETS artifact
 */
class VariantApiWithNameTest {

    @get: Rule
    val project =
        GradleTestProject.builder()
            .fromTestApp(KotlinHelloWorldApp.forPlugin("com.android.application"))
            .create()

    @Test
    fun testToTransformFilesWithName() {
        project.buildFile.appendText(
            """
        import com.android.build.api.artifact.SingleArtifact
        import java.nio.file.Files

        abstract class TransformBundle extends DefaultTask {

            @InputFile
            abstract RegularFileProperty getInputBundle()

            @OutputFile
            abstract RegularFileProperty getOutputBundle()

            @TaskAction
            void taskAction() {
                Files.copy(inputBundle.get().asFile.toPath(),
                    outputBundle.get().asFile.toPath())
            }
        }

        androidComponents {
            onVariants(selector().all(), { variant ->
                TaskProvider transformBundle = tasks.register("transform" + variant.name + "Bundle", TransformBundle.class)

                variant
                    .artifacts
                    .use(transformBundle)
                    .wiredWithFiles(
                        { it.getInputBundle()},
                        { it.getOutputBundle() }
                    )
                    .withName("my-fancy-name.aab")
                    .toTransform(SingleArtifact.BUNDLE.INSTANCE)

            })
        }
        """.trimIndent()
        )

        val result = project.executor().run("bundleDebug")
        Truth.assertThat(
            File(project.buildDir, "outputs/bundle/debug")
                .listFiles()
                .map(File::getName)
        ).containsExactly("my-fancy-name.aab")
    }

    @Test
    fun testToCreateFileWithName() {
        File(project.projectDir, "src/main/assets").let { assertDir ->
            assertDir.mkdirs()
            File(assertDir, "asset.txt").writeText("some asset")
        }
        project.buildFile.appendText(
            """
        import com.android.build.api.artifact.SingleArtifact
        import java.nio.file.Files

        abstract class CreateManifest extends DefaultTask {

            @OutputFile
            abstract RegularFileProperty getOutput()

            @TaskAction
            void taskAction() {
                throw new RuntimeException("This should never run !")
            }
        }

        androidComponents {
            onVariants(selector().all(), { variant ->
                TaskProvider createManifest = tasks.register("create" + variant.name + "Manifest", CreateManifest.class)

                variant
                    .artifacts
                    .use(createManifest)
                    .wiredWith(
                        { it.getOutput() }
                    )
                    .withName("my-fancy-manifest.xml")
                    .toCreate(SingleArtifact.MERGED_MANIFEST.INSTANCE)
            })
        }
        """.trimIndent()
        )

        val result = project.executor().expectFailure().run("processDebugManifestForPackage")
        Truth.assertThat(result.failureMessage).contains("You cannot use" +
                " `withName(\"my-fancy-manifest.xml\")` on a artifact like MERGED_MANIFEST which" +
                " name is set to always be \"AndroidManifest.xml")
    }

    @Test
    fun testToAppendFileWithName() {
        File(project.projectDir, "src/main/assets").let { assertDir ->
            assertDir.mkdirs()
            File(assertDir, "asset.txt").writeText("some asset")
        }
        project.buildFile.appendText(
            """
        android {
            buildTypes {
                release {
                    minifyEnabled true
                    proguardFiles getDefaultProguardFile(
                            'proguard-android-optimize.txt'),
                            'proguard-rules.pro'
                }
            }
        }
        import com.android.build.api.artifact.MultipleArtifact
        import java.nio.file.Files

        abstract class CreateProguardRules extends DefaultTask {

            @OutputFile
            abstract RegularFileProperty getOutput()

            @TaskAction
            void taskAction() {
                File asset = getOutput().get().getAsFile()
                asset.createNewFile()
                asset.text = '''some proguard rules'''
            }
        }

        androidComponents {
            onVariants(selector().all(), { variant ->
                TaskProvider createProguardRules = tasks.register("create" + variant.name + "Manifest", CreateProguardRules.class)

                variant
                    .artifacts
                    .use(createProguardRules)
                    .wiredWith(
                        { it.getOutput() }
                    )
                    .withName("my-fancy-proguard-rules.txt")
                    .toAppendTo(MultipleArtifact.MULTIDEX_KEEP_PROGUARD.INSTANCE)
            })
        }
        """.trimIndent()
        )

        val result = project.executor().run("assRel")
        Truth.assertThat(
            File(
                project.buildDir,
                "sources/multidex_keep_proguard/release/createreleaseManifest/my-fancy-proguard-rules.txt"
            ).exists()
        ).isTrue()
    }
}
