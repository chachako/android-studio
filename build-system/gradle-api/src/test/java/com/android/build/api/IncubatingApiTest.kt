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

package com.android.build.api

import com.android.build.api.dsl.CommonExtension
import com.google.common.reflect.ClassPath
import com.google.common.truth.Expect
import com.google.common.truth.Truth.assertThat
import org.gradle.api.Incubating
import org.junit.Rule
import org.junit.Test
import java.lang.reflect.AnnotatedElement
import java.lang.reflect.Method
import java.lang.reflect.ParameterizedType
import java.lang.reflect.Type
import java.lang.reflect.WildcardType

class IncubatingApiTest {

    @get:Rule
    val expect: Expect = Expect.create()

    @Suppress("UnstableApiUsage")
    @Test
    fun `all elements of incubating classes should be marked as incubating`() {
        val classes = ClassPath.from(CommonExtension::class.java.classLoader)
                .getTopLevelClassesRecursive("com.android.build.api.dsl")
                .filter(::filterNonApiClasses)
                .map { it.load() }
        assertThat(classes).isNotEmpty()

        val (incubatingClasses, stableClasses) = classes.partition { it.isIncubating }
                .let { Pair(it.first.toSet(), it.second.toSet()) }

        val nonIncubatingMembersOfIncubatingClasses = incubatingClasses.flatMap { clazz ->
            clazz.declaredMethods.filter { !it.isIncubating }.map { "${clazz.name}#${it.name}" }
        }
        expect.that(nonIncubatingMembersOfIncubatingClasses)
                .named("non-Incubating members of Incubating classes")
                .containsExactlyElementsIn(NON_INCUBATING_METHODS_TO_CLEAN_UP)

        val nonIncubatingMembersThatReferenceIncubatingClasses = stableClasses.flatMap { clazz ->
            clazz.declaredMethods
                    .filter { method ->
                        !method.isIncubating && method.referencesTypes.any {
                            incubatingClasses.contains(it)
                        }
                    }
                    .map { "${clazz.name}#${it.name}" }
        }.toSet()
        expect.that(nonIncubatingMembersThatReferenceIncubatingClasses)
                .named("Non incubating members that reference incubating classes")
                .isEmpty()
    }

    @Suppress("unused") // Used by reflection below
    fun exampleMethod(
            nestedList: List<Map<String, IntArray>>,
            nestedAction: (Long.() -> Boolean) -> Unit,
    ): Set<Int> {
        throw NotImplementedError()
    }

    @Test
    fun `test references types test util method`() {
        val method =
                IncubatingApiTest::class.java.declaredMethods.single { it.name == "exampleMethod" }
        assertThat(method.referencesTypes).containsExactly(
                List::class.java,
                Map::class.java,
                String::class.java,
                IntArray::class.java,
                loadClass("kotlin.jvm.functions.Function1"),
                loadClass("java.lang.Long"),
                loadClass("java.lang.Boolean"),
                Unit::class.java,
                loadClass("java.lang.Object"),
                Set::class.java,
        )
    }

    companion object {

        private fun loadClass(name: String): Class<*> =
                IncubatingApiTest::class.java.classLoader.loadClass(name)

        private val AnnotatedElement.isIncubating get() = annotations.any { it.annotationClass == Incubating::class }

        private val Method.referencesTypes: Set<Class<*>>
            get() =
                setOf(returnType) + genericParameterTypes.flatMap { it.referencesTypes }

        private val Type.referencesTypes: Set<Class<*>>
            get() = when (this) {
                is ParameterizedType -> {
                    setOf(rawType as Class<*>) + actualTypeArguments.flatMap { it.referencesTypes }
                }

                is WildcardType -> {
                    upperBounds.flatMap { it.referencesTypes }
                            .toSet() + lowerBounds.flatMap { it.referencesTypes }
                }

                is Class<*> -> setOf(this)
                else -> emptySet()
            }

        // TODO: clean these up
        private val NON_INCUBATING_METHODS_TO_CLEAN_UP = listOf(
                "com.android.build.api.dsl.JacocoOptions#getVersion",
                "com.android.build.api.dsl.JacocoOptions#setVersion",
                "com.android.build.api.dsl.BundleDeviceTier#getEnableSplit",
                "com.android.build.api.dsl.BundleDeviceTier#setEnableSplit",
                "com.android.build.api.dsl.BundleDeviceTier#getDefaultTier",
                "com.android.build.api.dsl.BundleDeviceTier#setDefaultTier",
                "com.android.build.api.dsl.NdkBuildFlags#targets",
                "com.android.build.api.dsl.NdkBuildFlags#arguments",
                "com.android.build.api.dsl.NdkBuildFlags#getArguments",
                "com.android.build.api.dsl.NdkBuildFlags#getAbiFilters",
                "com.android.build.api.dsl.NdkBuildFlags#abiFilters",
                "com.android.build.api.dsl.NdkBuildFlags#getCFlags",
                "com.android.build.api.dsl.NdkBuildFlags#cFlags",
                "com.android.build.api.dsl.NdkBuildFlags#getCppFlags",
                "com.android.build.api.dsl.NdkBuildFlags#cppFlags",
                "com.android.build.api.dsl.NdkBuildFlags#getTargets",
                "com.android.build.api.dsl.FailureRetention#getEnable",
                "com.android.build.api.dsl.FailureRetention#setEnable",
                "com.android.build.api.dsl.FailureRetention#getMaxSnapshots",
                "com.android.build.api.dsl.FailureRetention#setMaxSnapshots",
                "com.android.build.api.dsl.PostProcessing#initWith",
                "com.android.build.api.dsl.PostProcessing#consumerProguardFile",
                "com.android.build.api.dsl.PostProcessing#consumerProguardFiles",
                "com.android.build.api.dsl.PostProcessing#proguardFile",
                "com.android.build.api.dsl.PostProcessing#proguardFiles",
                "com.android.build.api.dsl.PostProcessing#setProguardFiles",
                "com.android.build.api.dsl.PostProcessing#testProguardFile",
                "com.android.build.api.dsl.PostProcessing#testProguardFiles",
                "com.android.build.api.dsl.PostProcessing#isRemoveUnusedCode",
                "com.android.build.api.dsl.PostProcessing#setRemoveUnusedCode",
                "com.android.build.api.dsl.PostProcessing#isRemoveUnusedResources",
                "com.android.build.api.dsl.PostProcessing#setRemoveUnusedResources",
                "com.android.build.api.dsl.PostProcessing#isObfuscate",
                "com.android.build.api.dsl.PostProcessing#setObfuscate",
                "com.android.build.api.dsl.PostProcessing#isOptimizeCode",
                "com.android.build.api.dsl.PostProcessing#setOptimizeCode",
                "com.android.build.api.dsl.PostProcessing#setTestProguardFiles",
                "com.android.build.api.dsl.PostProcessing#setConsumerProguardFiles",
                "com.android.build.api.dsl.PostProcessing#getCodeShrinker",
                "com.android.build.api.dsl.PostProcessing#setCodeShrinker",
                "com.android.build.api.dsl.TestFixtures#getAndroidResources",
                "com.android.build.api.dsl.TestFixtures#getEnable",
                "com.android.build.api.dsl.TestFixtures#setEnable",
                "com.android.build.api.dsl.TestFixtures#setAndroidResources",
                "com.android.build.api.dsl.BundleCodeTransparency#getSigning",
                "com.android.build.api.dsl.BundleCodeTransparency#signing",
                "com.android.build.api.dsl.ExternalNativeBuildFlags#getNdkBuild",
                "com.android.build.api.dsl.ExternalNativeBuildFlags#ndkBuild",
                "com.android.build.api.dsl.ExternalNativeBuildFlags#getCmake",
                "com.android.build.api.dsl.ExternalNativeBuildFlags#cmake",
                "com.android.build.api.dsl.HasInitWith#initWith",
                "com.android.build.api.dsl.ManagedVirtualDevice#getDevice",
                "com.android.build.api.dsl.ManagedVirtualDevice#setDevice",
                "com.android.build.api.dsl.ManagedVirtualDevice#getApiLevel",
                "com.android.build.api.dsl.ManagedVirtualDevice#setApiLevel",
                "com.android.build.api.dsl.ManagedVirtualDevice#getSystemImageSource",
                "com.android.build.api.dsl.ManagedVirtualDevice#setSystemImageSource",
                "com.android.build.api.dsl.ManagedVirtualDevice#getRequire64Bit",
                "com.android.build.api.dsl.ManagedVirtualDevice#setRequire64Bit",
                "com.android.build.api.dsl.EmulatorSnapshots#retainAll",
                "com.android.build.api.dsl.EmulatorSnapshots#getEnableForTestFailures",
                "com.android.build.api.dsl.EmulatorSnapshots#setEnableForTestFailures",
                "com.android.build.api.dsl.EmulatorSnapshots#getMaxSnapshotsForTestFailures",
                "com.android.build.api.dsl.EmulatorSnapshots#setMaxSnapshotsForTestFailures",
                "com.android.build.api.dsl.EmulatorSnapshots#getCompressSnapshots",
                "com.android.build.api.dsl.EmulatorSnapshots#setCompressSnapshots",
        )
    }
}
