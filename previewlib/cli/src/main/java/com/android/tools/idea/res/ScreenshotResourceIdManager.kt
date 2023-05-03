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
package com.android.tools.idea.res

import com.android.SdkConstants
import com.android.ide.common.rendering.api.ResourceNamespace
import com.android.ide.common.rendering.api.ResourceReference
import com.android.resources.ResourceType
import com.android.screenshot.cli.ComposeModule
import com.android.screenshot.cli.ComposeProject
import com.android.screenshot.cli.ScreenshotModuleClassLoaderManager
import com.android.tools.idea.layoutlib.LayoutLibraryLoader
import com.android.tools.idea.projectsystem.DependencyScopeType
import com.android.tools.lint.model.LintModelExternalLibrary
import com.android.tools.rendering.classloading.ModuleClassLoaderManager
import com.android.tools.res.ids.ResourceIdManager
import gnu.trove.TIntObjectHashMap
import java.lang.reflect.Field
import java.lang.reflect.Modifier
import java.util.EnumMap
import java.util.function.Consumer

class ScreenshotResourceIdManager(private val composeProject: ComposeProject, private val composeModule: ComposeModule) : ResourceIdManager {

    private val toIdMap = EnumMap<ResourceType, MutableMap<String, Int>>(ResourceType::class.java)
    private val fromIdMap = TIntObjectHashMap<Pair<ResourceType, String>>()
    override val finalIdsUsed: Boolean
        get() = false

    override fun getCompiledId(resource: ResourceReference): Int? {
        TODO("Not yet implemented")
    }

    override fun findById(id: Int): ResourceReference? {
        val deps = composeProject.defaultProjectSystem.getModuleSystem(composeModule.module).getAndroidLibraryDependencies(DependencyScopeType.MAIN)
            .filter { it.hasResources }.map {
                it.packageName.let { "$it.${SdkConstants.R_CLASS}" }
            }.mapNotNull { (ModuleClassLoaderManager.get() as ScreenshotModuleClassLoaderManager).lastClassLoader?.loadClass(it) }
        val rClass = LayoutLibraryLoader.LayoutLibraryProvider.EP_NAME.computeSafeIfAny { provider -> provider.frameworkRClass }
        if (rClass != null) {
            loadIdsFromResourceClass(rClass)
        }
        composeProject.lintProject.buildVariant!!
            .androidTestArtifact!!.dependencies.packageDependencies
            .getAllLibraries()
            .filterIsInstance<LintModelExternalLibrary>()
            .flatMap{it.jarFiles}
            .filter { it.name.equals("R.jar") }
            .map { it.absolutePath }.map { (ModuleClassLoaderManager.get() as ScreenshotModuleClassLoaderManager).lastClassLoader?.loadClass(it) }.forEach { loadIdsFromResourceClass(it!!) }
        deps.forEach { loadIdsFromResourceClass(it) }

        return fromIdMap[id]?.let { (type, name) -> ResourceReference(ResourceNamespace.RES_AUTO, type, name) }
    }

    override fun resetCompiledIds(rClassProvider: Consumer<ResourceIdManager.RClassParser>) {
        //TODO("Not yet implemented")
    }

    override fun resetDynamicIds() {
        TODO("Not yet implemented")
    }

    override fun getGeneration(): Long {
        TODO("Not yet implemented")
    }

    override fun getOrGenerateId(resourceReference: ResourceReference): Int {
        TODO("Not yet implemented")
    }

    private fun loadIdsFromResourceClass(
        klass: Class<*>,
        lookForAttrsInStyleables: Boolean = false) {
        assert(klass.simpleName == "R") { "Numeric ids can only be loaded from top-level R classes." }

        // Comparator for fields, which makes them appear in the same order as in the R class source code. This means that in R.styleable,
        // indices come after corresponding array and before other arrays, e.g. "ActionBar_logo" comes after "ActionBar" but before
        // "ActionBar_LayoutParams". This allows the invariant that int fields are indices into the last seen array field.
        val fieldOrdering: Comparator<Field> = Comparator { f1, f2 ->
            val name1 = f1.name
            val name2 = f2.name

            for(i in 0 until minOf(name1.length, name2.length)) {
                val c1 = name1[i]
                val c2 = name2[i]

                if (c1 != c2) {
                    return@Comparator when {
                        c1 == '_' -> -1
                        c2 == '_' -> 1
                        c1.isLowerCase() && c2.isUpperCase() -> -1
                        c1.isUpperCase() && c2.isLowerCase() -> 1
                        else -> c1 - c2
                    }
                }
            }

            name1.length - name2.length
        }

        for (innerClass in klass.declaredClasses) {
            val type = ResourceType.fromClassName(innerClass.simpleName) ?: continue
            when {
                type != ResourceType.STYLEABLE -> {
                    val toIdMap = toIdMap.getOrPut(type) { mutableMapOf() }
                    val fromIdMap = fromIdMap

                    for (field in innerClass.declaredFields) {
                        if (field.type != Int::class.java || !Modifier.isStatic(field.modifiers)) continue
                        val id = field.getInt(null)
                        val name = field.name
                        toIdMap.put(name, id)
                        fromIdMap.put(id, Pair(type, name))
                    }
                }
                type == ResourceType.STYLEABLE && lookForAttrsInStyleables -> {
                    val toIdMap = toIdMap.getOrPut(
                        ResourceType.ATTR) { mutableMapOf() }
                    val fromIdMap = fromIdMap

                    // We process fields by name, so that arrays come before indices into them. currentArray is initialized to a dummy value.
                    var currentArray = IntArray(0)
                    var currentStyleable = ""

                    val sortedFields = innerClass.fields.sortedArrayWith(fieldOrdering)
                    for (field in sortedFields) {
                        if (field.type.isArray) {
                            currentArray = field.get(null) as IntArray
                            currentStyleable = field.name
                        }
                        else {
                            val attrName: String = field.name.substring(currentStyleable.length + 1)
                            val attrId = currentArray[field.getInt(null)]
                            toIdMap.put(attrName, attrId)
                            fromIdMap.put(attrId, Pair(ResourceType.ATTR, attrName))
                        }
                    }
                }
                else -> {
                    // No interesting information in the styleable class, if we're not trying to infer attr ids from it.
                }
            }
        }
    }

}
