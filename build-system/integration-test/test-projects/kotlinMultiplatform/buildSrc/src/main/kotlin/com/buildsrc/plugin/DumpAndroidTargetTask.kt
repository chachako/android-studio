package com.buildsrc.plugin

import com.android.build.api.variant.KotlinMultiplatformAndroidTarget
import com.android.kotlin.multiplatform.models.AndroidCompilation
import com.android.kotlin.multiplatform.models.AndroidSourceSet
import com.android.kotlin.multiplatform.models.AndroidTarget
import com.google.gson.GsonBuilder
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.google.gson.JsonPrimitive
import com.google.gson.JsonSerializationContext
import com.google.gson.JsonSerializer
import com.google.protobuf.util.JsonFormat
import org.gradle.api.DefaultTask
import org.gradle.api.tasks.TaskAction
import org.jetbrains.kotlin.gradle.ExternalKotlinTargetApi
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.plugin.ide.IdeMultiplatformImport
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

        kotlinExtension.sourceSets.forEach { sourceSet ->
            IdeMultiplatformImport.instance(project).resolveDependencies(sourceSet)
        }

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

        private val jsonFormat =
            JsonFormat.printer()
                .usingTypeRegistry(
                    JsonFormat.TypeRegistry.newBuilder()
                        .add(AndroidTarget.getDescriptor())
                        .add(AndroidCompilation.getDescriptor())
                        .add(AndroidSourceSet.getDescriptor())
                        .build()
                )
                .includingDefaultValueFields()
                .sortingMapKeys()

        override fun serialize(src: Extras, typeOfSrc: Type, context: JsonSerializationContext): JsonElement {
            return JsonObject().apply {
                src.entries.forEach { entry ->

                    val value = (entry.value as? Function0<*>)?.let { it() } ?: entry.value

                    val valueElement = when (value) {
                        is AndroidTarget -> JsonParser.parseString(
                            jsonFormat.print(value)
                        )
                        is AndroidCompilation -> JsonParser.parseString(
                            jsonFormat.print(value)
                        )
                        is AndroidSourceSet -> JsonParser.parseString(
                            jsonFormat.print(value)
                        )
                        else -> runCatching { context.serialize(value) }.getOrElse {
                            JsonPrimitive(value.toString())
                        }
                    }

                    // the kotlin plugin uses a hashset which will not have a deterministic order,
                    // skip since this is reported anyways.
                    if (!entry.key.stableString.contains(
                            "org.jetbrains.kotlin.gradle.plugin.mpp.compilationImpl.KotlinCompilationSourceSetInclusion"
                        ) &&
                        // An object reference is dumped which changes on different runs
                        !entry.key.stableString.startsWith(
                            "org.jetbrains.kotlin.gradle.utils.Future"
                        ) &&
                        !entry.key.stableString.contains(
                            "org.jetbrains.kotlin.gradle.plugin.mpp.GranularMetadataTransformation"
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
