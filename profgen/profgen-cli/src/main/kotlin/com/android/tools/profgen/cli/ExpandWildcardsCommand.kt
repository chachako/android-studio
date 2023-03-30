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

import com.android.tools.profgen.expandWildcards
import kotlinx.cli.ArgType
import kotlinx.cli.ExperimentalCli
import kotlinx.cli.Subcommand
import kotlinx.cli.required
import kotlinx.cli.vararg

@ExperimentalCli
class ExpandWildcardsCommand: Subcommand("expandWildcards", "Dump a binary profile to a HRF") {
    private val hrpPath by option(ArgType.String, "profile", "p", "File path to the human readable profile")
        .required()
    private val outPath by option(
        ArgType.String, "output", "o",
        "File path for the resulting human readable profile without wildcards"
    )
        .required()
    private val programPaths by argument(
        ArgType.String,
        "program",
        "File paths to program sources (.class or .jar). "
                + "Class files must be on the form <src dir>:<path to file>.class, e.g., "
                + "src:pkg/Main.class.")
        .vararg()
    override fun execute() {
        expandWildcards(hrpPath, outPath, programPaths, StdErrorDiagnostics)
    }
}
