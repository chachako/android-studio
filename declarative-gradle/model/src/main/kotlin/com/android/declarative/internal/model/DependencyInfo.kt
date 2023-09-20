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
package com.android.declarative.internal.model

/**
 * Types of dependency supported. This corresponds to the various dependency declaration
 * that can be found in a Gradle's build file.
 *
 * @see https://docs.gradle.org/current/userguide/declaring_dependencies.html#sec:dependency-types
 */
enum class DependencyType {
    PROJECT,
    FILES,
    LIBRARY,
    NOTATION,
    ALIAS,
    EXTENSION_FUNCTION,
}

/**
 * Supertype for the description of a dependency with common information amongst
 * all dependency types.
 */
sealed class DependencyInfo(
    val configuration: String,
) {

    abstract val type: DependencyType

    /**
     * Dependency based on a Gradle notation. A gradle notation can be a full maven
     * identifier like "org.junit:junit:4.7.0" or a project reference like ":lib1",
     * or a version catalog reference like "libs.junit"/
     */
    class Notation(
        override val type: DependencyType,
        configuration: String,
        val notation: String,
    ) : DependencyInfo(configuration) {

        override fun toString(): String =
            "$type dependency on $notation"
    }

    class Alias(
        configuration: String,
        val alias: String,
    ) : DependencyInfo(configuration) {

        override val type: DependencyType = DependencyType.ALIAS
    }

    /**
     * A maven dependency with the maven group, name and version specified individually.
     */
    class Maven(
        configuration: String,
        val group: String,
        val name: String,
        val version: String,
    ) : DependencyInfo(configuration) {

        override val type: DependencyType = DependencyType.LIBRARY
    }

    /**
     * A files dependency with files added from the local file system.
     */
    class Files(
        configuration: String,
        val files: List<String>,
    ) : DependencyInfo(configuration) {

        override val type: DependencyType = DependencyType.FILES
    }

    /**
     * An extension function dependency is a dependency defined with an extension function on the
     * Dependencies declaration like kotlin('jvm').
     */
    class ExtensionFunction(
        configuration: String,
        val extension: String,
        val parameters: Map<String, String>,
    ) : DependencyInfo(configuration) {

        override val type: DependencyType = DependencyType.EXTENSION_FUNCTION
    }
}
