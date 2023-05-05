/*
 * Copyright (C) 2023 The Android Open Source Project
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

import org.eclipse.aether.AbstractRepositoryListener;
import org.eclipse.aether.RepositoryEvent;

/**
 * Custom repository listener that tracks and records artifacts that were downloaded from remote
 * repositories.
 */
public class CustomRepositoryListener extends AbstractRepositoryListener {

    private final ArtifactOriginWriter artifactOriginWriter;

    CustomRepositoryListener(String repoPath) {
        this.artifactOriginWriter = new ArtifactOriginWriter(repoPath);
    }

    @Override
    public void artifactDownloaded(RepositoryEvent event) {
        recordEvent(event);
    }

    @Override
    public void artifactResolved(RepositoryEvent event) {
        // Empty. Use this to track artifacts resolved on the non-remote (local) file system.
    }

    @Override
    public void metadataDownloaded(RepositoryEvent event) {
        recordEvent(event);
    }

    @Override
    public void metadataResolved(RepositoryEvent event) {
        // Empty. Use this to track artifacts resolved on the non-remote (local) file system.
    }

    private void recordEvent(RepositoryEvent event) {
        artifactOriginWriter.write(event.getRepository(), event.getArtifact());
    }
}
