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

package com.android.tools.profgen.cli

import com.android.tools.profgen.ArchiveClassFileResourceProvider
import com.android.tools.profgen.CLASS_EXTENSION
import com.android.tools.profgen.ClassFileResource
import com.android.tools.profgen.JAR_EXTENSION
import kotlinx.cli.ArgType
import kotlinx.cli.ExperimentalCli
import kotlinx.cli.Subcommand
import kotlinx.cli.required
import kotlinx.cli.vararg
import kotlin.io.path.Path

@ExperimentalCli
class ExpandWildcardsCommand: Subcommand("expandWildcards", "Dump a binary profile to a HRF") {
    val hrpPath by option(ArgType.String, "profile", "p", "File path to the human readable profile")
            .required()
    val outPath by option(
                ArgType.String, "output", "o",
                "File path for the resulting human readable profile without wildcards"
            )
            .required()
    val programPaths by argument(ArgType.String, "program", "File paths to program sources")
            .vararg()
    override fun execute() {
        val hrpFile = Path(hrpPath).toFile()
        require(hrpFile.exists()) { "File not found: $hrpPath" }

        val outFile = Path(outPath).toFile()
        require(outFile.parentFile.exists()) { "Directory does not exist: ${outFile.parent}" }

        require(programPaths.isNotEmpty()) { "Must pass at least one program source" }
        val programFiles = programPaths.map { Path(it).toFile() }
        for (programFile in programFiles) {
            require(programFile.exists()) { "File not found: $programFile" }
        }

        val hrp = readHumanReadableProfileOrExit(hrpFile)
        val archiveClassFileResourceProviders = mutableListOf<ArchiveClassFileResourceProvider>()
        val classFileResources = mutableListOf<ClassFileResource>()
        for (programFile in programFiles) {
            if (programFile.toString().endsWith(CLASS_EXTENSION)) {
                classFileResources += ClassFileResource(programFile.toPath())
            } else if (programFile.toString().endsWith(JAR_EXTENSION)) {
                val archiveClassFileResourceProvider =
                        ArchiveClassFileResourceProvider(programFile.toPath())
                archiveClassFileResourceProviders += archiveClassFileResourceProvider
                classFileResources += archiveClassFileResourceProvider.getClassFileResources()
            } else {
                throw IllegalArgumentException("Unexpected program file: $programFile")
            }
        }
        val result = hrp.expandWildcards(classFileResources)
        outFile.printWriter().use {
            result.printExact(it)
        }
        for (archiveClassFileResourceProvider in archiveClassFileResourceProviders) {
            try {
                archiveClassFileResourceProvider.close()
            } catch (throwable: Throwable) {
            }
        }
    }
}
