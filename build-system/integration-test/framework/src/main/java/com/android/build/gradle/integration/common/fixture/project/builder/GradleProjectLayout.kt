/*
 * Copyright (C) 2024 The Android Open Source Project
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

package com.android.build.gradle.integration.common.fixture.project.builder

import java.nio.file.Path

/**
 * Allows manipulating files of a [GradleProjectDefinition]
 */
interface GradleProjectLayout {

    /**
     * Adds a file to the given location with the given content.
     */
    fun addFile(relativePath: String, content: String)

    /**
     * Change the content of a file
     */
    fun changeFile(relativePath: String, action: (String) -> String)

    /**
     * Removes the file at the given location
     */
    fun removeFile(relativePath: String)
}
