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

package com.android.tools.maven;

import com.google.common.collect.ImmutableMap;
import java.util.Map;
import org.apache.maven.model.Model;
import org.apache.maven.repository.internal.ArtifactDescriptorReaderDelegate;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.resolution.ArtifactDescriptorResult;
import org.eclipse.aether.util.artifact.DelegatingArtifact;

public class HandleAarDescriptorReaderDelegate extends ArtifactDescriptorReaderDelegate {
    @Override
    public void populateResult(
            RepositorySystemSession session, ArtifactDescriptorResult result, Model model) {
        super.populateResult(session, result, model);

        if (model.getPackaging().equals("pom") &&!result.getArtifact().getClassifier().isEmpty()) {
            // We consider it OK to have a JAR dependency to an artifact that has packaging=pom as long as the
            // dependency also has a classifier. In this case, we don't need to overwrite the dependency type.
            //
            // For instance, the artifact com.google.protobuf:protoc:3.10.0 contains protoc compiler
            // executables as classified files, such as protoc-3.10.0-linux-x86_64.exe. As long as the
            // dependency is specified as com.google.protobuf:exe:linux-x86_64:protoc:3.10.0, we are fine.
            return;
        }

        if (!getArtifactExtension(model).equals(result.getArtifact().getExtension())) {
            // When the dependency type does not match the packaging type of the target, we may need
            // to use the packaging type of the target to clarify the dependency type.
            //
            // Example: An aar artifact can refer to other aar artifacts without explicitly stating
            // the dependency type to be "aar". Aether default is "jar", so here we have to convert
            // it back to "aar".
            //    android.arch.lifecycle:extensions:aar depends on support-fragment, but
            //    dependency does not have a type, so it defaults to type=jar.
            //    support-fragment has packaging=aar.
            if ("aar".equals(getArtifactExtension(model))) {
                result.setArtifact(
                        new DifferentExtensionArtifact(
                                getArtifactExtension(model), result.getArtifact()));
                return;
            }

            // Example: An artifact has a dependency of type "aar", but the target artifact has
            // packaging type that is not "aar". We use the packaging type.
            //    animation:jar depends on animation-core with dependency type=aar, but
            //    animation-core has packaging=jar
            if ("aar".equals(result.getArtifact().getExtension())) {
                result.setArtifact(
                        new DifferentExtensionArtifact(
                                getArtifactExtension(model), result.getArtifact()));
                return;
            }

            // Packaging type is pom, but the dependency type is jar.
            if ("pom".equals(getArtifactExtension(model))
                    && "jar".equals(result.getArtifact().getExtension())) {
                // We respect the packaging type implied by the dependency.
                // Example: We depend on equalsverifier:jar, but equalsverifier declares
                // packaging=pom.
                // In this case, we use the dependency type, i.e., jar.
                //
                // However, we have one exception. Example:
                // lint-gradle depends on groovy-all without expressing a dependency type. This
                // defaults to "jar" dependency type, but groovy-all has packaging type "pom", so we
                // have to convert it to "pom".
                if ("org.codehaus.groovy".equals(result.getArtifact().getGroupId())
                        && "groovy-all".equals(result.getArtifact().getArtifactId())) {
                    result.setArtifact(
                            new DifferentExtensionArtifact(
                                    getArtifactExtension(model), result.getArtifact()));
                    return;
                }

                return;
            }

            // For everything else, we use the packaging type in the pom file. This way, we don't
            // need to write the obvious file types for some artifacts.
            // Example: androidx.test:orchestrator[:apk]:1.5.2-alpha02
            result.setArtifact(
                    new DifferentExtensionArtifact(
                            getArtifactExtension(model), result.getArtifact()));
        }
    }

    private static class DifferentExtensionArtifact extends DelegatingArtifact {

        private final String extension;

        public DifferentExtensionArtifact(String extension, Artifact delegate) {
            super(delegate);
            this.extension = extension;
        }

        @Override
        protected DelegatingArtifact newInstance(Artifact delegate) {
            return new DifferentExtensionArtifact(extension, delegate);
        }

        @Override
        public String getExtension() {
            return extension;
        }
    }

    private static final Map<String, String> EXTENSIONS_MAP =
            ImmutableMap.of(
                    "bundle", "jar",
                    "maven-plugin", "jar",
                    "eclipse-plugin", "jar");

    private static String getArtifactExtension(Model model) {
        return EXTENSIONS_MAP.getOrDefault(model.getPackaging(), model.getPackaging());
    }
}
