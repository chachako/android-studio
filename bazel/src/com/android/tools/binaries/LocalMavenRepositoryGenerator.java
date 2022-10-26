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

import com.android.tools.json.JsonFileWriter;
import com.android.tools.maven.MavenRepository;
import com.android.tools.repository_generator.BuildFileWriter;
import com.android.tools.repository_generator.ResolutionResult;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Strings;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;
import org.apache.maven.model.Model;
import org.apache.maven.model.Parent;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.graph.DependencyNode;
import org.eclipse.aether.graph.DependencyVisitor;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.util.graph.transformer.ConflictResolver;

/**
 * A tool that generates a virtual Maven repository from a given list of initial artifacts, and
 * then generates a BUILD file that contains rules for all the artifacts in that Maven repository.
 *
 * <p>It uses Aether for Maven dependency resolution, extending it with the ability to collect Maven
 * parents and Maven resolution conflict losers, to make sure the generated repository is complete.
 */
public class LocalMavenRepositoryGenerator {

    /**
     * The coordinates of the Maven artifacts that will be used as seeds to produce the BUILD file.
     */
    private final List<String> coords;

    /**
     * Additional coordinates to generate maven_artifact for without resolving version conflicts.
     */
    private final List<String> noresolveCoords;

    /** The location of the BUILD file that will be produced. */
    private final String outputBuildFile;

    /** Location of the local maven repository for which the BUILD rules will be generated. */
    private final Path repoPath;

    /** Handle to the repo object that encapsulates Aether operations. */
    private final MavenRepository repo;

    /** If true, also writes the result in JSON format to a file and stdout. */
    private final boolean verbose;

    /** Whether to fetch dependencies from remote repositories. */
    private final boolean fetch;

    @VisibleForTesting
    public LocalMavenRepositoryGenerator(
            Path repoPath,
            String outputBuildFile,
            List<String> coords,
            List<String> noresolveCoords,
            boolean fetch,
            Map<String, String> remoteRepositories,
            boolean verbose) {
        this.noresolveCoords = noresolveCoords;
        this.coords = coords;
        this.outputBuildFile = outputBuildFile;
        this.repoPath = repoPath.toAbsolutePath();
        this.verbose = verbose;
        this.fetch = fetch;

        // This is where the artifacts will be downloaded from. We make it point to our own local
        // maven repo. If an artifact is required for dependency resolution but doesn't exist
        // there, it will be reported as an error.
        RemoteRepository remoteRepository =
                new RemoteRepository.Builder("prebuilts", "default", "file://" + this.repoPath)
                        .build();
        List<RemoteRepository> repositories = new ArrayList<>();
        if (fetch) {
            repositories.addAll(
                    remoteRepositories.entrySet().stream().map(entry -> {
                            String name = entry.getKey();
                            String url = entry.getValue();
                            return new RemoteRepository.Builder(name, "default", url).build();
                        }
                    ).collect(Collectors.toList())
            );
        }
        repositories.add(remoteRepository);
        repo = new MavenRepository(this.repoPath.toString(), repositories, verbose);
    }

    public void run() throws Exception {
        ResolutionResult result = new ResolutionResult();

        // Compute dependency graph with version resolution, but without conflict resolution.
        List<String> allCoords = new ArrayList<>();
        allCoords.addAll(coords);
        allCoords.addAll(noresolveCoords);
        List<DependencyNode> unresolvedNodes =
                collectNodes(repo.resolveDependencies(allCoords, false));
        // Compute dependency graph with version resolution and with conflict resolution.
        List<DependencyNode> resolvedNodes = collectNodes(repo.resolveDependencies(coords, true));

        // Add the nodes in the conflict-resolved graph into the |dependencies| section of
        // |result|.
        for (DependencyNode node : resolvedNodes) {
            processNode(node, true, result);
        }

        // Add the nodes in the conflict-unresolved graph, but not in the conflict-resolved
        // graph into the |unresolvedDependencies| section of |result|.
        Set<String> resolvedNodeCoords =
                resolvedNodes.stream()
                        .map(d -> d.getArtifact().toString())
                        .collect(Collectors.toSet());
        Set<DependencyNode> unresolvedDependencies =
                unresolvedNodes.stream()
                        .filter(d -> !resolvedNodeCoords.contains(d.getArtifact().toString()))
                        .collect(Collectors.toSet());
        for (DependencyNode node : unresolvedDependencies) {
            processNode(node, false, result);
        }

        // Add the transitive parents of all nodes in the conflict-unresolved graph into the
        // |parents| section of |result|.
        //
        // This is a worklist algorithm that starts with the parents of all nodes in the graph,
        // dequeues one parent at a time, adding the transitive parents back to the work list.
        // Parents of all nodes that will later need to be processed.
        List<Parent> parentsToProcess =
                unresolvedNodes.stream()
                        .map(node -> repo.getMavenModel(node.getArtifact()).getParent())
                        .filter(Objects::nonNull)
                        .collect(Collectors.toList());
        Set<String> alreadyProcessedParents = new HashSet<>();
        while (!parentsToProcess.isEmpty()) {
            Parent parent = parentsToProcess.remove(0);
            if (!alreadyProcessedParents.add(parent.toString())) {
                continue;
            }

            Model model =
                    repo.getMavenModel(
                            new DefaultArtifact(
                                    parent.getGroupId(),
                                    parent.getArtifactId(),
                                    "pom",
                                    parent.getVersion()));

            result.parents.add(
                    new ResolutionResult.Parent(
                            parent.toString(),
                            repoPath.relativize(model.getPomFile().toPath()).toString(),
                            model.getParent() == null ? null : model.getParent().toString()));

            if (model.getParent() != null) {
                parentsToProcess.add(model.getParent());
            }
        }

        result.sortByCoord();

        if (fetch) {
            for (DependencyNode node : unresolvedNodes) {
                copyNotice(node.getArtifact());
            }
        }

        if (verbose) {
            JsonFileWriter.write("output.json", result);
            JsonFileWriter.print(result);
        }

        new BuildFileWriter(repoPath, outputBuildFile).write(result);
    }

    /**
     * Returns a list that contains all the nodes in the Dependency graph with the given virtual
     * root (that contains all real roots as its children).
     *
     * <p>The virtual root is excluded in the returned list.
     *
     * <p>If input dependency graph was built using VERBOSE mode enabled (i.e., it preserved some of
     * the conflict loser nodes), the returned list also excludes any nodes that are conflict
     * losers.
     */
    private List<DependencyNode> collectNodes(DependencyNode root) {
        LinkedHashSet<DependencyNode> allNodes = new LinkedHashSet<DependencyNode>();

        root.accept(
                new DependencyVisitor() {
                    @Override
                    public boolean visitEnter(DependencyNode node) {
                        if (node.getArtifact() == null) return true;
                        if (allNodes.contains(node)) return false;

                        // Exclude conflict losers.
                        // Dependency loser nodes do not have their children populated, and
                        // therefore cause problems in analysis further downstream. It's better to
                        // handle them separately.
                        //
                        // Checks if this node was involved in a conflict, lost the conflict, and
                        // was upgraded to a different version.
                        DependencyNode winnerNode = getWinner(node);
                        boolean lostConflict =
                                winnerNode != null
                                        && winnerNode.getArtifact() != null
                                        && !winnerNode
                                                .getArtifact()
                                                .toString()
                                                .equals(node.getArtifact().toString());
                        if (lostConflict) return true;

                        allNodes.add(node);
                        return true;
                    }

                    @Override
                    public boolean visitLeave(DependencyNode node) {
                        return true;
                    }
                });

        return new ArrayList<>(allNodes);
    }

    /**
     * Processes the given node, converts it into a {@link ResolutionResult.Dependency} object, and
     * adds it into result.
     */
    private void processNode(DependencyNode node, boolean isResolved, ResolutionResult result) {
        // node does not contain any information about parent or pom file.
        // To obtain those, we use the maven-model-builder plugin.
        Model model = repo.getMavenModel(node.getArtifact());
        String parentCoord = model.getParent() != null ? model.getParent().toString() : null;

        // node does not contain any information about classifiers, or
        // sources jar. maven-model-provider does not provide a way of extracting
        // source jars either. Here, we try to find out whether there is a source
        // jar by manipulating the artifact path and checking whether such a jar
        // exists.
        String artifactPath = node.getArtifact().getFile().toPath().toString();
        String sourcesJarPath = null;
        if (artifactPath.endsWith(".jar")) {
            String path =
                    artifactPath.substring(0, artifactPath.length() - ".jar".length())
                            + "-sources.jar";
            if (new File(path).exists()) {
                sourcesJarPath = repoPath.relativize(Paths.get(path)).toString();
            }
        }

        // For every dependency (i.e., child) of this node, if the dependency was
        // involved in a conflict, then this will map requested dependency artifact
        // to its reconciled dependency artifact.
        Map<String, String> conflictResolution = new HashMap<>();
        // The dependencies after resolution grouped by scope.
        Map<String, List<String>> resolvedDeps = new TreeMap<>();
        // The dependencies that were involved in a conflict and got upgraded.
        List<String> originalDeps = new ArrayList<>();

        for (DependencyNode child : node.getChildren()) {
            originalDeps.add(child.getArtifact().toString());
        }

        if (isResolved) {
            for (DependencyNode child : node.getChildren()) {
                DependencyNode winnerChildNode = getWinner(child);
                if (winnerChildNode == null
                        || winnerChildNode.getArtifact() == null
                        || winnerChildNode
                                .getArtifact()
                                .toString()
                                .equals(child.getArtifact().toString())) {
                    // Winner doesn't exist, does not have an artifact, or is identical
                    // to the child node. This is a dependency that was not upgraded.
                    String scope = child.getDependency().getScope();
                    resolvedDeps.putIfAbsent(scope, new ArrayList<>());
                    resolvedDeps.get(scope).add(child.getArtifact().toString());
                } else {
                    // This dependency was in a conflict, and got upgraded.
                    conflictResolution.put(
                            child.getArtifact().toString(),
                            winnerChildNode.getArtifact().toString());
                    // We still maintain the original dependency scope.
                    String scope = child.getDependency().getScope();
                    resolvedDeps.putIfAbsent(scope, new ArrayList<>());
                    resolvedDeps.get(scope).add(winnerChildNode.getArtifact().toString());
                }
            }
        }

        if (!isResolved) {
            result.addUnresolvedDependency(
                    new ResolutionResult.Dependency(
                            node.getArtifact().toString(),
                            repoPath.relativize(node.getArtifact().getFile().toPath()).toString(),
                            repoPath.relativize(model.getPomFile().toPath()).toString(),
                            parentCoord,
                            null,
                            node.getChildren().stream()
                                    .map(d -> d.getArtifact().toString())
                                    .toArray(String[]::new),
                            null,
                            null));
        } else {
            result.addDependency(
                    new ResolutionResult.Dependency(
                            node.getArtifact().toString(),
                            repoPath.relativize(node.getArtifact().getFile().toPath()).toString(),
                            repoPath.relativize(model.getPomFile().toPath()).toString(),
                            parentCoord,
                            sourcesJarPath,
                            originalDeps.toArray(new String[0]),
                            resolvedDeps,
                            conflictResolution));
        }
    }

    /**
     * Extracts the dependency resolution winner information from the given node. By default, the
     * dependency graph does not contain this information, however we asked it to be kept by setting
     * the CONFIG_PROP_VERBOSE flag to true in graph construction.
     *
     * @return if there was a resolution conflict, returns the node that represents the conflict
     *     winner
     */
    private static DependencyNode getWinner(DependencyNode node) {
        return (DependencyNode) node.getData().get(ConflictResolver.NODE_DATA_WINNER);
    }

    /**
     * Searches the artifact's parent directories for a license file and, if such a
     * license file is found, copies it into the artifact's directory.
     * @param artifact the artifact for which a license file will be added
     */
    @SuppressWarnings("FileComparisons")
    private void copyNotice(Artifact artifact) {
        File artifactDir = artifact.getFile().getParentFile();
        String[] possibleNames = {"LICENSE", "LICENSE.txt", "NOTICE", "NOTICE.txt"};
        try {
            File currentDir = artifactDir.getCanonicalFile();
            while (!currentDir.equals(repoPath.toFile().getCanonicalFile())) {
                for (String name : possibleNames) {
                    File possibleLicense = new File(currentDir, name);
                    if (possibleLicense.exists()) {
                        Files.copy(
                                possibleLicense.toPath(),
                                (new File(artifactDir, "NOTICE")).toPath(),
                                StandardCopyOption.REPLACE_EXISTING);
                        return;
                    }
                }
                currentDir = currentDir.getParentFile();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        // No NOTICE found for this artifact. It must be added manually if it will
        // ever be used by the commandlinetools SDK component. That component can
        // detect if the NOTICE file is missing, and issue and error, so we do not
        // need to issue a warning here.
    }

    public static void main(String[] args) throws Exception {
        List<String> noresolveCoords = new ArrayList<>();
        List<String> coords = new ArrayList<>();
        Path repoPath = null;
        boolean verbose = false;
        boolean fetch = !Strings.isNullOrEmpty(System.getenv("MAVEN_FETCH"));
        Map<String, String> remoteRepositories = new TreeMap<>();
        String outputFile = "output.BUILD";
        for (int i = 0; i < args.length; i++) {
            String arg = args[i];
            if (arg.equals("-o")) {
                if (args.length <= i + 1) {
                    System.err.println("-o must be followed by a filename");
                    System.exit(1);
                }
                outputFile = args[i + 1];
                i++;
                continue;
            }
            if (arg.equals("-v")) {
                verbose = true;
                continue;
            }
            if (arg.equals("--repo-path")) {
                if (args.length <= i + 1) {
                    System.err.println("--repo-path must be followed by a path");
                    System.exit(1);
                }
                repoPath = Paths.get(args[i + 1]);
                i++;
                continue;
            }
            if (arg.equals("--remote-repo")) {
                if (args.length <= i + 1) {
                    System.err.println("--remote-repo must be followed by a \"name=URL\" pair");
                    System.exit(1);
                }
                String[] remoteRepo = args[i + 1].split("=", 2);
                if (remoteRepo.length != 2) {
                    System.err.println("Invalid argument after --remote-repo: " + args[i + 1]);
                    System.exit(1);
                }
                remoteRepositories.put(remoteRepo[0], remoteRepo[1]);
                i++;
                continue;
            }
            if (arg.equals("--fetch")) {
                fetch = true;
                continue;
            }

            // All other arguments are coords.
            // If a coordinate is passed in with a leading '+', like +com.example:id:art,
            // We treat it as additional coordinates that won't get resolved.
            if (args[i].startsWith("+")) {
                noresolveCoords.add(args[i].substring(1));
            } else {
                coords.add(args[i]);
            }
        }
        if (repoPath == null) {
            System.err.println("Missing argument: --repo-path");
            System.exit(1);
        }

        if (coords.isEmpty()) {
            System.err.println("At least one maven artifact coordinate must be provided");
            System.exit(1);
        }

        String workspacePath = System.getenv("BUILD_WORKSPACE_DIRECTORY");
        if (workspacePath != null) {
            // The tool is executed from inside "bazel run" command. Treat relative
            // repo and output file paths as relative to the WORKSPACE file so that
            // they refer to the source tree (i.e., fetch maven artifacts into source
            // tree, write output file to source tree).
            if (!repoPath.isAbsolute()) {
                repoPath = Paths.get(workspacePath, repoPath.toString());
            }

            if (!Paths.get(outputFile).isAbsolute()) {
                outputFile = Paths.get(workspacePath, outputFile).toString();
            }
        }

        new LocalMavenRepositoryGenerator(
                        repoPath,
                        outputFile,
                        coords,
                        noresolveCoords,
                        fetch,
                        remoteRepositories,
                        verbose)
                .run();
    }
}
