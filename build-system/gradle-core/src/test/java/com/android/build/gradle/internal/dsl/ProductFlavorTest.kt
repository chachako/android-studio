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

package com.android.build.gradle.internal.dsl

import com.android.build.gradle.internal.fixture.TestProjects
import com.android.build.gradle.internal.services.createDslServices
import com.android.testutils.internal.CopyOfTester
import com.google.common.collect.ImmutableMap
import org.gradle.api.Project
import org.gradle.testfixtures.ProjectBuilder
import org.junit.Before
import org.junit.Test

class ProductFlavorTest {
    private lateinit var project: Project
    private val dslServices = createDslServices()

    @Before
    @Throws(Exception::class)
    fun setUp() {
        project = ProjectBuilder.builder().build()
        TestProjects.prepareProject(project, ImmutableMap.of())
    }

    @Test
    fun testInitWith() {
        CopyOfTester.assertAllGettersCalled(
                ProductFlavor::class.java,
                dslServices.newDecoratedInstance(ProductFlavor::class.java,
                        "original",
                        dslServices),
                listOf(
                        // isDefault is not copied
                        "isDefault",
                        "getIsDefault",
                        // Extensions are not copied as AGP doesn't manage them
                        "getExtensions",
                        "getGeneratedDensities\$annotations",
                        // I sort of feel that some of these should be copied
                        "getExternalNativeBuild",
                        "getExternalNativeBuildOptions",
                        "getJavaCompileOptions",
                        "getOptimization",
                        "getMaxSdk",
                        "getInternalDimensionDefault\$gradle_core",
                        "getTargetSdkPreview",
                        "getAarMetadata",
                        "getApplicationId",
                        "getNdkConfig",
                        "getNdk",
                        "getShaders",
                        "getMinSdkPreview",
                        "getTargetSdk",
                        "getMinSdk",
                        "getGeneratedDensities",
                )
        ) { original: ProductFlavor ->
            val copy = dslServices.newDecoratedInstance(ProductFlavor::class.java,
                    original.name,
                    dslServices)
            copy.initWith(original)
        }
    }

}
