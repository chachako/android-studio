/*
 * Copyright (C) 2021 The Android Open Source Project
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

package com.android.build.gradle.internal.fixtures

import org.gradle.api.artifacts.component.BuildIdentifier

data class FakeBuildIdentifier(
    private val _buildPath: String = "defaultBuildPath"
): BuildIdentifier {
    @Deprecated("This property is deprecated starting with Gradle 8.2", ReplaceWith("\"\""))
    override fun getName(): String = ""
    @Deprecated("This property is deprecated starting with Gradle 8.2", ReplaceWith("false"))
    override fun isCurrentBuild(): Boolean = false
    override fun getBuildPath(): String = _buildPath
}
