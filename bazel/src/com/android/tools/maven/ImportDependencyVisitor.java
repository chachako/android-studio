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

import com.google.common.base.Strings;
import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.apache.maven.model.Model;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.graph.DefaultDependencyNode;
import org.eclipse.aether.graph.Dependency;
import org.eclipse.aether.graph.DependencyNode;
import org.eclipse.aether.graph.DependencyVisitor;
import org.eclipse.aether.util.graph.transformer.ConflictResolver;
import org.eclipse.aether.util.graph.visitor.PreorderNodeListGenerator;
import org.eclipse.aether.util.version.GenericVersionScheme;
import org.eclipse.aether.version.InvalidVersionSpecificationException;
import org.eclipse.aether.version.Version;
import org.eclipse.aether.version.VersionScheme;

/**
 * A dependency visitor that visits all nodes in the dependency graph and adds new dependency nodes
 * and edges that represent dependencies that have "scope=import".
 */
public class ImportDependencyVisitor implements DependencyVisitor {

    private final MavenRepository repository;

    // Contains all nodes in the dependency graph.
    // Key: The toString() representation of the Artifact associated with
    // the DependencyNode, with certain fields/aspects erased/fixed for grouping.
    // Specifically, the following are not taken into account when building the key:
    //     Dependency scope
    //     Artifact extension
    //     Artifact version (used in key if resolveConflicts=false).
    private final Map<String, DependencyNode> allNodes = new HashMap<>();

    // Contains processed nodes, so that we process each node only once,
    // in order to improve performance.
    private final Set<DependencyNode> visitedNodes = new HashSet<>();

    // For parsing artifact versions, e.g., "1.0".
    private final VersionScheme versionScheme = new GenericVersionScheme();

    // If true, then version conflict resolution will be performed on the
    // discovered import dependencies.
    private final boolean resolveConflicts;

    public ImportDependencyVisitor(
            DependencyNode root, MavenRepository repository, boolean resolveConflicts) {
        // Gather all nodes in the dependency graph.
        PreorderNodeListGenerator generator = new PreorderNodeListGenerator();
        root.accept(generator);
        for (DependencyNode node : generator.getNodes()) {

            String key =
                    new DefaultArtifact(
                                    node.getArtifact().getGroupId(),
                                    node.getArtifact().getArtifactId(),
                                    Strings.emptyToNull(node.getArtifact().getClassifier()),
                                    node.getArtifact().getExtension(),
                                    resolveConflicts ? null : node.getArtifact().getVersion(),
                                    Collections.emptyMap(),
                                    node.getArtifact().getFile())
                            .toString();
            allNodes.put(key, node);
        }

        this.repository = repository;
        this.resolveConflicts = resolveConflicts;
    }

    @Override
    public boolean visitEnter(DependencyNode node) {
        if (!visitedNodes.add(node)) {
            return false;
        }

        if (node.getArtifact() == null) return true;
        // Get the raw Pom model for the artifact. Note that the effective model
        // inlines the "scope=import" dependencies (which is also why the original
        // dependency graph does not have associated nodes and edges), so we must
        // use the raw model.
        Model rawPomModel =
                repository.getRawMavenModel(
                        new DefaultArtifact(
                                node.getArtifact().getGroupId(),
                                node.getArtifact().getArtifactId(),
                                "pom",
                                node.getArtifact().getVersion()));
        if (rawPomModel.getDependencyManagement() == null) return true;

        // Create new dependency nodes for the import dependencies.
        List<DependencyNode> importDependencyNodes =
                rawPomModel.getDependencyManagement().getDependencies().stream()
                        .filter(d -> d.getType().equals("pom") && d.getScope().equals("import"))
                        .map(
                                d -> {
                                    // Since we are working with the raw pom model, the model
                                    // may contain unsubstituted project variables.
                                    // https://maven.apache.org/guides/introduction/introduction-to-the-pom.html#project-interpolation-and-variables
                                    String version =
                                            d.getVersion().equals("${project.version}")
                                                    ? node.getArtifact().getVersion()
                                                    : d.getVersion();
                                    try {
                                        File pomFile =
                                                repository.getPomFile(
                                                        new DefaultArtifact(
                                                                d.getGroupId(),
                                                                d.getArtifactId(),
                                                                "pom",
                                                                version));
                                        return new DefaultArtifact(
                                                d.getGroupId(),
                                                d.getArtifactId(),
                                                Strings.emptyToNull(d.getClassifier()),
                                                "pom",
                                                version,
                                                Collections.emptyMap(),
                                                pomFile);
                                    } catch (Exception e) {
                                        throw new RuntimeException(e);
                                    }
                                })
                        .map(
                                a -> {
                                    // Deduplication:
                                    // When the dependency graph contains multiple nodes that
                                    // have import dependencies to the same target, then we
                                    // create a single node in the dependency graph for that
                                    // target.
                                    //
                                    // Scope Resolution:
                                    // When the dependency graph has a node that has an import
                                    // dependency to a target that is already represented in
                                    // the dependency graph with a node with
                                    // scope=compile|runtime, then we use that existing node
                                    // to represent the target of the import dependency.
                                    //
                                    // Version Resolution: (only if resolveConflicts=true)
                                    // When the dependency graph has multiple nodes that
                                    // have import dependencies to different versions of the
                                    // same target, then we create one node for each such
                                    // version, where the node with the highest version is
                                    // the "conflict winner", and all the other nodes (i.e.,
                                    // "conflict losers") are annotated to point to the
                                    // conflict winner.

                                    String key =
                                            new DefaultArtifact(
                                                            a.getGroupId(),
                                                            a.getArtifactId(),
                                                            Strings.emptyToNull(a.getClassifier()),
                                                            "pom",
                                                            resolveConflicts
                                                                    ? null
                                                                    : a.getVersion(),
                                                            Collections.emptyMap(),
                                                            a.getFile())
                                                    .toString();

                                    if (!allNodes.containsKey(key)) {
                                        // This is the first import dependency we have seen
                                        // with this key. No need for conflict resolution.
                                        // Save and continue.
                                        allNodes.put(
                                                key,
                                                new DefaultDependencyNode(
                                                        new Dependency(a, "import")));
                                        return allNodes.get(key);
                                    }

                                    // Compare scopes.
                                    if (!allNodes.get(key)
                                            .getDependency()
                                            .getScope()
                                            .equals("import")) {
                                        // There already is a node with a compile/runtime scope
                                        // in the dependency graph for this import. That node
                                        // has priority over this one, so use it.
                                        return allNodes.get(key);
                                    }

                                    if (resolveConflicts) {
                                        // Compare versions of the two imports.
                                        try {
                                            Version keyVersion =
                                                    versionScheme.parseVersion(
                                                            allNodes.get(key)
                                                                    .getArtifact()
                                                                    .getVersion());
                                            Version aVersion =
                                                    versionScheme.parseVersion(a.getVersion());
                                            if (keyVersion.compareTo(aVersion) < 0) {
                                                // The version of the newly seen import dependency
                                                // is higher than the import dependency that we
                                                // already have in allNodes.

                                                // Annotate the existing entry (conflict loser) in
                                                // allNodes
                                                // to point to the conflict winner. This is how
                                                // Aether
                                                // annotates conflict losers in its own dependency
                                                // conflict
                                                // resolution, and we know how to handle dependency
                                                // graphs
                                                // that use these annotations.
                                                DefaultDependencyNode winnerNode =
                                                        new DefaultDependencyNode(
                                                                new Dependency(a, "import"));
                                                HashMap data = new HashMap();
                                                data.put(
                                                        ConflictResolver.NODE_DATA_WINNER,
                                                        winnerNode);
                                                allNodes.get(key).setData(data);

                                                // This dependency with a higher version should
                                                // replace the entry in allNodes, so that the new
                                                // version is used by remaining nodes.
                                                allNodes.put(key, winnerNode);
                                            } else {
                                                // The version of the current import is lower
                                                // than the one we have in allNodes. I don't think
                                                // there is any point in creating a new node and
                                                // then marking it as a conflict loser.
                                                // We can directly associate the current dependency
                                                // with the latest version (conflict winner) that
                                                // we know so far.
                                                // Fall through intended.
                                            }
                                        } catch (InvalidVersionSpecificationException e) {
                                            throw new RuntimeException(e);
                                        }
                                    }

                                    return allNodes.get(key);
                                })
                        .collect(Collectors.toList());
        if (!importDependencyNodes.isEmpty()) {
            // The original children list is read-only, so we create a
            // new list that contains items from both lists.
            List<DependencyNode> children = new ArrayList<>(node.getChildren());
            children.addAll(importDependencyNodes);
            node.setChildren(children);
        }
        return true;
    }

    @Override
    public boolean visitLeave(DependencyNode node) {
        return true;
    }
}
