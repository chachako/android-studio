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
import com.android.tools.lint.model.LintModelExternalLibrary
import com.android.tools.lint.model.LintModelModule
import com.android.tools.rendering.classloading.ModuleClassLoaderManager
import com.android.tools.res.ResourceNamespacing
import com.android.tools.res.ids.ResourceIdManager
import com.android.tools.res.ids.ResourceIdManagerBase
import com.android.tools.res.ids.ResourceIdManagerModelModule
import com.android.tools.res.ids.buildResourceId
import com.intellij.openapi.vfs.StandardFileSystems
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.util.lang.UrlClassLoader
import com.jetbrains.rd.util.reflection.scanForClasses
import gnu.trove.TIntObjectHashMap
import gnu.trove.TObjectIntHashMap
import org.jetbrains.kotlin.fir.resolve.dfa.isNotEmpty
import org.jetbrains.kotlin.fir.resolve.dfa.stackOf
import java.lang.reflect.Field
import java.lang.reflect.Modifier
import java.util.Arrays
import java.util.EnumMap
import java.util.function.Consumer

private const val FIRST_PACKAGE_ID: Byte = 0x02
class ScreenshotResourceIdManager(private val composeProject: ComposeProject, private val composeModule: ComposeModule, private val rootLintModule: LintModelModule) :
    ResourceIdManagerBase(object: ResourceIdManagerModelModule {
        override val isAppOrFeature: Boolean
            get() = true
        override val namespacing: ResourceNamespacing
            get() = ResourceNamespacing.DISABLED

    }) {

    init {
        resetCompiledIds { parser ->

            val stack = stackOf<VirtualFile>()
            val classes = mutableListOf<String>()
            val loader = UrlClassLoader.build().files(
                rootLintModule.defaultVariant()!!.mainArtifact.classOutputs.filter { it.extension == "jar" }
                    .map { it.toPath() }
            ).get()
            rootLintModule.defaultVariant()!!.mainArtifact.classOutputs.filter { it.extension == "jar" }.map { val outputRoot = StandardFileSystems.local().findFileByPath(it.absolutePath)
                VirtualFileManager.getInstance().getFileSystem(
                    StandardFileSystems.JAR_PROTOCOL
                )
                    .findFileByPath(outputRoot!!.path + "!/")!!
            }.forEach { it.children.forEach { stack.push(it) } }
                    while(stack.isNotEmpty) {
                        val child = stack.pop()
                        if (child.isDirectory) {
                            child.children.forEach { stack.push(it) }
                        } else {
                            val name = child.path.substring(child.path.indexOf("!/")+2).replace("/",".").replace(".class","")
                            classes.add(name)
                        }
                    }
            classes.forEach {
                parser.parse(loader.loadClass(it))
            }
                //.forEach {
                //    parser.parse(it)
                //}
        }
    }

    override val finalIdsUsed: Boolean
        get() = false
}
