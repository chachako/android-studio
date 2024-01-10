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
package com.android.build.api.variant.impl

import com.android.build.api.variant.HasHostTestsBuilder
import com.android.build.api.variant.HostTestBuilder
import com.android.build.gradle.internal.dsl.ModulePropertyKey
import com.android.build.gradle.internal.services.VariantBuilderServices
import com.android.build.gradle.options.BooleanOption
import com.android.builder.core.ComponentType
import com.android.builder.core.ComponentTypeImpl

class HostTestBuilderImpl(
    override var enable: Boolean,
    override var type: String,
    val componentType: ComponentType,
) : HostTestBuilder {
    companion object {
        fun forUnitTest(
            variantBuilderServices: VariantBuilderServices,
        ): HostTestBuilderImpl = HostTestBuilderImpl(
            !variantBuilderServices.projectOptions[BooleanOption.ENABLE_NEW_TEST_DSL],
            HostTestBuilder.UNIT_TEST_TYPE,
            ComponentTypeImpl.UNIT_TEST,
        )

        fun forScreenshotTest(
            experimentalProperties: Map<String, Any>
        ): HostTestBuilderImpl = HostTestBuilderImpl(
            ModulePropertyKey.BooleanWithDefault.SCREENSHOT_TEST.getValue(experimentalProperties),
            HostTestBuilder.SCREENSHOT_TEST_TYPE,
            ComponentTypeImpl.SCREENSHOT_TEST,
        )
    }
}
