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

package com.android.build.api.variant.impl

import com.android.build.gradle.internal.CompileOptions
import com.android.build.gradle.internal.dsl.KotlinMultiplatformAndroidExtensionImpl
import com.android.build.gradle.internal.plugins.KotlinMultiplatformAndroidPlugin.Companion.androidExtensionOnKotlinExtensionName
import com.android.utils.appendCapitalized
import org.gradle.api.NamedDomainObjectFactory
import org.jetbrains.kotlin.gradle.ExternalKotlinTargetApi
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.plugin.mpp.external.ExternalKotlinCompilationDescriptor.*
import org.jetbrains.kotlin.gradle.plugin.mpp.external.createCompilation

@OptIn(ExternalKotlinTargetApi::class)
internal class KotlinMultiplatformAndroidCompilationFactory(
    private val target: KotlinMultiplatformAndroidTargetImpl,
    private val kotlinExtension: KotlinMultiplatformExtension,
    private val androidExtension: KotlinMultiplatformAndroidExtensionImpl
): NamedDomainObjectFactory<KotlinMultiplatformAndroidCompilation> {

    @Suppress("INVISIBLE_MEMBER")
    override fun create(name: String): KotlinMultiplatformAndroidCompilationImpl {
        if (KmpPredefinedAndroidCompilation.MAIN.compilationName != name &&
            androidExtension.androidTestOnJvmConfiguration?.compilationName != name &&
            androidExtension.androidTestOnDeviceConfiguration?.compilationName != name) {
            throw IllegalAccessException(
                "Kotlin multiplatform android plugin doesn't support creating arbitrary " +
                        "compilations. Only three types of compilations are supported:\n" +
                        "  * main compilation (named \"${KmpPredefinedAndroidCompilation.MAIN.compilationName}\"),\n" +
                        "  * test on jvm compilation (use `kotlin.$androidExtensionOnKotlinExtensionName.withAndroidTestOnJvm()` to enable),\n" +
                        "  * test on device compilation (use `kotlin.$androidExtensionOnKotlinExtensionName.withAndroidTestOnDevice()` to enable)."
            )
        }

        val isTestComponent = androidExtension.androidTestOnJvmConfiguration?.compilationName == name ||
                androidExtension.androidTestOnDeviceConfiguration?.compilationName == name

        return target.createCompilation<KotlinMultiplatformAndroidCompilationImpl> {
            compilationName = name
            defaultSourceSet = kotlinExtension.sourceSets.getByName(
                target.targetName.appendCapitalized(name)
            )
            compilationFactory =
                CompilationFactory(
                    ::KotlinMultiplatformAndroidCompilationImpl
                )
            compileTaskName = "compile".appendCapitalized(
                target.targetName.appendCapitalized(name)
            )

            if (isTestComponent) {
                compilationAssociator = CompilationAssociator { auxiliary, main ->
                    // No-op when associating a test compilation with a main compilation since we
                    // add a dependency from the configurations of the test components on the main
                    // project later.
                    if (main.compilationName != KmpPredefinedAndroidCompilation.MAIN.compilationName) {
                        // TODO(KT-59562): kotlin will provide an external API of this at some point
                        val defaultAssociator = org.jetbrains.kotlin.gradle.plugin.mpp.compilationImpl.DefaultKotlinCompilationAssociator
                        defaultAssociator.associate(
                            target,
                            auxiliary,
                            main
                        )
                    }
                }
            }
        }.also {
            it.compilerOptions.options.jvmTarget.set(
                JvmTarget.fromTarget(CompileOptions.DEFAULT_JAVA_VERSION.toString())
            )
        }
    }
}
