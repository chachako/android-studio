/*
 * Copyright (C) 2017 The Android Open Source Project
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

package com.android.build.gradle.integration.connected.testing;

import com.android.build.gradle.integration.common.fixture.GradleTaskExecutor;
import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import com.android.build.gradle.integration.common.utils.TestFileUtils;
import com.android.build.gradle.integration.connected.utils.EmulatorUtils;
import com.android.build.gradle.options.BooleanOption;

import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExternalResource;

import java.io.IOException;

public class SeparateTestModuleConnectedTest {

    @Rule
    public GradleTestProject project =
            GradleTestProject.builder()
                    .fromTestProject("separateTestModule")
                    // Enforcing unique package names to prevent regressions. Remove when b/116109681 fixed.
                    .addGradleProperties(BooleanOption.ENFORCE_UNIQUE_PACKAGE_NAMES.getPropertyName()
                            + "=true")
                    .addGradleProperties(BooleanOption.USE_ANDROID_X.getPropertyName() + "=true")
                    .create();

    @ClassRule public static final ExternalResource EMULATOR = EmulatorUtils.getEmulator();

    @Before
    public void setUp() throws IOException, InterruptedException {
        TestFileUtils.appendToFile(
                project.getSubproject("test").getBuildFile(),
                "\n"
                    + "android {\n"
                    + "  defaultConfig {\n"
                    + "    testInstrumentationRunner 'androidx.test.runner.AndroidJUnitRunner'\n"
                    + "  }\n"
                    + "  dependencies {\n"
                    + "    implementation ('androidx.test:runner:1.4.0-alpha06', {\n"
                    + "      exclude group: 'com.android.support', module: 'support-annotations'\n"
                    + "    })\n"
                    + "  }\n"
                    + "}\n");
        // fail fast if no response
        project.addAdbTimeout();
        // run the uninstall tasks in order to (1) make sure nothing is installed at the beginning
        // of each test and (2) check the adb connection before taking the time to build anything.
        executor().run("uninstallAll");
    }

    @Test
    public void checkWillRunWithoutInstrumentationInManifest() throws Exception {
        executor().run(":test:deviceCheck");
    }

    @Test
    public void checkConnectedCheckCompletesNormally() throws Exception {
        executor().run(":test:connectedCheck");
    }

    public GradleTaskExecutor executor() {
        return project.executor();
    }
}
