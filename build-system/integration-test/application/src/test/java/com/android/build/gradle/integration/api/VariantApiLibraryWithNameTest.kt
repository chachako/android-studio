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

import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.android.build.gradle.integration.common.fixture.app.KotlinHelloWorldApp
import com.google.common.truth.Truth
import org.junit.Rule
import org.junit.Test
import java.io.File

class VariantApiLibraryWithNameTest {

    @get: Rule
    val project =
        GradleTestProject.builder()
            .fromTestApp(KotlinHelloWorldApp.forPlugin("com.android.library"))
            .create()

    @Test
    fun testToCreateFileWithName() {
        project.buildFile.appendText(
            """
        import com.android.build.api.artifact.SingleArtifact
        import java.nio.file.Files

        abstract class CreateAar extends DefaultTask {

            @OutputFile
            abstract RegularFileProperty getOutput()

            @TaskAction
            void taskAction() {
                File output = getOutput().get().getAsFile()
                output.createNewFile()
                output.text = '''some AAR file'''
            }
        }

        androidComponents {
            onVariants(selector().all(), { variant ->
                TaskProvider createAar = tasks.register("create" + variant.name + "Aar", CreateAar.class)

                variant
                    .artifacts
                    .use(createAar)
                    .wiredWith(
                        { it.getOutput() }
                    )
                    .withName("my-fancy-library.aar")
                    .toCreate(SingleArtifact.AAR.INSTANCE)
            })
        }
        """.trimIndent()
        )

        project.executor().run("assembleDebug")
        Truth.assertThat(
            File(
                project.buildDir,
                "outputs/aar/debug/createdebugAar/my-fancy-library.aar"
            ).exists()
        ).isTrue()
    }
}
