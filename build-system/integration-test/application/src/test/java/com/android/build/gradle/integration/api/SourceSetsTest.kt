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

package com.android.build.gradle.integration.api

import com.android.Version
import com.android.build.gradle.integration.common.fixture.DEFAULT_COMPILE_SDK_VERSION
import com.android.build.gradle.integration.common.fixture.testprojects.PluginType
import com.android.build.gradle.integration.common.fixture.testprojects.createGradleProject
import com.google.common.truth.Truth
import org.junit.Rule
import org.junit.Test
import java.io.File

class SourceSetsTest {
    @JvmField
    @Rule
    var project = createGradleProject {
        val customPlugin = PluginType.Custom("com.example.generate.jni")
        subProject(":api-use") {
            plugins.add(PluginType.ANDROID_APP)
            plugins.add(customPlugin)
            useNewPluginsDsl = true
            android {
                compileSdk = DEFAULT_COMPILE_SDK_VERSION
                namespace = "com.example.api.use"
                applicationId = "com.example.api.use"
            }
        }

        settings {
            includedBuild("build-logic") {
                rootProject {
                    plugins.add(PluginType.JAVA_GRADLE_PLUGIN)
                    appendToBuildFile {
                        //language=groovy
                        """
                        group = "com.example.generate"
                        version = "0.1-SNAPSHOT"
                        gradlePlugin {
                            plugins {
                                // A plugin that generates .so files to add to jniLibs in the legacy variant API
                                generateJni {
                                    id = "${customPlugin.id}"
                                    implementationClass = "com.example.generate.SourceProducerPlugin"
                                }
                            }
                        }

                        """.trimIndent()
                    }
                    dependencies {
                        implementation("com.android.tools.build:gradle:${Version.ANDROID_GRADLE_PLUGIN_VERSION}")
                    }

                    addFile(
                        "src/main/java/com/example/generate/ReproducerTask.java",
                        // language=java
                        """
                        package com.example.generate;

                        import org.gradle.api.DefaultTask;
                        import org.gradle.api.file.DirectoryProperty;
                        import org.gradle.api.tasks.OutputDirectory;
                        import org.gradle.api.tasks.TaskAction;

                        /** Task to  generate a placeholder JNI lib */
                        public abstract class ReproducerTask extends DefaultTask {

                            @OutputDirectory
                            public abstract DirectoryProperty getOutputDirectory();

                            @TaskAction
                            public final void generate() {
                                System.out.println("ReproducerTask called !");
                            }
                        }
                        """.trimIndent()
                    )

                    addFile("src/main/java/com/example/generate/SourceProducerPlugin.java",
                        //language=java
                        """
                        package com.example.generate;

                        import org.gradle.api.Plugin;
                        import org.gradle.api.Project;
                        import org.gradle.api.provider.Provider;
                        import org.gradle.api.tasks.TaskProvider;
                        import com.android.build.api.variant.ApplicationAndroidComponentsExtension;


                        /* A Plugin that demonstrates adding generated res from a task using new variant API */
                        class SourceProducerPlugin implements Plugin<Project> {
                            @Override public void apply(Project project) {
                                project.getPluginManager()
                                        .withPlugin(
                                            "com.android.application",
                                            androidPlugin -> {
                                                registerGenerationTask(project);
                                            });
                            }

                            private void registerGenerationTask(Project project) {
                                ApplicationAndroidComponentsExtension extension = project.getExtensions().getByType(ApplicationAndroidComponentsExtension.class);

                                extension.onVariants(extension.selector().withBuildType("debug"), variant -> {
                                    TaskProvider<ReproducerTask> reproTask = project.getTasks().register(
                                            variant.getName() + "ReproTask",
                                            ReproducerTask.class,
                                            task -> {
                                                System.out.println("ReproTask configured.");
                                            }
                                    );
                                    variant.getSources().getRes().addGeneratedSourceDirectory(reproTask, ReproducerTask::getOutputDirectory);
                                });
                            }
                        }
                        """.trimIndent()
                    )
                }
            }
        }
    }

    @Test
    fun sourceRegistrationShouldBeSuccessful() {
        val result = project.executor().run(":api-use:assembleDebug")
        Truth.assertThat(result.didWorkTasks.contains(":api-use:debugReproTask")).isTrue()
        Truth.assertThat(result.stdout.findAll("ReproducerTask called !").count())
            .isEqualTo(1)
    }

    /**
     * Test that create tasks very early and ensures that a directory registered using the
     * old variant API is part of the sources for the project.
     */
    @Test
    fun testOldVariantApiWithEarlyTaskRealization() {
        File(project.getSubproject("build-logic").projectDir,
            "src/main/java/com/example/generate/SourceProducerPlugin.java").writeText(
            """
                        package com.example.generate;

                        import java.io.File;
                        import org.gradle.api.Plugin;
                        import org.gradle.api.Project;
                        import org.gradle.api.provider.Provider;
                        import org.gradle.api.file.Directory;
                        import org.gradle.api.tasks.TaskProvider;
                        import com.android.build.gradle.api.AndroidSourceSet;
                        import com.android.build.gradle.AppExtension;


                        /* A Plugin that demonstrates adding generated res from a task using new variant API */
                        class SourceProducerPlugin implements Plugin<Project> {
                            @Override public void apply(Project project) {
                                project.getPluginManager()
                                        .withPlugin(
                                            "com.android.application",
                                            androidPlugin -> {
                                                registerGenerationTask(project);
                                            });
                            }

                            private void registerGenerationTask(Project project) {
                                AppExtension extension = project.getExtensions().getByType(AppExtension.class);
                                project.getTasks().all(task -> { System.out.println("Task : " + task.getName());});
                                extension.getApplicationVariants().all(variant -> {
                                    TaskProvider<ReproducerTask> reproTask = project.getTasks().register(
                                            variant.getName() + "ReproTask",
                                            ReproducerTask.class,
                                            task -> {
                                                task.getOutputDirectory().set(new File(project.getProjectDir(), "tmp_test"));
                                            }
                                    );
                                    AndroidSourceSet variantSourceSet = extension.getSourceSets().getByName(variant.getName());
                                    Provider<Directory> outputDir = reproTask.flatMap(ReproducerTask::getOutputDirectory);
                                    variantSourceSet.getRes().srcDir(outputDir);
                                });
                            }
                        }
                """.trimIndent()
        )
        project.executor().run(":api-use:mapDebugSourceSetPaths")
        val content = File(
            project.projectDir,
            "./api-use/build/intermediates/source_set_path_map/debug/file-map.txt"
        ).readText()

        Truth.assertThat(content).contains("tmp_test")
    }
}
