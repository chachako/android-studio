/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.declarative.internal.model

/**
 * Resolved Module information. A module info becomes resolved once we have visited its
 * build file, and we obtained the list of all the plugins it uses as well as the list of
 * dependencies it declared.
 */
class ResolvedModuleInfo(
    val projectPath: String,
    val plugins: List<PluginInfo>,
    val dependencies: List<DependencyInfo>,
) {

    // TODO: support all types of modules like test, dynamic features, etc...
    enum class ModuleType {
        PROJECT, LIBRARY, JAVA_LIB
    }

    val moduleType: ModuleType by lazy {
            plugins.forEach {
                if (it.id == "com.android.application") return@lazy ModuleType.PROJECT
                if (it.id == "com.android.library") return@lazy ModuleType.LIBRARY
            }
            ModuleType.JAVA_LIB
        }

    override fun toString(): String =
        "$projectPath type: $moduleType, plugins : ${plugins.joinToString()}, dependencies : ${dependencies.joinToString()}"
}
