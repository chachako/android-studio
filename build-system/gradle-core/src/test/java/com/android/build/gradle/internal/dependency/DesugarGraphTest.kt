/*
 * Copyright (C) 2024 The Android Open Source Project
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

package com.android.build.gradle.internal.dependency

import com.android.builder.dexing.MutableDependencyGraph
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ObjectInputStream
import kotlin.test.assertFailsWith

class DesugarGraphTest {

    @get:Rule
    val tmp = TemporaryFolder()

    @Test
    fun `test write and read`() {
        val rootDir = tmp.newFolder("RootDir")
        val desugarGraph = DesugarGraph(
            rootDir = rootDir,
            relocatableDesugarGraph = MutableDependencyGraph(),
            filesToIgnore = emptySet()
        )

        val aKt = rootDir.resolve("com/example/A.kt")
        val bKt = rootDir.resolve("com/example/B.kt")
        val cKt = rootDir.resolve("com/example/C.kt")
        desugarGraph.addEdge(aKt, bKt)
        desugarGraph.addEdge(bKt, cKt)

        val xKt = rootDir.resolve("com/example/X.kt")
        val yKt = rootDir.resolve("com/example/Y.kt")
        val zKt = rootDir.resolve("com/example/Z.kt")
        desugarGraph.addEdge(xKt, yKt)
        desugarGraph.addEdge(yKt, zKt)
        desugarGraph.removeNode(yKt)

        assertThat(desugarGraph.getAllDependents(listOf(aKt))).isEmpty()
        assertThat(desugarGraph.getAllDependents(listOf(bKt))).containsExactlyElementsIn(listOf(aKt))
        assertThat(desugarGraph.getAllDependents(listOf(cKt))).containsExactlyElementsIn(listOf(aKt, bKt))

        assertThat(desugarGraph.getAllDependents(listOf(xKt))).isEmpty()
        assertThat(desugarGraph.getAllDependents(listOf(yKt))).isEmpty()
        assertThat(desugarGraph.getAllDependents(listOf(zKt))).isEmpty()

        // Also check that the serialized desugar graph is relocatable (i.e., it uses Unix-style
        // relative paths)
        val desugarGraphFile = tmp.newFile("desugar_graph.bin")
        desugarGraph.write(desugarGraphFile)
        val relocatableDesugarGraph = ObjectInputStream(desugarGraphFile.inputStream().buffered()).use {
            @Suppress("UNCHECKED_CAST")
            it.readObject() as MutableDependencyGraph<String>
        }
        assertThat(relocatableDesugarGraph.getAllDependents(listOf("com/example/C.kt")))
            .containsExactlyElementsIn(listOf("com/example/A.kt", "com/example/B.kt"))
    }

    @Test
    fun `test files located outside rootDir`() {
        val rootDir = tmp.newFolder("RootDir")
        val aKt = rootDir.resolve("com/example/A.kt")
        val bKt = rootDir.resolve("../outsideRootDir/com/example/B.kt").normalize()
        val cKt = rootDir.resolve("../outsideRootDir/com/example/C.kt").normalize()

        val desugarGraph = DesugarGraph(
            rootDir = rootDir,
            relocatableDesugarGraph = MutableDependencyGraph(),
            filesToIgnore = setOf(bKt)
        )

        // Add an edge to a file outside rootDir and the file is in the set of `filesToIgnore`,
        // expect no-op (regression test for b/362339872)
        desugarGraph.addEdge(aKt, bKt)
        assertThat(desugarGraph.getAllDependents(listOf(aKt))).isEmpty()

        // Add an edge to a file outside rootDir and the file is NOT in the set of `filesToIgnore`,
        // expect failure
        val exception = assertFailsWith<IllegalStateException> {
            desugarGraph.addEdge(aKt, cKt)
        }
        assertThat(exception.message)
            .isEqualTo("The given file '$cKt' is located outside the root directory '$rootDir'")
    }

}
