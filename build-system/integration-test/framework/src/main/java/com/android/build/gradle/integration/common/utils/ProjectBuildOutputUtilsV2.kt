/*
 * Copyright (C) 2022 The Android Open Source Project
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

@file:JvmName("ProjectBuildOutputUtilsV2")
package com.android.build.gradle.integration.common.utils

import com.android.build.api.variant.impl.BuiltArtifactImpl
import com.android.build.api.variant.impl.BuiltArtifactsImpl
import com.android.build.api.variant.impl.BuiltArtifactsLoaderImpl
import com.android.builder.model.v2.ide.Variant
import java.io.File
import java.lang.RuntimeException

fun Variant.getApkFolderOutput() =
        getBuiltArtifacts(mainArtifact.assembleTaskOutputListingFile!!)
                .elements
                .map(BuiltArtifactImpl::outputFile)

fun getBuiltArtifacts(assembleTaskOutputListingFile: File): BuiltArtifactsImpl =
    (BuiltArtifactsLoaderImpl.loadFromFile(assembleTaskOutputListingFile)
        ?: throw RuntimeException("Cannot load built artifacts from $assembleTaskOutputListingFile"))

fun Variant.getSingleOutputFile() = getApkFolderOutput().single()
