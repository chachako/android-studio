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

package com.android.build.api.component.analytics

import com.android.build.api.artifact.SingleArtifact
import com.android.build.api.artifact.Artifacts
import com.android.build.api.artifact.TaskBasedOperation
import com.android.build.api.variant.BuiltArtifactsLoader
import com.android.build.api.variant.ScopedArtifacts
import com.android.build.gradle.internal.fixtures.FakeObjectFactory
import com.android.tools.build.gradle.internal.profile.VariantPropertiesMethodType
import com.google.common.truth.Truth
import com.google.wireless.android.sdk.stats.GradleBuildVariant
import org.gradle.api.Task
import org.gradle.api.file.Directory
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.OutputFiles
import org.gradle.api.tasks.TaskProvider
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class AnalyticsEnabledArtifactsTest {
    abstract class FileBasedTask : Task {
        @get:InputFile
        abstract val inputDir: RegularFileProperty
        @get:OutputFiles
        abstract val outputDir: RegularFileProperty
    }

    private val task: FileBasedTask = mock()

    private val delegate: Artifacts = mock()

    private val stats = GradleBuildVariant.newBuilder()
    private lateinit var proxy: AnalyticsEnabledArtifacts

    @Before
    fun setup() {
        proxy = AnalyticsEnabledArtifacts(delegate, stats, FakeObjectFactory.factory)
    }

    @Test
    fun testGetBuiltArtifactsLoader() {
        @Suppress("UNCHECKED_CAST")
        val fakeLoader = mock<BuiltArtifactsLoader>()

        whenever(delegate.getBuiltArtifactsLoader()).thenReturn(fakeLoader)
        Truth.assertThat(proxy.getBuiltArtifactsLoader()).isEqualTo(fakeLoader)

        Truth.assertThat(stats.variantApiAccess.variantPropertiesAccessCount).isEqualTo(1)
        Truth.assertThat(
            stats.variantApiAccess.variantPropertiesAccessList.first().type
        ).isEqualTo(VariantPropertiesMethodType.GET_BUILT_ARTIFACTS_LOADER_VALUE)
        verify(delegate, times(1))
            .getBuiltArtifactsLoader()
    }

    @Test
    fun testGet() {
        @Suppress("UNCHECKED_CAST")
        val fakeProvider = mock<Provider<Directory>>()

        whenever(delegate.get(SingleArtifact.APK)).thenReturn(fakeProvider)
        Truth.assertThat(proxy.get(SingleArtifact.APK)).isEqualTo(fakeProvider)

        Truth.assertThat(stats.variantApiAccess.variantPropertiesAccessCount).isEqualTo(1)
        Truth.assertThat(
            stats.variantApiAccess.variantPropertiesAccessList.first().type
        ).isEqualTo(VariantPropertiesMethodType.GET_ARTIFACT_VALUE)
        verify(delegate, times(1))
            .get(SingleArtifact.APK)
    }

    @Test
    fun testUse() {
        val taskProvider = mock<TaskProvider<*>>()
        val taskBasedOperation = mock<TaskBasedOperation<*>>()

        whenever(delegate.use(taskProvider)).thenReturn(taskBasedOperation)
        Truth.assertThat(proxy.use(taskProvider)).isInstanceOf(
            TaskBasedOperation::class.java
        )

        Truth.assertThat(stats.variantApiAccess.variantPropertiesAccessCount).isEqualTo(1)
        Truth.assertThat(
            stats.variantApiAccess.variantPropertiesAccessList.first().type
        ).isEqualTo(VariantPropertiesMethodType.USE_TASK_VALUE)
        verify(delegate, times(1))
            .use(taskProvider)
    }

    @Test
    fun testForProjectScope() {
        val scopedArtifacts = mock<ScopedArtifacts>()

        whenever(delegate.forScope(ScopedArtifacts.Scope.PROJECT)).thenReturn(scopedArtifacts)
        Truth.assertThat(proxy.forScope(ScopedArtifacts.Scope.PROJECT)).isInstanceOf(
            ScopedArtifacts::class.java
        )

        Truth.assertThat(stats.variantApiAccess.variantPropertiesAccessCount).isEqualTo(1)
        Truth.assertThat(
            stats.variantApiAccess.variantPropertiesAccessList.first().type
        ).isEqualTo(VariantPropertiesMethodType.FOR_SCOPE_VALUE)
        verify(delegate, times(1))
            .forScope(ScopedArtifacts.Scope.PROJECT)
    }
}
