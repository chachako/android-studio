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

import com.android.build.api.dsl.KotlinMultiplatformAndroidTestOnJvmConfiguration
import com.android.build.gradle.internal.plugins.KotlinMultiplatformAndroidPlugin.Companion.getNamePrefixedWithTarget
import com.android.build.gradle.internal.services.DslServices
import org.gradle.api.tasks.testing.Test
import javax.inject.Inject

abstract class KotlinMultiplatformAndroidTestOnJvmConfigurationImpl @Inject constructor(
    compilationName: String,
    val dslServices: DslServices,
): KotlinMultiplatformAndroidTestOnJvmConfiguration, KotlinMultiplatformAndroidTestConfigurationImpl(compilationName) {
    // Used by testTasks.all below, DSL docs generator can't handle diamond operator.
    private val testTasks = dslServices.domainObjectSet(Test::class.java)

    override var defaultSourceSetName: String = compilationName.getNamePrefixedWithTarget()
    override var sourceSetTree: String? = null

    override var isReturnDefaultValues: Boolean = false
    override var isIncludeAndroidResources: Boolean = false
    override fun all(configAction: (Test) -> Unit) {
        testTasks.all(configAction)
    }
}
