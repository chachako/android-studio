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

import com.android.build.api.dsl.Installation
import com.android.build.api.dsl.KotlinMultiplatformAndroidTestOnDeviceConfiguration
import com.android.build.gradle.internal.plugins.KotlinMultiplatformAndroidPlugin.Companion.getNamePrefixedWithTarget
import com.android.build.gradle.internal.services.DslServices
import com.android.builder.model.TestOptions
import com.android.utils.HelpfulEnumConverter
import com.google.common.base.Preconditions
import com.google.common.base.Verify
import org.gradle.api.Action
import javax.inject.Inject

abstract class KotlinMultiplatformAndroidTestOnDeviceConfigurationImpl @Inject constructor(
    compilationName: String,
    val dslServices: DslServices,
): KotlinMultiplatformAndroidTestOnDeviceConfiguration, KotlinMultiplatformAndroidTestConfigurationImpl(compilationName) {

    private val executionConverter = HelpfulEnumConverter(TestOptions.Execution::class.java)
    private var _execution = TestOptions.Execution.HOST

    override var applicationId: String? = null
    override var functionalTest: Boolean? = null
    override var handleProfiling: Boolean? = null
    override var defaultSourceSetName: String = compilationName.getNamePrefixedWithTarget()
    override var sourceSetTree: String? = null

    override var animationsDisabled: Boolean = false

    override var execution: String
        get() = Verify.verifyNotNull(
            executionConverter.reverse().convert(_execution),
            "No string representation for enum."
        )
        set(value) {
            _execution = Preconditions.checkNotNull(
                executionConverter.convert(value),
                "The value of `execution` cannot be null."
            )
        }

    override val installation: Installation = dslServices.newDecoratedInstance(
        AdbOptions::class.java,
        dslServices
    )

    override fun installation(action: Installation.() -> Unit) {
        action.invoke(installation)
    }

    fun installation(action: Action<Installation>) {
        action.execute(installation)
    }
}
