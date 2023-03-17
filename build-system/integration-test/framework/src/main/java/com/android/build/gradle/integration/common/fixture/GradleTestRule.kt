package com.android.build.gradle.integration.common.fixture

import com.android.build.gradle.integration.common.fixture.BaseGradleExecutor.ConfigurationCaching
import com.android.build.gradle.integration.common.fixture.GradleTestProjectBuilder.MemoryRequirement
import com.android.build.gradle.integration.common.fixture.gradle_project.ProjectLocation
import org.junit.rules.TestRule
import java.io.File
import java.nio.file.Path

/**
 * Specialization of [TestRule] for Android related projects.
 */
interface GradleTestRule: TestRule {
    val androidSdkDir: File?
    val androidNdkSxSRootSymlink: File?
    val additionalMavenRepoDir: Path?
    val location: ProjectLocation
    val heapSize: MemoryRequirement
    val withConfigurationCaching: ConfigurationCaching

    fun getProfileDirectory(): Path?
    fun setLastBuildResult(lastBuildResult: GradleBuildResult)

}
