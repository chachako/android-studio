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

package com.android.build.gradle.internal.plugins

import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.jetbrains.kotlin.gradle.plugin.KotlinBaseApiPlugin

class AndroidKotlinPlugin : Plugin<Project> {

    override fun apply(project: Project) {
        val incompatiblePlugin = "org.jetbrains.kotlin.android"
        project.plugins.withId(incompatiblePlugin) {
            throw GradleException(
                """
                    The "$incompatiblePlugin" plugin has been applied, but it is not compatible with
                    the "com.android.experimental.built-in-kotlin" plugin.
                    Remove the "$incompatiblePlugin" plugin from ${project.buildFile.toURI()}.
                   """.trimMargin()
            )
        }
        project.plugins.apply(KotlinBaseApiPlugin::class.java)
    }
}

