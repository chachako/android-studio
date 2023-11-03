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

package com.android.build.gradle.integration.common.fixture.app

/**
 * Simple test application that prints "hello world!" with Kotlin build script.
 *
 * <p>Using this in a test application as a rule is usually done as:
 *
 * <pre>
 * {@literal @}Rule
 * public GradleTestProject project = GradleTestProject.builder()
 *     .fromTestApp(HelloWorldAppKts.forPlugin("com.android.application"))
 *     .create();
 * </pre>
 */
class HelloWorldAppKts private constructor(val namespace: String) : KotlinHelloWorldApp() {

    companion object {

        const val NAMESPACE = "com.example.helloworld"

        fun forPlugin(plugin: String): HelloWorldAppKts {
            return HelloWorldAppKts(plugin, NAMESPACE)
        }
    }

    constructor(plugin: String, namespace: String) : this(namespace) {
        val buildFile = TestSourceFile(
            "build.gradle.kts",
            """
            buildscript {
                apply(from = "../commonBuildScript.gradle")
                dependencies {
                    classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:${"$"}{libs.versions.kotlinVersion.get()}")
                    classpath("com.android.tools.build:gradle:${"$"}{libs.versions.buildVersion.get()}")
                }
            }

            plugins {
                id("$plugin") version libs.versions.buildVersion.get()
                id("org.jetbrains.kotlin.android") version libs.versions.kotlinVersion.get()
            }

            //import anchor

            android {
                    namespace = "$namespace"
                    compileSdk = libs.versions.latestCompileSdk.get().toInt()
                    buildToolsVersion = libs.versions.buildToolsVersion.get()
                    defaultConfig {
                        minSdkVersion(libs.versions.supportLibMinSdk.get())
                        testInstrumentationRunner = "android.support.test.runner.AndroidJUnitRunner"
                    }

                   kotlinOptions {
                        jvmTarget = "1.8"
                   }
            }

            dependencies {
              api("org.jetbrains.kotlin:kotlin-stdlib:${"$"}{libs.versions.kotlinVersion.get()}")
              androidTestImplementation("com.android.support.test:runner:${"$"}{libs.versions.testSupportLibVersion.get()}")
              androidTestImplementation("com.android.support.test:rules:${"$"}{libs.versions.testSupportLibVersion.get()}")
            }

        """.trimIndent()
        )
        addFile(buildFile)

        val settingsFile = TestSourceFile(
            "settings.gradle.kts", """
                apply(from = "../versionCatalog.gradle")
                pluginManagement{
                  apply(from = "../commonLocalRepo.gradle", to = pluginManagement)
                  repositories {
                    google()
                    mavenCentral()
                  }
                }
                dependencyResolutionManagement {
                  repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
                  apply(from = "../commonLocalRepo.gradle", to = dependencyResolutionManagement)
                  repositories {
                    google()
                    mavenCentral()
                  }
                }
            """.trimIndent())
        addFile(settingsFile)

    }

    override fun containsFullBuildScript() = true

}
