/*
 * Copyright (C) 2015 The Android Open Source Project
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

package com.android.build.gradle.internal.pipeline;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.build.api.transform.QualifiedContent;
import com.android.build.gradle.internal.component.VariantCreationConfig;
import com.android.build.gradle.internal.fixture.TestProjects;
import com.android.build.gradle.internal.fixtures.FakeProviderFactory;
import com.android.build.gradle.internal.fixtures.FakeSyncIssueReporter;
import com.android.build.gradle.internal.profile.AnalyticsService;
import com.android.build.gradle.internal.scope.ProjectInfo;
import com.android.build.gradle.internal.services.BuildServicesKt;
import com.android.build.gradle.internal.services.TaskCreationServices;
import com.android.build.gradle.internal.tasks.factory.GlobalTaskCreationConfig;
import com.android.build.gradle.internal.tasks.factory.TaskFactory;
import com.android.build.gradle.internal.tasks.factory.TaskFactoryImpl;
import com.android.build.gradle.internal.variant.VariantPathHelper;
import com.android.build.gradle.options.BooleanOption;
import com.android.build.gradle.options.ProjectOptions;
import com.android.builder.core.ComponentTypeImpl;
import com.android.builder.model.SyncIssue;
import com.android.builder.profile.NameAnonymizer;
import com.android.builder.profile.NameAnonymizerSerializer;
import com.android.utils.FileUtils;
import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.google.wireless.android.sdk.stats.GradleBuildProfile;
import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.security.CodeSource;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.file.FileCollection;
import org.gradle.testfixtures.ProjectBuilder;
import org.junit.Before;
import org.junit.Rule;
import org.junit.rules.TemporaryFolder;
import org.mockito.Mockito;

/**
 * Base class for Junit-4 based tests that need to manually instantiate tasks to test them.
 *
 * Right now this is limited to using the TransformManager but that could be refactored
 * to allow for other tasks using the AndroidTaskRegistry directly.
 */
public class TaskTestUtils {

    private final boolean allowIncremental;

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    protected static final String TASK_NAME = "task name";

    protected TaskFactory taskFactory;
    protected VariantCreationConfig creationConfig;
    protected FakeSyncIssueReporter issueReporter;

    protected Supplier<RuntimeException> syncIssueToException;
    protected Project project;

    public TaskTestUtils(boolean allowIncremental) {
        this.allowIncremental = allowIncremental;
    }

    @Before
    public void setUp() throws IOException {
        File projectDirectory = temporaryFolder.newFolder();
        FileUtils.mkdirs(projectDirectory);
        project = ProjectBuilder.builder().withProjectDir(projectDirectory).build();
        TestProjects.prepareProject(project, ImmutableMap.of());
        creationConfig = getCreationConfig();
        issueReporter = new FakeSyncIssueReporter();
        taskFactory = new TaskFactoryImpl(project.getTasks());
        syncIssueToException =
                () -> {
                    SyncIssue syncIssue = Iterables.getOnlyElement(issueReporter.getSyncIssues());
                    return new RuntimeException(
                            String.format(
                                    "Transform task creation failed.  Sync issue:\n %s",
                                    syncIssue.toString()));
                };

        project.getGradle()
                .getSharedServices()
                .registerIfAbsent(
                        BuildServicesKt.getBuildServiceName(AnalyticsService.class),
                        AnalyticsService.class,
                        it -> {
                            byte[] profile = GradleBuildProfile.newBuilder().build().toByteArray();
                            it.getParameters()
                                    .getProfile()
                                    .set(Base64.getEncoder().encodeToString(profile));
                            it.getParameters()
                                    .getAnonymizer()
                                    .set(
                                            new NameAnonymizerSerializer()
                                                    .toJson(new NameAnonymizer()));
                            it.getParameters().getProjects().set(new HashMap());
                            it.getParameters().getEnableProfileJson().set(true);
                            it.getParameters().getTaskMetadata().set(new HashMap());
                            it.getParameters().getRootProjectPath().set("/path");
                        });
    }

    @NonNull
    private VariantCreationConfig getCreationConfig() {
        GlobalTaskCreationConfig globalConfig = mock(GlobalTaskCreationConfig.class);
        TaskCreationServices taskCreationServices = mock(TaskCreationServices.class);
        ProjectInfo projectInfo = mock(ProjectInfo.class);
        when(taskCreationServices.getProjectInfo()).thenReturn(projectInfo);
        when(projectInfo.getBuildDir()).thenReturn(new File("build dir"));
        ImmutableMap<String, Boolean> properties =
                ImmutableMap.of(
                        BooleanOption.LEGACY_TRANSFORM_TASK_FORCE_NON_INCREMENTAL.getPropertyName(),
                        !allowIncremental);
        when(taskCreationServices.getProjectOptions())
                .thenReturn(
                        new ProjectOptions(
                                ImmutableMap.of(),
                                new FakeProviderFactory(
                                        FakeProviderFactory.getFactory(), properties)));

        VariantCreationConfig creationConfig = mock(VariantCreationConfig.class);
        when(creationConfig.getServices()).thenReturn(taskCreationServices);
        when(creationConfig.getGlobal()).thenReturn(globalConfig);
        when(creationConfig.getName()).thenReturn("theVariantName");
        when(creationConfig.getFlavorName()).thenReturn("theFlavorName");
        when(creationConfig.getBuildType()).thenReturn("debug");
        when(creationConfig.getComponentType()).thenReturn(ComponentTypeImpl.BASE_APK);

        when(creationConfig.computeTaskName(Mockito.anyString(), Mockito.eq("")))
                .thenReturn(TASK_NAME);

        VariantPathHelper paths = mock(VariantPathHelper.class);
        when(creationConfig.getPaths()).thenReturn(paths);
        when(creationConfig.getDirName()).thenReturn("config dir name");
        when(creationConfig.getComponentType()).thenReturn(ComponentTypeImpl.BASE_APK);
        when(creationConfig.getDebuggable()).thenReturn(true);
        return creationConfig;
    }

    /**
     * Returns the root dir for the gradle plugin project
     */
    private File getRootDir() {
        CodeSource source = getClass().getProtectionDomain().getCodeSource();
        if (source != null) {
            URL location = source.getLocation();
            try {
                File dir = new File(location.toURI());
                assertTrue(dir.getPath(), dir.exists());

                File f= dir.getParentFile().getParentFile().getParentFile().getParentFile().getParentFile().getParentFile().getParentFile();
                return  new File(
                        f,
                        Joiner.on(File.separator).join(
                                "tools",
                                "base",
                                "build-system",
                                "integration-test"));
            } catch (URISyntaxException e) {
                throw new AssertionError(e);
            }
        }

        fail("Fail to get the tools/build folder");
        return null;
    }
}
