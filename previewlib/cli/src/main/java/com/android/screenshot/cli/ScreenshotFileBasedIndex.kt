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

import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ContentIterator
import com.intellij.openapi.util.Condition
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.util.Processor
import com.intellij.util.indexing.FileBasedIndex
import com.intellij.util.indexing.ID
import com.intellij.util.indexing.IdFilter

class ScreenshotFileBasedIndex: FileBasedIndex() {

    override fun iterateIndexableFiles(
        processor: ContentIterator,
        project: Project,
        indicator: ProgressIndicator?
    ) {
        TODO("Not yet implemented")
    }

    override fun getFileBeingCurrentlyIndexed(): VirtualFile? {
        TODO("Not yet implemented")
    }

    override fun getFileWritingCurrentlyIndexes(): IndexWritingFile? {
        TODO("Not yet implemented")
    }

    override fun findFileById(project: Project?, id: Int): VirtualFile {
        TODO("Not yet implemented")
    }

    override fun requestRebuild(indexId: ID<*, *>, throwable: Throwable) {
        TODO("Not yet implemented")
    }

    override fun <K : Any, V : Any> processValues(
        indexId: ID<K, V>,
        dataKey: K,
        inFile: VirtualFile?,
        processor: ValueProcessor<in V>,
        filter: GlobalSearchScope
    ): Boolean {
        TODO("Not yet implemented")
    }

    override fun <K : Any, V : Any> getContainingFilesIterator(
        indexId: ID<K, V>,
        dataKey: K,
        filter: GlobalSearchScope
    ): MutableIterator<VirtualFile> {
        TODO("Not yet implemented")
    }
    override fun <K : Any, V : Any> getContainingFiles(
        indexId: ID<K, V>,
        dataKey: K,
        filter: GlobalSearchScope
    ): MutableCollection<VirtualFile> {
        TODO("Not yet implemented")
    }

    override fun <K : Any, V : Any> getValues(
        indexId: ID<K, V>,
        dataKey: K,
        filter: GlobalSearchScope
    ): MutableList<V> {
        TODO("Not yet implemented")
    }

    override fun <K : Any?, V : Any?> getIndexModificationStamp(
        indexId: ID<K, V>,
        project: Project
    ): Long {
        TODO("Not yet implemented")
    }

    override fun <K : Any?, V : Any?> processFilesContainingAllKeys(
        indexId: ID<K, V>,
        dataKeys: MutableCollection<out K>,
        filter: GlobalSearchScope,
        valueChecker: Condition<in V>?,
        processor: Processor<in VirtualFile>
    ): Boolean {
        TODO("Not yet implemented")
    }

    override fun <K : Any?, V : Any?> processFilesContainingAnyKey(
        indexId: ID<K, V>,
        dataKeys: MutableCollection<out K>,
        filter: GlobalSearchScope,
        idFilter: IdFilter?,
        valueChecker: Condition<in V>?,
        processor: Processor<in VirtualFile>
    ): Boolean {
        TODO("Not yet implemented")
    }

    override fun <K : Any?> getAllKeys(indexId: ID<K, *>, project: Project): MutableCollection<K> {
        TODO("Not yet implemented")
    }

    override fun <K : Any?> ensureUpToDate(
        indexId: ID<K, *>,
        project: Project?,
        filter: GlobalSearchScope?
    ) {
        TODO("Not yet implemented")
    }

    override fun <K : Any?> scheduleRebuild(indexId: ID<K, *>, e: Throwable) {
        TODO("Not yet implemented")
    }

    override fun requestReindex(file: VirtualFile) {
        TODO("Not yet implemented")
    }

    override fun <K : Any?, V : Any?> getFilesWithKey(
        indexId: ID<K, V>,
        dataKeys: MutableSet<out K>,
        processor: Processor<in VirtualFile>,
        filter: GlobalSearchScope
    ): Boolean {
        TODO("Not yet implemented")
    }

    override fun <K : Any?> processAllKeys(
        indexId: ID<K, *>,
        processor: Processor<in K>,
        project: Project?
    ): Boolean {
        TODO("Not yet implemented")
    }

    override fun <K : Any?, V : Any?> getFileData(
        id: ID<K, V>,
        virtualFile: VirtualFile,
        project: Project
    ): MutableMap<K, V> {
        TODO("Not yet implemented")
    }

    override fun <V : Any?> getSingleEntryIndexData(
        id: ID<Int, V>,
        virtualFile: VirtualFile,
        project: Project
    ): V? {
        TODO("Not yet implemented")
    }
}
