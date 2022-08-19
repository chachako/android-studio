package com.android.build.gradle.integration.application;

import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import com.android.build.gradle.integration.common.fixture.ModelContainerV2;
import com.android.build.gradle.integration.common.fixture.app.HelloWorldApp;
import com.android.build.gradle.integration.common.utils.TestFileUtils;
import com.android.builder.model.v2.ide.SyncIssue;
import com.android.builder.model.v2.models.BasicAndroidProject;
import com.android.prefs.AndroidLocationsSingleton;
import com.android.repository.testframework.FakeProgressIndicator;
import com.android.sdklib.IAndroidTarget;
import com.android.sdklib.repository.AndroidSdkHandler;
import com.android.sdklib.repository.targets.AndroidTargetManager;
import org.junit.Rule;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.util.Collection;

import static com.android.SdkConstants.FN_FRAMEWORK_LIBRARY;
import static com.android.testutils.truth.PathSubject.assertThat;
import static com.google.common.truth.Truth.assertThat;

/** Test for the new useLibrary mechanism */
public class OptionalLibraryTest {
    @Rule
    public GradleTestProject project =
            GradleTestProject.builder().fromTestApp(HelloWorldApp.noBuildFile()).create();

    @Test
    public void testUnknownUseLibraryTriggerSyncIssue() throws IOException {
        TestFileUtils.appendToFile(
                project.getBuildFile(),
                "\n"
                        + "apply plugin: 'com.android.application'\n"
                        + "android {\n"
                        + "    namespace \""
                        + HelloWorldApp.NAMESPACE
                        + "\"\n"
                        + "    compileSdkVersion "
                        + GradleTestProject.DEFAULT_COMPILE_SDK_VERSION
                        + "\n"
                        + "    buildToolsVersion '"
                        + GradleTestProject.DEFAULT_BUILD_TOOL_VERSION
                        + "'\n"
                        + "    useLibrary 'foo'\n"
                        + "}");

        ModelContainerV2 container = project.modelV2()
                .ignoreSyncIssues()
                .fetchModels()
                .getContainer();

        Collection<SyncIssue> syncIssues = container.getProject().getIssues().getSyncIssues();

        assertThat(syncIssues).hasSize(1);
        SyncIssue singleSyncIssue = syncIssues.iterator().next();
        assertThat(singleSyncIssue.getSeverity()).isEqualTo(SyncIssue.SEVERITY_ERROR);
        assertThat(singleSyncIssue.getType()).isEqualTo(SyncIssue.TYPE_OPTIONAL_LIB_NOT_FOUND);
        assertThat(singleSyncIssue.getData()).isEqualTo("foo");
    }

    @Test
    public void testUsingOptionalLibrary() throws IOException {
        TestFileUtils.appendToFile(
                project.getBuildFile(),
                "\n"
                        + "apply plugin: 'com.android.application'\n"
                        + "android {\n"
                        + "    namespace \""
                        + HelloWorldApp.NAMESPACE
                        + "\"\n"
                        + "    compileSdkVersion "
                        + GradleTestProject.DEFAULT_COMPILE_SDK_VERSION
                        + "\n"
                        + "    buildToolsVersion '"
                        + GradleTestProject.DEFAULT_BUILD_TOOL_VERSION
                        + "'\n"
                        + "    useLibrary 'org.apache.http.legacy'\n"
                        + "}");

        ModelContainerV2.ModelInfo model =
                project.modelV2().fetchModels().getContainer().getProject(null, ":");

        // get the SDK folder
        File sdkLocation = project.getAndroidSdkDir();
        FakeProgressIndicator progress = new FakeProgressIndicator();
        AndroidTargetManager targetMgr =
                AndroidSdkHandler.getInstance(
                                AndroidLocationsSingleton.INSTANCE, sdkLocation.toPath())
                        .getAndroidTargetManager(progress);
        IAndroidTarget target =
                targetMgr.getTargetFromHashString(GradleTestProject.getCompileSdkHash(), progress);

        File targetLocation = new File(target.getLocation());

        // the files that the bootclasspath should contain.
        File androidJar = new File(targetLocation, FN_FRAMEWORK_LIBRARY);
        File httpJar = new File(targetLocation, "optional/org.apache.http.legacy.jar");
        assertThat(model.getBasicAndroidProject().getBootClasspath())
                .containsExactly(androidJar.getAbsoluteFile(), httpJar.getAbsoluteFile());

        // for safety, let's make sure these files actually exists.
        assertThat(androidJar).isFile();
        assertThat(httpJar).isFile();
    }

    @Test
    public void testNotUsingOptionalLibrary() throws IOException {
        TestFileUtils.appendToFile(
                project.getBuildFile(),
                "\n"
                        + "apply plugin: 'com.android.application'\n"
                        + "android {\n"
                        + "    namespace \""
                        + HelloWorldApp.NAMESPACE
                        + "\"\n"
                        + "    compileSdkVersion "
                        + GradleTestProject.DEFAULT_COMPILE_SDK_VERSION
                        + "\n"
                        + "    buildToolsVersion '"
                        + GradleTestProject.DEFAULT_BUILD_TOOL_VERSION
                        + "'\n"
                        + "}");

        BasicAndroidProject model =
                project.modelV2().fetchModels().getContainer().getProject().getBasicAndroidProject();

        // get the SDK folder
        File sdkLocation = project.getAndroidSdkDir();
        FakeProgressIndicator progress = new FakeProgressIndicator();
        AndroidTargetManager targetMgr =
                AndroidSdkHandler.getInstance(
                                AndroidLocationsSingleton.INSTANCE, sdkLocation.toPath())
                        .getAndroidTargetManager(progress);
        IAndroidTarget target =
                targetMgr.getTargetFromHashString(GradleTestProject.getCompileSdkHash(), progress);

        File targetLocation = new File(target.getLocation());

        assertThat(model.getBootClasspath())
                .containsExactly(new File(targetLocation, FN_FRAMEWORK_LIBRARY).getAbsoluteFile());
    }
}
