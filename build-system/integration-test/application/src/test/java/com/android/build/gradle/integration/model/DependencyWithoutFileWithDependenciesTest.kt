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

package com.android.build.gradle.integration.model

import com.android.build.gradle.integration.common.fixture.ANDROIDX_VERSION
import com.android.build.gradle.integration.common.fixture.model.ModelComparator
import com.android.build.gradle.integration.common.fixture.testprojects.PluginType
import com.android.build.gradle.integration.common.fixture.testprojects.createGradleProject
import com.android.build.gradle.integration.common.fixture.testprojects.prebuilts.setUpHelloWorld
import com.android.build.gradle.options.BooleanOption
import com.android.builder.model.v2.ide.SyncIssue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/** Regression test for http://b/229298359. */
class DependencyWithoutFileWithDependenciesTest: ModelComparator() {

    @get:Rule
    val project = createGradleProject {
        subProject(":app") {
            plugins.add(PluginType.ANDROID_APP)
            android {
                setUpHelloWorld()
            }
            appendToBuildFile {
                """
                      dependencies {
                        testImplementation("com.foo:bar:1.0") {
                          capabilities {
                            requireCapability("com.foo:bar-custom:1.0")
                          }
                        }
                      }
                """.trimIndent()
            }
        }
        subProject(":bar") {
            plugins.add(PluginType.JAVA_LIBRARY)
            plugins.add(PluginType.MAVEN_PUBLISH)
            appendToBuildFile {
                """
                    group = "com.foo"
                    version = "1.0"

                    Configuration customCapability = configurations.create("customCapability")
                    customCapability.setCanBeConsumed(true)
                    customCapability.setCanBeResolved(false)
                    customCapability.attributes.attribute(
                      TargetJvmEnvironment.TARGET_JVM_ENVIRONMENT_ATTRIBUTE,
                      objects.named(TargetJvmEnvironment.class, TargetJvmEnvironment.STANDARD_JVM)
                    )
                    customCapability.outgoing.capability("com.foo:bar-custom:1.0")
                    dependencies.add("customCapability", 'androidx.annotation:annotation:$ANDROIDX_VERSION')
                    components.java.addVariantsFromConfiguration(customCapability) { mapToOptional() }

                    publishing {
                      repositories {
                        maven { url = '../repo' }
                      }
                      publications {
                        mavenJava(MavenPublication) {
                          from components.java
                        }
                      }
                    }
                """.trimIndent()
            }
        }
    }

    @Before
    fun setUpRepo( ) {
        project.settingsFile.appendText("""

            dependencyResolutionManagement {
                repositories {
                    maven {
                      url { 'repo' }
                    }
                }
            }
        """.trimIndent())
    }

    @Test
    fun `test models`() {
        project.executor().run(":bar:publish")
        val result = project.modelV2()
            .with(BooleanOption.USE_ANDROID_X, true)
            .ignoreSyncIssues(SyncIssue.SEVERITY_WARNING)
            .fetchModels(variantName = "debug")
        //todo fix me
        with(result).compareVariantDependencies(goldenFile = "VariantDependencies")
    }
}
