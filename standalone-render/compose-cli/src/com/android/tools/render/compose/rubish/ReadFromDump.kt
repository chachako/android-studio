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

package com.android.tools.render.compose.rubish

import java.io.File

private const val PACKAGE_NAME = "--package--"
private const val MAIN_CLASSES_DELIMITER = "--main_classes--"
private const val MAIN_RESOURCES_DELIMITER = "--main_resources--"
private const val CLASSES_DELIMITER = "--classes--"
private const val RESOURCES_DELIMITER = "--resources--"
private const val R_CLASSES_DELIMITER = "--rclasses--"

enum class ModuleType {
    LIBRARY,
    APP,
}

data class RenderDependencies(
    val moduleType: ModuleType,
    val packageName: String,
    val mainClasses: String,
    val classesJars: List<String>,
    val resourcesApk: String
) {
    companion object {
        fun readFromDump(dumpFilePath: String): RenderDependencies {
            val lines = File(dumpFilePath).useLines { it.toList() }
            val moduleType = when (lines[0]) {
                "library" -> ModuleType.LIBRARY
                "app" -> ModuleType.APP
                else -> throw IllegalStateException("Unknown module type ${lines[0]}")
            }

            assert(lines[1] == PACKAGE_NAME)
            assert(lines[3] == MAIN_CLASSES_DELIMITER)
            assert(lines[5] == CLASSES_DELIMITER)
            val resourcesFrom = lines.indexOf(RESOURCES_DELIMITER)
            return RenderDependencies(
                moduleType,
                lines[2],
                lines[4],
                lines.subList(6, resourcesFrom),
                lines[resourcesFrom+1],
            )
        }
    }
}
