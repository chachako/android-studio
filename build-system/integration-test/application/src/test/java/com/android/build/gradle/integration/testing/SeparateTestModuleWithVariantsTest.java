package com.android.build.gradle.integration.testing;

import static com.android.testutils.truth.PathSubject.assertThat;
import static com.google.common.truth.Truth.assertThat;

import com.android.build.gradle.integration.common.fixture.BaseGradleExecutor;
import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import com.android.build.gradle.integration.common.fixture.ModelContainerV2;
import com.android.build.gradle.integration.common.utils.TestFileUtils;
import com.android.build.gradle.options.BooleanOption;
import com.android.builder.model.v2.ide.TestedTargetVariant;
import com.android.builder.model.v2.ide.Variant;
import com.google.common.collect.Iterables;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

/**
 * Test for setup with 2 modules: app and test-app Checking the manifest merging for the test
 * modules.
 */
public class SeparateTestModuleWithVariantsTest {

    @Rule
    public GradleTestProject project =
            GradleTestProject.builder().fromTestProject("separateTestModule")
                    // Enforcing unique package names to prevent regressions. Remove when b/116109681 fixed.
                    .addGradleProperties(
                            BooleanOption.ENFORCE_UNIQUE_PACKAGE_NAMES.getPropertyName() + "=true")
                    .addGradleProperties(BooleanOption.USE_ANDROID_X.getPropertyName() + "=true")
                    .create();

    @Before
    public void setUp() throws IOException {
        TestFileUtils.appendToFile(
                project.getSubproject("test").getBuildFile(),
                "\n"
                        + "android {\n"
                        + "    defaultConfig {\n"
                        + "        testInstrumentationRunner 'android.support.test.runner.AndroidJUnitRunner'\n"
                        + "    }\n"
                        + "}\n");
    }

    @Test
    public void checkDependenciesBetweenTasks() throws Exception {
        // Check :test:assembleDebug succeeds on its own, i.e. compiles the app module.
        project.executor()
                .withConfigurationCaching(BaseGradleExecutor.ConfigurationCaching.ON)
                .run("clean", ":test:assembleDebug");
        project.executor()
                .withConfigurationCaching(BaseGradleExecutor.ConfigurationCaching.ON)
                .run(":test:checkDependencies");
    }

    @Test
    public void checkInstrumentationReadFromBuildFile() throws Exception {
        GradleTestProject testProject = project.getSubproject("test");
        addInstrumentationToManifest();
        project.execute("clean", ":test:assembleDebug");

        assertThat(
                        testProject.file(
                                "build/intermediates/packaged_manifests/debug/processDebugManifest/AndroidManifest.xml"))
                .containsAllOf(
                        "package=\"com.example.android.testing.blueprint.test\"",
                        "android:name=\"android.support.test.runner.AndroidJUnitRunner\"",
                        "android:targetPackage=\"com.android.tests.basic\"");
    }

    @Test
    public void checkInstrumentationAdded() {
        GradleTestProject testProject = project.getSubproject("test");
        project.execute("clean", ":test:assembleDebug");

        assertThat(
                        testProject.file(
                                "build/intermediates/packaged_manifests/debug/processDebugManifest/AndroidManifest.xml"))
                .containsAllOf(
                        "package=\"com.example.android.testing.blueprint.test\"",
                        "<instrumentation",
                        "android:name=\"android.support.test.runner.AndroidJUnitRunner\"",
                        "android:targetPackage=\"com.android.tests.basic\"");
    }

    @Test
    public void checkModelContainsTestedApksToInstall() {
        ModelContainerV2.ModelInfo model =
                project.modelV2().fetchModels().getContainer().getProject(":test");

        Variant variant = Iterables.getFirst(model.getAndroidProject().getVariants(), null);

        assertThat(variant).isNotNull();
        TestedTargetVariant testedVariant = variant.getTestedTargetVariant();
        assertThat(testedVariant.getTargetProjectPath()).isEqualTo(":app");
        assertThat(testedVariant.getTargetVariant()).isEqualTo("debug");
    }

    private void addInstrumentationToManifest() throws IOException {
        GradleTestProject testProject = project.getSubproject("test");
        Path manifest = testProject.getMainSrcDir().toPath().resolve("AndroidManifest.xml");
        Files.deleteIfExists(manifest);
        String content =
                "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"
                        + "<manifest xmlns:android=\"http://schemas.android.com/apk/res/android\">\n"
                        + "      <uses-sdk android:minSdkVersion=\"16\" android:targetSdkVersion=\"16\" />\n"
                        + "      <instrumentation android:name=\"android.test.InstrumentationTestRunner\"\n"
                        + "                       android:targetPackage=\"com.android.tests.basic\"\n"
                        + "                       android:handleProfiling=\"false\"\n"
                        + "                       android:functionalTest=\"false\"\n"
                        + "                       android:label=\"Tests for com.android.tests.basic\"/>\n"
                        + "</manifest>\n";
        Files.write(manifest, content.getBytes());
    }
}
