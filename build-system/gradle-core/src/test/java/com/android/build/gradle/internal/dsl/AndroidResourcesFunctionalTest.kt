/*
 * Copyright (C) 2019 The Android Open Source Project
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

import com.android.build.api.dsl.AndroidResources
import com.android.build.api.dsl.ApplicationExtension
import com.android.build.api.dsl.CommonExtension
import com.android.build.api.dsl.DynamicFeatureExtension
import com.android.build.api.dsl.LibraryExtension
import com.android.build.api.dsl.TestExtension
import com.android.build.gradle.BaseExtension
import com.android.build.gradle.internal.ProguardFileType
import com.android.build.gradle.internal.dsl.AgpDslLockedException
import com.android.build.gradle.internal.dsl.BaseAppModuleExtension
import com.android.build.gradle.internal.dsl.PostProcessingBlock
import com.android.build.gradle.internal.fixture.TestConstants
import com.android.build.gradle.internal.fixture.TestProjects
import com.android.build.gradle.internal.packaging.defaultExcludes
import com.android.build.gradle.internal.packaging.defaultMerges
import com.google.common.base.CaseFormat
import com.google.common.collect.ImmutableList
import com.google.common.collect.ImmutableMap
import com.google.common.truth.StringSubject
import com.google.common.truth.Truth.assertThat
import java.io.File
import kotlin.test.assertFailsWith
import org.gradle.api.Project
import org.gradle.api.plugins.ExtensionAware
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import java.lang.RuntimeException

/**
 * Functional tests for the new Kotlin DSL.
 *
 * The AndroidResources block is unusual as it was subclassed after initially being created
 * on common extension, which is currently unsupported by the DSL decorator.
 */
class AndroidResourcesFunctionalTest {

    @get:Rule
    val projectDirectory = TemporaryFolder()

    private fun buildProject(plugin: TestProjects.Plugin): Project {
        val project = TestProjects.builder(projectDirectory.newFolder("project").toPath())
                .withPlugin(plugin)
                .build()
        project.extensions.getByType(CommonExtension::class.java).apply {
            namespace = "com.example." + CaseFormat.UPPER_UNDERSCORE.to(CaseFormat.LOWER_CAMEL, plugin.name)
            compileSdk = TestConstants.COMPILE_SDK_VERSION
        }
        return project
    }

    private fun assertConsistency(project: Project, androidResources: AndroidResources) {
        val commonExtension = project.extensions.getByType(CommonExtension::class.java)
        commonExtension.androidResources {
            assertThat(commonExtension.androidResources).isSameInstanceAs(this)
            assertThat(androidResources).isSameInstanceAs(this)
            assertThat(commonExtension.androidResources.ignoreAssetsPatterns).contains("!.something")
        }
    }

    @Test
    fun testAppAndroidResourcesBlock() {
        val project = buildProject(TestProjects.Plugin.APP)
        val applicationExtension = project.extensions.getByType(ApplicationExtension::class.java)
        applicationExtension.apply {
            androidResources {
                ignoreAssetsPatterns += "!.something"
                assertThat(ignoreAssetsPatterns).contains("!.something")
                assertThat(applicationExtension.androidResources).isSameInstanceAs(this)
            }
        }
        assertConsistency(project, applicationExtension.androidResources)
    }

    @Test
    fun testLibraryAndroidResourcesBlock() {
        val project = buildProject(TestProjects.Plugin.LIBRARY)
        val applicationExtension = project.extensions.getByType(LibraryExtension::class.java)
        applicationExtension.apply {
            androidResources {
                ignoreAssetsPatterns += "!.something"
                assertThat(ignoreAssetsPatterns).contains("!.something")
                assertThat(applicationExtension.androidResources).isSameInstanceAs(this)
            }
        }
        assertConsistency(project, applicationExtension.androidResources)
    }

    @Test
    fun testDynamicFeatureAndroidResourcesBlock() {
        val project = buildProject(TestProjects.Plugin.DYNAMIC_FEATURE)
        val applicationExtension = project.extensions.getByType(DynamicFeatureExtension::class.java)
        applicationExtension.apply {
            androidResources {
                ignoreAssetsPatterns += "!.something"
                assertThat(ignoreAssetsPatterns).contains("!.something")
                assertThat(applicationExtension.androidResources).isSameInstanceAs(this)
            }
        }
        assertConsistency(project, applicationExtension.androidResources)
    }
    @Test
    fun testTestAndroidResourcesBlock() {
        val project = buildProject(TestProjects.Plugin.TEST)
        val applicationExtension = project.extensions.getByType(TestExtension::class.java)
        applicationExtension.apply {
            androidResources {
                ignoreAssetsPatterns += "!.something"
                assertThat(ignoreAssetsPatterns).contains("!.something")
                assertThat(applicationExtension.androidResources).isSameInstanceAs(this)
            }
        }
        assertConsistency(project, applicationExtension.androidResources)
    }
}
