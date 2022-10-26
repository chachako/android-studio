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

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.apache.maven.repository.internal.DefaultVersionRangeResolver;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.resolution.VersionRangeRequest;
import org.eclipse.aether.resolution.VersionRangeResolutionException;
import org.eclipse.aether.resolution.VersionRangeResult;
import org.eclipse.aether.util.version.GenericVersionScheme;
import org.eclipse.aether.version.InvalidVersionSpecificationException;
import org.eclipse.aether.version.Version;
import org.eclipse.aether.version.VersionConstraint;
import org.eclipse.aether.version.VersionRange;
import org.eclipse.aether.version.VersionScheme;

/**
 * Resolves maven version ranges (e.g., [15.0, 19.0)) into actual versions (e.g., listOf(15.0, 16.0,
 * 17.0)).
 *
 * <p>The default version range resolver requires the presence of a maven-metadata.xml file to
 * identify which versions within the given range actually exist in the repository. However, our
 * local maven repository does not have maven-metadata.xml files. This custom implementation uses
 * the filesystem to load all the available versions.
 */
public class CustomVersionRangeResolver extends DefaultVersionRangeResolver {
    private final boolean verbose;

    public CustomVersionRangeResolver(boolean verbose) {
        this.verbose = verbose;
    }

    @Override
    public VersionRangeResult resolveVersionRange(
            RepositorySystemSession session, VersionRangeRequest request)
            throws VersionRangeResolutionException {
        // Taken from DefaultVersionRangeResolver.
        VersionRangeResult result = new VersionRangeResult(request);
        VersionScheme versionScheme = new GenericVersionScheme();
        VersionConstraint versionConstraint;
        try {
            versionConstraint =
                    versionScheme.parseVersionConstraint(request.getArtifact().getVersion());
        } catch (InvalidVersionSpecificationException e) {
            result.addException(e);
            throw new VersionRangeResolutionException(result);
        }

        if (versionConstraint.getRange() != null) {
            // When there is a range, the DefaultVersionRangeResolver refers to a
            // maven-metadata.xml file to identify which versions exist. We don't have such
            // manifest files, so we need a workaround.

            VersionRange.Bound lower = versionConstraint.getRange().getLowerBound();
            VersionRange.Bound upper = versionConstraint.getRange().getUpperBound();
            if (verbose) {
                String lowerRange = lower.isInclusive() ? "[" : "(" + lower.getVersion().toString();
                String upperRange =
                        upper.getVersion().toString() + (upper.isInclusive() ? "]" : ")");
                System.out.printf(
                        "Resolving ranged artifact: %s ; ranges = %s, %s\n",
                        request.getArtifact(), lowerRange, upperRange);
            }

            // When version is [v], the constraint is parsed to be the range [v,v]. It's
            // easy to identify this, and return a single constraint.
            // This is the behavior documented in VersionRangeResolver interface, but
            // not respected by the DefaultVersionRangeResolver.
            // For instance, this block enables the following dependency to be properly
            // resolved:
            //     androidx.navigation:navigation-safe-args-gradle-plugin:jar:2.3.1 ->
            //         androidx.navigation:navigation-safe-args-generator:jar:[2.3.1]
            if (lower.getVersion() == upper.getVersion()) {
                result.setVersionConstraint(versionConstraint);
                result.addVersion(versionConstraint.getRange().getLowerBound().getVersion());
                return result;
            }
            // Add support to resolve only if pointing locally
            for (RemoteRepository repository : request.getRepositories()) {
                if (verbose) {
                    System.out.println("Checking repository: " + repository.getUrl());
                }
                if (!repository.getUrl().startsWith("file://")) {
                    // Ignore non-local repositories.
                    continue;
                }

                // Windows has trouble with "file://C:\\users\\..." style File paths.
                File repoPath = new File(repository.getUrl().substring("file://".length()));
                Artifact artifact = request.getArtifact();
                String path =
                        session.getLocalRepositoryManager()
                                .getPathForRemoteArtifact(artifact, repository, "");
                File artifactPath = new File(repoPath, path).getParentFile().getParentFile();
                File[] files = artifactPath.listFiles();
                List<Version> versions = new ArrayList<>();
                if (files != null) {
                    if (verbose) {
                        System.out.println(
                                "Found version files for artifact: " + Arrays.toString(files));
                    }
                    for (File version : files) {
                        if (!version.isDirectory()) {
                            continue;
                        }
                        try {
                            Version parsedVersion = versionScheme.parseVersion(version.getName());
                            if (versionConstraint.containsVersion(parsedVersion)) {
                                versions.add(parsedVersion);
                            }
                        } catch (InvalidVersionSpecificationException e) {
                            // Ignore invalid versions.
                            continue;
                        }
                    }
                    // Sort and traverse in descending order. This is used to deterministically
                    // add the highest versions first, regardless of filesystem order.
                    Collections.sort(versions);
                    for (int i = versions.size() - 1; i >= 0; i--) {
                        result.addVersion(versions.get(i));
                        if (verbose) {
                            System.out.println("Added version: " + versions.get(i));
                        }
                    }
                    if (!result.getVersions().isEmpty()) {
                        result.setVersionConstraint(versionConstraint);
                        return result;
                    }
                }

                // This is a local repository, and we could not resolve the version range.
                result.addException(new Exception("Failed to resolve version"));
                throw new VersionRangeResolutionException(result);
            }
        }

        // If we are fetching, then we can use the default version range resolver.
        return super.resolveVersionRange(session, request);
    }
}
