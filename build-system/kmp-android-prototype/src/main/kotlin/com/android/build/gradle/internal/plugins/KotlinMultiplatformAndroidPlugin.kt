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

package com.android.build.gradle.internal.plugins

import com.android.build.gradle.internal.SdkComponentsBuildService
import com.android.build.gradle.internal.crash.afterEvaluate
import com.android.build.gradle.internal.dsl.KotlinMultiplatformAndroidExtension
import com.android.build.gradle.internal.dsl.KotlinMultiplatformAndroidExtensionImpl
import com.android.build.gradle.internal.dsl.decorator.androidPluginDslDecorator
import com.android.build.gradle.internal.services.DslServicesImpl
import com.google.wireless.android.sdk.stats.GradleBuildProject
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.provider.Provider
import org.gradle.build.event.BuildEventsListenerRegistry
import javax.inject.Inject

abstract class KotlinMultiplatformAndroidPlugin @Inject constructor(
    listenerRegistry: BuildEventsListenerRegistry
): AndroidPluginBaseServices(listenerRegistry), Plugin<Project> {

    private lateinit var androidExtension: KotlinMultiplatformAndroidExtension

    private val dslServices by lazy {
        withProject("dslServices") { project ->
            val sdkComponentsBuildService: Provider<SdkComponentsBuildService> =
                SdkComponentsBuildService.RegistrationAction(
                    project,
                    projectServices.projectOptions
                ).execute()

            DslServicesImpl(
                projectServices,
                sdkComponentsBuildService
            )
        }
    }

    // TODO(b/243387425): Support analytics
    override fun getAnalyticsPluginType(): GradleBuildProject.PluginType? =
        GradleBuildProject.PluginType.UNKNOWN_PLUGIN_TYPE

    override fun configureProject(project: Project) { }

    override fun createTasks(project: Project) { }

    override fun apply(project: Project) {
        super.basePluginApply(project)

        afterEvaluate {
            if (!project.plugins.hasPlugin("org.jetbrains.kotlin.multiplatform")) {
                throw RuntimeException("Kotlin multiplatform plugin was not found. This plugin needs" +
                        " to be applied as part of the kotlin multiplatform plugin.")
            }
        }
    }

    override fun configureExtension(project: Project) {
        val extensionImplClass = androidPluginDslDecorator
            .decorate(KotlinMultiplatformAndroidExtensionImpl::class.java)
        androidExtension = project.extensions.create(
            KotlinMultiplatformAndroidExtension::class.java,
            "android",
            extensionImplClass,
            dslServices
        )
    }
}
