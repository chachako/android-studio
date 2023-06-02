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
package com.android.declarative.internal.model

import java.util.*

/**
 * DAG containing all the project dependencies.
 *
 * The DAG is organized as a [List] of [Node]s and a [List] of [Edge]s.
 */
class ProjectDependenciesDAG {

    companion object {
        fun create(modulesInfo: Map<String, ResolvedModuleInfo>): ProjectDependenciesDAG =
            ProjectDependenciesDAG().also { dependenciesDAG ->
                modulesInfo.forEach { moduleDependency ->
                    val node = dependenciesDAG.maybeCreate(
                        moduleDependency.key,
                    )
                    moduleDependency.value.dependencies
                        .filter { it.type == DependencyType.PROJECT }
                        .forEach { dependency ->
                            val edgeType = try {
                                ProjectDependenciesDAG.EdgeType.valueOf(
                                    dependency.configuration.uppercase(Locale.getDefault())
                                )
                            } catch (e: IllegalArgumentException) {
                                EdgeType.OTHER
                            }
                            dependenciesDAG.addEdge(
                                edgeType,
                                node,
                                dependenciesDAG.maybeCreate((dependency as NotationDependencyInfo).notation)
                            )
                        }
                }
            }
    }

    // nodes are actually organized as a Map with the node name being the module
    // path, for easy lookup.
    private val nodes = mutableMapOf<String, Node>()
    private val edges = mutableListOf<Edge>()

    /**
     * Creates a [Node] using the project's path as an identifier if it
     * does not exist in the list of [Node]s. If it exists, return the
     * existing [Node] instance.
     */
    fun maybeCreate(path: String): Node =
        nodes.getOrPut(path) { NodeImpl(this, path) }

    /**
     * Lookup a node by its path.
     */
    fun getNode(name: String): Node? = nodes.get(name)

    /**
     * Adds an edge to the DAG between [source] and [target] [Node]s
     *
     * @param edgeType the type of reference between project.
     * @param source the referring project.
     * @param target the referred project.
     */
    fun addEdge(edgeType: EdgeType, source: Node, target: Node) {
        edges.add(Edge(edgeType, source, target))
    }

    /**
     * Performs an [action] for each [Node] is this DAG.
     */
    fun forEachNode(action: (Node) -> Unit) {
        nodes.values.forEach { action(it) }
    }

    /**
     * Performs an [action] on each [Edge] of this DAG.
     */
    fun forEachEdge(action: (Edge) -> Unit) {
        edges.forEach { action(it) }
    }

    /**
     * Definition of a DAG node, identified by its Gradle's project path.
     */
    interface Node {
        val path: String

        /**
         * @return all transitive dependencies of this [Node] as a
         * [Collection] of [Node]s
         */
        fun getTransitiveDependencies(): Collection<Node>

        /**
         * @return a collection of [Node]s which have direct or
         * indirect dependencies on this [Node] instance.
         * This takes care of api/implementation type of references to
         * only include [Node]s that have API visibilities on this [Node]
         */
        fun getIncomingReferences(): Collection<Node>
    }

    private class NodeImpl(
        val owner: ProjectDependenciesDAG,
        override val path: String,
    ): Node {
        override fun getTransitiveDependencies(): Collection<Node> {
            val transitiveDependencies = mutableSetOf<Node>()
            // use a Set rather than a Deque to eliminate all duplicates.
            val queue = mutableSetOf<Node>().also {
                it.add(this)
            }

            while (queue.isNotEmpty()) {
                val node = queue.first()
                queue.remove(node)
                transitiveDependencies.add(node)
                owner.forEachEdge {
                    if (transitiveDependencies.contains(it.source)
                        && !transitiveDependencies.contains(it.target)) {
                            queue.add(it.target)
                    }
                }
            }
            return transitiveDependencies
        }

        // TODO: Add logging.
        override fun getIncomingReferences(): Collection<Node> {
            val incomingReferences = mutableSetOf<Node>()
            val queue = mutableSetOf<Node>().also {
                it.add(this)
            }
            while (queue.isNotEmpty()) {
                val node = queue.first()
                queue.remove(node)
                owner.forEachEdge {
                    if (node == it.target) {
                        incomingReferences.add(it.source)
                        when (it.type) {
                            EdgeType.API -> {
                                queue.add(it.source)
                            }
                            EdgeType.IMPLEMENTATION, EdgeType.OTHER -> {
                                // we already added the source of the reference, we stop there
                                // as the reference is not exported further.
                            }

                        }
                    }
                }
            }
            return incomingReferences
        }

    }

    /**
     * Defines the type of project dependency. This defines visibility of [Node]s to other
     * instances in the DAG.
     *
     * This corresponds to Gradle's configuration visibility rules.
     */
    enum class EdgeType {

        /**
         * Gradle's API dependency, which mean the target node is visible to the source node users.
         */
        API,

        /**
         * Gradle's implementation dependency, which mean the target node is not visible to the
         * source node users.
         */
        IMPLEMENTATION,

        /**
         * TBD.
         *
         * TODO: refine
         */
        OTHER
    }

    /**
     * A DAG edge definition
     *
     * @param edgeType the type of edge as defined in the build files.
     * @param source the referring [Node]
     * @param target the referred [Node]
     */
    class Edge(
        val type: EdgeType,
        val source: Node,
        val target: Node
    )
}
