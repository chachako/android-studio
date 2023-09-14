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

package com.android.build.gradle.internal.fixtures

import org.gradle.api.file.Directory
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.FileCollection
import org.gradle.api.file.ProjectLayout
import org.gradle.api.file.RegularFile
import org.gradle.api.provider.Provider
import java.io.File

class FakeProjectLayout : ProjectLayout {

    override fun getProjectDirectory(): Directory {
        TODO("Not yet implemented")
    }

    override fun getBuildDirectory(): DirectoryProperty {
        TODO("Not yet implemented")
    }

    override fun file(file: Provider<File>): Provider<RegularFile> {
        return FakeGradleProvider(FakeGradleRegularFile(file.get()))
    }

    override fun dir(file: Provider<File>): Provider<Directory> {
        TODO("Not yet implemented")
    }

    override fun files(vararg paths: Any?): FileCollection {
        TODO("Not yet implemented")
    }
}
