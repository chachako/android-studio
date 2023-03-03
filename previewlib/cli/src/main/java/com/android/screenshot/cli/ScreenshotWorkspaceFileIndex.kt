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

import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.ThreeState
import com.intellij.workspaceModel.core.fileIndex.WorkspaceFileIndex
import com.intellij.workspaceModel.core.fileIndex.WorkspaceFileSet
import com.intellij.workspaceModel.core.fileIndex.WorkspaceFileSetData
import com.intellij.workspaceModel.core.fileIndex.WorkspaceFileSetWithCustomData

class ScreenshotWorkspaceFileIndex : WorkspaceFileIndex {

    override fun findFileSet(
        file: VirtualFile,
        honorExclusion: Boolean,
        includeContentSets: Boolean,
        includeExternalSets: Boolean,
        includeExternalSourceSets: Boolean
    ): WorkspaceFileSet? {
        TODO("Not yet implemented")
    }

    override fun <D : WorkspaceFileSetData> findFileSetWithCustomData(
        file: VirtualFile,
        honorExclusion: Boolean,
        includeContentSets: Boolean,
        includeExternalSets: Boolean,
        includeExternalSourceSets: Boolean,
        customDataClass: Class<out D>
    ): WorkspaceFileSetWithCustomData<D>? {
        TODO("Not yet implemented")
    }

    override fun getContentFileSetRoot(file: VirtualFile, honorExclusion: Boolean): VirtualFile? {
        TODO("Not yet implemented")
    }

    override fun isInContent(file: VirtualFile): Boolean {
        TODO("Not yet implemented")
    }

    override fun isInWorkspace(file: VirtualFile): Boolean {
        TODO("Not yet implemented")
    }

    override fun isUrlInContent(url: String): ThreeState {
        TODO("Not yet implemented")
    }
}
