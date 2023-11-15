package com.android.build.gradle.integration.application;

import static com.android.build.gradle.integration.common.truth.TruthHelper.assertThat;

import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import com.android.build.gradle.integration.common.utils.TestFileUtils;
import org.junit.AfterClass;
import org.junit.ClassRule;
import org.junit.Test;

public class ArtifactApiTest {

    @ClassRule
    public static GradleTestProject project =
            GradleTestProject.builder().fromTestProject("artifactApi").create();

    @AfterClass
    public static void cleanUp() {
        project = null;
    }

    @Test
    public void backwardsCompatible() throws Exception {
        // ATTENTION Author and Reviewers - please make sure required changes to the build file
        // are backwards compatible before updating this test.
        assertThat(TestFileUtils.sha1NormalizedLineEndings(project.file("build.gradle")))
                .isEqualTo("8f48cd72e6aa6ff3fb15cb6fc3dc111fd4f5940d");
    }
}
