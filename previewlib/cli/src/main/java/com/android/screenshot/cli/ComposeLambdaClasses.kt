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
package com.android.screenshot.cli

import com.intellij.facet.frameworks.beans.Artifact
import com.intellij.facet.frameworks.beans.ArtifactItem
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ContentIterator
import com.intellij.openapi.roots.OrderEntry
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.util.Pair
import com.intellij.openapi.util.io.FileUtilRt
import com.intellij.openapi.vfs.StandardFileSystems
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileFilter
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.util.download.DownloadableFileDescription
import com.intellij.util.download.DownloadableFileService
import com.intellij.util.download.DownloadableFileSetDescription
import com.intellij.util.download.DownloadableFileSetVersions
import com.intellij.util.download.FileDownloader
import com.intellij.util.download.impl.DownloadableFileDescriptionImpl
import com.intellij.util.download.impl.DownloadableFileSetDescriptionImpl
import com.intellij.util.download.impl.FileSetVersionsFetcherBase
import com.intellij.workspaceModel.ide.BuilderSnapshot
import com.intellij.workspaceModel.ide.StorageReplacement
import com.intellij.workspaceModel.ide.WorkspaceModel
import com.intellij.workspaceModel.storage.EntityStorageSnapshot
import com.intellij.workspaceModel.storage.MutableEntityStorage
import com.intellij.workspaceModel.storage.VersionedEntityStorage
import org.jetbrains.jps.model.module.JpsModuleSourceRootType
import java.io.File
import java.net.URL
import java.util.concurrent.CompletableFuture
import javax.swing.JComponent

/**
 * This file represents minimal set of default implementations of Application/Project level services
 * these implementations don't matter too much, but are required for the current version of the
 * screenshot tool.
 */
internal class DownloadableFileServiceImpl : DownloadableFileService() {

    override fun createFileDescription(
        downloadUrl: String,
        fileName: String
    ): DownloadableFileDescription {
        return DownloadableFileDescriptionImpl(
            downloadUrl,
            FileUtilRt.getNameWithoutExtension(fileName),
            FileUtilRt.getExtension(fileName)
        )
    }

    override fun createFileSetVersions(
        groupId: String?,
        vararg localUrls: URL
    ): DownloadableFileSetVersions<DownloadableFileSetDescription> {
        return object : FileSetVersionsFetcherBase<DownloadableFileSetDescription, DownloadableFileDescription>(
            groupId,
            localUrls
        ) {
            override fun createVersion(
                version: Artifact,
                files: List<DownloadableFileDescription>
            ): DownloadableFileSetDescription {
                return DownloadableFileSetDescriptionImpl(version.name, version.version, files)
            }

            override fun createFileDescription(
                item: ArtifactItem,
                url: String,
                prefix: String?
            ): DownloadableFileDescription {
                return getInstance().createFileDescription(url, item.name)
            }
        }
    }

    override fun createDownloader(description: DownloadableFileSetDescription): FileDownloader {
        return createDownloader(description.files, description.name)
    }

    override fun createDownloader(
        fileDescriptions: List<DownloadableFileDescription>,
        presentableDownloadName: String
    ): FileDownloader {
        return object : FileDownloader {
            override fun downloadWithBackgroundProgress(
                targetDirectoryPath: String?,
                project: Project?
            ): CompletableFuture<MutableList<Pair<VirtualFile, DownloadableFileDescription>>?> {
                TODO("Not yet implemented")
            }

            override fun downloadFilesWithProgress(
                targetDirectoryPath: String?,
                project: Project?,
                parentComponent: JComponent?
            ): MutableList<VirtualFile>? {
                TODO("Not yet implemented")
            }

            override fun downloadWithProgress(
                targetDirectoryPath: String?,
                project: Project?,
                parentComponent: JComponent?
            ): MutableList<Pair<VirtualFile, DownloadableFileDescription>>? {
                TODO("Not yet implemented")
            }

            override fun download(targetDir: File): MutableList<Pair<File, DownloadableFileDescription>> {
                TODO("Not yet implemented")
            }

            override fun download(): Array<VirtualFile>? {
                TODO("Not yet implemented")
            }

            override fun toDirectory(directoryForDownloadedFilesPath: String): FileDownloader {
                TODO("Not yet implemented")
            }

        }
    }

    override fun createDownloader(
        fileDescriptions: List<DownloadableFileDescription>,
        project: Project?,
        parent: JComponent, presentableDownloadName: String
    ): FileDownloader {
        return object : FileDownloader {
            override fun downloadWithBackgroundProgress(
                targetDirectoryPath: String?,
                project: Project?
            ): CompletableFuture<MutableList<Pair<VirtualFile, DownloadableFileDescription>>?> {
                TODO("Not yet implemented")
            }

            override fun downloadFilesWithProgress(
                targetDirectoryPath: String?,
                project: Project?,
                parentComponent: JComponent?
            ): MutableList<VirtualFile>? {
                TODO("Not yet implemented")
            }

            override fun downloadWithProgress(
                targetDirectoryPath: String?,
                project: Project?,
                parentComponent: JComponent?
            ): MutableList<Pair<VirtualFile, DownloadableFileDescription>>? {
                TODO("Not yet implemented")
            }

            override fun download(targetDir: File): MutableList<Pair<File, DownloadableFileDescription>> {
                TODO("Not yet implemented")
            }

            override fun download(): Array<VirtualFile>? {
                TODO("Not yet implemented")
            }

            override fun toDirectory(directoryForDownloadedFilesPath: String): FileDownloader {
                TODO("Not yet implemented")
            }

        }
    }
}

class WorkspaceModelI(
    override val entityStorage: VersionedEntityStorage
) : WorkspaceModel {

    override fun getBuilderSnapshot(): BuilderSnapshot {
        TODO("Not yet implemented")
    }

    override fun replaceProjectModel(replacement: StorageReplacement): Boolean {
        TODO("Not yet implemented")
    }

    override fun <R> updateProjectModel(
        description: String,
        updater: (MutableEntityStorage) -> R
    ): R {
        TODO("Not yet implemented")
    }

    override fun <R> updateProjectModelSilent(
        description: String,
        updater: (MutableEntityStorage) -> R
    ): R {
        TODO("Not yet implemented")
    }
    //
    //override val currentSnapshot: EntityStorageSnapshot
    //    get() = entityStorage.current.toSnapshot()
    //override val currentSnapshotOfUnloadedEntities: EntityStorageSnapshot
    //    get() = entityStorage.current.toSnapshot()
    //
    //override fun updateProjectModel(description: String, updater: (MutableEntityStorage) -> Unit) {
    //    TODO("Not yet implemented")
    //}
    //
    //override fun updateUnloadedEntities(
    //    description: String,
    //    updater: (MutableEntityStorage) -> Unit
    //) {
    //    TODO("Not yet implemented")
    //}
}


class ScreenshotProjectFileIndex : ProjectFileIndex {

    var project: ComposeProject? = null
    var module: ComposeModule? = null
    override fun iterateContent(processor: ContentIterator): Boolean {
        TODO("Not yet implemented")
    }

    override fun iterateContent(processor: ContentIterator, filter: VirtualFileFilter?): Boolean {
        TODO("Not yet implemented")
    }

    override fun iterateContentUnderDirectory(
        dir: VirtualFile,
        processor: ContentIterator
    ): Boolean {
        TODO("Not yet implemented")
    }

    override fun iterateContentUnderDirectory(
        dir: VirtualFile,
        processor: ContentIterator,
        customFilter: VirtualFileFilter?
    ): Boolean {
        TODO("Not yet implemented")
    }

    override fun isInContent(fileOrDir: VirtualFile): Boolean {
        TODO("Not yet implemented")
    }

    override fun isInSourceContent(fileOrDir: VirtualFile): Boolean {
        TODO("Not yet implemented")
    }

    override fun isInTestSourceContent(fileOrDir: VirtualFile): Boolean {
        TODO("Not yet implemented")
    }

    override fun isUnderSourceRootOfType(
        fileOrDir: VirtualFile,
        rootTypes: MutableSet<out JpsModuleSourceRootType<*>>
    ): Boolean {
        TODO("Not yet implemented")
    }

    override fun isInProject(file: VirtualFile): Boolean {
        TODO("Not yet implemented")
    }

    override fun isInProjectOrExcluded(file: VirtualFile): Boolean {
        TODO("Not yet implemented")
    }

    override fun getModuleForFile(file: VirtualFile): Module? {
        return module?.getIdeaModule()
    }

    override fun getModuleForFile(file: VirtualFile, honorExclusion: Boolean): Module? {
        TODO("Not yet implemented")
    }

    override fun getOrderEntriesForFile(file: VirtualFile): MutableList<OrderEntry> {
        TODO("Not yet implemented")
    }

    override fun getClassRootForFile(file: VirtualFile): VirtualFile? {
        TODO("Not yet implemented")
    }

    override fun getSourceRootForFile(file: VirtualFile): VirtualFile? {
        TODO("Not yet implemented")
    }

    override fun getContentRootForFile(file: VirtualFile): VirtualFile? {
        TODO("Not yet implemented")
    }

    override fun getContentRootForFile(file: VirtualFile, honorExclusion: Boolean): VirtualFile? {
        TODO("Not yet implemented")
    }

    override fun getPackageNameByDirectory(dir: VirtualFile): String? {
        TODO("Not yet implemented")
    }

    override fun isLibraryClassFile(file: VirtualFile): Boolean {
        TODO("Not yet implemented")
    }

    override fun isInSource(fileOrDir: VirtualFile): Boolean {
        TODO("Not yet implemented")
    }

    override fun isInLibraryClasses(fileOrDir: VirtualFile): Boolean {
        TODO("Not yet implemented")
    }

    override fun isInLibrary(fileOrDir: VirtualFile): Boolean {
        return StandardFileSystems.local().findFileByPath(fileOrDir.path) != null
    }

    override fun isInLibrarySource(fileOrDir: VirtualFile): Boolean {
        TODO("Not yet implemented")
    }

    override fun isIgnored(file: VirtualFile): Boolean {
        TODO("Not yet implemented")
    }

    override fun isExcluded(file: VirtualFile): Boolean {
        TODO("Not yet implemented")
    }

    override fun isUnderIgnored(file: VirtualFile): Boolean {
        TODO("Not yet implemented")
    }

    override fun getContainingSourceRootType(file: VirtualFile): JpsModuleSourceRootType<*>? {
        TODO("Not yet implemented")
    }

    override fun isInGeneratedSources(file: VirtualFile): Boolean {
        TODO("Not yet implemented")
    }

    //override fun getUnloadedModuleNameForFile(fileOrDir: VirtualFile): String? {
    //    TODO("Not yet implemented")
    //}
}
