/*
 * Copyright (C) 2019 The Android Open Source Project
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

package com.android.build.gradle.internal;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.android.annotations.NonNull;
import com.android.build.gradle.internal.fixtures.FakeFileCollection;
import com.android.build.gradle.internal.fixtures.FakeGradleProvider;
import com.android.build.gradle.internal.fixtures.FakeObjectFactory;
import com.android.builder.core.BuilderConstants;
import com.android.ide.common.rendering.api.ResourceNamespace;
import com.android.ide.common.resources.ResourceSet;
import com.google.common.collect.Lists;
import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.gradle.api.Project;
import org.gradle.api.artifacts.ArtifactCollection;
import org.gradle.api.artifacts.component.ComponentArtifactIdentifier;
import org.gradle.api.artifacts.component.ProjectComponentIdentifier;
import org.gradle.api.artifacts.result.ResolvedArtifactResult;
import org.gradle.api.file.Directory;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.Provider;
import org.gradle.testfixtures.ProjectBuilder;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.mockito.Mockito;

public class DependencyResourcesComputerTest {

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();
    private DependencyResourcesComputer computer;
    private List<ResourceSet> folderSets;
    Provider<Directory> defaultEmptyProvider = new FakeGradleProvider<>(null);


    @Before
    public void setUp() throws IOException {
        ObjectFactory factory = FakeObjectFactory.getFactory();
        computer = factory.newInstance(DependencyResourcesComputer.class);
        folderSets = Lists.newArrayList();
        computer.getValidateEnabled().set(false);
    }

    @After
    public void tearDown() {
        computer = null;
        folderSets = null;
    }

    @Test
    public void singleSetWithSingleFile() throws Exception {
        File file = temporaryFolder.newFolder("src", "main");
        ResourceSet mainSet =
                createResourceSet(folderSets, BuilderConstants.MAIN, file);

        assertThat(computer.compute(null, defaultEmptyProvider)).containsExactly(mainSet);
    }

    @Test
    public void singleSetWithMultiFiles() throws Exception {
        File file = temporaryFolder.newFolder("src", "main");
        File file2 = temporaryFolder.newFolder("src", "main2");
        ResourceSet mainSet =
                createResourceSet(folderSets, BuilderConstants.MAIN, file, file2);

        assertThat(computer.compute(null, defaultEmptyProvider)).containsExactly(mainSet);
    }

    @Test
    public void twoSetsWithSingleFile() throws Exception {
        File file = temporaryFolder.newFolder("src", "main");
        ResourceSet mainSet =
                createResourceSet(folderSets, BuilderConstants.MAIN, file);

        File file2 = temporaryFolder.newFolder("src", "debug");
        ResourceSet debugSet = createResourceSet(folderSets, "debug", file2);

        assertThat(computer.compute(null, defaultEmptyProvider)).containsExactly(mainSet, debugSet);
    }

    @Test
    public void singleSetWithDependency() throws Exception {
        File file = temporaryFolder.newFolder("src", "main");
        ResourceSet mainSet =
                createResourceSet(folderSets, BuilderConstants.MAIN, file);

        File file2 = temporaryFolder.newFolder("foo", "bar", "1.0");
        List<ResourceSet> librarySets = setupLibraryDependencies(file2, ":path");

        assertThat(computer.getLibraries().get().getArtifactFiles()).containsExactly(file2);

        List<ResourceSet> computedSets = computer.compute(null, defaultEmptyProvider);
        assertThat(computedSets).containsExactly(librarySets.get(0), mainSet).inOrder();
    }

    @Test
    public void singleSetWithRenderscript() throws Exception {
        File file = temporaryFolder.newFolder("src", "main");
        ResourceSet mainSet =
                createResourceSet(folderSets, BuilderConstants.MAIN, file);
        ResourceSet generatedSet = createResourceSet(folderSets, BuilderConstants.GENERATED);

        File rsFile = temporaryFolder.newFolder("rs");
        Directory rsFileDirectory = Mockito.mock(Directory.class);
        Mockito.when(rsFileDirectory.getAsFile()).thenReturn(rsFile);
        Provider<Directory> provider = new FakeGradleProvider<>(rsFileDirectory);

        generatedSet.addSource(rsFile);

        assertThat(computer.compute(null, provider)).containsExactly(mainSet, generatedSet);
        // rs file should have been added to the main resource sets.
        assertThat(generatedSet.getSourceFiles()).containsExactly(rsFile);
    }

    @Test
    public void singleSetWithGeneratedRes() throws Exception {
        File file = temporaryFolder.newFolder("src", "main");
        ResourceSet mainSet =
                createResourceSet(folderSets, BuilderConstants.MAIN, file);
        ResourceSet generatedSet = createResourceSet(folderSets, BuilderConstants.GENERATED);

        File genFile = temporaryFolder.newFolder("generated");
        computer.getGeneratedResOutputDir().from(genFile);
        generatedSet.addSource(genFile);

        assertThat(computer.compute(null, defaultEmptyProvider))
                .containsExactly(mainSet, generatedSet);
        assertThat(generatedSet.getSourceFiles()).containsExactly(genFile);
    }

    @Test
    public void singleSetWithMicroApkRes() throws Exception {
        File file = temporaryFolder.newFolder("src", "main");
        ResourceSet mainSet =
                createResourceSet(folderSets, BuilderConstants.MAIN, file);
        ResourceSet generatedSet = createResourceSet(folderSets, BuilderConstants.GENERATED);

        File microFile = temporaryFolder.newFolder("micro");
        computer.getMicroApkResDirectory().from(microFile);
        generatedSet.addSource(microFile);

        assertThat(computer.compute(null, defaultEmptyProvider))
                .containsExactly(mainSet, generatedSet);
        assertThat(generatedSet.getSourceFiles()).containsExactly(microFile);
    }

    @Test
    public void singleSetWithExtraRes() throws Exception {
        File file = temporaryFolder.newFolder("src", "main");
        ResourceSet mainSet =
                createResourceSet(folderSets, BuilderConstants.MAIN, file);
        ResourceSet generatedSet = createResourceSet(folderSets, BuilderConstants.GENERATED);

        File extraFile = temporaryFolder.newFolder("extra");
        computer.getExtraGeneratedResFolders().from(extraFile);
        generatedSet.addSource(extraFile);

        assertThat(computer.compute(null, defaultEmptyProvider))
                .containsExactly(mainSet, generatedSet);
        assertThat(generatedSet.getSourceFiles()).containsExactly(extraFile);
    }

    @Test
    public void generatedResourceSetMissing() throws Exception {
        File file = temporaryFolder.newFolder("src", "main");
        ResourceSet mainSet = createResourceSet(folderSets, BuilderConstants.MAIN, file);

        File extraFile = temporaryFolder.newFolder("extra");
        computer.getExtraGeneratedResFolders().from(extraFile);

        RuntimeException ex =
                assertThrows(
                        RuntimeException.class, () -> computer.compute(null, defaultEmptyProvider));

        assertThat(ex).hasMessageThat().contains("Generated resource set does not exist");
    }

    @Test
    public void everything() throws Exception {
        File file = temporaryFolder.newFolder("src", "main");
        File file2 = temporaryFolder.newFolder("src", "main2");
        ResourceSet mainSet =
                createResourceSet(folderSets, BuilderConstants.MAIN, file, file2);

        File debugFile = temporaryFolder.newFolder("src", "debug");
        ResourceSet debugSet = createResourceSet(folderSets,  "debug", debugFile);

        File libFile = temporaryFolder.newFolder("foo", "bar", "1.0");
        File libFile2 = temporaryFolder.newFolder("foo", "bar", "2.0");

        // the order returned by the dependency is meant to be in the wrong order (consumer first,
        // when we want dependent first for the merger), so the order in the res set should be
        // the opposite order.
        List<ResourceSet> librarySets = setupLibraryDependencies(
                libFile, ":path1",
                libFile2, ":path2");
        ResourceSet librarySet = librarySets.get(0);
        ResourceSet librarySet2 = librarySets.get(1);

        ResourceSet generatedSet = createResourceSet(folderSets, BuilderConstants.GENERATED);
        // Note: the order of files are added to mainSet matters.
        File rsFile = temporaryFolder.newFolder("rs");
        Directory rsFileDirectory = Mockito.mock(Directory.class);
        Mockito.when(rsFileDirectory.getAsFile()).thenReturn(rsFile);
        Provider<Directory> renderscriptResProvider = new FakeGradleProvider<>(rsFileDirectory);
        generatedSet.addSource(rsFile);

        File genFile = temporaryFolder.newFolder("generated");
        computer.getGeneratedResOutputDir().from(genFile);
        generatedSet.addSource(genFile);

        File extraFile = temporaryFolder.newFolder("extra");
        computer.getExtraGeneratedResFolders().from(extraFile);
        generatedSet.addSource(extraFile);

        File microFile = temporaryFolder.newFolder("micro");
        computer.getMicroApkResDirectory().from(microFile);
        generatedSet.addSource(microFile);

        assertThat(computer.getLibraries().get().getArtifactFiles())
                .containsExactly(libFile, libFile2);
        assertThat(computer.compute(null, renderscriptResProvider))
                .containsExactly(librarySet2, librarySet, mainSet, debugSet, generatedSet)
                .inOrder();
        assertThat(generatedSet.getSourceFiles())
                .containsExactly(rsFile, genFile, extraFile, microFile);
        assertThat(computer.getLibraries().get().getArtifactFiles())
                .containsExactly(libFile, libFile2);
    }

    @NonNull
    private ResourceSet createResourceSet(List<ResourceSet> folderSets, String name, File... files)
            throws IOException {
        File testDir = temporaryFolder.newFolder();
        Project project = ProjectBuilder.builder().withProjectDir(testDir).build();

        ResourceSet mainSet = new ResourceSet(name, ResourceNamespace.RES_AUTO, null, false, null);
        List<Directory> directories =
                Arrays.stream(files)
                        .map(
                                file ->
                                        project.getLayout()
                                                .getProjectDirectory()
                                                .dir(file.getAbsolutePath()))
                        .collect(Collectors.toList());
        Provider<Collection<Directory>> artifact = project.provider(() -> directories);
        Map<String, Provider<Collection<Directory>>> artifactMap = new LinkedHashMap<>();
        artifactMap.put(name, artifact);
        mainSet.addSources(Arrays.asList(files));
        folderSets.add(mainSet);
        computer.addResourceSets(
                artifactMap,
                () ->
                        FakeObjectFactory.getFactory()
                                .newInstance(
                                        DependencyResourcesComputer.ResourceSourceSetInput.class));
        return mainSet;
    }

    @NonNull
    private List<ResourceSet> setupLibraryDependencies(Object... objects) {
        ArtifactCollection libraries = mock(ArtifactCollection.class);

        Set<ResolvedArtifactResult> artifacts = new LinkedHashSet<>();
        Set<File> files = new HashSet<>();
        List<ResourceSet> resourceSets = Lists.newArrayListWithCapacity(objects.length/2);

        for (int i = 0, count = objects.length; i < count ; i+=2) {
            assertThat(objects[i]).isInstanceOf(File.class);
            assertThat(objects[i+1]).isInstanceOf(String.class);

            File file = (File) objects[i];
            String path = (String) objects[i+1];

            files.add(file);

            ResolvedArtifactResult artifact = mock(ResolvedArtifactResult.class);
            artifacts.add(artifact);

            ComponentArtifactIdentifier artifactId = mock(ComponentArtifactIdentifier.class);
            ProjectComponentIdentifier id = mock(ProjectComponentIdentifier.class);

            when(id.getBuildTreePath()).thenReturn(path);
            when(artifactId.getComponentIdentifier()).thenReturn(id);
            when(artifact.getFile()).thenReturn(file);
            when(artifact.getId()).thenReturn(artifactId);

            // create a resource set that must match the one returned by the computation
            ResourceSet set = new ResourceSet(path, ResourceNamespace.RES_AUTO, null, false, null);
            set.addSource(file);
            set.setFromDependency(true);
            resourceSets.add(set);
        }

        when(libraries.getArtifacts()).thenReturn(artifacts);
        when(libraries.getArtifactFiles()).thenReturn(new FakeFileCollection(files));

        computer.getLibraries().set(libraries);

        return resourceSets;
    }
}
