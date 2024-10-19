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

import com.android.build.api.artifact.CombiningOperationRequest
import com.android.build.api.artifact.InAndOutDirectoryOperationRequest
import com.android.build.api.artifact.InAndOutFileOperationRequest
import com.android.build.api.artifact.MultipleArtifactTypeOutOperationRequest
import com.android.build.api.artifact.OutOperationRequest
import com.android.build.api.artifact.TaskBasedOperation
import com.android.build.gradle.internal.fixtures.FakeObjectFactory
import com.android.tools.build.gradle.internal.profile.VariantPropertiesMethodType
import com.google.common.truth.Truth
import com.google.wireless.android.sdk.stats.GradleBuildVariant
import org.gradle.api.Task
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFile
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.OutputFiles
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class AnalyticsEnabledTaskBaseOperationTest {

    abstract class FileBasedTask : Task {
        @get:InputFile abstract val inputFile: RegularFileProperty
        @get:OutputFile abstract val outputFile: RegularFileProperty
    }

    private val stats = GradleBuildVariant.newBuilder()

    @Test
    fun testWiredWith() {
        @Suppress("UNCHECKED_CAST")
        val delegate: TaskBasedOperation<FileBasedTask> =
            mock<TaskBasedOperation<FileBasedTask>>()
        @Suppress("UNCHECKED_CAST")
        val fakeOutputRequest: OutOperationRequest<RegularFile> =
            mock<OutOperationRequest<RegularFile>>()
        val proxy = AnalyticsEnabledTaskBaseOperation(delegate, stats, FakeObjectFactory.factory)

        whenever(delegate.wiredWith(FileBasedTask::outputFile)).thenReturn(fakeOutputRequest)
        Truth.assertThat(proxy.wiredWith(FileBasedTask::outputFile)).isInstanceOf(
            OutOperationRequest::class.java
        )

        Truth.assertThat(stats.variantApiAccess.variantPropertiesAccessCount).isEqualTo(1)
        Truth.assertThat(
            stats.variantApiAccess.variantPropertiesAccessList.first().type
        ).isEqualTo(VariantPropertiesMethodType.WIRED_WITH_VALUE)
        verify(delegate, times(1))
            .wiredWith(FileBasedTask::outputFile)
    }

    @Test
    fun testWiredWithFiles() {
        @Suppress("UNCHECKED_CAST")
        val delegate: TaskBasedOperation<FileBasedTask> =
            mock<TaskBasedOperation<FileBasedTask>>()
        @Suppress("UNCHECKED_CAST")
        val proxy = AnalyticsEnabledTaskBaseOperation(delegate, stats, FakeObjectFactory.factory)
        val fakeOutputRequest: InAndOutFileOperationRequest =
            mock<InAndOutFileOperationRequest>()

        whenever(delegate.wiredWithFiles(
            FileBasedTask::inputFile,
            FileBasedTask::outputFile
        )).thenReturn(fakeOutputRequest)
        Truth.assertThat(proxy.wiredWithFiles(FileBasedTask::inputFile, FileBasedTask::outputFile)).isInstanceOf(
            InAndOutFileOperationRequest::class.java
        )

        Truth.assertThat(stats.variantApiAccess.variantPropertiesAccessCount).isEqualTo(1)
        Truth.assertThat(
            stats.variantApiAccess.variantPropertiesAccessList.first().type
        ).isEqualTo(VariantPropertiesMethodType.WIRED_WITH_FILES_VALUE)
        verify(delegate, times(1))
            .wiredWithFiles(
                FileBasedTask::inputFile,
                FileBasedTask::outputFile)
    }

    @Test
    fun testWiredWithDirectories() {
        abstract class DirectoryBasedTask : Task {
            @get:InputFiles abstract val inputDir: DirectoryProperty
            @get:OutputFiles abstract val outputDir: DirectoryProperty
        }

        @Suppress("UNCHECKED_CAST")
        val delegate: TaskBasedOperation<DirectoryBasedTask> =
            mock<TaskBasedOperation<DirectoryBasedTask>>()
        @Suppress("UNCHECKED_CAST")
        val proxy = AnalyticsEnabledTaskBaseOperation(delegate, stats, FakeObjectFactory.factory)
        @Suppress("UNCHECKED_CAST")
        val fakeOutputRequest = mock<InAndOutDirectoryOperationRequest<DirectoryBasedTask>>()

        whenever(delegate.wiredWithDirectories(
            DirectoryBasedTask::inputDir,
            DirectoryBasedTask::outputDir
        )).thenReturn(fakeOutputRequest)
        Truth.assertThat(proxy.wiredWithDirectories(
            DirectoryBasedTask::inputDir,
            DirectoryBasedTask::outputDir)
        ).isInstanceOf(
            InAndOutDirectoryOperationRequest::class.java
        )

        Truth.assertThat(stats.variantApiAccess.variantPropertiesAccessCount).isEqualTo(1)
        Truth.assertThat(
            stats.variantApiAccess.variantPropertiesAccessList.first().type
        ).isEqualTo(VariantPropertiesMethodType.WIRED_WITH_DIRECTORIES_VALUE)
        verify(delegate, times(1))
            .wiredWithDirectories(
                DirectoryBasedTask::inputDir,
                DirectoryBasedTask::outputDir)
    }

    @Test
    fun testWiredWithAndCombining() {
        abstract class DirectoryBasedTask : Task {
            @get:InputFiles abstract val inputFiles: ListProperty<RegularFile>
            @get:OutputFiles abstract val outputFile: RegularFileProperty
        }
        @Suppress("UNCHECKED_CAST")
        val delegate: TaskBasedOperation<DirectoryBasedTask> =
            mock<TaskBasedOperation<DirectoryBasedTask>>()
        @Suppress("UNCHECKED_CAST")
        val proxy = AnalyticsEnabledTaskBaseOperation(delegate, stats, FakeObjectFactory.factory)
        @Suppress("UNCHECKED_CAST")
        val fakeOutputRequest = mock<CombiningOperationRequest<RegularFile>>()

        whenever(delegate.wiredWith(
            DirectoryBasedTask::inputFiles,
            DirectoryBasedTask::outputFile
        )).thenReturn(fakeOutputRequest)
        Truth.assertThat(proxy.wiredWith(
            DirectoryBasedTask::inputFiles,
            DirectoryBasedTask::outputFile
        )).isInstanceOf(
            CombiningOperationRequest::class.java
        )

        Truth.assertThat(stats.variantApiAccess.variantPropertiesAccessCount).isEqualTo(1)
        Truth.assertThat(
            stats.variantApiAccess.variantPropertiesAccessList.first().type
        ).isEqualTo(VariantPropertiesMethodType.WIRED_WITH_LIST_VALUE)
        verify(delegate, times(1))
            .wiredWith(
                DirectoryBasedTask::inputFiles,
                DirectoryBasedTask::outputFile)
    }

    @Test
    fun testWiredWithMultiple() {
        abstract class MultipleRegularFilesBasedTask : Task {
            @get:InputFiles abstract val inputFiles: ListProperty<RegularFile>
        }
        @Suppress("UNCHECKED_CAST")
        val delegate: TaskBasedOperation<MultipleRegularFilesBasedTask> =
            mock<TaskBasedOperation<MultipleRegularFilesBasedTask>>()
        @Suppress("UNCHECKED_CAST")
        val proxy = AnalyticsEnabledTaskBaseOperation(delegate, stats, FakeObjectFactory.factory)
        @Suppress("UNCHECKED_CAST")
        val fakeOutputRequest = mock<MultipleArtifactTypeOutOperationRequest<RegularFile>>()

        whenever(delegate.wiredWithMultiple(
            MultipleRegularFilesBasedTask::inputFiles,
        )).thenReturn(fakeOutputRequest)
        Truth.assertThat(proxy.wiredWithMultiple(
            MultipleRegularFilesBasedTask::inputFiles,
        )).isInstanceOf(
            MultipleArtifactTypeOutOperationRequest::class.java
        )

        Truth.assertThat(stats.variantApiAccess.variantPropertiesAccessCount).isEqualTo(1)
        Truth.assertThat(
            stats.variantApiAccess.variantPropertiesAccessList.first().type
        ).isEqualTo(VariantPropertiesMethodType.WIRED_WITH_MULTIPLE_VALUE)
        verify(delegate, times(1))
            .wiredWithMultiple(
                MultipleRegularFilesBasedTask::inputFiles)
    }
}
