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
package com.android.declarative.internal.model

/**
 * Model for all information parsed from the `pluginManagement` block in build files.
 */
class PluginManagementInfo(
    /**
     * [List] of [RepositoryInfo] to download plugins from.
     */
    val repositories: List<RepositoryInfo>,

    /**
     * [List] of included builds, each included build is the path to the composite build
     */
    val includedBuilds: List<String>
)
