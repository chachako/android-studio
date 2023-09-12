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
import com.android.build.gradle.integration.common.fixture.app.KotlinHelloWorldApp
import com.android.build.gradle.integration.common.truth.ScannerSubject
import org.junit.Rule
import org.junit.Test

class GetRuntimeSymbolListTest {

    @get:Rule
    val project =
        GradleTestProject.builder()
            .fromTestApp(KotlinHelloWorldApp.forPlugin("com.android.application"))
            .create()

    @Test
    fun getRuntimeSymbolListTest() {
        project.buildFile.appendText(
            """

       import org.gradle.api.DefaultTask
       import org.gradle.api.file.RegularFileProperty
       import org.gradle.api.tasks.InputFiles
       import org.gradle.api.tasks.TaskAction
       import org.gradle.api.tasks.Optional
       import com.android.build.api.artifact.SingleArtifact

       abstract class CheckRuntimeSymbolListTask extends DefaultTask {

           @InputFiles
           abstract RegularFileProperty getRuntimeSymbolListFile()

           @TaskAction
           void taskAction() {
                System.out.println("Checking symbol list in " + runtimeSymbolListFile.getAsFile().get().absolutePath)
           }
       }

       androidComponents {
           onVariants(selector().all(), { variant ->
               project.tasks.register("check" + variant.name + "SymbolList", CheckRuntimeSymbolListTask.class) {
                   runtimeSymbolListFile.set(
                       variant.artifacts.get(SingleArtifact.RUNTIME_SYMBOL_LIST.INSTANCE)
                   )
               }
           })
       }
   """.trimIndent()
        )

        val result = project.executor().run("checkDebugSymbolList")
        ScannerSubject.assertThat(result.stdout).contains(
            "Checking symbol list"
        )
    }
}
