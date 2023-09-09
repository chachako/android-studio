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
package com.android.declarative.internal.parsers

import com.android.declarative.internal.IssueLogger
import com.android.declarative.internal.model.PreDefinedRepositoryInfo
import com.android.declarative.internal.model.RepositoryInfo
import com.android.declarative.internal.toml.InvalidTomlException
import com.android.declarative.internal.toml.mapTable
import org.tomlj.TomlArray

/**
 * Parse toml array containing repository's definition.
 */
class RepositoriesParser(
    private val issueLogger: IssueLogger,
) {

    /**
     * Parse a [TomlArray] of repository's definition and return a [List] of
     * [RepositoryInfo] models.
     *
     * @param repositoriesDeclaration the toml 'repositories' array
     * @return [List] of [RepositoryInfo]
     */
    fun parseToml(repositoriesDeclaration: TomlArray): List<RepositoryInfo> =
        repositoriesDeclaration.mapTable { repository ->
            // so far we only support pre-defined repositories
            if (repository.keySet().isNotEmpty()) {
                if (repository.contains("name")) {
                    return@mapTable repository.getString("name")?.let {
                        PreDefinedRepositoryInfo(it)
                    }
                } else {
                    issueLogger.raiseError(
                        InvalidTomlException(
                            repository.inputPositionOf(repository.keySet().first()),
                            "Unsupported repository declaration : ${repository.dottedKeySet()}",
                        )
                    )
                }
            }
            null
        }
}
