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

package com.android.tools.preview.multipreview

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

private const val ANNOTATIONS_PER_LEVEL = 2
private const val LAYERS = 30

class GraphTest {
    /**
     * Here we are creating a large DAG of [LAYERS] layers with [ANNOTATIONS_PER_LEVEL] per each
     * layer. Every i-th layer depends on all the annotations of the layer i+1. The last layer
     * depends on [ANNOTATIONS_PER_LEVEL] base annotations and the very first layer is the
     * dependency for [ANNOTATIONS_PER_LEVEL]. Here we check that all these potential permutations
     * when doing [Graph.prune] and [Graph.getAnnotations] are properly handled by the graph
     * and complexity does not grow exponentially.
     */
    @Test
    fun testPruneDeepGraphStressTest() {
        val graph = Graph()

        (0 until ANNOTATIONS_PER_LEVEL).forEach { mi ->
            val refs = AnnotationReferencesRecorder()
            (0 until ANNOTATIONS_PER_LEVEL).forEach { i ->
                refs.derivedAnnotations.add(DerivedAnnotationRepresentation("level0Annotation${i}"))
            }
            graph.addMethodNode(
                MethodRepresentation("method${mi}", emptyList()),
                refs
            )
        }

        (0 until LAYERS).forEach {  lvl ->
            (0 until ANNOTATIONS_PER_LEVEL).forEach { ai ->
                graph.addAnnotationNode(DerivedAnnotationRepresentation("level${lvl}Annotation${ai}"))
                    .apply {
                        (0 until ANNOTATIONS_PER_LEVEL).forEach { i ->
                            this.recordDerivedAnnotation(DerivedAnnotationRepresentation("level${lvl+1}Annotation${i}"))
                        }
                    }
            }
        }

        (0 until ANNOTATIONS_PER_LEVEL).forEach { ai ->
            graph.addAnnotationNode(DerivedAnnotationRepresentation("level${LAYERS}Annotation${ai}"))
                .apply {
                    this.recordBaseAnnotation(BaseAnnotationRepresentation(emptyMap()))
                    this.recordBaseAnnotation(BaseAnnotationRepresentation(mapOf("foo" to "bar")))
                }
        }

        graph.prune()

        val methods = graph.methods.toList()

        assertEquals(2, methods.size)

        assertArrayEquals(
            arrayOf(
                BaseAnnotationRepresentation(emptyMap()),
                BaseAnnotationRepresentation(mapOf("foo" to "bar"))
            ),
            graph.getAnnotations(methods[0]).sortedWith(MultipreviewTest.Companion.AnnotationComparator).toTypedArray()
        )

        assertArrayEquals(
            arrayOf(
                BaseAnnotationRepresentation(emptyMap()),
                BaseAnnotationRepresentation(mapOf("foo" to "bar"))
            ),
            graph.getAnnotations(methods[0]).sortedWith(MultipreviewTest.Companion.AnnotationComparator).toTypedArray()
        )
    }
}
