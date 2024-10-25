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

package com.android.build.gradle.integration.lint

import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.android.build.gradle.integration.common.fixture.app.MinimalSubProject
import com.android.build.gradle.integration.common.fixture.app.MultiModuleTestProject
import com.android.build.gradle.options.BooleanOption

/**
 * Creates a multi-module android project, including an app module, a dynamic-feature module, an
 * android library module, and a java library module, with lint issues scattered throughout.
 *
 * @param name the name of the project
 * @param heapSize the max heap size of the project
 */
fun createGradleTestProject(name: String, heapSize: String = "2048M"): GradleTestProject {
    val app =
        MinimalSubProject.Companion.app("com.example.test.app")
            .addLintIssues()
            .appendToBuild(
                """

                    android {
                        dynamicFeatures =  [':feature']
                        buildFeatures {
                            buildConfig true
                        }
                        buildTypes {
                            debug {
                                // this triggers ByteOrderMarkDetector in build directory
                                buildConfigField "String", "FOO", "\"\uFEFF\""
                            }
                        }
                        lint {
                            abortOnError = false
                            checkDependencies = true
                            textOutput = file("lint-report.txt")
                            checkGeneratedSources = true
                            checkAllWarnings = true
                            checkTestSources = true
                        }
                        testFixtures.enable = true
                    }
                """.trimIndent()
            )
    val feature =
        MinimalSubProject.Companion.dynamicFeature("com.example.test.feature")
            .addLintIssues()
            .appendToBuild(
                """

                    android {
                        buildFeatures {
                            buildConfig true
                        }
                        buildTypes {
                            debug {
                                // this triggers ByteOrderMarkDetector in build directory
                                buildConfigField "String", "FOO", "\"\uFEFF\""
                            }
                        }
                        lint {
                            abortOnError = false
                            checkGeneratedSources = true
                            checkAllWarnings = true
                            checkTestSources = true
                        }
                    }
                """.trimIndent()
            )
    val lib =
        MinimalSubProject.Companion.lib("com.example.lib")
            .addLintIssues()
            .appendToBuild(
                """

                    android {
                        buildFeatures {
                            buildConfig true
                        }
                        buildTypes {
                            debug {
                                // this triggers ByteOrderMarkDetector in build directory
                                buildConfigField "String", "FOO", "\"\uFEFF\""
                            }
                        }
                        lint {
                            abortOnError = false
                            checkGeneratedSources = true
                            checkAllWarnings = true
                            checkTestSources = true
                        }
                        testFixtures.enable = true
                    }
                """.trimIndent()
            )
    val javaLib =
        MinimalSubProject.Companion.javaLibrary()
            .addLintIssues(isAndroid = false)
            .appendToBuild(
                """

                    apply plugin: 'com.android.lint'

                    lint {
                        abortOnError = false
                        checkGeneratedSources = true
                        checkAllWarnings = true
                        checkTestSources = true
                    }
                """.trimIndent()
            )

    // Add lintPublish dependency from lib to javalib as a regression test for Issue 224967104
    return GradleTestProject.Companion.builder()
        .withName(name)
        .fromTestApp(
            MultiModuleTestProject.builder()
                .subproject(":app", app)
                .subproject(":feature", feature)
                .subproject(":lib", lib)
                .subproject(":java-lib", javaLib)
                .dependency(feature, app)
                .dependency(app, lib)
                .dependency(app, javaLib)
                .dependency("lintPublish", lib, javaLib)
                .build()
        )
        // Enforcing unique package names to prevent regressions. Remove when b/116109681 fixed.
        .addGradleProperties("${BooleanOption.ENFORCE_UNIQUE_PACKAGE_NAMES.propertyName}=true")
        .withHeap(heapSize)
        .create()
}

private fun MinimalSubProject.addLintIssues(isAndroid: Boolean = true): MinimalSubProject {
    val byteOrderMark = "\ufeff"
    this.withFile(
        "src/main/java/com/example/Foo.java",
        """
            package com.example;

            public class Foo {
                private String foo = "$byteOrderMark";
            }
        """.trimIndent()
    )
    this.withFile(
        "src/test/java/com/example/Bar.java",
        """
            package com.example;

            public class Bar {
                private String bar = "$byteOrderMark";
            }
        """.trimIndent()
    )
    if (isAndroid) {
        this.withFile(
            "src/androidTest/java/com/example/Baz.java",
            """
                package com.example;

                public class Baz {
                    private String baz = "$byteOrderMark";
                }
            """.trimIndent()
        )
        this.withFile(
            "src/testFixtures/java/com/example/Qux.java",
            """
                package com.example;

                public class Qux {
                    private String qux = "$byteOrderMark";
                }
            """.trimIndent()
        )
    }
    return this
}
