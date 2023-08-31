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

package com.android.build.gradle.integration.bundle

import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.android.build.gradle.integration.common.truth.GradleTaskSubject.assertThat
import com.android.build.gradle.integration.common.utils.TestFileUtils
import com.android.build.gradle.internal.scope.InternalArtifactType
import com.android.testutils.TestInputsGenerator
import com.android.testutils.truth.PathSubject.assertThat
import com.android.utils.FileUtils
import com.google.common.collect.ImmutableList
import org.junit.Rule
import org.junit.Test
import java.io.File

class DynamicAppPackageDependenciesTest {
    @Rule
    @JvmField
    val project = GradleTestProject.builder()
        .withGradleBuildCacheDirectory(File("local-build-cache"))
        .fromTestProject("dynamicApp").create()

    /** Regression test for http://b/150438232. */
    @Test
    fun testPackagedDependenciesCaching() {
        project.executor().withArgument("--build-cache").run("assembleDebug")

        project.executor().withArgument("--build-cache").run("clean")
        project.executor().withArgument("--build-cache").run("assembleDebug")

        val feature1Dependencies = project.getSubproject("feature1").getIntermediateFile(
            InternalArtifactType.PACKAGED_DEPENDENCIES.getFolderName(),
            "debug/deps.txt"
        )
        assertThat(feature1Dependencies).contains("feature1::debug")

        val feature2Dependencies = project.getSubproject("feature2").getIntermediateFile(
            InternalArtifactType.PACKAGED_DEPENDENCIES.getFolderName(),
            "debug/deps.txt"
        )
        assertThat(feature2Dependencies).contains("feature2::debug")

        val buildResult =
            project.executor()
                .withArgument("--build-cache")
                .run(":app:generateReleaseFeatureTransitiveDeps")
        assertThat(buildResult.getTask(":app:generateReleaseFeatureTransitiveDeps")).didWork()
    }

    /**
     * Regression test for http://b/248576022
     */
    @Test
    fun testPackagingOfDifferentVersionsOfTheSameArtifact() {
        TestFileUtils.appendToFile(
            project.getSubproject("app").buildFile,
            """
                dependencies {
                  api 'com.google.guava:guava:19.0'
                }
            """.trimIndent()
        )
        TestFileUtils.appendToFile(
            project.getSubproject("feature1").buildFile,
            """
                dependencies {
                  api 'com.google.guava:guava:20.0'
                }
            """.trimIndent()
        )

        project.executor().run(":feature1:generateDebugFeatureTransitiveDeps")

        val feature1Dependencies = project.getSubproject("feature1").getIntermediateFile(
            InternalArtifactType.PACKAGED_DEPENDENCIES.getFolderName(),
            "debug/deps.txt"
        )
        assertThat(feature1Dependencies).doesNotContain("guava")
    }

    /**
     * regression test for b/295205663
     */
    @Test
    fun testExclusionOfLocalFileDependency() {
        // Add a local jar
        FileUtils.join(
            project.projectDir,
            "libs",
            "local.jar"
        ).apply {
            parentFile.mkdirs()
            writeBytes(
                TestInputsGenerator.jarWithEmptyClasses(ImmutableList.of("com/example/jar/JarClass"))
            )
        }

        TestFileUtils.appendToFile(
            project.getSubproject("app").buildFile,
            //language=groovy
            """
                dependencies {
                  implementation rootProject.files("libs/local.jar")
                }

                android {
                    buildTypes {
                       debug {
                            minifyEnabled = true
                        }
                    }
                }

                import kotlin.Unit

                import com.android.build.api.instrumentation.AsmClassVisitorFactory
                import com.android.build.api.instrumentation.ClassData
                import com.android.build.api.instrumentation.ClassContext
                import com.android.build.api.instrumentation.InstrumentationParameters
                import com.android.build.api.instrumentation.InstrumentationScope

                import org.objectweb.asm.ClassVisitor
                import org.objectweb.asm.util.TraceClassVisitor

                abstract class ClassVisitorFactory
                        implements AsmClassVisitorFactory<InstrumentationParameters.None> {

                    @Override
                    ClassVisitor createClassVisitor(
                            ClassContext classContext, ClassVisitor nextClassVisitor) {
                        return new TraceClassVisitor(nextClassVisitor, new PrintWriter(
                                new StringWriter()
                        ))
                    }

                    @Override
                    boolean isInstrumentable(ClassData classData) {
                        return true
                    }
                }

                androidComponents {
                    onVariants(selector().all(), { variant ->
                        variant.instrumentation.transformClassesWith(
                                ClassVisitorFactory.class,
                                InstrumentationScope.ALL,
                                params -> Unit.INSTANCE)
                    })
                }
            """.trimIndent()
        )
        TestFileUtils.appendToFile(
            project.getSubproject("feature1").buildFile,
            """
                dependencies {
                  implementation rootProject.files("libs/local.jar")
                }
            """.trimIndent()
        )

        project.executor().run("assembleDebug")
    }
}
