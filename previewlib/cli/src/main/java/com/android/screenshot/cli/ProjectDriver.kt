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
package com.android.screenshot.cli

import com.android.SdkConstants
import com.android.tools.lint.client.api.LintDriver
import com.android.tools.lint.client.api.UastParser
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Project
import com.android.tools.lint.detector.api.SourceSetType
import com.intellij.openapi.util.io.FileUtil
import java.io.File

/**
 * Loosely based on the [LintDriver]
 * There are some private functions that are needed to configure the project for proper UAST handling
 * this class mirrors some of that functionality.
 */
class ProjectDriver(private val  driver: LintDriver, private val project: Project) {

    private fun gatherJavaFiles(dir: File, result: MutableList<File>) {
        val files = dir.listFiles()
        if (files != null) {
            for (file in files.sorted()) {
                if (file.isFile) {
                    val path = file.path
                    if (path.endsWith(SdkConstants.DOT_JAVA) || path.endsWith(SdkConstants.DOT_KT)) {
                        result.add(file)
                    }
                } else if (file.isDirectory) {
                    gatherJavaFiles(file, result)
                }
            }
        }
    }
    private fun prepareUast(sourceList: UastParser.UastSourceList) {
        val parser = sourceList.parser
        val allContexts = sourceList.allContexts
        for (context in allContexts) {
            context.uastParser = parser
        }
        !parser.prepare(allContexts)
    }
    private fun findUastSources(
        contexts: List<JavaContext>,
        testContexts: List<JavaContext>,
        testFixturesContexts: List<JavaContext>,
        generatedContexts: List<JavaContext>,
        gradleKtsContexts: List<JavaContext>
    ): UastParser.UastSourceList {
        val capacity =
            contexts.size +
                    testContexts.size +
                    generatedContexts.size +
                    gradleKtsContexts.size +
                    testFixturesContexts.size
        val allContexts = ArrayList<JavaContext>(capacity)
        allContexts.addAll(contexts)
        allContexts.addAll(testContexts)
        allContexts.addAll(testFixturesContexts)
        allContexts.addAll(generatedContexts)
        allContexts.addAll(gradleKtsContexts)

        val parser = driver.client.getUastParser(project)
        return UastParser.UastSourceList(
            parser,
            allContexts,
            contexts,
            testContexts,
            testFixturesContexts,
            generatedContexts,
            gradleKtsContexts
        )
    }
    private fun findUastSources(project: Project, main: Project?, files: List<File>): UastParser.UastSourceList {
        val contexts = ArrayList<JavaContext>(files.size)
        val testContexts = ArrayList<JavaContext>(files.size)
        val testFixturesContexts = ArrayList<JavaContext>(files.size)
        val generatedContexts = ArrayList<JavaContext>(files.size)
        val gradleKtsContexts = ArrayList<JavaContext>(files.size)
        val unitTestFolders = project.unitTestSourceFolders
        val instrumentationTestFolders = project.instrumentationTestSourceFolders
        val otherTestFolders =
            project.testSourceFolders.minus((unitTestFolders + instrumentationTestFolders).toSet())
        val testFixturesFolders = project.testFixturesSourceFolders

        val generatedFolders = project.generatedSourceFolders
        for (file in files) {
            val path = file.path
            if (path.endsWith(SdkConstants.DOT_JAVA) || path.endsWith(SdkConstants.DOT_KT) || path.endsWith(
                    SdkConstants.DOT_KTS
                )) {
                val context = JavaContext(driver, project, main, file)

                when {
                    // Figure out if this file is a Gradle .kts context
                    path.endsWith(SdkConstants.DOT_KTS) -> {
                        gradleKtsContexts.add(context)
                    }
                    // instrumented test context
                    instrumentationTestFolders.any { FileUtil.isAncestor(it, file, false) } -> {
                        context.sourceSetType = SourceSetType.INSTRUMENTATION_TESTS
                        context.isTestSource = true
                        testContexts.add(context)
                    }
                    // unit text context
                    unitTestFolders.any { FileUtil.isAncestor(it, file, false) } -> {
                        context.sourceSetType = SourceSetType.UNIT_TESTS
                        context.isTestSource = true
                        testContexts.add(context)
                    }
                    // or a test context which is not unit test or instrumented test
                    otherTestFolders.any { FileUtil.isAncestor(it, file, false) } -> {
                        context.sourceSetType = SourceSetType.UNKNOWN_TEST
                        context.isTestSource = true
                        testContexts.add(context)
                    }
                    // or a testFixtures context
                    testFixturesFolders.any { FileUtil.isAncestor(it, file, false) } -> {
                        // while testFixtures sources are not test sources, they will be eventually consumed by
                        // test sources, and so we set
                        // isTestSource flag to true to run test-related checks on them.
                        context.sourceSetType = SourceSetType.TEST_FIXTURES
                        context.isTestSource = true
                        testFixturesContexts.add(context)
                    }
                    // or a generated context
                    generatedFolders.any { FileUtil.isAncestor(it, file, false) } -> {
                        context.sourceSetType = SourceSetType.MAIN
                        context.isGeneratedSource = true
                        generatedContexts.add(context)
                    }
                    else -> {
                        context.sourceSetType = SourceSetType.MAIN
                        contexts.add(context)
                    }
                }
            }
        }

        // We're not sure if these individual files are tests or non-tests; treat them
        // as non-tests now. This gives you warnings if you're editing an individual
        // test file for example.

        return findUastSources(
            contexts,
            testContexts,
            testFixturesContexts,
            generatedContexts,
            gradleKtsContexts
        )
    }
    private fun findUastSources(project: Project, main: Project?): UastParser.UastSourceList {
        val sourceFolders = project.javaSourceFolders
        val unitTestFolders = project.unitTestSourceFolders
        val instrumentationTestFolders = project.instrumentationTestSourceFolders
        val otherTestFolders = project.testSourceFolders.minus((unitTestFolders + instrumentationTestFolders).toSet())

        // Gather all Java source files in a single pass; more efficient.
        val sources = ArrayList<File>(100)
        for (folder in sourceFolders) {
            gatherJavaFiles(folder, sources)
        }

        val contexts = ArrayList<JavaContext>(2 * sources.size)
        for (file in sources) {
            val context = JavaContext(driver, project, main, file)
            context.sourceSetType = SourceSetType.MAIN
            contexts.add(context)
        }

        // Even if checkGeneratedSources == false, we must include generated sources
        // in our context list such that the source files are found by the Kotlin analyzer
        sources.clear()
        for (folder in project.generatedSourceFolders) {
            gatherJavaFiles(folder, sources)
        }
        val generatedContexts = ArrayList<JavaContext>(sources.size)
        for (file in sources) {
            val context = JavaContext(driver, project, main, file)
            context.isGeneratedSource = true
            generatedContexts.add(context)
        }

        // Android Test sources
        sources.clear()
        instrumentationTestFolders.forEach { gatherJavaFiles(it, sources) }
        val instrumentationTestContexts: List<JavaContext> =
                sources.map {
                    JavaContext(driver, project, main, it).apply {
                        sourceSetType = SourceSetType.INSTRUMENTATION_TESTS
                        isTestSource = true
                    }
                }

        // Unit Test sources
        sources.clear()
        unitTestFolders.forEach { gatherJavaFiles(it, sources) }
        val unitTestContexts: List<JavaContext> =
                    sources.map {
                        JavaContext(driver, project, main, it).apply {
                            sourceSetType = SourceSetType.UNIT_TESTS
                            isTestSource = true
                        }
            }

        // Test source which are neither unit nor instrumentation tests

        sources.clear()
        otherTestFolders.forEach { gatherJavaFiles(it, sources) }
        val otherTestContexts: List<JavaContext> =
                    sources.map {
                        JavaContext(driver, project, main, it).apply {
                            sourceSetType = SourceSetType.UNKNOWN_TEST
                            isTestSource = true
                        }
            }

        // TestFixtures sources
        sources.clear()
        project.testFixturesSourceFolders.forEach {
            gatherJavaFiles(
                it,
                sources
            )
        }
        val testFixturesContexts: List<JavaContext> =
                sources.map {
                    JavaContext(driver, project, main, it).apply {
                        sourceSetType = SourceSetType.TEST_FIXTURES
                        isTestSource = true
                    }
                }

        // build.gradle.kts files.
        val gradleKtsContexts =
            project.gradleBuildScripts
                .asSequence()
                .filter { it.name.endsWith(SdkConstants.DOT_KTS) }
                .map { JavaContext(driver, project, main, it) }
                .toList()

        return findUastSources(
            contexts,
            instrumentationTestContexts + unitTestContexts + otherTestContexts,
            testFixturesContexts,
            generatedContexts,
            gradleKtsContexts
        )
    }
    fun prepareUastFileList() {
        val files = project.subset
        val uastSourceList =
            project.getUastSourceList(driver, project)
                ?: if (files != null) {
                    findUastSources(project, project, files)
                } else {
                    findUastSources(project, project)
                }
        prepareUast(uastSourceList)
    }
}
