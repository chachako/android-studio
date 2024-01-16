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

package com.android.tools.render.environment

import com.android.ide.common.rendering.api.RenderResources
import com.android.ide.common.resources.ResourceResolver
import com.android.ide.common.util.PathString
import com.android.tools.analytics.crash.CrashReport
import com.android.tools.analytics.crash.CrashReporter
import com.android.tools.idea.layoutlib.LayoutLibrary
import com.android.tools.layoutlib.LayoutlibContext
import com.android.tools.rendering.IRenderLogger
import com.android.tools.rendering.RenderProblem
import com.android.tools.rendering.api.EnvironmentContext
import com.android.tools.rendering.api.IncludeReference
import com.android.tools.rendering.api.NavGraphResolver
import com.android.tools.rendering.classloading.ModuleClassLoader
import com.android.tools.rendering.classloading.ModuleClassLoaderManager
import com.android.tools.rendering.parsers.RenderXmlFile
import com.android.tools.rendering.parsers.RenderXmlFileSnapshot
import com.android.tools.rendering.security.RenderSecurityManager
import com.android.tools.sdk.AndroidPlatform
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile

/** [EnvironmentContext] for the CLI case with no UI. */
internal class StandaloneEnvironmentContext(
    private val project: Project,
    private val moduleClassLoaderManager: ModuleClassLoaderManager<out ModuleClassLoader>
) : EnvironmentContext {
    private val crashReporter = ConsoleCrashReporter()
    override val layoutlibContext: LayoutlibContext = object : LayoutlibContext {
        override fun hasLayoutlibCrash(): Boolean = false
        override fun register(layoutlib: LayoutLibrary) { }
    }

    override val runnableFixFactory: RenderProblem.RunnableFixFactory =
        RenderProblem.RunnableFixFactory { _, _ -> Runnable { } }

    override fun reportMissingSdkDependency(logger: IRenderLogger) { }

    override fun createIncludeReference(
        xmlFile: RenderXmlFile,
        resolver: RenderResources
    ): IncludeReference = IncludeReference.NONE

    /** Not used in the Compose standalone rendering. TODO(): implement for general rendering */
    override fun getFileText(fileName: String): String? = null

    override fun getXmlFile(filePath: PathString): RenderXmlFile =
        RenderXmlFileSnapshot(project, filePath)

    override fun getNavGraphResolver(resourceResolver: ResourceResolver): NavGraphResolver =
        NavGraphResolver { null }

    override fun createRenderSecurityManager(
        projectPath: String?,
        platform: AndroidPlatform?
    ): RenderSecurityManager {
        val sdkLocationPath = platform?.sdkData?.location?.toString()!!
        return object : RenderSecurityManager(sdkLocationPath, projectPath, false, emptyArray(), { true }) { }
    }

    // This is only to be called from RenderErrorContributor and never in the standalone rendering
    override fun getOriginalFile(psiFile: PsiFile): PsiFile {
        throw UnsupportedOperationException("Should not be called in standalone rendering")
    }

    override fun getModuleClassLoaderManager(): ModuleClassLoaderManager<out ModuleClassLoader> =
        moduleClassLoaderManager

    override fun getCrashReporter(): CrashReporter = crashReporter

    override fun createCrashReport(t: Throwable): CrashReport = ThrowableCrashReport(t)

    override fun isInTest(): Boolean = false
}
