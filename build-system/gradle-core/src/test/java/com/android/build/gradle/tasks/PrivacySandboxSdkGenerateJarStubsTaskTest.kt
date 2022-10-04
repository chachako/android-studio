/*
 * Copyright (C) 2022 The Android Open Source Project
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

package com.android.build.gradle.tasks

import com.android.build.gradle.internal.fixtures.FakeConfigurableFileCollection
import com.android.build.gradle.internal.transforms.testdata.Animal
import com.android.build.gradle.internal.transforms.testdata.CarbonForm
import com.android.build.gradle.internal.transforms.testdata.Cat
import com.android.build.gradle.internal.transforms.testdata.OuterClass
import com.android.build.gradle.tasks.testdata.MySdk
import com.android.testutils.TestInputsGenerator
import com.android.testutils.TestUtils
import com.android.utils.FileUtils
import com.google.common.truth.Truth.assertThat
import org.gradle.api.Project
import org.gradle.testfixtures.ProjectBuilder
import org.junit.Ignore
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.jar.JarFile

internal class PrivacySandboxSdkGenerateJarStubsTaskTest {

    @Rule
    @JvmField
    var temporaryFolder = TemporaryFolder()

    val build: File by lazy {
        temporaryFolder.newFolder("build")
    }

    @Ignore
    @Test
    fun testStubJarContainsExpectedEntries() {
        testWithTask { dir: File, outputStubJar: File, task: PrivacySandboxSdkGenerateJarStubsTask ->

            TestInputsGenerator.pathWithClasses(
                    dir.toPath(),
                    listOf(
                            MySdk::class.java,
                            Cat::class.java,
                            Animal::class.java,
                            CarbonForm::class.java,
                            OuterClass::class.java,
                            OuterClass.InnerClass::class.java
                    ),
            )
            FileUtils.createFile(
                    FileUtils.join(dir, "META-INF", "MANIFEST.mf"),
                    "Manifest-Version: 1.0")

            task.doTaskAction()

            JarFile(outputStubJar).use { outputJar ->
                val entries = outputJar.entries().toList().map { it.name.replace(File.separatorChar, '/') }
                assertThat(entries).containsExactlyElementsIn(
                        listOf(
                                "com/android/build/gradle/tasks/testdata/MySdk.class",
                        )
                )
            }
            //TODO(lukeedgar) Add test case for ensuring bytecode stripping once supported by apipackager.
        }
    }

    private fun testWithTask(action: (File, File, PrivacySandboxSdkGenerateJarStubsTask) -> Unit) {
        val project: Project = ProjectBuilder.builder().withProjectDir(temporaryFolder.root).build()
        val task = project.tasks.register("privacySandboxClassesJarStubs",
                PrivacySandboxSdkGenerateJarStubsTask::class.java).get()
        val classesDir = temporaryFolder.newFolder("src")

        task.mergedClasses.from(classesDir)
        val outJar =
                FileUtils.join(build,
                        PrivacySandboxSdkGenerateJarStubsTask.privacySandboxSdkStubJarFilename)
                        .also { FileUtils.createFile(it, "") }

        val apipackagerDependencyJars = listOf(
                "androidx/privacysandbox/tools/tools/1.0.0-SNAPSHOT/tools-1.0.0-SNAPSHOT.jar",
                "androidx/privacysandbox/tools/tools-apipackager/1.0.0-SNAPSHOT/tools-apipackager-1.0.0-SNAPSHOT.jar",
        ).map { TestUtils.getLocalMavenRepoFile(it).toFile() }

        task.apiPackager.setFrom(FakeConfigurableFileCollection(apipackagerDependencyJars))
        task.outputJar.set(outJar)
        action(classesDir, outJar, task)
    }
}
