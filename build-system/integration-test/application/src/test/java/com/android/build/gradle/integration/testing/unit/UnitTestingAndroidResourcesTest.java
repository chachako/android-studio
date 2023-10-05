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

package com.android.build.gradle.integration.testing.unit;

import static com.android.build.gradle.integration.common.truth.GradleTaskSubject.assertThat;
import static com.android.testutils.truth.PathSubject.assertThat;
import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertNotNull;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.build.gradle.integration.common.fixture.GradleBuildResult;
import com.android.build.gradle.integration.common.fixture.GradleTaskExecutor;
import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import com.android.build.gradle.integration.common.utils.AndroidProjectUtilsV2;
import com.android.build.gradle.integration.common.utils.TestFileUtils;
import com.android.builder.model.v2.ide.AndroidArtifact;
import com.android.builder.model.v2.ide.JavaArtifact;
import com.android.builder.model.v2.ide.Variant;
import com.android.builder.model.v2.models.AndroidProject;
import com.android.tools.build.apkzlib.utils.IOExceptionFunction;
import com.android.utils.SdkUtils;
import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import java.io.File;
import java.io.IOException;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;
import java.util.stream.Collectors;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

/** Checks that the test_config.properties object is generated correctly. */
@RunWith(Parameterized.class)
public class UnitTestingAndroidResourcesTest {

    public static final String SDK_VERSION = "7.0.0_r1-robolectric-r1-i4";
    public static final String PLATFORM_JAR_NAME =
            String.format("android-all-instrumented-%s.jar", SDK_VERSION);
    public static final String PLATFORM_JAR_RELATIVE_PATH =
            String.format(
                    "org/robolectric/android-all-instrumented/%s/%s",
                    SDK_VERSION, PLATFORM_JAR_NAME);

    enum Plugin {
        LIBRARY,
        APPLICATION
    }

    @Rule
    public GradleTestProject project =
            GradleTestProject.builder().fromTestProject("unitTestingAndroidResources").create();

    @Parameterized.Parameters(name = "plugin={0}")
    public static Object[][] data() {
        return new Object[][] {
            {Plugin.APPLICATION}, {Plugin.LIBRARY},
        };
    }

    @Parameterized.Parameter public Plugin plugin;

    @Before
    public void changePlugin() throws Exception {
        if (plugin == Plugin.LIBRARY) {
            TestFileUtils.searchAndReplace(
                    project.getBuildFile(), "com.android.application", "com.android.library");
        }
    }

    /**
     * Copies the Robolectric platform jar into the project directory, so we can run a fully offline
     * build.
     */
    @Before
    public void copyPlatformJar() throws Exception {
        boolean found = false;
        for (Path path : GradleTestProject.getLocalRepositories()) {
            Path platformJar = path.resolve(PLATFORM_JAR_RELATIVE_PATH);
            if (Files.exists(platformJar)) {
                found = true;
                Path robolectricLibs = project.file("robolectric-libs").toPath();
                Files.createDirectory(robolectricLibs);
                Files.copy(platformJar, robolectricLibs.resolve(PLATFORM_JAR_NAME));
                break;
            }
        }

        if (!found) {
            Assert.fail(
                    String.format(
                            "Failed to find Robolectric platform jar %s in prebuilts.",
                            PLATFORM_JAR_RELATIVE_PATH));
        }
    }

    @Test
    public void runUnitTests() throws Exception {
        GradleTaskExecutor runGradleTasks = project.executor();

        runGradleTasks.run("testDebugUnitTest");

        Files.write(project.file("src/main/assets/foo.txt").toPath(), "CHANGE".getBytes());
        GradleBuildResult result = runGradleTasks.run("testDebugUnitTest");

        assertThat(result.getTask(":testDebugUnitTest")).didWork();

        // Sanity check: make sure we're actually executing Robolectric code.
        File xmlResults =
                project.file(
                        "build/test-results/testDebugUnitTest/"
                                + "TEST-com.android.tests.WelcomeActivityTest.xml");
        assertThat(xmlResults).isFile();

        runGradleTasks.run("clean");

        // Check that the model contains the generated file
        AndroidProject androidProject =
                project.modelV2().fetchModels().getContainer().getProject(":").getAndroidProject();

        Variant debug = AndroidProjectUtilsV2.getVariantByName(androidProject, "debug");
        JavaArtifact debugUnitTest = debug.getUnitTestArtifact();

        ImmutableList.Builder<String> commands = ImmutableList.builder();

        commands.add(":" + debug.getMainArtifact().getSourceGenTaskName());

        for (String taskName : debugUnitTest.getIdeSetupTaskNames()) {
            commands.add(":" + taskName);
        }
        commands.add(debugUnitTest.getCompileTaskName());
        runGradleTasks.run(commands.build());

        Path configFile = getConfigFile(debugUnitTest.getClassesFolders());
        assertNotNull(configFile);
        Properties properties = new Properties();
        try (Reader reader = Files.newBufferedReader(configFile)) {
            properties.load(reader);
        }
        properties.forEach(
                (name, value) -> {
                    if (name.equals("android_custom_package")) {
                        try (URLClassLoader cl =
                                makeClassloader(debug.getMainArtifact(), debugUnitTest)) {
                            try {
                                cl.loadClass(value + ".R");
                            } catch (ClassNotFoundException e) {
                                throw new AssertionError(
                                        "expected R class at "
                                                + value
                                                + ".R, with classpath \n    -"
                                                + Arrays.stream(cl.getURLs())
                                                        .map(Object::toString)
                                                        .collect(Collectors.joining("\n    - ")),
                                        e);
                            }
                        } catch (IOException e) {
                            throw new UncheckedIOException(e);
                        }
                    } else {
                        assertThat(project.file(value.toString())).exists();
                    }
                });

        assertThat(properties.getProperty("android_resource_apk")).isNotNull();

        File manifestFile = project.file(properties.getProperty("android_merged_manifest"));
        String manifestContent = Joiner.on("\n").join(Files.readAllLines(manifestFile.toPath()));
        assertThat(manifestContent).contains("android:targetSdkVersion=");

        // Check the tests see the assets from dependencies, even in the library case where they
        // would not otherwise be merged.
        List<String> filenames =
                Files.walk(project.file(properties.getProperty("android_merged_assets")).toPath())
                        .filter(Files::isRegularFile)
                        .map(path -> path.getFileName().toString())
                        .collect(Collectors.toList());

        assertThat(filenames)
                .containsExactly("foo.txt", "bar.txt", "test-asset.txt", "test-asset2.txt");
    }

    @Nullable
    private static Path getConfigFile(@NonNull Iterable<File> directories) {
        for (File dir : directories) {
            Path candidateConfigFile =
                    dir.toPath()
                            .resolve("com")
                            .resolve("android")
                            .resolve("tools")
                            .resolve("test_config.properties");
            if (Files.isRegularFile(candidateConfigFile)) {
                return candidateConfigFile;
            }
        }
        return null;
    }

    private static URLClassLoader makeClassloader(
            @NonNull AndroidArtifact main, @NonNull JavaArtifact test) {
        ImmutableList.Builder<File> files = ImmutableList.builder();
        files.addAll(main.getClassesFolders());
        files.addAll(test.getClassesFolders());
        return new URLClassLoader(
                files.build()
                        .stream()
                        .map(IOExceptionFunction.asFunction(SdkUtils::fileToUrl))
                        .toArray(URL[]::new));
    }
}
