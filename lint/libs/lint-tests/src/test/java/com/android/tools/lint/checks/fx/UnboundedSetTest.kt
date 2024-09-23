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
package com.android.tools.lint.checks.fx

import com.google.common.truth.Truth
import org.junit.Test

class UnboundedSetTest {

  @Test
  fun `inclusion works`() {
    Truth.assertThat(unboundedSetOf("foo", "bar") isSubsetOf unboundedSetOf("qux", "bar", "foo"))
      .isTrue()
    Truth.assertThat(unboundedSetOf("foo", "bar") isSubsetOf unboundedSetOfAll()).isTrue()
    Truth.assertThat(unboundedSetOfAll<String>() isSubsetOf unboundedSetOfAll()).isTrue()
  }

  @Test
  fun `intersection works`() {
    Truth.assertThat(unboundedSetOf("foo", "bar") intersectedWith unboundedSetOf("qux", "bar"))
      .isEqualTo(unboundedSetOf("bar"))
    Truth.assertThat(unboundedSetOf("foo", "bar") intersectedWith unboundedSetOfAll())
      .isEqualTo(unboundedSetOf("foo", "bar"))
  }

  @Test
  fun `union works`() {
    Truth.assertThat(unboundedSetOf("foo", "bar") unionedWith unboundedSetOf("bar", "qux"))
      .isEqualTo(unboundedSetOf("foo", "bar", "qux"))
    Truth.assertThat(unboundedSetOf("foo", "bar") unionedWith unboundedSetOfAll())
      .isEqualTo(unboundedSetOfAll<String>())
  }

  @Test
  fun `map works`() {
    Truth.assertThat(unboundedSetOf("foo", "bar", "yeah").map { it.length })
      .isEqualTo(unboundedSetOf(3, 4))

    // TODO unintuitive?
    Truth.assertThat((unboundedSetOfAll<String>()).map { it.length })
      .isEqualTo(unboundedSetOfAll<Int>())
  }

  @Test
  fun `map preserves id and composition`() {
    val pool = listOf(unboundedSetOf(), unboundedSetOf(0, 1, 2, 3, 4, 5), unboundedSetOfAll())
    testMapPreservingId(pool)
    testMapPreservingComposition(pool, fst = { it.toString() }, snd = { it.hashCode() % 3 })
  }

  private fun <X> testMapPreservingId(pool: List<UnboundedSet<X>>) {
    for (s in pool) Truth.assertThat(s.map { it }).isEqualTo(s)
  }

  private fun <X, Y, Z> testMapPreservingComposition(
    pool: List<UnboundedSet<X>>,
    fst: (X) -> Y,
    snd: (Y) -> Z,
  ) {
    for (s in pool) {
      val s1 = s.map(fst).map(snd)
      val s2 = s.map { snd(fst(it)) }
      Truth.assertThat(s1).isEqualTo(s2)
    }
  }
}

class ConstraintLatticeTest :
  LatticeTest<UnboundedSet<String>>(
    lattice = constraintLattice(),
    poolInits = listOf(unboundedSetOf("Ui"), unboundedSetOf("Binder"), unboundedSetOf("Worker")),
  )

class PossibilityLatticeTest :
  LatticeTest<UnboundedSet<String>>(
    lattice = possibilityLattice(),
    poolInits = listOf(unboundedSetOf("Cat"), unboundedSetOf("Dog")),
  )
