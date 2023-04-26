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

import com.android.sdklib.AndroidVersion
import com.android.tools.module.AndroidModuleInfo
import com.google.common.util.concurrent.ListenableFuture

class ScreenshotAndroidModuleInfo(private val composeProject: ComposeProject) : AndroidModuleInfo {

    override val moduleMinApi: Int
        get() = composeProject.lintProject.minSdk
    override val packageName: String?
        get() = composeProject.lintProject.`package`
    override val runtimeMinSdkVersion: ListenableFuture<AndroidVersion>
        get() = TODO("Not yet implemented")
    override val minSdkVersion: AndroidVersion
        get() = composeProject.lintProject.minSdkVersion
    override val targetSdkVersion: AndroidVersion
        get() = composeProject.lintProject.targetSdkVersion
    override val buildSdkVersion: AndroidVersion?
        get() = composeProject.lintProject.targetSdkVersion
}
