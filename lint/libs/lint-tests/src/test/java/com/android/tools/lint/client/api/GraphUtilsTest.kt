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
package com.android.tools.lint.client.api

import com.android.tools.lint.detector.api.GraphUtils
import com.google.common.truth.Truth
import org.junit.Test

class GraphUtilsTest {
  @Test
  fun `linearizing a graph from leaves touches leaves first`() {
    fun <T> subsetGraph(root: Set<T>): Map<Set<T>, List<Set<T>>> =
      hashMapOf<Set<T>, List<Set<T>>>().also { out ->
        fun <T> minusOneSubsets(s: Set<T>): List<Set<T>> = s.map { n -> s - n }
        fun buildNextDepth(s: Set<T>) {
          out[s] = minusOneSubsets(s)
          out[s]!!.forEach(::buildNextDepth)
        }
        buildNextDepth(root)
      }

    val all = setOf(1, 2, 3)
    val graph = subsetGraph(all)
    val nodes = GraphUtils.reverseTopologicalSort(listOf(all)) { graph[it]!!.asSequence() }.toList()

    Truth.assertThat(nodes).hasSize(1 shl all.size)
    // Sets shouldn't appear before their subsets
    for ((l, node_l) in nodes.withIndex()) {
      for (node_r in nodes.subList(l + 1, nodes.size)) {
        Truth.assertThat(node_l.containsAll(node_r)).isFalse()
      }
    }
  }
}
