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
package com.android.screenshot.cli

import com.android.ide.common.rendering.api.RenderResources
import com.android.ide.common.resources.ResourceResolver
import com.android.ide.common.util.PathString
import com.android.tools.analytics.crash.CrashReport
import com.android.tools.analytics.crash.CrashReporter
import com.android.tools.idea.AndroidPsiUtils
import com.android.tools.rendering.api.IncludeReference
import com.android.tools.rendering.api.NavGraphResolver
import com.android.tools.idea.rendering.PsiIncludeReference
import com.android.tools.idea.rendering.ShowFixFactory
import com.android.tools.idea.rendering.parsers.PsiXmlFile
import com.android.tools.idea.util.toVirtualFile
import com.android.tools.layoutlib.LayoutlibContext
import com.android.tools.rendering.IRenderLogger
import com.android.tools.rendering.RenderProblem
import com.android.tools.rendering.api.EnvironmentContext
import com.android.tools.rendering.classloading.ModuleClassLoaderManager
import com.android.tools.rendering.parsers.RenderXmlFile
import com.android.tools.rendering.security.RenderSecurityManager
import com.android.tools.sdk.AndroidPlatform
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.util.Computable
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.psi.PsiFile
import com.intellij.psi.xml.XmlFile
import org.apache.http.HttpEntity
import org.apache.http.entity.mime.MultipartEntityBuilder
import org.jetbrains.android.dom.navigation.getStartDestLayoutId
import java.util.concurrent.CompletableFuture

class ScreenshotEnvironmentContext(private val project: ComposeProject, private val dependencies: Dependencies) : EnvironmentContext {
    private val classLoaderManager = ScreenshotModuleClassLoaderManager(dependencies)
    private val stubCrashReporter = object : CrashReporter {
        override fun submit(crashReport: CrashReport): CompletableFuture<String> =
            CompletableFuture.completedFuture("")

        override fun submit(
            crashReport: CrashReport,
            userReported: Boolean
        ): CompletableFuture<String> = CompletableFuture.completedFuture("")

        override fun submit(kv: MutableMap<String, String>): CompletableFuture<String> =
            CompletableFuture.completedFuture("")

        override fun submit(entity: HttpEntity): CompletableFuture<String> =
            CompletableFuture.completedFuture("")
    }

    private class StubCrashReport : CrashReport("", "", emptyMap(), "") {
        override fun serializeTo(builder: MultipartEntityBuilder) { }
    }

    override val layoutlibContext: LayoutlibContext
        get() = object : LayoutlibContext {
            override val parentDisposable: Disposable = project.lintProject.ideaProject!!

            override fun hasLayoutlibCrash(): Boolean = false //TODO

        }

    override val runnableFixFactory: RenderProblem.RunnableFixFactory = ShowFixFactory
    override fun reportMissingSdkDependency(logger: IRenderLogger) {
        TODO("Not yet implemented")
    }

    override fun createIncludeReference(
        xmlFile: RenderXmlFile,
        resolver: RenderResources
    ): IncludeReference =
        PsiIncludeReference.get(xmlFile, resolver)

    override fun getFileText(fileName: String): String? {
        val virtualFile = LocalFileSystem.getInstance().findFileByPath(fileName)
        if (virtualFile != null) {
            val psiFile =
                AndroidPsiUtils.getPsiFileSafely(project.lintProject.ideaProject!!, virtualFile)
            if (psiFile != null) {
                return if (ApplicationManager.getApplication().isReadAccessAllowed) psiFile.text
                else ApplicationManager.getApplication().runReadAction(
                    Computable { psiFile.text } as Computable<String>)
            }
        }
        return null
    }

    override fun getXmlFile(filePath: PathString): RenderXmlFile? {
        val file = filePath.toVirtualFile()
        return file?.let {
            AndroidPsiUtils.getPsiFileSafely(
                project.lintProject.ideaProject!!,
                it
            ) as? XmlFile
        }?.let { PsiXmlFile(it) }
    }

    override fun getNavGraphResolver(resourceResolver: ResourceResolver): NavGraphResolver {
        return NavGraphResolver { navGraph ->
            getStartDestLayoutId(
                navGraph,
                project.lintProject.ideaProject!!,
                resourceResolver
            )
        }
    }

    override fun createRenderSecurityManager(
        projectPath: String?,
        platform: AndroidPlatform?
    ): RenderSecurityManager {
        return RenderSecurityManager(null,null,false)
    }

    // Can be anything, used for RenderErrorContributor
    override fun getOriginalFile(psiFile: PsiFile): PsiFile = psiFile

    override fun getModuleClassLoaderManager(): ModuleClassLoaderManager {
        return classLoaderManager
    }

    override fun getCrashReporter(): CrashReporter = stubCrashReporter

    override fun createCrashReport(t: Throwable): CrashReport = StubCrashReport()

    override fun isInTest(): Boolean = false
}
