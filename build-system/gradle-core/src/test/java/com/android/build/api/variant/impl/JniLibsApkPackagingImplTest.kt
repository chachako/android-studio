/*
 * Copyright (C) 2020 The Android Open Source Project
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

import com.android.build.api.dsl.Packaging
import com.android.build.gradle.internal.dsl.JniLibsPackagingImpl
import com.android.build.gradle.internal.dsl.decorator.androidPluginDslDecorator
import com.android.build.gradle.internal.services.DslServices
import com.android.build.gradle.internal.services.createDslServices
import com.android.build.gradle.internal.services.createProjectServices
import com.android.build.gradle.internal.services.createVariantPropertiesApiServices
import com.android.sdklib.AndroidVersion.VersionCodes.M
import com.google.common.collect.Sets
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test

class JniLibsApkPackagingImplTest {

    private lateinit var dslPackaging: Packaging
    private val projectServices = createProjectServices()
    private val dslServices: DslServices = createDslServices(projectServices)
    private val variantPropertiesApiServices = createVariantPropertiesApiServices(projectServices)

    interface PackagingOptionsWrapper {
        val packaging: Packaging
    }

    @Before
    fun setUp() {
        dslPackaging = androidPluginDslDecorator.decorate(PackagingOptionsWrapper::class.java)
            .getDeclaredConstructor(DslServices::class.java)
            .newInstance(dslServices)
            .packaging
    }

    @Test
    fun testExcludes() {
        dslPackaging.excludes.add("foo")
        dslPackaging.jniLibs.excludes.add("bar")
        // test setExcludes method too
        val dslJniLibsPackagingOptionsImpl =
            dslPackaging.jniLibs
                as JniLibsPackagingImpl
        dslJniLibsPackagingOptionsImpl.setExcludes(
            Sets.union(dslPackaging.jniLibs.excludes, setOf("baz"))
        )

        val jniLibsApkPackagingOptions =
            JniLibsApkPackagingImpl(dslPackaging, variantPropertiesApiServices, M)

        assertThat(jniLibsApkPackagingOptions.excludes.get()).containsExactly("foo", "bar", "baz")
    }

    @Test
    fun testPickFirsts() {
        dslPackaging.pickFirsts.add("foo")
        dslPackaging.jniLibs.pickFirsts.add("bar")
        // test setPickFirsts method too
        val dslJniLibsPackagingOptionsImpl =
            dslPackaging.jniLibs
                as JniLibsPackagingImpl
        dslJniLibsPackagingOptionsImpl.setPickFirsts(
            Sets.union(dslPackaging.jniLibs.pickFirsts, setOf("baz"))
        )

        val jniLibsApkPackagingOptions =
            JniLibsApkPackagingImpl(dslPackaging, variantPropertiesApiServices, M)

        assertThat(jniLibsApkPackagingOptions.pickFirsts.get()).containsExactly("foo", "bar", "baz")
    }

    @Test
    fun testKeepDebugSymbols() {
        dslPackaging.doNotStrip.add("foo")
        dslPackaging.jniLibs.keepDebugSymbols.add("bar")
        // test setKeepDebugSymbols method too
        val dslJniLibsPackagingOptionsImpl =
            dslPackaging.jniLibs
                as JniLibsPackagingImpl
        dslJniLibsPackagingOptionsImpl.setKeepDebugSymbols(
            Sets.union(dslPackaging.jniLibs.keepDebugSymbols, setOf("baz"))
        )

        val jniLibsApkPackagingOptions =
            JniLibsApkPackagingImpl(dslPackaging, variantPropertiesApiServices, M)

        assertThat(jniLibsApkPackagingOptions.keepDebugSymbols.get())
            .containsExactly("foo", "bar", "baz")
    }

    @Test
    fun testDefaultUseLegacyPackaging() {
        val jniLibsApkPackagingOptions =
            JniLibsApkPackagingImpl(dslPackaging, variantPropertiesApiServices, M)
        val legacyJniLibsApkPackagingOptions =
            JniLibsApkPackagingImpl(dslPackaging, variantPropertiesApiServices, M - 1)

        assertThat(jniLibsApkPackagingOptions.useLegacyPackaging.get()).isFalse()
        assertThat(legacyJniLibsApkPackagingOptions.useLegacyPackaging.get()).isTrue()
    }

    @Test
    fun testExplicitUseLegacyPackaging() {
        dslPackaging.jniLibs.useLegacyPackaging = true

        val jniLibsApkPackagingOptions =
            JniLibsApkPackagingImpl(dslPackaging, variantPropertiesApiServices, M)

        assertThat(jniLibsApkPackagingOptions.useLegacyPackaging.get()).isTrue()
    }

    @Test
    fun testDefaultUseLegacyPackagingFromBundle() {
        val jniLibsApkPackagingOptions =
            JniLibsApkPackagingImpl(dslPackaging, variantPropertiesApiServices, M)
        val legacyJniLibsApkPackagingOptions =
            JniLibsApkPackagingImpl(dslPackaging, variantPropertiesApiServices, M - 1)

        assertThat(jniLibsApkPackagingOptions.useLegacyPackagingFromBundle.get()).isFalse()
        assertThat(legacyJniLibsApkPackagingOptions.useLegacyPackagingFromBundle.get()).isFalse()
    }

    @Test
    fun testExplicitUseLegacyPackagingFromBundle() {
        dslPackaging.jniLibs.useLegacyPackaging = true

        val jniLibsApkPackagingOptions =
            JniLibsApkPackagingImpl(dslPackaging, variantPropertiesApiServices, M)

        assertThat(jniLibsApkPackagingOptions.useLegacyPackagingFromBundle.get()).isTrue()
    }

}
