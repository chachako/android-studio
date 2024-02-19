package com.android.build.gradle.integration.testing;

import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import com.android.build.gradle.integration.common.fixture.TestVersions;
import com.android.build.gradle.integration.common.utils.TestFileUtils;
import java.io.File;
import java.io.IOException;

import com.android.build.gradle.options.BooleanOption;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;

public class SeparateTestModuleWithAppDependenciesTest {

    @ClassRule
    public static GradleTestProject project =
            GradleTestProject.builder().fromTestProject("separateTestModule")
                    // Enforcing unique package names to prevent regressions. Remove when b/116109681 fixed.
                    .addGradleProperties(
                            BooleanOption.ENFORCE_UNIQUE_PACKAGE_NAMES.getPropertyName() + "=true")
                    .addGradleProperties(BooleanOption.USE_ANDROID_X.getPropertyName() + "=true")
                    .create();

    @BeforeClass
    public static void setup() throws IOException, InterruptedException {
        TestFileUtils.appendToFile(
                project.getSubproject("app").getBuildFile(),
                "\n"
                        + "android {\n"
                        + "    defaultConfig {\n"
                        + "        minSdkVersion \n"
                        + TestVersions.SUPPORT_LIB_MIN_SDK
                        + "    }\n"
                        + "}\n"
                        + "dependencies {\n"
                        + "    api 'com.google.android.gms:play-services-base:"
                        + TestVersions.PLAY_SERVICES_VERSION
                        + "'\n"
                        + "    api 'androidx.appcompat:appcompat:1.6.1'\n"
                        + "}\n");

        File srcDir = project.getSubproject("app").getMainSrcDir();
        srcDir = new File(srcDir, "foo");
        srcDir.mkdirs();
        TestFileUtils.appendToFile(
                new File(srcDir, "FooActivity.java"),
                "\n"
                        + "package foo;\n"
                        + "\n"
                        + "import android.os.Bundle;\n"
                        + "import androidx.appcompat.app.AppCompatActivity;\n"
                        + "import android.view.View;\n"
                        + "import android.widget.TextView;\n"
                        + "\n"
                        + "public class FooActivity extends AppCompatActivity {\n"
                        + "\n"
                        + "    @Override\n"
                        + "    protected void onCreate(Bundle savedInstanceState) {\n"
                        + "        super.onCreate(savedInstanceState);\n"
                        + "    }\n"
                        + "}\n");

        TestFileUtils.appendToFile(
                project.getSubproject("test").getBuildFile(),
                "\n"
                        + "dependencies {\n"
                        + "    implementation 'androidx.test:rules:1.3.0'\n"
                        + "    implementation 'androidx.annotation:annotation:1.6.0'\n"
                        + "}\n");

        srcDir = project.getSubproject("test").getMainSrcDir();
        srcDir = new File(srcDir, "foo");
        srcDir.mkdirs();
        TestFileUtils.appendToFile(
                new File(srcDir, "FooActivityTest.java"),
                "\n"
                        + "package foo;\n"
                        + "\n"
                        + "public class FooActivityTest {\n"
                        + "    @org.junit.Rule \n"
                        + "    androidx.test.rule.ActivityTestRule<foo.FooActivity> activityTestRule =\n"
                        + "            new androidx.test.rule.ActivityTestRule<>(foo.FooActivity.class);\n"
                        + "}\n");

        project.execute("clean", "test:assembleDebug");
    }

    @AfterClass
    public static void cleanUp() {
        project = null;
    }

    @Test
    public void checkModel() throws Exception {
        // check the content of the test model.
    }
}
