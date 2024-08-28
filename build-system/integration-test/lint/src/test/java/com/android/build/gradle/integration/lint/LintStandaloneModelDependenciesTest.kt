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

package com.android.build.gradle.integration.lint

import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.android.build.gradle.integration.common.fixture.SUPPORT_LIB_VERSION
import com.android.build.gradle.integration.common.fixture.app.MinimalSubProject
import com.android.build.gradle.integration.common.fixture.app.MultiModuleTestProject
import com.android.build.gradle.integration.common.utils.TestFileUtils
import com.android.testutils.truth.PathSubject.assertThat
import com.android.utils.FileUtils
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class LintStandaloneModelDependenciesTest {

    private val javaLib1 =
        MinimalSubProject.javaLibrary()
            .appendToBuild(
                """
                    apply plugin: 'com.android.lint'

                    dependencies {
                        compileOnly 'com.google.guava:guava:19.0'
                        testImplementation 'junit:junit:4.12'
                        // Add gradleApi() dependency as regression test for b/198453608
                        implementation gradleApi()
                        // Add external Android dependency as regression test for b/198449627
                        implementation 'com.android.support:appcompat-v7:${SUPPORT_LIB_VERSION}'
                        // Add xml dependency as regression test for b/198048896
                        implementation 'com.example:xml:1.0'
                    }
                """.trimIndent()
            )

    private val javaLib2 = MinimalSubProject.javaLibrary()
    private val javaLib3 = MinimalSubProject.javaLibrary()

    @get:Rule
    val project: GradleTestProject =
        GradleTestProject.builder()
            .withName("project")
            .fromTestApp(
                MultiModuleTestProject.builder()
                    .subproject(":java-lib1", javaLib1)
                    .subproject(":java-lib2", javaLib2)
                    .subproject(":java-lib3", javaLib3)
                    .dependency("implementation", javaLib1, javaLib2)
                    .dependency("compileOnly", javaLib1, javaLib3)
                    .build()
            )
            .create()

    @Before
    fun before() {
        createIvyRepo()

        TestFileUtils.appendToFile(
            project.settingsFile,
            """
                dependencyResolutionManagement {
                     repositories {
                         // Add the Ivy repository
                        ivy {
                            url = uri("ivyRepo") // Set the repository URL
                            patternLayout {
                                ivy("[organisation]/[module]/[revision]/[module]-[revision].ivy")
                                artifact("[organisation]/[module]/[revision]/[artifact]-[revision](-[classifier])(.[ext])")
                                m2compatible = true
                            }
                        }
                    }
                }
            """.trimIndent()
        )
    }

    // Regression test for b/197146610
    @Test
    fun testLintModelDependencies() {
        project.executor().run("clean", ":java-lib1:lint")

        val artifactDependenciesFile =
            FileUtils.join(
                project.getSubproject("java-lib1").intermediatesDir,
                "lintAnalyzeJvmMain",
                "android-lint-model",
                "main-artifact-dependencies.xml"
            )
        assertThat(artifactDependenciesFile).exists()
        assertThat(artifactDependenciesFile).containsAllOf(
            "guava",
            "java-lib2",
            "java-lib3",
            "gradle-api"
        )
        assertThat(artifactDependenciesFile).doesNotContain("junit")
        assertThat(artifactDependenciesFile).doesNotContain("java-lib1")

        val testArtifactDependenciesFile =
            FileUtils.join(
                project.getSubproject("java-lib1").intermediatesDir,
                "lintAnalyzeJvmTest",
                "android-lint-model",
                "main-artifact-dependencies.xml"
            )
        assertThat(testArtifactDependenciesFile).exists()
        assertThat(testArtifactDependenciesFile).containsAllOf(
            "junit",
            "java-lib1.jar",
            "java-lib2",
            "gradle-api"
        )
        assertThat(testArtifactDependenciesFile).doesNotContain("guava")
        assertThat(testArtifactDependenciesFile).doesNotContain("java-lib3")
    }

    private fun createIvyRepo() {
        val ivyFile =
            project.projectDir
                .resolve("ivyRepo/com/example/xml/1.0/xml-1.0.ivy")
                .also { it.parentFile.mkdirs() }
        TestFileUtils.appendToFile(
            ivyFile,
            // language=xml
            """
                <ivy-module version="2.0" xmlns:m="http://ant.apache.org/ivy/maven">
                  <info organisation="com.example" module="xml" revision="1.0" status="integration" publication="20240221004910"/>
                  <configurations>
                    <conf name="annotationProcessor" visibility="private"/>
                    <conf name="api" visibility="private" extends="compile"/>
                    <conf name="apiElements" visibility="private" extends="api,compileOnlyApi,runtime"/>
                    <conf name="archives" visibility="public"/>
                    <conf name="checkstyle" visibility="private"/>
                    <conf name="cmpt-def" visibility="public"/>
                    <conf name="cmptdefGeneratorRuntime" visibility="private"/>
                    <conf name="compile" visibility="private"/>
                    <conf name="compileClasspath" visibility="private" extends="compileOnly,implementation,javaee"/>
                    <conf name="compileOnly" visibility="private" extends="compileOnlyApi"/>
                    <conf name="compileOnlyApi" visibility="private"/>
                    <conf name="default" visibility="public" extends="cmpt-def,runtimeElements"/>
                    <conf name="filteredCmptDefCompileClasspathMain" visibility="private" extends="compileClasspath"/>
                    <conf name="filteredCmptDefCompileClasspathTest" visibility="private" extends="testCompileClasspath"/>
                    <conf name="implementation" visibility="private" extends="api,compile"/>
                    <conf name="jacocoAgent" visibility="private"/>
                    <conf name="jacocoAnt" visibility="private"/>
                    <conf name="javadoc" visibility="public"/>
                    <conf name="javadocElements" visibility="private"/>
                    <conf name="javaee" visibility="public"/>
                    <conf name="runtime" visibility="private" extends="compile"/>
                    <conf name="runtimeClasspath" visibility="private" extends="implementation,javaee,runtime,runtimeOnly"/>
                    <conf name="runtimeElements" visibility="private" extends="implementation,runtime,runtimeOnly"/>
                    <conf name="runtimeOnly" visibility="private"/>
                    <conf name="sources" visibility="public"/>
                    <conf name="sourcesElements" visibility="private"/>
                    <conf name="testAnnotationProcessor" visibility="private"/>
                    <conf name="testCompile" visibility="private" extends="compile"/>
                    <conf name="testCompileClasspath" visibility="private" extends="javaee,testCompileOnly,testImplementation"/>
                    <conf name="testCompileOnly" visibility="private" extends="compileOnlyApi"/>
                    <conf name="testImplementation" visibility="private" extends="implementation,testCompile"/>
                    <conf name="testRuntime" visibility="private" extends="runtime,testCompile"/>
                    <conf name="testRuntimeClasspath" visibility="private" extends="javaee,testImplementation,testRuntime,testRuntimeOnly"/>
                    <conf name="testRuntimeOnly" visibility="private" extends="runtimeOnly"/>
                  </configurations>
                  <publications>
                    <artifact name="xml" type="jar" ext="jar" conf="apiElements,archives,runtime,runtimeElements"/>
                    <artifact name="xml-def" type="cmpt-def" ext="xml" conf="archives,cmpt-def"/>
                    <artifact name="xml" type="jar" ext="jar" conf="javadoc,javadocElements" m:classifier="javadoc"/>
                    <artifact name="xml" type="jar" ext="jar" conf="sources,sourcesElements" m:classifier="sources"/>
                  </publications>
                  <dependencies>
                  </dependencies>
                </ivy-module>
            """.trimIndent()
        )
        val xmlFile =
            project.projectDir.resolve("ivyRepo/com/example/xml/1.0/xml-def-1.0.xml")
        TestFileUtils.appendToFile(
            xmlFile,
            // language=xml
            """
                <?xml version="1.0" encoding="UTF-8" ?>
                <definition>
                </definition>
            """.trimIndent()
        )
        val jarFile = project.projectDir.resolve("ivyRepo/com/example/xml/1.0/xml-1.0.jar")
        jarFile.writeBytes(ByteArray(0))
    }
}
