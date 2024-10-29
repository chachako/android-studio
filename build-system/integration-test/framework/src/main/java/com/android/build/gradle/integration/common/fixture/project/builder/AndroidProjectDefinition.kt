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

package com.android.build.gradle.integration.common.fixture.project.builder

import com.android.build.api.dsl.ApplicationExtension
import com.android.build.api.dsl.CommonExtension
import com.android.build.api.dsl.DynamicFeatureExtension
import com.android.build.api.dsl.LibraryExtension
import com.android.build.gradle.integration.common.fixture.testprojects.PluginType

interface AndroidProjectDefinition<T: CommonExtension<*,*,*,*,*,*>>: GradleProjectDefinition {
    val android: T
    fun android(action: T.() -> Unit)
}

internal abstract class AndroidProjectDefinitionImpl<T : CommonExtension<*, *, *, *, *, *>>(
    path: String
): GradleProjectDefinitionImpl(path), AndroidProjectDefinition<T> {

    override fun android(action: T.() -> Unit) {
        action(android)
    }
}

internal class AndroidApplicationDefinitionImpl(path: String): AndroidProjectDefinitionImpl<ApplicationExtension>(path) {
    init {
        plugins.add(PluginType.ANDROID_APP)
    }

    override val android: ApplicationExtension
        get() = throw RuntimeException("todo")
}

internal class AndroidLibraryDefinitionImpl(path: String): AndroidProjectDefinitionImpl<LibraryExtension>(path) {
    init {
        plugins.add(PluginType.ANDROID_LIB)
    }

    override val android: LibraryExtension
        get() = throw RuntimeException("todo")
}

internal class AndroidDynamicFeatureDefinitionImpl(path: String): AndroidProjectDefinitionImpl<DynamicFeatureExtension>(path) {
    init {
        plugins.add(PluginType.ANDROID_DYNAMIC_FEATURE)
    }

    override val android: DynamicFeatureExtension
        get() = throw RuntimeException("todo")
}
