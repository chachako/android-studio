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

import com.android.tools.lint.checks.fx.Lattice.Companion.dual
import com.google.common.truth.Truth
import kotlin.math.max
import kotlin.math.min
import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.persistentSetOf
import org.junit.Test

abstract class LatticeTest<L>(private val lattice: Lattice<L>, poolInits: List<L>) :
  Lattice<L> by lattice {
  private val pool = buildSet {
    val allInits = (poolInits + lattice.top + lattice.bottom).distinct()
    addAll(allInits)
    for (x in allInits) for (y in allInits) {
      add(lattice.joinOf(x, y))
      add(lattice.meetOf(x, y))
    }
  }

  @Test
  fun `dual flips the operations`() {
    val dual = dual()
    Truth.assertThat(top).isEqualTo(dual.bottom)
    Truth.assertThat(bottom).isEqualTo(dual.top)
    forall { x, y -> x join y == dual.meetOf(x, y) && x meet y == dual.joinOf(x, y) }
  }

  @Test fun `order is reflexive`() = forall { x -> x precedes x }

  @Test
  fun `order is transitive`() = forall { x, y, z ->
    (x precedes y && y precedes z) implies { x precedes z }
  }

  @Test
  fun `order is anti-symmetric`() = forall { x, y ->
    (x precedes y && y precedes x) implies { x == y }
  }

  @Test fun `bottom is least`() = forall { x -> bottom precedes x }

  @Test fun `bottom is id of join`() = forall { x -> x join bottom == x && bottom join x == x }

  @Test
  fun `bottom is annihilator of meet`() = forall { x ->
    x meet bottom == bottom && bottom meet x == bottom
  }

  @Test
  fun `meet gives greatest common lower bound`() = forall { x, y ->
    val m = x meet y
    (m precedes x && m precedes y) &&
      pool.all { l -> (l precedes x && l precedes y) implies { l precedes m } }
  }

  @Test fun `top is greatest`() = forall { x -> x precedes top }

  @Test fun `top is id of meet`() = forall { x -> x meet top == x && top meet x == x }

  @Test fun `top is annihilator of join`() = forall { x -> x join top == top && top join x == top }

  @Test
  fun `join gives greatest common lower bound`() = forall { x, y ->
    val j = x join y
    (x precedes j && y precedes j) &&
      pool.all { u -> (x precedes u && y precedes u) implies { j precedes u } }
  }

  protected fun forall(p: (L) -> Boolean) {
    for (x in pool) {
      assert(p(x)) { "$x fails" }
    }
  }

  protected fun forall(p: (L, L) -> Boolean) {
    for (x in pool) for (y in pool) {
      assert(p(x, y)) { "$x, $y fails" }
    }
  }

  protected fun forall(p: (L, L, L) -> Boolean) {
    for (x in pool) for (y in pool) for (z in pool) {
      assert(p(x, y, z)) { "$x, $y, $z fails" }
    }
  }
}

class UnitLatticeTest : LatticeTest<Unit>(lattice = UnitLattice, poolInits = listOf())

class ImplicationLatticeTest :
  LatticeTest<Boolean>(lattice = ImplicationLattice, poolInits = listOf())

class DiscreteLatticeTest :
  LatticeTest<Discrete<Int>>(
    lattice = DiscreteLattice(),
    poolInits = listOf(Discrete.Value(1), Discrete.Value(2), Discrete.Value(3)),
  )

class TotalOrderLatticeTest :
  LatticeTest<Int>(lattice = TotalOrderLattice(7), poolInits = listOf(1, 2, 3))

class Product2LatticeTest :
  LatticeTest<Pair<Boolean, Int>>(
    lattice =
      Lattice.product(
        ::Pair,
        Pair<Boolean, Int>::first,
        Pair<Boolean, Int>::second,
        ImplicationLattice,
        TotalOrderLattice(3),
      ),
    poolInits = listOf(false to 2, true to 0),
  )

class Product3LatticeTest :
  LatticeTest<Triple<Boolean, Int, UnboundedSet<String>>>(
    lattice =
      Lattice.product(
        ::Triple,
        Triple<Boolean, Int, UnboundedSet<String>>::first,
        Triple<Boolean, Int, UnboundedSet<String>>::second,
        Triple<Boolean, Int, UnboundedSet<String>>::third,
        ImplicationLattice.dual(),
        TotalOrderLattice(4),
        possibilityLattice(),
      ),
    poolInits =
      listOf(
        Triple(true, 3, null),
        Triple(false, 2, unboundedSetOf("foo")),
        Triple(true, 0, unboundedSetOf()),
      ),
  )

class PointWiseLatticeTest :
  LatticeTest<PersistentMap<String, UnboundedSet<Int>>?>(
    lattice = Lattice.pointWise(possibilityLattice()),
    poolInits =
      listOf(
        persistentMapOf("foo" to persistentSetOf(3), "bar" to persistentSetOf(4, 5), "hi" to null),
        persistentMapOf("bar" to persistentSetOf(5, 6), "qux" to persistentSetOf(6, 7)),
      ),
  ) {

  @Test
  fun `keys of joined maps subsume both`() = forall { m1, m2 ->
    when (val m = m1 join m2) {
      null -> m1 == null || m2 == null
      else -> m1 != null && m2 != null && m.keys.containsAll(m1.keys) && m.keys.containsAll(m2.keys)
    }.also { if (!it) println("Joined: ${m1 join m2}") }
  }

  @Test
  fun `keys of met maps are included in both`() = forall { m1, m2 ->
    when (val m = m1 meet m2) {
      null -> m1 == null && m2 == null
      else ->
        (m1 == null || m1.keys.containsAll(m.keys)) && (m2 == null || m2.keys.containsAll(m.keys))
    }
  }
}

open class SingletonLattice<X>(val value: X) : Lattice<X> {
  final override val bottom = value
  final override val top = value

  final override fun meetOf(first: X, second: X) =
    value.also {
      require(first == value)
      require(second == value)
    }

  final override fun joinOf(first: X, second: X) =
    value.also {
      require(first == value)
      require(second == value)
    }

  final override fun precede(first: X, second: X) =
    true.also {
      require(first == value)
      require(second == value)
    }
}

object UnitLattice : SingletonLattice<Unit>(Unit)

object ImplicationLattice : Lattice<Boolean> {
  override val bottom = false
  override val top = true

  override fun meetOf(first: Boolean, second: Boolean) = first && second

  override fun joinOf(first: Boolean, second: Boolean) = first || second

  override fun precede(first: Boolean, second: Boolean) = first implies { second }
}

class TotalOrderLattice(val modulo: Int) : Lattice<Int> {
  override val bottom = 0
  override val top = modulo - 1

  override fun meetOf(first: Int, second: Int) = min(first, second)

  override fun joinOf(first: Int, second: Int) = max(first, second)

  override fun precede(first: Int, second: Int) = first <= second
}

sealed interface Discrete<out T> {
  data object Top : Discrete<Nothing>

  data object Btm : Discrete<Nothing>

  data class Value<out T>(val value: T) : Discrete<T>

  companion object {
    fun <T> lift(op: (T, T) -> T): (Discrete<T>, Discrete<T>) -> Discrete<T> = { l, r ->
      when {
        l is Value && r is Value -> Value(op(l.value, r.value))
        l is Btm || r is Btm -> Btm
        else -> Top
      }
    }
  }
}

/**
 * Given a type [T], make a lattice by slapping in distinct elements [Discrete.Top] and
 * [Discrete.Btm], and the order between elements in [T] only comes from equality.
 */
class DiscreteLattice<T> : Lattice<Discrete<T>> {
  override val bottom = Discrete.Btm
  override val top = Discrete.Top

  override fun meetOf(first: Discrete<T>, second: Discrete<T>) =
    combine(id = Discrete.Top, overApprox = Discrete.Btm, first, second)

  override fun joinOf(first: Discrete<T>, second: Discrete<T>) =
    combine(id = Discrete.Btm, overApprox = Discrete.Top, first, second)

  override fun precede(first: Discrete<T>, second: Discrete<T>) =
    first is Discrete.Btm || second is Discrete.Top || first == second

  private fun combine(id: Discrete<T>, overApprox: Discrete<T>, l: Discrete<T>, r: Discrete<T>) =
    when {
      l == id -> r
      r == id -> l
      l == r -> l
      else -> overApprox
    }
}

private infix fun Boolean.implies(that: () -> Boolean) = !this || that()
