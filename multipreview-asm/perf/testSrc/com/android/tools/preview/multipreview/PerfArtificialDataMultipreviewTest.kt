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

package com.android.tools.preview.multipreview

import com.android.testutils.TestClassesGenerator
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.nio.file.Files
import java.nio.file.Path
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

private const val ROOT_PKG = "com/example/test"
private const val BASE_ANNOTATION = "$ROOT_PKG/base/annotations/BaseAnnotation"

class PerfArtificialDataMultipreviewTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private val settings = MultipreviewSettings(
        "com.example.test.base.annotations.BaseAnnotation",
        "com.example.test.param.PreviewParam", //  not used atm
    )

    @Test
    fun testFullProject() {
        val paths = prepareClassPath()
        computeAndRecordMetric(
            "artificial_data_no_filter",
            "artificial_data_no_filter"
        ) {
            val metric = MultipreviewMetric()
            metric.beforeTest()
            val multipreview = buildMultipreview(settings, paths)
            metric.afterTest()
            // Validity check
            assertEquals(1400, multipreview.methods.size)
            metric
        }
    }

    @Test
    fun testFullProject_withMainFilter() {
        val paths = prepareClassPath()
        computeAndRecordMetric(
            "artificial_data_package_filter",
            "artificial_data_package_filter"
        ) {
            val metric = MultipreviewMetric()
            metric.beforeTest()
            val multipreview = buildMultipreview(settings, paths) {
                it.startsWith("com.example.test.main")
            }
            metric.afterTest()
            // Validity check
            assertEquals(300, multipreview.methods.size)
            metric
        }
    }

    /**
     * This roughly tries to mimic the structure of the real android project that uses Compose:
     *
     * * Library with a base annotation
     * * Main module that depends on the number (100) of other modules and libraries (1000)
     * * Each module contains some classes with methods and annotations
     */
    private fun prepareClassPath(): List<String> {
        val rootFolder = temporaryFolder.newFolder()
        val multiMultiMainId = 4
        val multiMultiModuleId = 4
        val multiMultiLibId = 4
        val multiMultiFolderId = 4
        return listOf(
            // 1 + 100 classes
            createJar(rootFolder.toPath().resolve("base.jar"), sequence {
                yield(BASE_ANNOTATION to TestClassesGenerator.annotationClass(BASE_ANNOTATION, listOf("param1", "param2")))
                (0 until 100).map { "$ROOT_PKG/base/SimpleClass$it" }.forEach { name ->
                    yield(
                        name to TestClassesGenerator.classWithFieldsAndMethods(
                            name,
                            (0 until 20).map { "field$it" },
                            (0 until 20).map { "method$it:()V" },
                        )
                    )
                }
            }),
            // 100 + 20 + 10 + 40 + 10 + 4 + 100 + 10 + 40 = 334 classes
            createJar(
                jarPath = rootFolder.toPath().resolve("main.jar"),
                pkg = "$ROOT_PKG/main",
                config = PackageConfig(
                    UnrelatedClasses(20, 100),
                    20,
                    listOf(
                        MultiAnnotations(10, true, 10),
                        MultiAnnotations(multiMultiMainId, true, 40),
                        MultiAnnotations(3, false, 10, multiMultiMainId),
                    ),
                    listOf(
                        AnnotatedClasses(
                            20,
                            listOf(TestClassesGenerator.Annotation("$ROOT_PKG/main/UnrelatedAnnotation0")),
                            100
                        ),
                        AnnotatedClasses(
                            10,
                            listOf(TestClassesGenerator.Annotation("$ROOT_PKG/main/Multi4Annotation0")),
                            10
                        ),
                        AnnotatedClasses(
                            5,
                            listOf(
                                TestClassesGenerator.Annotation(BASE_ANNOTATION, listOf("foo", "bar")),
                                TestClassesGenerator.Annotation(BASE_ANNOTATION, listOf("qwe", "asd"))
                            ),
                            40
                        )
                    )
                )
            ),
            // 20 * (100 + 20 + 10 + 4 + 10 + 100 + 5 + 10) = 5180 classes
            *(0 until 20).map { moduleId ->
                createJar(
                    jarPath = rootFolder.toPath().resolve("classes$moduleId.jar"),
                    pkg = "$ROOT_PKG/module$moduleId",
                    config = PackageConfig(
                        UnrelatedClasses(20, 100),
                        20,
                        listOf(
                            MultiAnnotations(10, true, 10),
                            MultiAnnotations(multiMultiModuleId, true, 40),
                            MultiAnnotations(3, false, 10, multiMultiModuleId),
                        ),
                        listOf(
                            AnnotatedClasses(
                                20,
                                listOf(TestClassesGenerator.Annotation("$ROOT_PKG/module$moduleId/UnrelatedAnnotation0")),
                                100
                            ),
                            AnnotatedClasses(
                                5,
                                listOf(TestClassesGenerator.Annotation("$ROOT_PKG/module$moduleId/Multi4Annotation0")),
                                5
                            ),
                            AnnotatedClasses(
                                3,
                                listOf(
                                    TestClassesGenerator.Annotation(BASE_ANNOTATION, listOf("foo$moduleId", "bar$moduleId")),
                                    TestClassesGenerator.Annotation(BASE_ANNOTATION, listOf("qwe$moduleId", "asd$moduleId"))
                                ),
                                10
                            )

                        )
                    )
                )
            }.toTypedArray(),
            // 100 * (100 + 20 + 10 + 4 + 10 + 100) = 24400 classes
            *(0 until 100).map { libId ->
                val pkg = "$ROOT_PKG/lib$libId"
                createJar(
                    jarPath = rootFolder.toPath().resolve("lib_classes$libId.jar"),
                    pkg = pkg,
                    config = PackageConfig(
                        UnrelatedClasses(20, 100),
                        20,
                        listOf(
                            MultiAnnotations(10, true, 10),
                            MultiAnnotations(multiMultiLibId, true, 40),
                            MultiAnnotations(3, false, 10, multiMultiLibId),
                        ),
                        listOf(
                            AnnotatedClasses(
                                20,
                                listOf(TestClassesGenerator.Annotation("$pkg/UnrelatedAnnotation0")),
                                100
                            )
                        )
                    )
                )
            }.toTypedArray(),
            // 20 * (100 + 20 + 10 + 4 + 10 + 100) = 4880 classes
            *(0 until 20).map { folderId ->
                val pkg = "$ROOT_PKG/generated$folderId"
                createFolder(
                    folderPath = rootFolder.toPath().resolve("generated_classes$folderId"),
                    pkg = pkg,
                    config = PackageConfig(
                        UnrelatedClasses(20, 100),
                        20,
                        listOf(
                            MultiAnnotations(10, true, 10),
                            MultiAnnotations(multiMultiFolderId, true, 40),
                            MultiAnnotations(3, false, 10, multiMultiFolderId),
                        ),
                        listOf(
                            AnnotatedClasses(
                                20,
                                listOf(TestClassesGenerator.Annotation("$ROOT_PKG/lib$folderId/UnrelatedAnnotation$folderId")),
                                100
                            )
                        )
                    )
                )
            }.toTypedArray(),
            // 3 * (100 + 500) = 1800 classes
            *(0 until 3).map { bigJarId ->
                createJar(
                    jarPath = rootFolder.toPath().resolve("verybig_classes$bigJarId.jar"),
                    pkg = "$ROOT_PKG/verybig$bigJarId",
                    config = PackageConfig(
                        unrelatedAnnotationsCount = 100,
                        annotatedClasses =  listOf(
                            AnnotatedClasses(
                                20,
                                listOf(TestClassesGenerator.Annotation("$ROOT_PKG/verybig$bigJarId/UnrelatedAnnotation1")),
                                500
                            )
                        )
                    )
                )
            }.toTypedArray()
        )
    }

    private fun createJar(jarPath: Path, entries: Sequence<Pair<String, ByteArray>>): String {
        ZipOutputStream(Files.newOutputStream(jarPath)).use { zip ->
            entries.forEach {
                val entry = ZipEntry("${it.first}.class")
                zip.putNextEntry(entry)
                zip.write(it.second)
                zip.closeEntry()
            }
        }
        return jarPath.toString()
    }

    private fun createClasses(pkg: String, config: PackageConfig): Sequence<Pair<String, ByteArray>> = sequence {
        config.multiAnnotations.forEach { (m, isBase, n) ->
            (0 until n).forEach { i ->
                val name = if (isBase) "$pkg/Multi${m}Annotation$i" else "$pkg/MultiMulti${m}Annotation$i"
                yield(name to TestClassesGenerator.annotationClass(name, emptyList(), (0 until m).map { j ->
                    if (isBase)
                        TestClassesGenerator.Annotation(BASE_ANNOTATION, (0..1).map { "val${j}_${it}" })
                    else
                        TestClassesGenerator.Annotation("$pkg/Multi${m}Annotation${i * m + j}", emptyList())
                }))
            }
        }
        (0 until config.unrelatedAnnotationsCount).map { "$pkg/UnrelatedAnnotation$it" }.forEach { name ->
            yield(name to TestClassesGenerator.annotationClass(name))
        }
        config.unrelatedClasses.let { (m, n) ->
            (0 until n).map { "$pkg/Simple${m}Class$it" }.forEach { name ->
                yield(
                    name to TestClassesGenerator.classWithAnnotatedMethods(
                        name,
                        (0 until m).map { "method$it:()V" },
                        emptyList(),
                        m
                    )
                )
            }
        }
        config.annotatedClasses.forEach { (m, annotations, n) ->
            (0 until n).map { "$pkg/AnnotatedMethods${m}Class$it" }.forEach { name ->
                yield(
                    name to TestClassesGenerator.classWithAnnotatedMethods(
                        name,
                        (0 until m).map { "AnnotatedMethod$it:(Ljava/lang/String;Ljava/lang/String;)V" },
                        annotations,
                        m
                    )
                )
            }
        }
    }

    private fun createJar(jarPath: Path, pkg: String, config: PackageConfig): String =
        createJar(jarPath, createClasses(pkg, config))

    private fun createFolder(folderPath: Path, entries: Sequence<Pair<String, ByteArray>>): String {
        folderPath.toFile().mkdirs()
        entries.forEach { (name, content) ->
            val folderName = name.substringBeforeLast("/")
            val fileName = name.substringAfterLast("/")
            val fullFolderPath = folderPath.resolve(folderName)
            fullFolderPath.toFile().mkdirs()
            Files.write(fullFolderPath.resolve("$fileName.class"), content)
        }
        return folderPath.toString()
    }

    /** [methodsCount] - numeber of methods per class, [count] - numeber of classes */
    private data class UnrelatedClasses(val methodsCount: Int, val count: Int)

    /**
     * [multiAnnotationsCount] - number of (multipreview) annotations annotating each of this
     * multipreview annotations.
     * [isBase] - whether the parent annotation is base
     * [count] - number of multi-multipreview annotations
     * [parentMultiId] - id of parent annotation if not base.
     */
    private data class MultiAnnotations(
        val multiAnnotationsCount: Int,
        val isBase: Boolean,
        val count: Int,
        val parentMultiId: Int = -1,
    )

    /**
     * [methodsCount] - number of methods of a class
     * [annotations] - annotations to annotate the methods (all methods annotated with the same
     * annotations)
     * [count] - number of classes
     */
    private data class AnnotatedClasses(
        val methodsCount: Int,
        val annotations: List<TestClassesGenerator.Annotation>,
        val count: Int
    )

    private data class PackageConfig(
        val unrelatedClasses: UnrelatedClasses = UnrelatedClasses(0, 0),
        val unrelatedAnnotationsCount: Int = 0,
        val multiAnnotations: List<MultiAnnotations> = emptyList(),
        val annotatedClasses: List<AnnotatedClasses> = emptyList(),
    )

    private fun createFolder(
        folderPath: Path,
        pkg: String,
        config: PackageConfig,
    ): String = createFolder(folderPath, createClasses(pkg, config))
}
