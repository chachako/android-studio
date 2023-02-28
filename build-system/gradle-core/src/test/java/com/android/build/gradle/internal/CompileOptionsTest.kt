/*
 * Copyright (C) 2015 The Android Open Source Project
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
package com.android.build.gradle.internal

import com.android.build.gradle.internal.CompileOptions.Companion.parseJavaVersion
import com.android.build.gradle.internal.dsl.decorator.androidPluginDslDecorator
import com.android.build.gradle.internal.services.DslServices
import com.android.build.gradle.internal.services.createDslServices
import com.google.common.truth.Truth.assertThat
import org.gradle.api.JavaVersion
import org.junit.Test
import kotlin.test.assertFailsWith

/** Tests for [CompileOptions] */
class CompileOptionsTest {

    private fun compileOptions() = androidPluginDslDecorator
        .decorate(CompileOptions::class.java)
        .getDeclaredConstructor(DslServices::class.java)
        .newInstance(createDslServices())

    @Test
    fun `test source and targetCompatibility, dslVersion == null, toolchainVersion == null`() {
        val options = compileOptions()
        options.finalizeSourceAndTargetCompatibility(toolchainVersion = null)

        assertThat(options.sourceCompatibility).isEqualTo(JavaVersion.VERSION_1_8)
        assertThat(options.targetCompatibility).isEqualTo(JavaVersion.VERSION_1_8)
    }

    @Test
    fun `test source and targetCompatibility, dslVersion == null, toolchainVersion != null`() {
        val options = compileOptions()
        options.finalizeSourceAndTargetCompatibility(toolchainVersion = JavaVersion.VERSION_11)

        assertThat(options.sourceCompatibility).isEqualTo(JavaVersion.VERSION_11)
        assertThat(options.targetCompatibility).isEqualTo(JavaVersion.VERSION_11)
    }

    @Test
    fun `test source and targetCompatibility, dslVersion != null, toolchainVersion == null`() {
        val options = compileOptions()
        options.sourceCompatibility = JavaVersion.VERSION_17
        options.targetCompatibility = JavaVersion.VERSION_17
        options.finalizeSourceAndTargetCompatibility(toolchainVersion = null)

        assertThat(options.sourceCompatibility).isEqualTo(JavaVersion.VERSION_17)
        assertThat(options.targetCompatibility).isEqualTo(JavaVersion.VERSION_17)
    }

    @Test
    fun `test source and targetCompatibility, dslVersion != null, toolchainVersion != null`() {
        val options = compileOptions()
        options.sourceCompatibility = JavaVersion.VERSION_17
        options.targetCompatibility = JavaVersion.VERSION_17
        options.finalizeSourceAndTargetCompatibility(toolchainVersion = JavaVersion.VERSION_11)

        assertThat(options.sourceCompatibility).isEqualTo(JavaVersion.VERSION_17)
        assertThat(options.targetCompatibility).isEqualTo(JavaVersion.VERSION_17)
    }

    @Test
    fun `test source and targetCompatibility, read before finalization`() {
        val options = compileOptions()

        assertFailsWith<IllegalStateException> {
            options.sourceCompatibility
        }.also {
            assertThat(it.message).isEqualTo("sourceCompatibility is not yet finalized")
        }
        assertFailsWith<IllegalStateException> {
            options.targetCompatibility
        }.also {
            assertThat(it.message).isEqualTo("targetCompatibility is not yet finalized")
        }
    }

    @Test
    fun `test source and targetCompatibility, write after finalization`() {
        val options = compileOptions()
        options.finalizeSourceAndTargetCompatibility(toolchainVersion = null)

        assertFailsWith<IllegalStateException>{
            options.sourceCompatibility = JavaVersion.VERSION_1_8
        }.also {
            assertThat(it.message).isEqualTo("sourceCompatibility has been finalized")
        }
        assertFailsWith<IllegalStateException>{
            options.targetCompatibility = JavaVersion.VERSION_1_8
        }.also {
            assertThat(it.message).isEqualTo("targetCompatibility has been finalized")
        }
    }

    @Test
    fun `test parseJavaVersion`() {
        assertThat(parseJavaVersion(JavaVersion.VERSION_17)).isEqualTo(JavaVersion.VERSION_17)
        assertThat(parseJavaVersion(11)).isEqualTo(JavaVersion.VERSION_11)
        assertThat(parseJavaVersion("1.8")).isEqualTo(JavaVersion.VERSION_1_8)
        assertThat(parseJavaVersion("VERSION_1_7")).isEqualTo(JavaVersion.VERSION_1_7)
        assertThat(parseJavaVersion("Version_1_6")).isEqualTo(JavaVersion.VERSION_1_6)
    }

    @Test
    fun `test isCoreLibraryDesugaringEnabled`() {
        val options = compileOptions()
        assertThat(options.isCoreLibraryDesugaringEnabled).isFalse()

        options.isCoreLibraryDesugaringEnabled = true
        assertThat(options.isCoreLibraryDesugaringEnabled).isTrue()
    }
}
