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

package com.android.build.gradle.internal.ide.proto

import com.android.build.gradle.internal.ide.v2.LibraryImpl
import com.android.build.gradle.internal.ide.v2.LibraryInfoImpl
import com.android.build.gradle.internal.ide.v2.ProjectInfoImpl
import com.android.build.gradle.internal.ide.v2.TestInfoImpl
import com.android.builder.model.v2.ide.AndroidGradlePluginProjectFlags
import com.android.builder.model.v2.ide.ComponentInfo
import com.android.builder.model.v2.ide.Library
import com.android.builder.model.v2.ide.TestInfo
import com.android.utils.FileUtils
import com.google.common.truth.Truth
import org.junit.Test
import java.io.File
import java.lang.reflect.Method

/**
 * A test to assert compatibility and correct conversion between v2 models and their proto
 * representation used in kotlin multiplatform android import.
 */
class ConvertersTest {

    @Test
    fun convertTestInfo() {
        val testInfo = TestInfoImpl(
            animationsDisabled = false,
            execution = TestInfo.Execution.ANDROIDX_TEST_ORCHESTRATOR,
            additionalRuntimeApks = listOf(nextFile(), nextFile()),
            instrumentedTestTaskName = nextString()
        )

        val result = assertAllGettersMatch(
            obj = testInfo,
            objType = TestInfo::class.java,
            convertedObj = testInfo.convert(),
            callChain = "TestInfo"
        )

        Truth.assertThat(result.gettersCalled).isEqualTo(4)
    }

    @Test
    fun convertExecutionEnum() {
        TestInfo.Execution.values().forEach {
            val protoValue = it.convert()

            Truth.assertWithMessage(
                enumMissingErrorMessage("TestInfo.Execution")
            ).that(it.name).isEqualTo(protoValue.name)
        }
    }

    @Test
    fun convertBooleanFlag() {
        AndroidGradlePluginProjectFlags.BooleanFlag.values().forEach {
            val protoValue = it.convert()

            Truth.assertWithMessage(
                enumMissingErrorMessage("AndroidGradlePluginProjectFlags.BooleanFlag")
            ).that(it.name).isEqualTo(protoValue.name)
        }
    }

    private fun enumMissingErrorMessage(className: String) =
        "The proto representation of $className doesn't contain all values that the original class" +
                " contains. Update the proto file with the missing enum."

    @Test
    fun testAndroidLibraryConversion() {
        val library = LibraryImpl.createAndroidLibrary(
            key = nextString(),
            libraryInfo = LibraryInfoImpl(
                buildType = nextString(),
                productFlavors = mapOf(nextString() to nextString()),
                attributes = mapOf(nextString() to nextString()),
                capabilities = listOf(nextString(), nextString()),
                group = nextString(),
                name = nextString(),
                version = nextString(),
                isTestFixtures = true
            ),
            artifact = nextFile(),
            manifest = nextFile(),
            compileJarFiles = listOf(nextFile(), nextFile()),
            runtimeJarFiles = listOf(nextFile(), nextFile()),
            resFolder = nextFile(),
            resStaticLibrary = nextFile(),
            assetsFolder = nextFile(),
            jniFolder = nextFile(),
            aidlFolder = nextFile(),
            renderscriptFolder = nextFile(),
            proguardRules = nextFile(),
            lintJar = nextFile(),
            srcJar = nextFile(),
            docJar = nextFile(),
            samplesJar = nextFile(),
            externalAnnotations = nextFile(),
            publicResources = nextFile(),
            symbolFile = nextFile(),
        )

        val result = assertAllGettersMatch(
            obj = library,
            convertedObj = library.convert().build(),
            objType = Library::class.java,
            callChain = "Library"
        )

        Truth.assertThat(result.gettersCalled).isEqualTo(30)
    }

    @Test
    fun testJavaLibraryConversion() {
        val library = LibraryImpl.createJavaLibrary(
            key = nextString(),
            libraryInfo = LibraryInfoImpl(
                buildType = nextString(),
                productFlavors = mapOf(nextString() to nextString()),
                attributes = mapOf(nextString() to nextString()),
                capabilities = listOf(nextString(), nextString()),
                group = nextString(),
                name = nextString(),
                version = nextString(),
                isTestFixtures = true
            ),
            artifact = nextFile(),
            srcJar = nextFile(),
            docJar = nextFile(),
            samplesJar = nextFile(),
        )

        val result = assertAllGettersMatch(
            obj = library,
            convertedObj = library.convert().build(),
            objType = Library::class.java,
            callChain = "Library"
        )

        Truth.assertThat(result.gettersCalled).isEqualTo(14)
    }

    @Test
    fun testProjectLibraryConversion() {
        val library = LibraryImpl.createProjectLibrary(
            key = nextString(),
            projectInfo = ProjectInfoImpl(
                buildType = nextString(),
                productFlavors = mapOf(nextString() to nextString()),
                attributes = mapOf(nextString() to nextString()),
                capabilities = listOf(nextString(), nextString()),
                buildId = nextString(),
                projectPath = nextString(),
                isTestFixtures = true
            ),
            artifactFile = nextFile(),
            lintJar = nextFile()
        )

        val result = assertAllGettersMatch(
            obj = library,
            convertedObj = library.convert().build(),
            objType = Library::class.java,
            callChain = "Library"
        )

        Truth.assertThat(result.gettersCalled).isEqualTo(11)
    }

    @Test
    fun testRelocatedLibraryConversion() {
        val library = LibraryImpl.createRelocatedLibrary(
            key = nextString(),
            libraryInfo = LibraryInfoImpl(
                buildType = nextString(),
                productFlavors = mapOf(nextString() to nextString()),
                attributes = mapOf(nextString() to nextString()),
                capabilities = listOf(nextString(), nextString()),
                group = nextString(),
                name = nextString(),
                version = nextString(),
                isTestFixtures = true
            ),
        )

        val result = assertAllGettersMatch(
            obj = library,
            convertedObj = library.convert().build(),
            objType = Library::class.java,
            callChain = "Library"
        )

        Truth.assertThat(result.gettersCalled).isEqualTo(10)
    }

    @Test
    fun testNoArtifactLibraryConversion() {
        val library = LibraryImpl.createNoArtifactFileLibrary(
            key = nextString(),
            libraryInfo = LibraryInfoImpl(
                buildType = nextString(),
                productFlavors = mapOf(nextString() to nextString()),
                attributes = mapOf(nextString() to nextString()),
                capabilities = listOf(nextString(), nextString()),
                group = nextString(),
                name = nextString(),
                version = nextString(),
                isTestFixtures = true
            ),
        )

        val result = assertAllGettersMatch(
            obj = library,
            convertedObj = library.convert().build(),
            objType = Library::class.java,
            callChain = "Library"
        )

        Truth.assertThat(result.gettersCalled).isEqualTo(10)
    }

    private val methodMapper = { method: Method ->
        if (Collection::class.java.isAssignableFrom(method.returnType)) {
            method.name + "List"
        } else if (Map::class.java.isAssignableFrom(method.returnType)) {
            method.name + "Map"
        } else if (method.name.startsWith("is")) {
            "getIs" + method.name.removePrefix("is")
        } else {
            method.name
        }
    }

    private val objectMapper = { obj: Any, method: Method ->
        if (method.declaringClass == ComponentInfo::class.java) {
            obj::class.java.getMethod("getComponentInfo").invoke(obj)
        } else {
            obj
        }
    }

    private fun assertAllGettersMatch(
        obj: Any,
        objType: Class<*>,
        convertedObj: Any,
        callChain: String
    ): VisitorResult {
        if (objType.isEnum) {
            Truth.assertWithMessage(
                "Expected $callChain to be $obj, instead was $convertedObj"
            ).that(obj.toString()).isEqualTo(convertedObj.toString())
            return VisitorResult(1)
        } else if (Collection::class.java.isAssignableFrom(objType)) {
            Truth.assertWithMessage(
                "Expected $callChain to have the same list size"
            ).that((obj as Collection<*>).size).isEqualTo((convertedObj as List<*>).size)

            if (!List::class.java.isAssignableFrom(objType)) {
                return assertAllGettersMatch(
                    obj.toList().sortedBy { it.toString() },
                    List::class.java,
                    convertedObj.toList().sortedBy { it.toString() },
                    callChain
                )
            }

            val result = VisitorResult(0)

            for (index in 0 until obj.size) {
                val ret = assertAllGettersMatch(
                    obj = (obj as List<*>)[index]!!,
                    objType = obj[index]!!::class.java,
                    convertedObj = convertedObj[index]!!,
                    callChain = "$callChain[$index]",
                )

                result.gettersCalled += ret.gettersCalled
            }

            return result
        } else if (Map::class.java.isAssignableFrom(objType)) {
            Truth.assertWithMessage(
                "Expected $callChain to be the same"
            ).that(obj as Map<*, *>).isEqualTo(convertedObj as Map<*, *>)
            return VisitorResult(1)
        }

        when (objType) {
            String::class.java -> {
                Truth.assertWithMessage(
                    "Expected $callChain to be $obj, instead was $convertedObj"
                ).that(obj).isEqualTo(convertedObj)
                return VisitorResult(1)
            }
            File::class.java -> {
                Truth.assertWithMessage(
                    "Expected $callChain to be $obj, instead was $convertedObj"
                ).that((obj as File).absolutePath).isEqualTo(
                    (convertedObj as com.android.builder.model.proto.ide.File).absolutePath
                )
                return VisitorResult(1)
            }
        }

        fun getProtoEquivalent(
            convertedObj: Any,
            methodName: String
        ): Any? {
            val hasValueMethodName = "has" + methodName.removePrefix("get")
            val hasValueMethod = try {
                convertedObj::class.java.getMethod(hasValueMethodName)
            } catch (e: NoSuchMethodException) {
                null
            }

            if (hasValueMethod?.invoke(convertedObj) == false) {
                return null
            }

            return convertedObj::class.java.getMethod(methodName).invoke(convertedObj)
        }

        val result = VisitorResult(0)

        objType.methods.toList().forEach { method ->
            val currentCallChain = "$callChain.${method.name}"
            val original = method.invoke(obj)
            val converted = getProtoEquivalent(
                objectMapper(convertedObj, method), methodMapper(method)
            )

            if (original == null) {
                Truth.assertWithMessage(
                    "Expected $currentCallChain to be null, instead was $converted"
                ).that(
                    converted
                ).isNull()
            } else {
                Truth.assertWithMessage(
                    "Expected $currentCallChain to be $original, instead was null"
                ).that(
                    converted
                ).isNotNull()

                val ret = assertAllGettersMatch(
                    obj = original,
                    objType = method.returnType,
                    convertedObj = converted!!,
                    callChain = currentCallChain
                )

                result.gettersCalled += ret.gettersCalled
            }
        }

        return result
    }

    private var intIterator = 0

    // Generate distinct strings to be used in data conversion to make sure that each value is
    // mapped correctly.
    private fun nextString() = intIterator++.toString()
    private fun nextFile() = File(FileUtils.join(nextString(), nextString(), nextString()))

    private class VisitorResult(var gettersCalled: Int)
}
