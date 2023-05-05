/*
 * Copyright (C) 2021 The Android Open Source Project
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

package com.android.tools.binaries;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.android.testutils.TestUtils;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class LocalMavenRepositoryGeneratorTest {

    @Rule public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void testGenerator() throws Exception {
        // All aritfacts will be downloaded to a local repository in this temporary folder.
        Path localRepoPath = temporaryFolder.newFolder("fake_local_repository").toPath();

        // A local directory will be used as if it's a remote repository.
        String fakeRemoteRepoName = "Fake Remote Repository";
        Path fakeRemoteRepoPath =
                Paths.get("tools/base/bazel/test/local_maven_repository_generator/fake_repository")
                        .toAbsolutePath();
        List<String> coords = Arrays.asList(
            "com.google.example:a:1",
            "com.google.example:b:1",
            "com.google.example:h:pom:1",
            "com.google.example:j:jar:linux:1",
            "com.google.example:k:1",
            "com.google.example:l:1",
            "com.google.example:p:1",
            "com.google.example:u:1",
            "com.google.example:x:1"
        );
        List<String> data = Arrays.asList("com.google.example:a:2");
        String outputBuildFile = "generated.BUILD";
        LocalMavenRepositoryGenerator generator =
                new LocalMavenRepositoryGenerator(
                        localRepoPath,
                        outputBuildFile,
                        coords,
                        data,
                        true,
                        Map.of(fakeRemoteRepoName, "file://" + fakeRemoteRepoPath),
                        false);
        generator.run();

        // Verify the BUILD file.
        Path goldenBuild = fakeRemoteRepoPath.resolveSibling("BUILD.golden");
        Path generatedBuild = Paths.get(outputBuildFile);

        assertTrue(generatedBuild.toFile().exists());
        String goldenBuildContents = Files.readString(goldenBuild);
        String generatedBuildContents = Files.readString(generatedBuild);
        if (!goldenBuildContents.equals(generatedBuildContents)) {
            System.err.println("=== Start generated BUILD file contents ===");
            System.err.println(generatedBuildContents);
            System.err.println("=== End generated BUILD file contents ===");
            Files.copy(generatedBuild, TestUtils.getTestOutputDir().resolve(outputBuildFile));
        }
        assertEquals("The BUILD files differ!", goldenBuildContents, generatedBuildContents);

        // Verify that every downloaded pom file has an origin.json file next to it.
        // We ignore non-pom files, as we assume it's not possible to download a Maven
        // artifact without the pom file.
        try (Stream<Path> stream = Files.walk(localRepoPath)) {
            stream.forEach(
                    path -> {
                        if (Files.isRegularFile(path) && path.toString().endsWith(".pom")) {
                            Path originFile = path.resolveSibling("origin.json");
                            assertTrue(
                                    "File not found: " + originFile, originFile.toFile().exists());
                        }
                    });
        }
    }
}
