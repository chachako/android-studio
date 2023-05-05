/*
 * Copyright (C) 2022 The Android Open Source Project
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

package com.android.tools.maven;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Reader;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.repository.ArtifactRepository;
import org.eclipse.aether.repository.RemoteRepository;

/** Utility for recording the origin information of downloaded artifacts. */
public class ArtifactOriginWriter {

    private final Path repoPath;

    // Maps a file path (relative to the root of the local maven repository
    // it was downloaded to) to the URL of the repository that it
    // was downloaded from.
    private final Map<String, String> fileToRepo = new HashMap<>();

    public ArtifactOriginWriter(String repoPath) {
        this.repoPath = Paths.get(repoPath).toAbsolutePath();
    }

    public void write(ArtifactRepository repo, Artifact artifact) {
        if (repo instanceof RemoteRepository) {
            write((RemoteRepository) repo, artifact);
        } // else: Ignore local repositories.
    }

    private static class ArtifactOriginInfo {
        public ArtifactOriginInfo(String file, String repo, String artifact) {
            this.file = file;
            this.repo = repo;
            this.artifact = artifact;
        }

        private String file;
        private String repo;
        private String artifact;
    }

    private static class OriginInfoFile {
        public List<ArtifactOriginInfo> artifacts = new ArrayList<>();
    }

    private void write(RemoteRepository repo, Artifact artifact) {
        Path fileAbsolutePath = Paths.get(artifact.getFile().getAbsolutePath());
        String fileRelativePath = repoPath.relativize(fileAbsolutePath).toString();

        String res = fileToRepo.get(fileRelativePath);
        if (res != null) {
            if (!res.equals(repo.getUrl())) {
                throw new RuntimeException(
                        "Repository mismatch for file: "
                                + artifact.getFile()
                                + "\nExisting repo = "
                                + res
                                + "\nNew repo = "
                                + repo.getUrl());
            } else {
                return; // No need to write it again.
            }
        }
        String repoUrl = repo.getUrl();
        fileToRepo.put(fileRelativePath, repoUrl);
        if (repoUrl.startsWith("file://")) {
            // Use the name of the repository, if it's a local repo. This makes it more robust (no
            // need
            // to worry about the variable Bazel paths, or username in absolute paths).
            repoUrl = repo.getId();
        }

        Path originFilePath = fileAbsolutePath.getParent().resolve("origin.json");

        // Do not allow simultaneous writes to the file by this tool.
        synchronized (this) {
            Gson gson = new GsonBuilder().setPrettyPrinting().create();
            OriginInfoFile originInfoFile;
            if (originFilePath.toFile().exists()) {
                try (Reader reader = new FileReader(originFilePath.toFile())) {
                    originInfoFile = gson.fromJson(reader, OriginInfoFile.class);
                } catch (IOException e) {
                    throw new RuntimeException("Failed to read JSON file at: " + originFilePath);
                }
            } else {
                originInfoFile = new OriginInfoFile();
            }

            originInfoFile.artifacts.add(
                    new ArtifactOriginInfo(fileRelativePath, repoUrl, artifact.toString()));

            try (FileWriter writer = new FileWriter(originFilePath.toFile())) {
                gson.toJson(originInfoFile, writer);
            } catch (IOException e) {
                throw new RuntimeException("Failed to write to JSON file at: " + originFilePath);
            }
        }
    }
}
