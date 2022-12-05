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

package com.android.build.gradle.integration.model

import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.android.build.gradle.integration.common.fixture.TestProject
import com.android.build.gradle.integration.common.fixture.app.HelloWorldApp
import com.android.build.gradle.integration.common.fixture.app.MinimalSubProject
import com.android.build.gradle.integration.common.fixture.app.MultiModuleTestProject
import com.android.build.gradle.integration.common.fixture.model.toValueString
import com.android.build.gradle.integration.common.utils.TestFileUtils
import com.android.builder.model.SyncIssue
import com.google.common.io.Resources
import com.google.common.truth.Truth
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * Integration tests for fetching javadoc, source and samples.
 */
class AdditionalArtifactsModelTest {

    @get:Rule
    val project = GradleTestProject.builder()
            .fromTestApp(setUpTestProject())
            .create()

    private fun setUpTestProject(): TestProject {
        return MultiModuleTestProject.builder()
                .subproject(APP_MODULE, HelloWorldApp.forPlugin("com.android.application"))
                .subproject(LIBRARY_MODULE, MinimalSubProject.lib(LIBRARY_PACKAGE))
                .build()
    }

    private lateinit var app: GradleTestProject
    private lateinit var library: GradleTestProject

    @Before
    fun setUp() {
        app = project.getSubproject(APP_MODULE)
        library = project.getSubproject(LIBRARY_MODULE)

        TestFileUtils.appendToFile(
                app.buildFile,
                """
                repositories {
                    maven { url '../testrepo' }
                }

                dependencies {
                    implementation 'com.example.android:myLib:1.0'
                }
            """.trimIndent()
        )

        TestFileUtils.appendToFile(
                library.buildFile,
                """
                apply plugin: 'maven-publish'

                afterEvaluate {
                    publishing {
                        repositories {
                            maven { url '../testrepo' }
                        }
                    }
                }
            """.trimIndent()
        )
    }

    @Test
    fun testModelFetchingArtifactsWithMultipleVariants() {
        addPublication(DEFAULT)
        TestFileUtils.appendToFile(
                library.buildFile,
                """
                android {
                    flavorDimensions "version"
                    productFlavors {
                        demo { }
                        full { }
                    }

                    publishing {
                        multipleVariants("$DEFAULT") {
                            allVariants()
                            withSourcesJar()
                            withJavadocJar()
                        }
                    }
                }
            """.trimIndent()
        )
        TestFileUtils.appendToFile(
                app.buildFile,
                """
                    android {
                        flavorDimensions "version"
                        productFlavors {
                            demo { }
                            full { }
                        }
                    }
                """.trimIndent()
        )

        library.execute("clean", "publish")
        val result = app.modelV2()
                .ignoreSyncIssues(SyncIssue.SEVERITY_WARNING)
                .fetchModels(variantName = "demoDebug")

        val variantDeps = result.container.getProject(":app").variantDependencies
                ?: throw RuntimeException("No VariantDependencies model for :app")

        val lib = variantDeps.libraries.values.singleOrNull {
            it.libraryInfo?.let { info ->
                info.name == "myLib" && info.attributes["org.gradle.usage"]== "java-runtime"
            } ?: false
        }
        Truth.assertWithMessage("myLib").that(lib).isNotNull()
        Truth.assertThat(lib?.srcJar?.toValueString(result.normalizer)).isEqualTo(
                "{PROJECT}/testrepo/com/example/android/myLib/1.0/myLib-1.0-demoDebug-sources.jar{F}"
        )
        Truth.assertThat(lib?.docJar?.toValueString(result.normalizer)).isEqualTo(
                "{PROJECT}/testrepo/com/example/android/myLib/1.0/myLib-1.0-demoDebug-javadoc.jar{F}"
        )
    }

    @Test
    fun testModelFetchingArtifactsWithSingleVariant() {
        addPublication(RELEASE)
        TestFileUtils.appendToFile(
                library.buildFile,
                """
                android {
                    publishing {
                        singleVariant("$RELEASE") {
                            withSourcesJar()
                            withJavadocJar()
                        }
                    }
                }
            """.trimIndent()
        )
        library.execute("clean", "publish")
        val result = app.modelV2()
                .ignoreSyncIssues(SyncIssue.SEVERITY_WARNING)
                .fetchModels(variantName = "release")

        val variantDeps = result.container.getProject(":app").variantDependencies
                ?: throw RuntimeException("No VariantDependencies model for :app")

        val lib = variantDeps.libraries.values.singleOrNull {
            it.libraryInfo?.let { info ->
                info.name == "myLib" && info.attributes["org.gradle.usage"]== "java-runtime"
            } ?: false
        }
        Truth.assertWithMessage("myLib").that(lib).isNotNull()
        Truth.assertThat(lib?.srcJar?.toValueString(result.normalizer)).isEqualTo(
                "{PROJECT}/testrepo/com/example/android/myLib/1.0/myLib-1.0-sources.jar{F}"
        )
        Truth.assertThat(lib?.docJar?.toValueString(result.normalizer)).isEqualTo(
                "{PROJECT}/testrepo/com/example/android/myLib/1.0/myLib-1.0-javadoc.jar{F}"
        )
    }

    @Test
    fun testModelFetchingForSampleSource() {
        setUpRepoForSample()
        addSampleArtifactDependency()

        val result = app.modelV2()
                .ignoreSyncIssues(SyncIssue.SEVERITY_WARNING)
                .fetchModels(variantName = "debug")

        val variantDeps = result.container.getProject(":app").variantDependencies
                ?: throw RuntimeException("No VariantDependencies model for :app")

        val libWithSample = variantDeps.libraries.values.singleOrNull{
            it.libraryInfo?.let { info ->
                info.name == "lib1" && info.attributes["org.gradle.usage"] == "java-runtime"
            } ?: false
        }
        Truth.assertWithMessage("lib1").that(libWithSample).isNotNull()
        Truth.assertThat(libWithSample?.samplesJar?.toValueString(result.normalizer)).isEqualTo(
                "{PROJECT}/testrepo/com/example/libraryWithSamples/lib1/1.0.0/lib1-1.0.0-samplessources.jar{F}"
        )
        val libWithoutSample = variantDeps.libraries.values.singleOrNull {
            it.libraryInfo?.let { info ->
                info.name == "support-core-utils"
            } ?: false
        }
        Truth.assertWithMessage("support-core-utils").that(libWithoutSample).isNotNull()
        Truth.assertThat(libWithoutSample?.samplesJar).isEqualTo(null)
    }

    private fun addPublication(componentName: String) {
        TestFileUtils.appendToFile(
                library.buildFile,
                """
                afterEvaluate {
                    publishing {

                        publications {
                            myPublication(MavenPublication) {
                                groupId = 'com.example.android'
                                artifactId = 'myLib'
                                version = '1.0'

                                from components.$componentName
                            }
                        }
                    }
                }
            """.trimIndent()
        )
    }

    private fun setUpRepoForSample() {
        val sampleJar = Resources.getResource(
                AdditionalArtifactsModelTest::class.java,
                "AdditionalArtifactsModelTest/lib1-1.0.0-samplessources.jar"
        )

        val mainJar = Resources.getResource(
                AdditionalArtifactsModelTest::class.java,
                "AdditionalArtifactsModelTest/lib1-1.0.0.jar"
        )

        val pom = Resources.getResource(
                AdditionalArtifactsModelTest::class.java,
                "AdditionalArtifactsModelTest/lib1-1.0.0.pom"
        )

        val artifactsRoot = app.projectDir.parentFile
                .resolve("testrepo/com/example/libraryWithSamples/lib1/1.0.0")
                .also { it.mkdirs() }
        artifactsRoot.resolve("lib1-1.0.0-samplessources.jar")
                .writeBytes(Resources.toByteArray(sampleJar))
        artifactsRoot.resolve("lib1-1.0.0.pom").writeBytes(Resources.toByteArray(pom))
        artifactsRoot.resolve("lib1-1.0.0.jar").writeBytes(Resources.toByteArray(mainJar))
    }

    private fun addSampleArtifactDependency() {
        TestFileUtils.appendToFile(
                app.buildFile,
                """
                dependencies {
                   components {
                       withModule("com.example.libraryWithSamples:lib1") { details ->
                           details.addVariant("samplessources") { vm ->
                               vm.attributes { container ->
                                   container.attribute(
                                           Category.CATEGORY_ATTRIBUTE, objects.named(Category.class, Category.DOCUMENTATION))
                                   container.attribute(
                                           DocsType.DOCS_TYPE_ATTRIBUTE, objects.named(DocsType.class, "samplessources"))
                               }
                               vm.withFiles {
                                   it.addFile('lib1-1.0.0-samplessources.jar')
                               }
                           }
                       }
                   }

                    implementation 'com.example.libraryWithSamples:lib1:1.0.0'

                    implementation 'com.android.support:support-core-ui:28.0.0'
                }
            """.trimIndent()
        )
    }

    companion object {
        private const val APP_MODULE = ":app"
        private const val LIBRARY_MODULE = ":library"
        private const val LIBRARY_PACKAGE = "com.example.lib"
        private const val DEFAULT: String = "default"
        private const val RELEASE: String = "release"
    }
}
