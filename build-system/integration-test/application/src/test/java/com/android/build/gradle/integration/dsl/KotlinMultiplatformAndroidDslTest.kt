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

package com.android.build.gradle.integration.dsl

import com.android.build.api.variant.Component
import com.android.build.api.variant.ComponentIdentity
import com.android.build.api.variant.KotlinMultiplatformAndroidVariant
import com.android.build.api.variant.Variant
import com.google.common.truth.Truth
import org.junit.Test

class KotlinMultiplatformAndroidDslTest {

    @Test
    fun variantDslMethodsExistInKotlinMultiplatformAndroid() {
        val variantMethods = Variant::class.java.methods
            .filter { !it.annotations.any { it.annotationClass == Deprecated::class } }
            .map {
                it.toString()
                    .replace("${Variant::class.java.name}.", "")
                    .replace("${Component::class.java.name}.", "")
                    .replace("${ComponentIdentity::class.java.name}.", "")
            }.filter { it !in extraVariantMethods }

        val kmpVariantMethods = KotlinMultiplatformAndroidVariant::class.java.methods.map {
            it.toString()
                .replace("${KotlinMultiplatformAndroidVariant::class.java.name}.", "")
        }.filter { it !in extraKmpMethods }

        Truth.assertWithMessage(
            "Mismatches found between DSL exposed in the kmp variant interface and the variant. " +
            "Either add the missing DSL methods or update the test."
        ).that(variantMethods).containsExactlyElementsIn(kmpVariantMethods)
    }

    companion object {
        private val extraKmpMethods = setOf(
            // These exist on the plugin-specific variant level.
            "public abstract com.android.build.api.variant.AndroidTest com.android.build.api.variant.HasAndroidTest.getAndroidTest()",
            "public abstract com.android.build.api.variant.UnitTest com.android.build.api.variant.HasUnitTest.getUnitTest()",
            "public abstract java.util.List com.android.build.api.variant.HasDeviceTests.getDeviceTests()",
            "public abstract com.android.build.api.variant.DeviceTest com.android.build.api.variant.HasDeviceTests.getDefaultDeviceTest()",
            "public abstract com.android.build.api.variant.DeviceTest com.android.build.api.variant.HasDeviceTests.getByName(java.lang.String)",
            "public abstract com.android.build.api.variant.TestedComponentPackaging com.android.build.api.variant.HasAndroidTest.getPackaging()",
        )

        private val extraVariantMethods = setOf(
            // Dsl related to features that are disabled in kmp
            "public abstract com.android.build.api.variant.ExternalNativeBuild getExternalNativeBuild()",
            "public abstract com.android.build.api.variant.ResValue\$Key com.android.build.api.variant.HasAndroidResources.makeResValueKey(java.lang.String,java.lang.String)",
            "public abstract org.gradle.api.provider.ListProperty getProguardFiles()",
            "public abstract org.gradle.api.provider.MapProperty com.android.build.api.variant.HasAndroidResources.getResValues()",
            "public abstract org.gradle.api.provider.MapProperty getBuildConfigFields()",
            "public abstract org.gradle.api.provider.MapProperty getManifestPlaceholders()",
            "public abstract org.gradle.api.provider.Property com.android.build.api.variant.HasAndroidResources.getPseudoLocalesEnabled()",

            // Not applicable due to the single-variant nature of kmp
            "public abstract boolean getDebuggable()",
            "public abstract java.lang.String getBuildType()",
            "public abstract java.lang.String getFlavorName()",
            "public abstract java.util.List getProductFlavors()",

            // Managed by the kotlin plugin DSL
            "public abstract com.android.build.api.variant.JavaCompilation getJavaCompilation()",
            "public abstract com.android.build.api.variant.Sources getSources()",
            "public abstract org.gradle.api.artifacts.Configuration getAnnotationProcessorConfiguration()", // kapt-ksp
            "public abstract org.gradle.api.artifacts.Configuration getCompileConfiguration()",
            "public abstract org.gradle.api.artifacts.Configuration getRuntimeConfiguration()",

            // Exposed on the extension-level only. Since this is a single variant, it doesn't make
            // sense to have a way to override these values per variant.
            "public abstract com.android.build.api.variant.Packaging getPackaging()",
            "public abstract org.gradle.api.provider.MapProperty getExperimentalProperties()",
            "public abstract void missingDimensionStrategy(java.lang.String,java.lang.String[])",

            // Exposed on the extension-level only. Since this is a single variant, users can wrap
            // the DSL value in a provider to get the final value directly.
            "public abstract com.android.build.api.variant.AndroidVersion getMinSdk()",
            "public abstract java.lang.Integer getMaxSdk()",
            "public abstract org.gradle.api.provider.Provider getNamespace()",

            // KotlinMultiplatformAndroidVariant doesn't extend component
            "public abstract java.util.List getComponents()",

            // Not supported
            "public abstract java.lang.Object getExtension(java.lang.Class)",
            "public abstract java.lang.String computeTaskName(java.lang.String,java.lang.String)",
            "public abstract boolean getViewBinding()",
            "public abstract boolean getDataBinding()"
        )
    }
}
