/*
 * Copyright (C) 2018 The Android Open Source Project
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

@file:JvmName("ProjectBuildOutputUtils")
package com.android.build.gradle.integration.common.utils

import com.android.build.api.variant.impl.BuiltArtifactImpl
import com.android.build.api.variant.impl.BuiltArtifactsImpl
import com.android.build.api.variant.impl.BuiltArtifactsLoaderImpl
import com.android.build.gradle.integration.common.truth.TruthHelper
import com.android.builder.core.BuilderConstants
import com.android.builder.model.AndroidProject
import com.android.builder.model.VariantBuildInformation
import org.junit.Assert
import java.io.File
import java.lang.RuntimeException

/**
 * Returns the APK file for a single-output variant.
 *
 * @param variantName the name of the variant to return
 * @return the output file, always, or assert before.
 */
fun AndroidProject.findOutputFileByVariantName(variantName: String): File {

    val variantOutput = getVariantBuildInformationByName(variantName)
    Assert.assertNotNull("variant '$variantName' null-check", variantOutput)

    val variantOutputFiles = variantOutput.getApkFolderOutput()
    Assert.assertNotNull("variantName '$variantName' outputs null-check", variantOutputFiles)
    // we only support single output artifact in this helper method.
    Assert.assertEquals(
            "variantName '$variantName' outputs size check",
            1,
            variantOutputFiles.size.toLong())

    val output = variantOutput.getSingleOutputFile()
    Assert.assertNotNull(
            "variantName '$variantName' single output null-check",
            output)

    return File(output)
}
fun getBuiltArtifacts(assembleTaskOutputListingFile: File): BuiltArtifactsImpl =
        (BuiltArtifactsLoaderImpl.loadFromFile(assembleTaskOutputListingFile)
                ?: throw RuntimeException("Cannot load built artifacts from $assembleTaskOutputListingFile"))

fun VariantBuildInformation.getApkFolderOutput() =
    getBuiltArtifacts(File(assembleTaskOutputListingFile!!))
        .elements
        .map(BuiltArtifactImpl::outputFile)

fun VariantBuildInformation.getSingleOutputFile() =
    getApkFolderOutput().single()
