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

import com.android.ide.common.rendering.api.SessionParams
import com.android.ide.common.resources.configuration.FolderConfiguration
import com.android.screenshot.cli.diff.ImageDiffer
import com.android.screenshot.cli.diff.Verify
import com.android.tools.idea.AndroidPsiUtils
import com.android.tools.idea.compose.preview.ComposeAdapterLightVirtualFile
import com.android.tools.idea.compose.preview.ComposePreviewElement
import com.android.tools.idea.compose.preview.ComposePreviewElementInstance
import com.android.tools.idea.compose.preview.ComposePreviewElementTemplate
import com.android.tools.idea.compose.preview.applyTo
import com.android.tools.idea.configurations.Configuration
import com.android.tools.idea.rendering.RenderResult
import com.android.tools.idea.rendering.ShowFixFactory
import com.android.tools.idea.rendering.StudioHtmlLinkManager
import com.android.tools.idea.rendering.StudioRenderConfiguration
import com.android.tools.idea.rendering.StudioRenderService
import com.intellij.mock.MockComponentManager
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleManager
import com.intellij.psi.xml.XmlFile
import com.intellij.workspaceModel.ide.impl.legacyBridge.module.ModuleManagerComponentBridge
import com.android.tools.rendering.ModuleRenderContext
import org.mockito.Mockito
import java.awt.image.BufferedImage
import java.io.File
import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.SimpleFileVisitor
import java.nio.file.attribute.BasicFileAttributes
import javax.imageio.ImageIO
import kotlin.io.path.name
import kotlin.io.path.pathString

/**
 * This is the main driver behind capturing screenshots, and saving them to disk.
 * TODO: Better error handling in general
 * TODO: Double check any messages from the inflate result as success doesn't mean it worked.
 */
class ScreenshotProvider(
    private val project: com.android.tools.lint.detector.api.Project,
    private val sdkPath: String,
    private val dependencies: Dependencies
) {

    lateinit var mModule: Module
    lateinit var projectRoot: String
    lateinit var moduleFilePath: String
    val composeApplication = ComposeApplication(project.env, dependencies)
    val projectFileIndex = ScreenshotProjectFileIndex()
    val composeProject = ComposeProject(project, projectFileIndex)
    val composeModule = ComposeModule(composeProject, dependencies)

    init {
        try {
            projectFileIndex.project = composeProject
            projectFileIndex.module = composeModule
            projectRoot = project.dir.absolutePath
            moduleFilePath = project.manifestFiles[0].parentFile.absolutePath
            mModule = composeModule.module
            Mockito.`when`(mModule.moduleFilePath).thenReturn(moduleFilePath)
            Mockito.`when`(mModule.name).thenReturn("NowInAndroidApp")
            composeProject.defaultProjectSystem.setModuleSystem(
                composeModule.module,
                ScreenshotAndroidModuleSystem(
                    ::getDeps,
                    composeProject,
                    composeModule
                )
            )
            setupProjectLink()
        } catch (t: Throwable) {
            t.printStackTrace()
        }
    }

    fun setupProjectLink() {
        val componentManager = (composeProject.lintProject.ideaProject as MockComponentManager)!!
        val mmcb = Mockito.mock(ModuleManagerComponentBridge::class.java)
        Mockito.`when`(mmcb.areModulesLoaded()).thenReturn(true)
        Mockito.`when`(mmcb.modules).thenReturn(arrayOf(composeModule.module))
        componentManager.addComponent(ModuleManager::class.java, mmcb)
        componentManager.registerService(ModuleManager::class.java, mmcb)
    }

    fun getDeps(): MutableList<String> {
        val deps = mutableListOf<String>()
        deps.addAll(dependencies.systemDependencies)
        return deps
    }

    fun verifyScreenshot(
        previewNodes: List<ComposePreviewElement>,
        goldenLocation: String,
        outputLocation: String,
        recordGoldens: Boolean
    ): List<Verify.AnalysisResult> {
        val results = mutableListOf<Verify.AnalysisResult>()
        val instances =
            previewNodes.filterIsInstance<ComposePreviewElementTemplate>()
                .flatMap { it.instances(ModuleRenderContext.forFile(composeModule) { it.previewElementDefinitionPsi?.containingFile }) }
                .toMutableList()
        instances.addAll(previewNodes.filterIsInstance<ComposePreviewElementInstance>())
        instances.distinct()
        val model = ScreenshotRenderModelModule(composeApplication, composeProject, composeModule, sdkPath)
        for (previewElement in instances) {
            val renderResult = renderPreviewElement(previewElement, model)
            val fileName = previewElement.displaySettings.name + ".png"
            if (recordGoldens) {
                renderResult!!.renderedImage.copy?.let { saveImage(it, goldenLocation + fileName) }
            } else {
                val result = compareImages(
                    renderResult!!,
                    goldenLocation,
                    outputLocation,
                    fileName
                )
                if ( result is Verify.AnalysisResult.Failed) {
                    saveImage(result.imageDiff.highlights, outputLocation + previewElement.instanceId + "_diff.png")
                    renderResult.renderedImage.copy?.let { saveImage(it, outputLocation + previewElement.instanceId + "_actual.png") }
                }
                results.add(result)
            }
        }
        return results
    }

    private fun renderPreviewElement(previewElement: ComposePreviewElementInstance, model: ScreenshotRenderModelModule): RenderResult? {
        val config =
            Configuration.create(
                createConfigManager(composeProject, composeModule, sdkPath),
                null,
                FolderConfiguration.createDefault()
            )
        previewElement.applyTo(config)
        val file = ComposeAdapterLightVirtualFile(
            "singlePreviewElement.xml",
            previewElement.toPreviewXml()
                .toolsAttribute("paintBounds", "false")
                .toolsAttribute("findDesignInfoProviders", "false")
                .buildString()
        ) { previewElement.previewElementDefinitionPsi?.virtualFile }

        val service = StudioRenderService.getInstance(project.ideaProject!!)
        val task =
            service.taskBuilder(model, StudioRenderConfiguration(config),
                                service.createLogger(composeProject.lintProject.ideaProject,
                                                     true, ShowFixFactory, ::StudioHtmlLinkManager)
            )
                .withRenderingMode(SessionParams.RenderingMode.SHRINK)
                .disableDecorations()
                .useTransparentBackground()
                .usePrivateClassLoader()
                .doNotReportOutOfDateUserClasses()
                .withPsiFile(
                    AndroidPsiUtils.getPsiFileSafely(
                        project.ideaProject!!,
                        file
                    )!! as XmlFile
                )
                .build().get()
        return task.render().get()
    }

    private fun saveImage(image: BufferedImage, fileName: String) {
        ImageIO.write(
            image,
            "png",
            File(fileName)
        )

    }

    private fun compareImages(renderResult: RenderResult, goldenLocation: String, outputLocation: String, fileName: String): Verify.AnalysisResult {
        val verifier = Verify(ImageDiffer.MSSIMMatcher, outputLocation + fileName)
        return verifier.assertMatchGolden(goldenLocation + fileName, renderResult.renderedImage.copy!!)
    }
}
