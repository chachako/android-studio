/*
 * Copyright (C) 2021 The Android Open Source Project
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

import com.android.build.gradle.integration.common.fixture.DEFAULT_MIN_SDK_VERSION
import com.android.build.gradle.integration.common.fixture.GradleTaskExecutor
import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.android.build.gradle.integration.common.fixture.app.MinimalSubProject
import com.android.build.gradle.integration.common.fixture.app.MultiModuleTestProject
import com.android.build.gradle.integration.common.truth.TruthHelper.assertThat
import com.android.build.gradle.options.BooleanOption
import com.android.testutils.MavenRepoGenerator
import com.android.testutils.generateAarWithContent
import com.android.tools.build.libraries.metadata.Library
import com.google.common.truth.Truth
import org.gradle.internal.impldep.org.apache.maven.model.io.xpp3.MavenXpp3Reader
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import java.io.File
import java.nio.charset.Charset
import java.util.zip.ZipFile

class FusedLibraryTest {

    private val androidLib1 = MinimalSubProject.lib("com.example.androidLib1").also {
        it.appendToBuild("""
        dependencies {
            implementation 'junit:junit:4.12'
        }
        """.trimIndent())
        it.addFile(
                "src/main/res/values/strings.xml",
                """<resources>
                <string name="string_from_android_lib_1">androidLib2</string>
              </resources>"""
        )
    }
    private val androidLib2 = MinimalSubProject.lib("com.example.androidLib2")

    private val mavenRepo = mavenRepo()

    fun mavenRepo() : MavenRepoGenerator {
        val remoteDepA = generateAarWithContent("com.remotedep.remoteaar.a",
            resources = mapOf("values/strings.xml" to
                    // language=XML
                    """<?xml version="1.0" encoding="utf-8"?>
                    <resources>
                    <string name="remote_b_string">Remote String from remoteaar a</string>
                    </resources>""".trimIndent().toByteArray(Charset.defaultCharset())
            ),
        )
        val remoteDepB = generateAarWithContent(
            "com.remotedep.remoteaar.b",
            resources = mapOf(
                "values/strings.xml" to
                        // language=XML
                        """<?xml version="1.0" encoding="utf-8"?>
                    <resources>
                    <string name="remote_b_string">Remote String from remoteaar b</string>
                    </resources>""".trimIndent().toByteArray(Charset.defaultCharset())
            ),
        )
        return MavenRepoGenerator(
            listOf(
                MavenRepoGenerator.Library(
                    "com.remotedep:remoteaar-a:1",
                    "aar",
                    remoteDepA,
                    "com.remotedep:remoteaar-b:1"
                ),
                MavenRepoGenerator.Library(
                    "com.remotedep:remoteaar-b:1",
                    "aar",
                    remoteDepB
                )
            )
        )
    }

    private val fusedLibrary = MinimalSubProject.fusedLibrary("com.example.fusedLib1").also {
        it.appendToBuild(
                """
                apply plugin: 'maven-publish'

                dependencies {
                    include project(":androidLib1")
                    include project(":androidLib2")
                    include 'com.remotedep:remoteaar-a:1'
                }

                androidFusedLibrary {
                    minSdk = $DEFAULT_MIN_SDK_VERSION
                }
                """.trimIndent()
        )
    }

    @JvmField
    @Rule
    val project = GradleTestProject.builder()
            .fromTestApp(
                    MultiModuleTestProject.builder()
                            .subproject("androidLib1", androidLib1)
                            .subproject("androidLib2", androidLib2)
                            .subproject("fusedLib1", fusedLibrary)
                            .build()
            )
            .withAdditionalMavenRepo(mavenRepo)
            .addGradleProperties("${BooleanOption.FUSED_LIBRARY_SUPPORT.propertyName}=true")
            .create()


    @Test
    fun checkAarNoPublishing() {
        executor().run(":fusedLib1:assemble")
        val fusedLib1BuildDir = project.getSubproject(":fusedLib1").buildDir
        File(fusedLib1BuildDir, "bundle/bundle.aar").also { aarFile ->
            Truth.assertThat(aarFile.exists()).isTrue()
        }
    }

    @Test
    fun checkAarPublishing() {
        executor().run("generatePomFileForMavenPublication", "generateMetadataFileForMavenPublication")
        project.getSubproject(":fusedLib1").buildDir.resolve("publications/maven")
            .also { publicationDir ->
            val pom = File(publicationDir, "pom-default.xml")
            Truth.assertThat(pom.exists()).isTrue()
            val xmlMavenPomReader = MavenXpp3Reader()
            pom.inputStream().use { inStream ->
                val parsedPom = xmlMavenPomReader.read(inStream)
                assertThat(parsedPom.dependencies.map {
                    "${it.groupId}:${it.artifactId}:${it.version} scope:${it.scope}"
                })
                    .containsExactly(
                        "junit:junit:4.12 scope:runtime",
                        "org.hamcrest:hamcrest-core:1.3 scope:runtime",
                        "com.remotedep:remoteaar-b:1 scope:runtime"
                    )
            }
            Truth.assertThat(File(publicationDir, "module.json").exists()).isTrue()
        }
    }

    private fun executor(): GradleTaskExecutor {
        return project.executor()
    }
}
