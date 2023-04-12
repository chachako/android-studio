package com.buildsrc.plugin

import com.android.build.api.variant.impl.KotlinMultiplatformAndroidTarget
import com.google.gson.GsonBuilder
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonPrimitive
import com.google.gson.JsonSerializationContext
import com.google.gson.JsonSerializer
import org.gradle.api.DefaultTask
import org.gradle.api.tasks.TaskAction
import org.jetbrains.kotlin.gradle.ExternalKotlinTargetApi
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.plugin.mpp.external.extras
import org.jetbrains.kotlin.tooling.core.Extras
import java.lang.reflect.Type

abstract class DumpAndroidTargetTask: DefaultTask() {

    @OptIn(ExternalKotlinTargetApi::class)
    @TaskAction
    fun dump() {
        val kotlinExtension = project.extensions.getByType(KotlinMultiplatformExtension::class.java)

        val out = project.buildDir.resolve("ide/targets/androidTarget.json")
        out.parentFile.mkdirs()

        val gson = GsonBuilder().setLenient().setPrettyPrinting()
            .registerTypeHierarchyAdapter(Extras::class.java, ExtrasAdapter)
            .create()

        kotlinExtension.targets.withType(KotlinMultiplatformAndroidTarget::class.java) { target ->
            val json = gson.toJson(
                TargetData(
                    targetName = target.targetName,
                    compilations = target.compilations.map { compilation ->
                        CompilationData(
                            compilationName = compilation.compilationName,
                            defaultSourceSet = compilation.defaultSourceSet.let { sourceSet ->
                                SourceSetData(
                                    sourceSetName = sourceSet.name,
                                    extras = sourceSet.extras
                                )
                            },
                            allSourceSets = compilation.allKotlinSourceSets.map { sourceSet ->
                                BasicSourceSet(
                                    sourceSetName = sourceSet.name,
                                )
                            },
                            extras = compilation.extras
                        )
                    },
                    extras = target.extras
                )
            )

            out.writeText(json)
        }
    }

    private object ExtrasAdapter : JsonSerializer<Extras> {

        override fun serialize(src: Extras, typeOfSrc: Type, context: JsonSerializationContext): JsonElement {
            return JsonObject().apply {
                src.entries.forEach { entry ->
                    val valueElement = runCatching { context.serialize(entry.value) }.getOrElse {
                        JsonPrimitive(entry.value.toString())
                    }

                    // the kotlin plugin uses a hashset which will not have a deterministic order,
                    // skip since this is reported anyways.
                    if (!entry.key.stableString.contains(
                            "org.jetbrains.kotlin.gradle.plugin.mpp.compilationImpl.KotlinCompilationSourceSetInclusion"
                    )) {
                        add(entry.key.stableString, valueElement)
                    }
                }
            }
        }
    }

    data class TargetData(
        val targetName: String,
        val compilations: List<CompilationData>,
        val extras: Extras
    )

    data class CompilationData(
        val compilationName: String,
        val defaultSourceSet: SourceSetData,
        val allSourceSets: List<BasicSourceSet>,
        val extras: Extras
    )

    data class BasicSourceSet(
        val sourceSetName: String,
    )

    data class SourceSetData(
        val sourceSetName: String,
        val extras: Extras
    )
}
