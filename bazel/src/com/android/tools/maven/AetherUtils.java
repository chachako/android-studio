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

import static com.google.common.base.Preconditions.checkNotNull;

import org.apache.maven.repository.internal.ArtifactDescriptorReaderDelegate;
import org.apache.maven.repository.internal.MavenRepositorySystemUtils;
import org.eclipse.aether.DefaultRepositorySystemSession;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.connector.basic.BasicRepositoryConnectorFactory;
import org.eclipse.aether.impl.DefaultServiceLocator;
import org.eclipse.aether.impl.VersionRangeResolver;
import org.eclipse.aether.repository.LocalRepository;
import org.eclipse.aether.spi.connector.RepositoryConnectorFactory;
import org.eclipse.aether.spi.connector.transport.TransporterFactory;
import org.eclipse.aether.transport.file.FileTransporterFactory;
import org.eclipse.aether.transport.http.HttpTransporterFactory;
import org.eclipse.aether.util.graph.transformer.ConflictResolver;

/** Utility class for constructing Aether objects. */
public class AetherUtils {

    // Prevents instantiating utility class.
    private AetherUtils() {}

    public static DefaultServiceLocator newServiceLocator(boolean verbose) {
        DefaultServiceLocator locator = MavenRepositorySystemUtils.newServiceLocator();
        locator.addService(RepositoryConnectorFactory.class, BasicRepositoryConnectorFactory.class);
        locator.addService(TransporterFactory.class, FileTransporterFactory.class);
        locator.addService(TransporterFactory.class, HttpTransporterFactory.class);
        locator.setServices(VersionRangeResolver.class, new CustomVersionRangeResolver(verbose));
        return locator;
    }

    public static RepositorySystem newRepositorySystem(DefaultServiceLocator locator) {
        // Note that, if any of the inputs transitively depend on an artifact using a version
        // range, the default version range resolver will not be able to resolve the version,
        // because our prebuilts repository does NOT have any maven-metadata.xml files.
        // In that case, we will have to write our own, custom, version range resolver,
        // then register it here.
        return checkNotNull(locator.getService(RepositorySystem.class));
    }

    public static DefaultRepositorySystemSession newSession(
            RepositorySystem system, String repoPath) {
        DefaultRepositorySystemSession session = MavenRepositorySystemUtils.newSession();

        // This is where the artifacts will be downloaded to. Since it points to our own local
        // maven repo, artifacts  that are already there will not be re-downloaded.
        session.setLocalRepositoryManager(
                system.newLocalRepositoryManager(session, new LocalRepository(repoPath)));

        session.setIgnoreArtifactDescriptorRepositories(true);

        // When this flag is false, conflict losers are removed from the dependency graph. E.g.,
        // even though common:28 depends on guava:28, it will not be in the dependency graph if
        // there is a conflict and guava:30 wins over guava:28.
        // When this flag is true, these nodes are kept in the dependency graph, but have a
        // special marker (NODE_DATA_WINNER).
        session.setConfigProperty(ConflictResolver.CONFIG_PROP_VERBOSE, true);
        session.setConfigProperty(
                ArtifactDescriptorReaderDelegate.class.getName(),
                new HandleAarDescriptorReaderDelegate());

        return session;
    }
}
