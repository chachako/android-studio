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
 * Types of repository definition.
 */
enum class RepositoryType {

    /**
     * A pre-defined repository is known to Gradle and added by invoking a method on the
     * Gradle's RepositoryHandler.
     */
    PRE_DEFINED
}

/**
 * Common information to all types of repositories definitions.
 */
interface RepositoryInfo {
    val type: RepositoryType
}

/**
 * Pre-defined repository definition, known to Gradle, like 'google' or 'mavenCentral'
 */
class PreDefinedRepositoryInfo(
    val name: String
): RepositoryInfo {

    override val type: RepositoryType = RepositoryType.PRE_DEFINED
}
