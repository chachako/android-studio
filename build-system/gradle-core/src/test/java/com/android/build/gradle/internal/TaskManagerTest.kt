package com.android.build.gradle.internal

import com.android.build.api.artifact.ScopedArtifact
import com.android.build.api.artifact.impl.ArtifactsImpl
import com.android.build.api.artifact.impl.InternalScopedArtifacts
import com.android.build.api.variant.ScopedArtifacts
import com.android.build.gradle.internal.scope.InternalArtifactType
import com.android.build.gradle.internal.tasks.factory.GlobalTaskCreationConfig
import com.google.common.truth.Truth
import io.grpc.Internal
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.tasks.TaskProvider
import org.gradle.testfixtures.ProjectBuilder
import org.junit.Rule
import org.junit.Test
import org.mockito.junit.MockitoJUnit
import org.mockito.junit.MockitoRule
import org.mockito.kotlin.mock
import org.mockito.quality.Strictness
import java.io.File

class TaskManagerTest {

    @get:Rule
    var mockitoJUnitRule: MockitoRule =
        MockitoJUnit.rule().strictness(Strictness.STRICT_STUBS)

    private val project = ProjectBuilder.builder().build()

    private val globalTaskCreationConfig: GlobalTaskCreationConfig = mock()

    class TestTaskManager(project: Project,
        globalConfig: GlobalTaskCreationConfig
    ) : TaskManager(project, globalConfig) {

        override val javaResMergingScopes: Set<InternalScopedArtifacts.InternalScope>
            get() = setOf()
    }

    @Test
    fun testEmptyAllScope() {
        val taskManager = TestTaskManager(project, globalTaskCreationConfig)
        val artifacts = ArtifactsImpl(project, "test")
        taskManager.initializeAllScope(artifacts)
        Truth.assertThat(
            artifacts.forScope(ScopedArtifacts.Scope.ALL)
                .getFinalArtifacts(ScopedArtifact.CLASSES)
                .files
        ).isEmpty()
    }

    @Test
    fun testAllScopeFromProject() {
        val taskManager = TestTaskManager(project, globalTaskCreationConfig)
        val artifacts = ArtifactsImpl(project, "test")
        artifacts.forScope(ScopedArtifacts.Scope.PROJECT).
            setInitialContent(
                ScopedArtifact.CLASSES,
                project.files("/project/classes")
            )
        artifacts.forScope(InternalScopedArtifacts.InternalScope.SUB_PROJECTS).
        setInitialContent(
            ScopedArtifact.CLASSES,
            project.files("/sub-project/classes")
        )
        artifacts.forScope(InternalScopedArtifacts.InternalScope.EXTERNAL_LIBS).
        setInitialContent(
            ScopedArtifact.CLASSES,
            project.files("/external-libs/classes")
        )
        artifacts.forScope(ScopedArtifacts.Scope.PROJECT)
            .setInitialContent(
                ScopedArtifact.JAVA_RES,
                project.files("/project/res")
            )

        artifacts.forScope(InternalScopedArtifacts.InternalScope.SUB_PROJECTS)
            .setInitialContent(
                ScopedArtifact.JAVA_RES,
                project.files("/sub-project/res")
            )
        artifacts.forScope(InternalScopedArtifacts.InternalScope.EXTERNAL_LIBS)
            .setInitialContent(
                ScopedArtifact.JAVA_RES,
                project.files("/external-libs/res")
            )

        taskManager.initializeAllScope(artifacts)

        Truth.assertThat(
            artifacts.forScope(ScopedArtifacts.Scope.ALL)
                .getFinalArtifacts(ScopedArtifact.CLASSES)
                .files
                .map(File::getAbsolutePath)
        ).containsExactly(
            toProjectFile("/project/classes"),
            toProjectFile("/sub-project/classes"),
            toProjectFile("/external-libs/classes"),
        )

        Truth.assertThat(
            artifacts.forScope(ScopedArtifacts.Scope.ALL)
                .getFinalArtifacts(ScopedArtifact.JAVA_RES)
                .files
                .map(File::getAbsolutePath)
        ).containsExactly(
            toProjectFile("/project/res"),
            toProjectFile("/sub-project/res"),
            toProjectFile("/external-libs/res"),
        )
    }

    private fun toProjectFile(path: String) =
        project.layout.projectDirectory.file(path).asFile.absolutePath
}
