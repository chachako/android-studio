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

package com.buildsrc.plugin

import com.google.gson.GsonBuilder
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.google.gson.JsonPrimitive
import com.google.gson.JsonSerializationContext
import com.google.gson.JsonSerializer
import com.google.protobuf.util.JsonFormat
import org.gradle.api.DefaultTask
import org.gradle.api.Project
import org.gradle.api.tasks.TaskAction
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.plugin.ide.IdeDependencyResolver
import org.jetbrains.kotlin.gradle.plugin.ide.IdeMultiplatformImport
import org.jetbrains.kotlin.gradle.ExternalKotlinTargetApi
import org.jetbrains.kotlin.tooling.core.Extras
import java.io.File
import java.lang.reflect.Type

@OptIn(ExternalKotlinTargetApi::class)
abstract class DumpSourceSetDependenciesTask: DefaultTask() {

    @TaskAction
    fun dump() {
        val kotlinExtension = project.extensions.getByType(KotlinMultiplatformExtension::class.java)

        val outDir = project.buildDir.resolve("ide/dependencies/json")
        outDir.mkdirs()

        val gson = GsonBuilder().setLenient().setPrettyPrinting()
            .registerTypeHierarchyAdapter(Extras::class.java, ExtrasAdapter)
            .registerTypeHierarchyAdapter(IdeDependencyResolver::class.java, IdeDependencyResolverAdapter)
            .registerTypeAdapter(File::class.java, FileAdapter(project))
            .create()

        kotlinExtension.sourceSets.forEach { sourceSet ->
            val ideImportService = IdeMultiplatformImport.instance(project)
            val dependencies = ideImportService.resolveDependencies(sourceSet)
            val jsonOutput = outDir.resolve("${sourceSet.name}.json")
            jsonOutput.writeText(gson.toJson(dependencies))
        }
    }

    private object ExtrasAdapter : JsonSerializer<Extras> {

        override fun serialize(src: Extras, typeOfSrc: Type, context: JsonSerializationContext): JsonElement {
            return JsonObject().apply {
                src.entries.forEach { entry ->

                    val valueElement = runCatching { context.serialize(entry.value) }.getOrElse {
                        JsonPrimitive(entry.value.toString())
                    }

                    add(entry.key.stableString, valueElement)
                }
            }
        }
    }

    private object IdeDependencyResolverAdapter : JsonSerializer<IdeDependencyResolver> {
        override fun serialize(src: IdeDependencyResolver, typeOfSrc: Type, context: JsonSerializationContext): JsonElement {
            return JsonPrimitive(src.javaClass.name)
        }
    }

    private class FileAdapter(private val project: Project) : JsonSerializer<File> {
        override fun serialize(src: File, typeOfSrc: Type?, context: JsonSerializationContext?): JsonElement {
            return JsonPrimitive(src.path)
        }
    }
}
