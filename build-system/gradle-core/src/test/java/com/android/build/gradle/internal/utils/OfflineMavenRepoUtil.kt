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

package com.android.build.gradle.internal.utils

import com.android.testutils.RepoLinker
import com.android.testutils.TestUtils
import com.android.utils.FileUtils
import java.io.File
import java.nio.file.Files
import java.nio.file.Path

/**
 * When executing bazel tests that require artifacts in the offline maven repo at runtime, this
 * function should be called from the static/init method to copy the repository for usage.
 */
internal fun importOfflineMavenRepo() {
    if (TestUtils.runningFromBazel()) {
        val originOfflineRepo: Path =
                TestUtils.getWorkspaceRoot().parent.resolve("maven/repository")
        if (!Files.exists(originOfflineRepo)) {
            throw IllegalArgumentException("$originOfflineRepo does not exist")
        }
        val destinationOfflineRepo: File = TestUtils.getPrebuiltOfflineMavenRepo().toFile()
        if (destinationOfflineRepo.walkTopDown().count() > 1) {
            // Offline maven repo is already created.
            return
        }
        FileUtils.copyDirectoryContentToDirectory(
                originOfflineRepo.toFile(), destinationOfflineRepo)
        val offlineRepoManifest =
                TestUtils.getWorkspaceRoot().resolve(
                        // generated from :runtime_test_dependencies target in BUILD
                        "tools/base/build-system/gradle-core/runtime_test_dependencies.manifest")
        val manifestContent = Files.readAllLines(offlineRepoManifest)
        RepoLinker().link(destinationOfflineRepo.toPath(), manifestContent)
    }
}
