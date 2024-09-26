/*
 * Copyright (C) 2018 The Android Open Source Project
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

package com.android.build.gradle.internal.tasks

import com.android.build.gradle.internal.core.dsl.ComponentDslInfo
import com.android.build.gradle.internal.tasks.factory.GlobalTaskCreationAction
import com.android.build.gradle.internal.tasks.factory.PrivacySandboxSdkTaskCreationAction
import com.android.build.gradle.internal.tasks.factory.TaskCreationAction
import com.android.build.gradle.internal.tasks.factory.VariantTaskCreationAction
import com.android.build.gradle.tasks.PackageAndroidArtifact
import com.android.ide.common.workers.WorkerExecutorFacade
import com.google.common.reflect.ClassPath
import com.google.common.reflect.TypeToken
import com.google.common.truth.Truth
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import org.gradle.api.Task
import org.gradle.api.file.RegularFile
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.services.BuildService
import org.gradle.api.services.ServiceReference
import org.gradle.api.tasks.Classpath
import org.gradle.api.tasks.CompileClasspath
import org.gradle.api.tasks.Console
import org.gradle.api.tasks.Destroys
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.LocalState
import org.gradle.api.tasks.Nested
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputDirectories
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.OutputFiles
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.SkipWhenEmpty
import org.gradle.api.tasks.TaskAction
import org.gradle.work.Incremental
import org.junit.Test
import java.lang.reflect.AnnotatedElement
import java.lang.reflect.Field
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import java.lang.reflect.ParameterizedType
import java.util.function.Supplier

class TaskMethodModifiersAndAnnotationsTest {

    @Test
    fun `check for non-public methods with gradle input or output annotations`() {
        val nonPublicMethods = taskInputOutputMethods.filter { !Modifier.isPublic(it.modifiers) }

        if (nonPublicMethods.isEmpty()) {
            return
        }

        // Otherwise generate a descriptive error message.
        val error =
            StringBuilder().append("The following gradle-annotated methods are not public:\n")
        for (nonPublicMethod in nonPublicMethods) {
            error.append(nonPublicMethod.declaringClass.toString().substringAfter(" "))
                .append("::${nonPublicMethod.name}\n")
        }
        throw AssertionError(error.toString())
    }

    @Test
    fun `check for relative path sensitivity on files`() {
        val relativePathSensitivityOnFiles = taskInputOutputMethods.filter {
            (it.returnType == RegularFile::class.java || it.returnType == RegularFileProperty::class.java) &&
                    it.getAnnotation(PathSensitive::class.java)?.value == PathSensitivity.RELATIVE
        }.map {
            "${it.declaringClass.toString().substringAfter(" ")}::${it.name}"
        }
        if (relativePathSensitivityOnFiles.isEmpty()) {
            return
        }
        throw AssertionError(
                "The following file inputs are annotated as PathSensitive(PathSensitivity.RELATIVE).\n" +
                        "This is misleading as for file inputs it is equivalent to PathSensitivity.NAME_ONLY\n  " +
                        relativePathSensitivityOnFiles.joinToString("\n  ")
        )
    }

    @Test
    fun `check for TaskAction use in tasks that extend tasks that already define it`() {
        val baseTaskClasses = listOf(NonIncrementalGlobalTask::class.java, NonIncrementalTask::class.java, NewIncrementalTask::class.java)
        val extendsAgpBaseTaskClasses = tasks.filter { !baseTaskClasses.contains(it) && baseTaskClasses.any { baseClass -> baseClass.isAssignableFrom(it) } }
        val taskActionMethods = extendsAgpBaseTaskClasses.associateWith { it.declaredMethods.filter { method -> method.getAnnotation(TaskAction::class.java) != null } }
        val methodsThatUseTaskAction = taskActionMethods.filter { it.value.isNotEmpty() }.map { methods ->
            methods.key.toString().substringAfter(" ") + " ${methods.value.map { it.name }}" }

        if (methodsThatUseTaskAction.isEmpty()) {
            return
        }
        throw AssertionError(
                "The following classes have @TaskAction methods, and extend one of ${baseTaskClasses.map { it.simpleName }}.\n" +
                        "Please remove the @TaskAction annotation from methods of these tasks.\n  " +
                        methodsThatUseTaskAction.joinToString("\n  ")
        )
    }

    @Test
    fun `check that tasks should extend a set of base tasks`() {
        // There are 2 types of tasks:
        //   1. Tasks extending com.android.build.gradle.internal.tasks.BaseTask
        //   2. Tasks extending org.gradle.api.DefaultTask
        val (tasksExtendingBaseTask, tasksExtendingDefaultTask) = tasks.partition {
            BaseTask::class.java.isAssignableFrom(it)
        }

        // For type 1, they need to extend one of the 3 following subtypes
        val type1Violations = tasksExtendingBaseTask.filter {
            !NewIncrementalTask::class.java.isAssignableFrom(it)
                    && !NonIncrementalTask::class.java.isAssignableFrom(it)
                    && !NonIncrementalGlobalTask::class.java.isAssignableFrom(it)
        }.minus(
            setOf(
                BaseTask::class.java,
                AndroidVariantTask::class.java,
                UnsafeOutputsTask::class.java,
                AndroidGlobalTask::class.java,
                UnsafeOutputsGlobalTask::class.java
            )
        ).map { it.name }.sorted()

        // For type 2, they need to implement one of the 2 following interfaces
        val type2Violations = tasksExtendingDefaultTask.filter {
            !VariantTask::class.java.isAssignableFrom(it)
                    && !GlobalTask::class.java.isAssignableFrom(it)
        }.map { it.name }.sorted()

        assertWithMessage(
            "All AGP tasks should extend ${NewIncrementalTask::class.java.simpleName}, " +
                    "${NonIncrementalTask::class.java.simpleName}, or " +
                    "${NonIncrementalGlobalTask::class.java.simpleName}. " +
                    "For AGP tasks that extend a Gradle task (e.g., Zip, Test) directly, " +
                    "they should implement ${VariantTask::class.java.simpleName} or " +
                    "${GlobalTask::class.java.simpleName}."
        )
            .that(type1Violations + type2Violations).containsExactly(
                // Don't add new tasks to this list
                "com.android.build.gradle.internal.lint.AndroidLintCopyReportTask",
                "com.android.build.gradle.internal.lint.AndroidLintGlobalTask",
                "com.android.build.gradle.internal.tasks.AndroidReportTask",
                "com.android.build.gradle.internal.tasks.DependencyReportTask",
                "com.android.build.gradle.internal.tasks.DeviceSerialTestTask",
                "com.android.build.gradle.internal.tasks.ManagedDeviceSetupTask",
                "com.android.build.gradle.internal.tasks.SigningReportTask",
                "com.android.build.gradle.internal.tasks.SourceSetsTask",
                "com.android.build.gradle.tasks.ExternalNativeBuildJsonTask",
                "com.android.build.gradle.tasks.ExternalNativeBuildTask",
                "com.android.build.gradle.tasks.FusedLibraryBundle",
                "com.android.build.gradle.tasks.FusedLibraryBundleAar",
                "com.android.build.gradle.tasks.FusedLibraryMergeClasses",
                "com.android.build.gradle.tasks.PrivacySandboxSdkGenerateJarStubsTask",
            )
    }

    @Test
    fun `check that task creation actions should extend a set of base task creation actions`() {
        val violations = taskCreationActions.filter {
            !VariantTaskCreationAction::class.java.isAssignableFrom(it)
                    && !GlobalTaskCreationAction::class.java.isAssignableFrom(it)
                    && !PrivacySandboxSdkTaskCreationAction::class.java.isAssignableFrom(it)
        }
            .minus(TaskCreationAction::class.java)
            .map { it.name }.sorted()

        assertWithMessage(
            "All AGP task creation actions should extend ${VariantTaskCreationAction::class.java.simpleName}, ${PrivacySandboxSdkTaskCreationAction::class.java.simpleName} or " +
                    "${GlobalTaskCreationAction::class.java.simpleName}."
        )
            .that(violations).containsExactly(
                // Don't add new items to this list
                "com.android.build.gradle.internal.res.PrivacySandboxSdkLinkAndroidResourcesTask\$CreationAction",
                "com.android.build.gradle.internal.res.namespaced.CompileRClassTaskCreationAction",
                "com.android.build.gradle.internal.tasks.AndroidReportTask\$CreationAction",
                "com.android.build.gradle.internal.tasks.AppClasspathCheckTask\$CreationAction",
                "com.android.build.gradle.internal.tasks.AppMetadataTask\$CreationForAssetPackBundleAction",
                "com.android.build.gradle.internal.tasks.AppMetadataTask\$PrivacySandboxSdkCreationAction",
                "com.android.build.gradle.internal.tasks.AssetPackPreBundleTask\$CreationForAssetPackBundleAction",
                "com.android.build.gradle.internal.tasks.BaseTask\$CreationAction",
                "com.android.build.gradle.internal.tasks.FinalizeBundleTask\$CreationForAssetPackBundleAction",
                "com.android.build.gradle.internal.tasks.GeneratePrivacySandboxProguardRulesTask\$CreationAction",
                "com.android.build.gradle.internal.tasks.LinkManifestForAssetPackTask\$CreationForAssetPackBundleAction",
                "com.android.build.gradle.internal.tasks.ListingFileRedirectTask\$CreationAction",
                "com.android.build.gradle.internal.tasks.MergeJavaResourceTask\$FusedLibraryCreationAction",
                "com.android.build.gradle.internal.tasks.MergeJavaResourceTask\$PrivacySandboxSdkCreationAction",
                "com.android.build.gradle.internal.tasks.PackageBundleTask\$CreationForAssetPackBundleAction",
                "com.android.build.gradle.internal.tasks.PerModuleBundleTask\$PrivacySandboxSdkCreationAction",
                "com.android.build.gradle.internal.tasks.ProcessAssetPackManifestTask\$CreationForAssetPackBundleAction",
                "com.android.build.gradle.internal.tasks.ProguardConfigurableTask\$PrivacySandboxSdkCreationAction",
                "com.android.build.gradle.internal.tasks.R8Task\$PrivacySandboxSdkCreationAction",
                "com.android.build.gradle.internal.tasks.SignAsbTask\$CreationActionPrivacySandboxSdk",
                "com.android.build.gradle.internal.tasks.SourceSetsTask\$CreationAction",
                "com.android.build.gradle.internal.tasks.ValidateSigningTask\$CreationForAssetPackBundleAction",
                "com.android.build.gradle.internal.tasks.ValidateSigningTask\$PrivacySandboxSdkCreationAction",
                "com.android.build.gradle.internal.tasks.factory.AndroidVariantTaskCreationAction",
                "com.android.build.gradle.internal.tasks.factory.TaskConfigurationActionsTest\$createTaskAction\$creationAction$1",
                "com.android.build.gradle.tasks.ExternalNativeBuildTaskKt\$createWorkingCxxBuildTask$1",
                "com.android.build.gradle.tasks.FusedLibraryBundle\$CreationAction",
                "com.android.build.gradle.tasks.FusedLibraryBundleAar\$CreationAction",
                "com.android.build.gradle.tasks.FusedLibraryBundleClasses\$CreationAction",
                "com.android.build.gradle.tasks.FusedLibraryClassesRewriteTask\$CreationAction",
                "com.android.build.gradle.tasks.FusedLibraryManifestMergerTask\$CreationAction",
                "com.android.build.gradle.tasks.FusedLibraryMergeArtifactTask\$CreateActionFusedLibrary",
                "com.android.build.gradle.tasks.FusedLibraryMergeArtifactTask\$CreateActionPrivacySandboxSdk",
                "com.android.build.gradle.tasks.FusedLibraryMergeClasses\$FusedLibraryCreationAction",
                "com.android.build.gradle.tasks.FusedLibraryMergeClasses\$PrivacySandboxSdkCreationAction",
                "com.android.build.gradle.tasks.FusedLibraryMergeResourceCompileSymbolsTask\$CreationAction",
                "com.android.build.gradle.tasks.FusedLibraryMergeResourcesTask\$CreationAction",
                "com.android.build.gradle.tasks.GeneratePrivacySandboxAsar\$CreationAction",
                "com.android.build.gradle.tasks.JavaCompileCreationAction",
                "com.android.build.gradle.tasks.PackagePrivacySandboxSdkBundle\$CreationAction",
                "com.android.build.gradle.tasks.PrivacySandboxSdkDexTask\$CreationAction",
                "com.android.build.gradle.tasks.PrivacySandboxSdkGenerateJarStubsTask\$CreationAction",
                "com.android.build.gradle.tasks.PrivacySandboxSdkGenerateRClassTask\$CreationAction",
                "com.android.build.gradle.tasks.PrivacySandboxSdkManifestGeneratorTask\$CreationAction",
                "com.android.build.gradle.tasks.PrivacySandboxSdkManifestMergerTask\$CreationAction",
                "com.android.build.gradle.tasks.PrivacySandboxSdkMergeDexTask\$CreationAction",
                "com.android.build.gradle.tasks.PrivacySandboxSdkMergeResourcesTask\$CreationAction",
                "com.android.build.gradle.tasks.PrivacySandboxValidateConfigurationTask\$CreationAction",
            )
    }

    @Test
    fun `check for fields with gradle input or output annotations`() {
        val annotatedFields =
            classPath
                .getTopLevelClassesRecursive("com.android.build")
                .map { classInfo -> classInfo.load() as Class<*> }
                .flatMap { it.declaredFields.asIterable() }
                .filter { it.hasGradleInputOrOutputAnnotation() }

        if (annotatedFields.isEmpty()) {
            return
        }

        // Otherwise generate a descriptive error message.
        val error =
            StringBuilder().append(
                "The following fields are annotated with gradle input/output annotations, which "
                        + "should only be used on methods (e.g., the corresponding getters):\n")
        for (annotatedField in annotatedFields) {
            error.append(annotatedField.declaringClass.toString().substringAfter(" "))
                .append(".${annotatedField.name}\n")
        }
        throw AssertionError(error.toString())
    }

    @Test
    fun `check for public task setters`() {

        val currentPublicSetters =
            listOf(
                "com.android.build.gradle.internal.res.LinkApplicationAndroidResourcesTask::setType",
                "com.android.build.gradle.internal.tasks.AndroidReportTask::setIgnoreFailures",
                "com.android.build.gradle.internal.tasks.AndroidReportTask::setReportType",
                "com.android.build.gradle.internal.tasks.AndroidReportTask::setWillRun",
                "com.android.build.gradle.internal.tasks.AndroidVariantTask::setVariantName",
                "com.android.build.gradle.internal.tasks.DeviceProviderInstrumentTestTask::setIgnoreFailures",
                "com.android.build.gradle.internal.tasks.DeviceProviderInstrumentTestTask::setSerialOption",
                "com.android.build.gradle.internal.tasks.DeviceProviderInstrumentTestTask::setUtpTestResultListener",
                "com.android.build.gradle.internal.tasks.DeviceSerialTestTask::setSerialOption",
                "com.android.build.gradle.internal.tasks.InstallVariantTask::setInstallOptions",
                "com.android.build.gradle.internal.tasks.InstallVariantTask::setTimeOutInMs",
                "com.android.build.gradle.internal.tasks.ManagedDeviceCleanTask::setPreserveDefinedOption",
                "com.android.build.gradle.internal.tasks.ManagedDeviceTestTask::setIgnoreFailures",
                "com.android.build.gradle.internal.tasks.ManagedDeviceInstrumentationTestTask::setDisplayEmulatorOption",
                "com.android.build.gradle.internal.tasks.ManagedDeviceInstrumentationTestTask::setIgnoreFailures",
                "com.android.build.gradle.internal.tasks.NdkTask::setNdkConfig",
                "com.android.build.gradle.internal.tasks.PackageRenderscriptTask::setVariantName",
                "com.android.build.gradle.internal.tasks.ProcessJavaResTask::setVariantName",
                "com.android.build.gradle.internal.tasks.SigningReportTask::setComponents",
                "com.android.build.gradle.internal.tasks.TestServerTask::setTestServer",
                "com.android.build.gradle.internal.tasks.UninstallTask::setTimeOutInMs",
                "com.android.build.gradle.tasks.factory.AndroidUnitTest::setVariantName",
                "com.android.build.gradle.tasks.BundleAar::setVariantName",
                "com.android.build.gradle.tasks.ExtractAnnotations::setBootClasspath",
                "com.android.build.gradle.tasks.ExtractAnnotations::setEncoding",
                "com.android.build.gradle.tasks.PackageAndroidArtifact::setJniDebugBuild",
                "com.android.build.gradle.tasks.RenderscriptCompile::setImportDirs",
                "com.android.build.gradle.tasks.RenderscriptCompile::setObjOutputDir",
                "com.android.build.gradle.tasks.ShaderCompile::setDefaultArgs",
                "com.android.build.gradle.tasks.ShaderCompile::setScopedArgs",
                "com.android.build.gradle.tasks.SourceJarTask::setVariantName",
                "com.android.build.gradle.tasks.JavaDocJarTask::setVariantName",
            )

        val taskInterface = TypeToken.of(Task::class.java)
        val publicSetters =
            classPath
                .getTopLevelClassesRecursive("com.android.build")
                .map { classInfo -> classInfo.load() as Class<*> }
                .filter { clazz -> TypeToken.of(clazz).types.contains(taskInterface) }
                .flatMap { it.declaredMethods.asIterable() }
                .filter {
                    it.name.startsWith("set")
                            && !it.name.contains('$')
                            && Modifier.isPublic(it.modifiers)
                }

        val publicSettersAsStrings = publicSetters
                .map { "${it.declaringClass.toString().substringAfter(" ")}::${it.name}" }

        assertThat(publicSettersAsStrings)
            .named("Task public setters")
            .containsExactlyElementsIn(currentPublicSetters)

        // Check for getters and setters that have different types than can upset gradle's instansiator.
        val mismatchingGetters = publicSetters.filter { setter ->
            val matchingGetter = getMatchingGetter(setter)
            matchingGetter != null && setter.parameters.size == 1 && setter.parameters[0].type != matchingGetter.returnType
        }
        assertWithMessage("Getters and setter types don't match")
            .that(mismatchingGetters.map { "${getMatchingGetter(it)}  -  $it" }).isEmpty()
    }

    @Test
    fun checkWorkerFacadeIsNotAField() {
        Truth.assertThat(findTaskFieldsOfType(WorkerExecutorFacade::class.java)).isEmpty()
    }

    @Test
    fun checkVariantConfigurationIsNotAField() {
        Truth.assertThat(findTaskFieldsOfType(ComponentDslInfo::class.java)).isEmpty()
    }

    @Test
    fun checkSupplierIsNotAField() {
        assertThat(findTaskFieldsOfType(Supplier::class.java)).isEmpty()
    }

    /**
     * Test that the workaround for https://github.com/gradle/gradle/issues/16976 in
     * [PackageAndroidArtifact] is kept up to date.
     *
     * Adding new incremental inputs to [PackageAndroidArtifact] without updating this test will
     * cause this test to fail. Please add any new incremental inputs to the appropriate
     * non-incremental input (e.g., [PackageAndroidArtifact.getAllClasspathInputFiles]) before
     * updating this test.
     */
    @Test
    fun checkIncrementalPackagingInputs() {
        val incrementalInputs =
            classPath
                .getTopLevelClasses("com.android.build.gradle.tasks")
                .filter { it.simpleName == "PackageAndroidArtifact" }
                .map { classInfo -> classInfo.load() as Class<*> }
                .flatMap { it.declaredMethods.asIterable() }
                .filter { it.getAnnotation(Incremental::class.java) != null }
                .map {
                    val annotation = when {
                        it.getAnnotation(Classpath::class.java) != null ->
                            it.getAnnotation(Classpath::class.java).toString()
                        it.getAnnotation(PathSensitive::class.java) != null ->
                            it.getAnnotation(PathSensitive::class.java).toString()
                                .replace("value=", "")
                        else -> "OTHER"
                    }
                    return@map "${it.name}  $annotation"
                }
        assertThat(incrementalInputs).containsExactly(
            "getAppMetadata  @org.gradle.api.tasks.PathSensitive(NAME_ONLY)",
            "getAssets  @org.gradle.api.tasks.PathSensitive(RELATIVE)",
            "getDexFolders  @org.gradle.api.tasks.PathSensitive(RELATIVE)",
            "getFeatureDexFolder  @org.gradle.api.tasks.PathSensitive(RELATIVE)",
            "getFeatureJavaResourceFiles  @org.gradle.api.tasks.Classpath()",
            "getJavaResourceFiles  @org.gradle.api.tasks.Classpath()",
            "getJniFolders  @org.gradle.api.tasks.Classpath()",
            "getManifests  @org.gradle.api.tasks.PathSensitive(RELATIVE)",
            "getMergedArtProfile  @org.gradle.api.tasks.PathSensitive(NAME_ONLY)",
            "getMergedArtProfileMetadata  @org.gradle.api.tasks.PathSensitive(NAME_ONLY)",
            "getResourceFiles  @org.gradle.api.tasks.PathSensitive(RELATIVE)",
            "getVersionControlInfoFile  @org.gradle.api.tasks.PathSensitive(NAME_ONLY)",
            "getPrivacySandboxRuntimeEnabledSdkTable  @org.gradle.api.tasks.PathSensitive(NAME_ONLY)"
        )
    }

    /** Regression test for b/300617088. */
    @Test
    fun `check BuildService properties are annotated with @ServiceReference`() {
        val tasks = classPath.allClasses.asSequence()
            .filter { it.name.startsWith("com.android.build") }
            .map { it.load() as Class<*> }
            .filter { Task::class.java.isAssignableFrom(it) }

        fun Method.isBuildServiceProperty(): Boolean {
            val genericReturnType = genericReturnType as? ParameterizedType ?: return false
            val rawType = genericReturnType.rawType as? Class<*> ?: return false
            val actualTypeArgument = (genericReturnType.actualTypeArguments.singleOrNull() ?: return false) as? Class<*> ?: return false
            return Property::class.java.isAssignableFrom(rawType) && BuildService::class.java.isAssignableFrom(actualTypeArgument)
        }

        fun Method.isAnnotatedWithServiceReference(): Boolean {
            @Suppress("UnstableApiUsage")
            return annotations.any { it.annotationClass == ServiceReference::class }
        }

        fun Method.isNestedProperty(): Boolean {
            return annotations.any { Nested::class.java.isAssignableFrom(it.javaClass) }
        }

        val nestedProperties = tasks.flatMap { task ->
            task.methods.filter { it.isNestedProperty() }.map { it.returnType }
        }.toSet()

        val violations = (tasks + nestedProperties).flatMap { it.methods.asSequence() }.toSet()
            .filter { it.isBuildServiceProperty() && !it.isAnnotatedWithServiceReference() }
            .map { "${it.declaringClass.name}.${it.name}" }.sorted()

        // TODO(b/300617088): Fix these violations
        assertThat(violations).containsExactly(
            "com.android.build.gradle.internal.UsesSdkComponentsBuildService.getSdkComponentsBuildService",
            "com.android.build.gradle.internal.ide.dependencies.UsesLibraryDependencyCacheBuildService.getLibraryDependencyCacheBuildService",
            "com.android.build.gradle.internal.lint.AndroidLintTask.getLintFixBuildService",
            "com.android.build.gradle.internal.lint.LintTool.getLintClassLoaderBuildService",
            "com.android.build.gradle.internal.lint.VariantInputs.getMavenCoordinatesCache",
            "com.android.build.gradle.internal.res.LinkApplicationAndroidResourcesTask.getSymbolTableBuildService",
            "com.android.build.gradle.internal.services.UsesAapt2DaemonBuildService.getAapt2DaemonBuildService",
            "com.android.build.gradle.internal.services.UsesAapt2ThreadPoolBuildService.getAapt2ThreadPoolBuildService",
            "com.android.build.gradle.internal.tasks.CheckJetifierTask.getCheckJetifierBuildService",
            "com.android.build.gradle.internal.tasks.DependencyReportTask.getMavenCoordinateCache",
            "com.android.build.gradle.internal.tasks.DeviceProviderInstrumentTestTask\$TestRunnerFactory.getSdkBuildService",
            "com.android.build.gradle.internal.tasks.ExtractNativeDebugMetadataTask.getSdkBuildService",
            "com.android.build.gradle.internal.tasks.ManagedDeviceCleanTask.getAvdService",
            "com.android.build.gradle.internal.tasks.ManagedDeviceInstrumentationTestSetupTask.getAvdService",
            "com.android.build.gradle.internal.tasks.ManagedDeviceInstrumentationTestSetupTask.getSdkService",
            "com.android.build.gradle.internal.tasks.ManagedDeviceInstrumentationTestTask\$TestRunnerFactory.getAvdComponents",
            "com.android.build.gradle.internal.tasks.ManagedDeviceInstrumentationTestTask\$TestRunnerFactory.getSdkBuildService",
            "com.android.build.gradle.internal.tasks.RecalculateStackFramesTask.getClassesHierarchyBuildService",
            "com.android.build.gradle.internal.tasks.StripDebugSymbolsTask.getSdkBuildService",
            "com.android.build.gradle.internal.tasks.UsesAnalytics.getAnalyticsService",
            "com.android.build.gradle.internal.tasks.VerifyLibraryClassesTask.getSymbolTableBuildService",
            "com.android.build.gradle.internal.tasks.databinding.DataBindingGenBaseClassesTask.getSymbolTableBuildService",
            "com.android.build.gradle.tasks.ExternalNativeBuildJsonTask.getNativeLocationsBuildService",
            "com.android.build.gradle.tasks.ExternalNativeBuildJsonTask.getSdkComponents",
            "com.android.build.gradle.tasks.ExternalNativeCleanTask.getSdkComponents",
            "com.android.build.gradle.tasks.FusedLibraryClassesRewriteTask.getSymbolTableBuildService",
            "com.android.build.gradle.tasks.FusedLibraryMergeResourceCompileSymbolsTask.getSymbolTableBuildService",
            "com.android.build.gradle.tasks.FusedLibraryMergeResourcesTask.getAnalytics",
            "com.android.build.gradle.tasks.MergeResources.getAapt2ThreadPoolBuildService",
            "com.android.build.gradle.tasks.PrefabPackageTask.getSdkComponents",
            "com.android.build.gradle.tasks.PrivacySandboxSdkGenerateRClassTask.getSymbolTableBuildService",
            "com.android.build.gradle.tasks.PrivacySandboxSdkMergeResourcesTask.getAnalytics",
            "com.android.build.gradle.tasks.RenderscriptCompile.getSdkBuildService",
            "com.android.build.gradle.tasks.ShaderCompile.getSdkBuildService",
            "com.android.build.gradle.tasks.TransformClassesWithAsmTask.getClassesHierarchyBuildService"
        )
    }

    private fun getMatchingGetter(setter: Method) : Method? {
        val name = setter.name.removePrefix("set")
        val nameWithGet = "get$name"
        val nameWithIs = "is$name"
        return setter.declaringClass.declaredMethods.firstOrNull {
            it.name == nameWithGet || it.name == nameWithIs
        }
    }

    private fun findTaskFieldsOfType(ofType: Class<*>): List<Field> {
        val taskInterface = TypeToken.of(Task::class.java)
        val fieldType = TypeToken.of(ofType)
        return classPath
            .getTopLevelClassesRecursive("com.android.build")
            .map { classInfo -> classInfo.load() as Class<*> }
            .filter { clazz -> TypeToken.of(clazz).types.contains(taskInterface) }
            .flatMap { it.declaredFields.asIterable() }
            .filter { TypeToken.of(it.type).isSubtypeOf(fieldType) }
    }



    companion object {

        private val classPath: ClassPath by lazy(LazyThreadSafetyMode.PUBLICATION) {
            ClassPath.from(this.javaClass.classLoader)
        }


        private fun AnnotatedElement.hasGradleInputOrOutputAnnotation(): Boolean {
            // look for all org.gradle.api.tasks annotations, except @CacheableTask, @Internal, and
            // @TaskAction.
            return getAnnotation(Classpath::class.java) != null
                    || getAnnotation(CompileClasspath::class.java) != null
                    || getAnnotation(Console::class.java) != null
                    || getAnnotation(Destroys::class.java) != null
                    || getAnnotation(Input::class.java) != null
                    || getAnnotation(InputDirectory::class.java) != null
                    || getAnnotation(InputFile::class.java) != null
                    || getAnnotation(InputFiles::class.java) != null
                    || getAnnotation(LocalState::class.java) != null
                    || getAnnotation(Nested::class.java) != null
                    || getAnnotation(Optional::class.java) != null
                    || getAnnotation(OutputDirectories::class.java) != null
                    || getAnnotation(OutputDirectory::class.java) != null
                    || getAnnotation(OutputFile::class.java) != null
                    || getAnnotation(OutputFiles::class.java) != null
                    || getAnnotation(PathSensitive::class.java) != null
                    || getAnnotation(SkipWhenEmpty::class.java) != null
        }

        private val comAndroidBuildClasses by lazy(LazyThreadSafetyMode.PUBLICATION) {
            classPath.getTopLevelClassesRecursive("com.android.build")
                .map { classInfo -> classInfo.load() as Class<*> }
        }

        private val taskInputOutputMethods: List<Method> by lazy(LazyThreadSafetyMode.PUBLICATION) {
            comAndroidBuildClasses
                    .flatMap { it.declaredMethods.asIterable() }
                    .filter { it.hasGradleInputOrOutputAnnotation() }
        }

        private val tasks: List<Class<out Task>> by lazy(LazyThreadSafetyMode.PUBLICATION) {
            comAndroidBuildClasses.filterIsInstance<Class<out Task>>().filter { Task::class.java.isAssignableFrom(it) }
        }

        private val taskCreationActions: List<Class<out TaskCreationAction<out Task>>> by lazy(LazyThreadSafetyMode.PUBLICATION) {
            classPath.allClasses
                .filter { it.name.startsWith("com.android.build") }
                .map { classInfo -> classInfo.load() as Class<*> }
                .filterIsInstance<Class<out TaskCreationAction<out Task>>>()
                .filter { TaskCreationAction::class.java.isAssignableFrom(it) }
        }
    }
}
