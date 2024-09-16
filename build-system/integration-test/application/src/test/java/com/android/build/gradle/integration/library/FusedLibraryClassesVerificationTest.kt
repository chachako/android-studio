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

package com.android.build.gradle.integration.library

import com.android.SdkConstants
import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.android.build.gradle.integration.common.fixture.GradleTestProject.ApkType.Companion.DEBUG
import com.android.build.gradle.integration.common.fixture.testprojects.PluginType
import com.android.build.gradle.integration.common.fixture.testprojects.createGradleProjectBuilder
import com.android.build.gradle.integration.common.truth.TruthHelper.assertThat
import com.android.build.gradle.integration.common.utils.TestFileUtils
import com.android.build.gradle.internal.fusedlibrary.FusedLibraryInternalArtifactType
import com.android.build.gradle.options.BooleanOption
import com.android.build.gradle.tasks.FusedLibraryReport
import com.android.testutils.MavenRepoGenerator
import com.android.testutils.TestInputsGenerator
import com.android.testutils.generateAarWithContent
import com.android.testutils.truth.ZipFileSubject
import com.android.utils.FileUtils
import com.google.common.collect.ImmutableList
import org.gradle.api.JavaVersion
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import kotlin.io.path.deleteIfExists

/**
 * Tests to verify the classes that are packaged within the AAR are correct or cause an expected
 * build time failure when invalid.
 */
class FusedLibraryClassesVerificationTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private val mavenRepo = MavenRepoGenerator(
        listOf(
            MavenRepoGenerator.Library(
                "com.externaldep:externalaar:1",
                "aar",
                generateAarWithContent(
                    "com.externaldep.externalaar",
                    // language=xml
                    manifest = """
                     <manifest package="com.externaldex.externalaar" xmlns:android="http://schemas.android.com/apk/res/android">
                         <uses-sdk android:targetSdkVersion="34" android:minSdkVersion="21" />
                         <uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
                     </manifest>
                                    """.trimIndent(),
                    mainJar = TestInputsGenerator.jarWithEmptyClasses(
                        ImmutableList.of("com/externaldep/externaljar/ExternalClass")
                    )
                )
            )
        )
    )

    @JvmField
    @Rule
    val project = createGradleProjectBuilder {
        subProject(":androidLib1") {
            plugins.add(PluginType.ANDROID_LIB)
            plugins.add(PluginType.KOTLIN_ANDROID)
            android {
                defaultCompileSdk()
                namespace = "com.example.androidLib1"
                compileOptions {
                    sourceCompatibility = JavaVersion.VERSION_1_8
                    targetCompatibility = JavaVersion.VERSION_1_8
                }
                kotlinOptions {
                    jvmTarget = "1.8"
                }
            }
            addFile(
                "src/main/java/com/example/androidLib1/ClassFromAndroidLib1.kt",
                // language=kotlin
                """
                    package com.example.androidLib1

                    class ClassFromAndroidLib1 {

                        fun foo(): String {
                            return "foo"
                        }
                    }
                """.trimIndent()
            )
        }
        subProject(":androidLib2") {
            useNewPluginsDsl = true
            plugins.add(PluginType.ANDROID_LIB)
            plugins.add(PluginType.KOTLIN_ANDROID)
            android {
                defaultCompileSdk()
                namespace = "com.example.androidLib2"
                compileOptions {
                    sourceCompatibility = JavaVersion.VERSION_1_8
                    targetCompatibility = JavaVersion.VERSION_1_8
                }
                kotlinOptions {
                    jvmTarget = "1.8"
                }
            }
            addFile(
                "src/main/java/com/example/androidLib2/ClassFromAndroidLib2.kt",
                // language=kotlin

                """
                package com.example.androidLib2

                class ClassFromAndroidLib2 {

                    fun foo(): String {
                        return "foo"
                    }
                }"""
            )
            dependencies {
                implementation(project(":androidLib1"))
            }
        }
        subProject(":$ANDROID_LIB_WITH_EXTERNAL_LIB_DEPENDENCY") {
            useNewPluginsDsl = true
            plugins.add(PluginType.ANDROID_LIB)
            plugins.add(PluginType.KOTLIN_ANDROID)
            android {
                defaultCompileSdk()
                namespace = "com.example.androidLib3"
                compileOptions {
                    sourceCompatibility = JavaVersion.VERSION_1_8
                    targetCompatibility = JavaVersion.VERSION_1_8
                }
                kotlinOptions {
                    jvmTarget = "1.8"
                }
            }
            addFile(
                "src/main/java/com/example/androidLib2/ClassFromAndroidLib3.kt",
                // language=kotlin

                """
                package com.example.androidLib3

                class ClassFromAndroidLib3 {

                    fun foo(): String {
                        return "foo"
                    }
                }"""
            )
            dependencies {
                implementation("com.externaldep:externalaar:1")
            }
        }
        subProject(":$ANDROID_LIB_WITH_DATABINDING") {
            plugins.add(PluginType.ANDROID_LIB)
            plugins.add(PluginType.KOTLIN_ANDROID)
            android {
                defaultCompileSdk()
                namespace = "com.example.androidLibWithDatabinding"
                compileOptions {
                    sourceCompatibility = JavaVersion.VERSION_1_8
                    targetCompatibility = JavaVersion.VERSION_1_8
                }
                kotlinOptions {
                    jvmTarget = "1.8"
                }
                buildFeatures {
                    dataBinding = true
                    viewBinding = true
                }
            }
        }
        subProject(":$FUSED_LIBRARY_PROJECT_NAME") {
            plugins.add(PluginType.FUSED_LIBRARY)
            plugins.add(PluginType.MAVEN_PUBLISH)
            androidFusedLibrary {
                namespace = "com.example.fusedLib1"
                minSdk = 34
            }
            // Use addDependenciesToFusedLibProject() for setting dependencies.
            dependencies {}
        }
        subProject(":app") {
            plugins.add(PluginType.ANDROID_APP)
            plugins.add(PluginType.KOTLIN_ANDROID)
            android {
                defaultCompileSdk()
                minSdk = 34
                namespace = "com.example.myapp"
            }
            dependencies {
                implementation(project(":$FUSED_LIBRARY_PROJECT_NAME"))
            }
        }
        gradleProperties {
            set(BooleanOption.FUSED_LIBRARY_SUPPORT, true)
            set(BooleanOption.USE_ANDROID_X, true)
        }
        withKotlinPlugin = true
    }
        .withAdditionalMavenRepo(mavenRepo)
        .create()

    @Test
    fun testClassesFromDirectDependenciesAreIncludedInAar() {
        val dependenciesBlock = """
            include(project(":androidLib1"))
            include(project(":androidLib2"))
        """.trimIndent()
        addDependenciesToFusedLibProject(dependenciesBlock)
        val classesFromDirectDependencies = listOf(
            "com/example/androidLib2/ClassFromAndroidLib2.class",
            "com/example/androidLib1/ClassFromAndroidLib1.class",
        )

        assertFusedLibAarContainsExpectedClasses(classesFromDirectDependencies)
        checkFusedLibReportContents(
            listOf("project :androidLib1", "project :androidLib2"),
            listOf(
                "org.jetbrains.kotlin:kotlin-stdlib:<version>",
                "org.jetbrains:annotations:<version>"
            ),
        )
    }

    @Test
    fun checkTransitivesAreNotIncludedInAarImplicitly() {
        val dependenciesBlock = """
            include(project(":androidLib1"))
            include(project(":androidLib2"))
            include(project(":$ANDROID_LIB_WITH_EXTERNAL_LIB_DEPENDENCY"))
        """.trimIndent()
        addDependenciesToFusedLibProject(dependenciesBlock)

        val classesFromDirectDependencies = listOf(
            "com/example/androidLib2/ClassFromAndroidLib2.class", // From :androidLib2
            "com/example/androidLib3/ClassFromAndroidLib3.class", // From :androidLib3
            "com/example/androidLib1/ClassFromAndroidLib1.class" // From :androidLib1
        )

        assertFusedLibAarContainsExpectedClasses(classesFromDirectDependencies)
        checkFusedLibReportContents(
            listOf("project :androidLib1", "project :androidLib2", "project :androidLib3"),
            listOf(
                "org.jetbrains.kotlin:kotlin-stdlib:<version>",
                "org.jetbrains:annotations:<version>",
                "com.externaldep:externalaar:1"
            )
        )
    }

    @Test
    fun checkExternalLibraryClassesIncludedInFusedAar() {
        val dependenciesBlock = """
            include("com.externaldep:externalaar:1")
            include(project(":androidLib1"))
        """.trimIndent()
        addDependenciesToFusedLibProject(dependenciesBlock)
        val classesFromDirectDependencies = listOf(
            "com/example/androidLib1/ClassFromAndroidLib1.class", // From :androidLib1
            "com/externaldep/externaljar/ExternalClass.class" // From com.externaldep:externalaar:1
        )

        assertFusedLibAarContainsExpectedClasses(classesFromDirectDependencies)

        checkFusedLibReportContents(
            listOf("com.externaldep:externalaar:1", "project :androidLib1"),
            listOf(
                "org.jetbrains.kotlin:kotlin-stdlib:<version>",
                "org.jetbrains:annotations:<version>"
            )
        )
    }

    @Test
    fun checkFusedLibraryAarForClassesFromLocalJarDependencies() {
        val localProjectTestJar = project.projectDir.resolve("testClass.jar")
        val appProject = project.getSubproject(":app")
        val fusedLib1Project = project.getSubproject(":$FUSED_LIBRARY_PROJECT_NAME")
        val localResourceJar =
            TestInputsGenerator.jarWithClasses(mutableListOf(TestClass::class.java) as Collection<Class<*>>?)

        localProjectTestJar.writeBytes(localResourceJar)
        val dependenciesBlock = """
            include(files("${localProjectTestJar.invariantSeparatorsPath}"))
        """.trimIndent()

        addDependenciesToFusedLibProject(dependenciesBlock)
        project.execute(":$FUSED_LIBRARY_PROJECT_NAME:bundle")

        val aar = FileUtils.join(fusedLib1Project.buildDir, "bundle", "bundle.aar")
        ZipFileSubject.assertThat(aar) {
            it.contains("libs/testClass.jar")
        }

        FileUtils.join(fusedLib1Project.mainSrcDir, "com", "example", "myapp", "AppClass.kt").also {
            it.parentFile.mkdirs()
            it.writeText(
                //language=kotlin
                """
            package com.example.myapp
            import com.android.build.gradle.integration.library.TestClass

            class AppClass {
                abstract fun aFunctionThatReturnsATypeFromFusedLibraryLibsJars(): TestClass
            }
        """.trimIndent()
            )
        }

        project.execute(":app:assembleDebug")
        localProjectTestJar.toPath().deleteIfExists()

        appProject.getApk(DEBUG).use {
            assertThat(it).hasClass("Lcom/android/build/gradle/integration/library/TestClass;") }
    }

    @Test
    fun checkPublishingFailsForLibrariesWithDatabinding() {
        val dependenciesBlock = """

            include(project(":androidLib1"))
            include(project(":$ANDROID_LIB_WITH_DATABINDING"))
        """.trimIndent()

        addDependenciesToFusedLibProject(dependenciesBlock)
        for (enableAndroidx in listOf(true, false)) {
            val failure =
                project.executor()
                    .with(BooleanOption.USE_ANDROID_X, enableAndroidx)
                    .expectFailure()
                    .run(":$FUSED_LIBRARY_PROJECT_NAME:generatePomFileForMavenPublication")

            failure.assertErrorContains(
                "Fused Library plugin does not allow dependencies with databinding.")
        }
    }

    private fun checkFusedLibReportContents(
        included: List<String>,
        dependencies: List<String>
    ) {
        project.execute(":$FUSED_LIBRARY_PROJECT_NAME:report")
        val reportFile = project.getSubproject(":$FUSED_LIBRARY_PROJECT_NAME:").buildDir.resolve(
            "reports/${FusedLibraryInternalArtifactType.FUSED_LIBRARY_REPORT.getFolderName()}/single/report.json"
        )
        val fusedLibReport = FusedLibraryReport.readFromFile(reportFile)
        assertThat(fusedLibReport.included).containsExactlyElementsIn(included)
        val idWithoutVersionPlaceholder =
            { str: String -> str.substringBeforeLast(":<version>") }
        assertThat(dependencies.count()).isEqualTo(fusedLibReport.dependencies.count())
        dependencies.map(idWithoutVersionPlaceholder).zip(fusedLibReport.dependencies).forEach() {
            assertThat(it.first).isEqualTo(it.second.substring(0, it.first.length))
        }
    }

    private fun addDependenciesToFusedLibProject(dependenciesBlock: String) {
        val fusedLib1Project = project.getSubproject(":$FUSED_LIBRARY_PROJECT_NAME")
        TestFileUtils.searchAndReplace(
            fusedLib1Project.buildFile,
            "dependencies {",
            "dependencies { $dependenciesBlock"
        )
    }

    private fun assertFusedLibAarContainsExpectedClasses(classesFromDirectDependencies: List<String>) {
        project.executor()
            .run(":$FUSED_LIBRARY_PROJECT_NAME:bundle")
        val fusedLib1Project = project.getSubproject(":$FUSED_LIBRARY_PROJECT_NAME")
        val classesJar = extractClassesJar(fusedLib1Project)

        ZipFile(classesJar).use {
            assertThat(
                it.entries()
                    .asSequence()
                    .map { it.toString() }
                    .filter { it.endsWith(SdkConstants.DOT_CLASS) }
                    .toList()
            ).containsExactlyElementsIn(classesFromDirectDependencies)
        }
    }

    private fun extractClassesJar(fusedLib1Project: GradleTestProject): File {
        val aar = FileUtils.join(fusedLib1Project.buildDir, "bundle", "bundle.aar")
        val tempFolder = temporaryFolder.newFolder()
        val classesJar = File(tempFolder, SdkConstants.FN_CLASSES_JAR)

        ZipFile(aar).use {
            assertThat(
                it.entries().toList().map { it.toString() }
            ).containsAtLeastElementsIn(listOf(SdkConstants.FN_CLASSES_JAR))
            classesJar.writeBytes(
                it.getInputStream(ZipEntry(SdkConstants.FN_CLASSES_JAR)).readAllBytes()
            )
        }
        return classesJar
    }

    companion object {
        const val FUSED_LIBRARY_PROJECT_NAME = "fusedLib1"
        const val ANDROID_LIB_WITH_EXTERNAL_LIB_DEPENDENCY = "androidLib3"
        const val ANDROID_LIB_WITH_DATABINDING = "androidLibWithDatabinding"
        const val FUSED_LIBRARY_R_CLASS = "com/example/fusedLib1/R.class"
    }
}

private class TestClass
