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

package com.android.build.gradle.integration.application

import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.android.build.gradle.integration.common.fixture.testprojects.PluginType
import com.android.build.gradle.integration.common.fixture.testprojects.createGradleProject
import com.android.build.gradle.integration.common.truth.TruthHelper.assertThat
import com.android.build.gradle.integration.common.utils.TestFileUtils
import com.android.utils.FileUtils
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.objectweb.asm.ClassReader
import org.objectweb.asm.Opcodes
import org.objectweb.asm.tree.ClassNode
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import kotlin.io.path.readBytes

/*
* Tests to verify that AARs produced from library modules in build/output/aar are in a state
* which can be published
* e.g. to a public repository like Maven, AAR contains expected file structure.
*/
class AarPublishTest {

    @get:Rule
    val project = createGradleProject {
        subProject(":library") {
            plugins.add(PluginType.ANDROID_LIB)
            android {
                defaultCompileSdk()
                namespace = "com.example.library"
                buildTypes {
                    named("debug") {
                        testCoverageEnabled = true
                    }
                }
                addFile(
                    "src/main/res/values/strings.xml",
                    "<resources>\n" +
                            "<string name=\"one\">Some string</string>\n" +
                            "</resources>"
                )
            }
        }
    }

    @get:Rule
    val temporaryDirectory = TemporaryFolder()

    /* Test to verify that AARs do not include Jacoco dependencies when published. */
    @Test
    fun canPublishLibraryAarWithCoverageEnabled() {
        val librarySubproject = project.getSubproject(":library")
        TestFileUtils.appendToFile(
            librarySubproject.buildFile,
            """
                android {
                    buildFeatures {
                        buildConfig true
                    }
                }
            """.trimIndent()
        )
        project.execute("library:assembleDebug")

        val libraryPublishedAar =
            FileUtils.join(librarySubproject.outputDir, "aar", "library-debug.aar")
        val tempTestData = temporaryDirectory.newFolder("testData")
        val extractedJar = File(tempTestData, "classes.jar")
        // Extracts the zipped BuildConfig.class in library-debug.aar/classes.jar to
        // the extractedBuildConfigClass temporary file, so it can be later loaded
        // into a classloader.
        ZipFile(libraryPublishedAar).use { libraryAar ->
            libraryAar.getInputStream(libraryAar.getEntry("classes.jar")).use { stream ->
                extractedJar.writeBytes(stream.readBytes())
            }
        }
        val classNode = ClassNode(Opcodes.ASM9)
        ClassReader(ZipFile(extractedJar).use {
            it.getInputStream(ZipEntry("com/example/library/BuildConfig.class")).readBytes()
        }
        ).accept(classNode, 0)
        assertThat(classNode.methods.map { it.name }).containsExactly("<init>", "<clinit>")
        assertThat(classNode.fields.map { it.name }).containsExactly(
            "DEBUG",
            "LIBRARY_PACKAGE_NAME",
            "BUILD_TYPE"
        )
    }

    @Test
    fun canPublishMinifiedLibraryAarWithCoverageEnabled() {
        val librarySubproject = project.getSubproject(":library")
        librarySubproject.buildFile.appendText(
            """
                android {
                    buildTypes {
                        release {
                            minifyEnabled = true
                            proguardFiles getDefaultProguardFile("proguard-android-optimize.txt"),
                                    "proguard-rules.pro"
                        }
                    }
                }
            """.trimIndent()
        )
        librarySubproject.projectDir.resolve("src/main/java/com/example").also {
            it.mkdirs()
            it.resolve("Foo.java").writeText(
                """
                package com.example;
                public class Foo { }
            """.trimIndent()
            )
            it.resolve("Bar.java").writeText(
                """
                package com.example;
                public class Bar { }
            """.trimIndent()
            )
        }
        librarySubproject.projectDir.resolve("proguard-rules.pro").writeText(
            """
            -keep class com.example.Foo
        """.trimIndent()
        )
        project.execute("library:assembleRelease")

        librarySubproject.getAar("release") { aar ->
            aar.getEntryAsZip("classes.jar").use { classesJar ->
                val classNode = ClassNode(Opcodes.ASM9)
                ClassReader(classesJar.getEntry("com/example/Foo.class").readBytes()).accept(
                    classNode,
                    0
                )
                assertThat(classNode.methods.map { it.name }).containsExactly("<init>")
                assertThat(classNode.fields).isEmpty()
                assertThat(classesJar.getEntry("com/example/Bar.class")).isNull()
            }
        }
    }

    @Test
    fun aarContainsAllowedRootDirectories() {
        project.execute("library:assembleDebug")
        project.getSubproject(":library").assertThatAar(GradleTestProject.ApkType.DEBUG.buildType) {
            containsFile("/AndroidManifest.xml")
            containsFile("/R.txt")
            containsFile("/classes.jar")
            containsFile("/res/values/values.xml")
            containsFile("META-INF/com/android/build/gradle/aar-metadata.properties")
            // Regression test for b/232117952
            doesNotContain("/values/")
        }
    }
}
