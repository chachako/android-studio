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

import com.google.common.truth.Truth.assertThat
import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.persistentMapOf
import org.junit.Test

class MonotoneTest {

  /*
   * Example 1: cyclic, unproductive dependency
   */

  @Test
  fun `unproductive cyclic dependency gives empty result as least fix point`() {
    val trivial = // f ↦ x ↦ f(x)
      object : Monotone<Unit, Unit>, Lattice<Unit> by UnitLattice {
        override fun invoke(rec: (Unit) -> Unit, x: Unit) = rec(x)
      }

    // We maintain a convention of not explicitly storing `⊥` entries,
    // so here we're asserting the empty table as the only valid representation.
    assertThat(trivial.leastFixPoint(Unit)).isEmpty()
  }

  /*
   * Example 2: cyclic (but productive) dependency
   */

  @Test
  fun `monotone over functions climbing up a finite-height lattice has a least fixpoint`() {
    val productivelyCyclic = // f ↦ x ↦ {(n+2)%10 | n ∈ f(x)} ⊔ {0}
      object :
        Monotone<Unit, UnboundedSet<Int>>, Lattice<UnboundedSet<Int>> by possibilityLattice() {
        override fun invoke(rec: (Unit) -> UnboundedSet<Int>, x: Unit) =
          rec(x).map { (it + 2) % 10 } join unboundedSetOf(0)
      }

    val m = productivelyCyclic.leastFixPoint(Unit)
    assertThat(m[Unit]!!).isEqualTo(unboundedSetOf(0, 2, 4, 6, 8))
  }

  /*
   * Example 3: fibonacci, effectively tabled
   */

  @Test
  fun `fibonacci effectively tabled`() {
    val fib =
      object : Monotone<Long, Discrete<Long>>, Lattice<Discrete<Long>> by DiscreteLattice() {
        override fun invoke(rec: (Long) -> Discrete<Long>, n: Long) =
          when {
            n < 2 -> Discrete.Value(n)
            else -> Discrete.lift(Long::plus)(rec(n - 1), rec(n - 2))
          }
      }

    val m = fib.leastFixPoint(100L)
    assertThat(m[100L]!!).isEqualTo(Discrete.Value(3736710778780434371))
  }

  /*
   * Example 4: lambda calculus concrete interpreter
   */

  private sealed interface Expr {
    data class Var(val name: String) : Expr

    data class App(val fn: Expr, val arg: Expr) : Expr

    data class Lam(val param: String, val body: Expr) : Expr

    data class Const(val n: Int) : Expr, Val

    data class Set(val lhs: String, val rhs: Expr) : Expr

    data class If(val scrutiny: Expr, val ifFun: Expr, val ifConst: Expr) : Expr

    companion object {
      // Syntactic sugar
      fun Let(lhs: String, rhs: Expr, body: Expr) = App(Lam(lhs, body), rhs)

      fun Sequence(vararg statements: Expr) =
        statements.reduceRightOrNull { init, rest -> Let("_", init, rest) } ?: Const(0)
    }
  }

  private sealed interface Val

  private data class Closure(val expr: Expr, val env: Map<String, Discrete<Val>>) : Val

  // This is the full *concrete* interpreter that doesn't guarantee termination, so
  // it's up to the individual tests to pick examples that don't build up unbounded fresh terms
  private val lcInterpreter =
    object : Monotone<Closure, Discrete<Val>>, Lattice<Discrete<Val>> by DiscreteLattice() {
      override fun invoke(
        assumeResultOn: (Closure) -> Discrete<Val>,
        context: Closure,
      ): Discrete<Val> {
        /**
         * Structural recursion on [context.expr], resorting to [assumeResultOn] for otherwise
         * non-structural recursion
         */
        fun loop(context: Closure): Discrete<Val> {
          val (expr, env) = context
          return when (expr) {
            is Expr.Var -> env[expr.name]!!
            is Expr.App -> {
              val fRes = loop(Closure(expr.fn, env))
              val xRes = loop(Closure(expr.arg, env))
              if (fRes !is Discrete.Value) return fRes
              val f = fRes.value as? Closure ?: return Discrete.Btm
              val e = f.expr as? Expr.Lam ?: return Discrete.Btm
              assumeResultOn(Closure(e.body, f.env + (e.param to xRes)))
            }
            is Expr.Lam -> Discrete.Value(Closure(expr, env))
            is Expr.Const -> Discrete.Value(expr)
            is Expr.Set -> throw UnsupportedOperationException("Need the store for mutable states")
            is Expr.If -> {
              val scrutiny = loop(Closure(expr.scrutiny, env))
              val scrutinyValue = (scrutiny as? Discrete.Value)?.value ?: return scrutiny
              loop(
                Closure(
                  when (scrutinyValue) {
                    is Expr.Const -> expr.ifConst
                    is Closure -> expr.ifFun
                  },
                  env,
                )
              )
            }
          }
        }
        return loop(context)
      }
    }

  private fun testEval(e: Expr, v: Discrete<Val>) =
    with(lcInterpreter) {
      val ctx = Closure(e, mapOf())
      val m = leastFixPoint(ctx)
      val r = m[ctx] ?: bottom
      assertThat(r).isEqualTo(v)
    }

  @Test
  fun `(λx, 42) 43 = 42`() =
    testEval(
      Expr.App(Expr.Lam("x", Expr.Const(42)), Expr.Const(43)),
      Discrete.Value(Expr.Const(42)),
    )

  @Test
  fun `(λx, x) 13 = 13`() =
    testEval(Expr.App(Expr.Lam("x", Expr.Var("x")), Expr.Const(13)), Discrete.Value(Expr.Const(13)))

  @Test
  fun `(λx, x x) (λx, x x) diverges`() =
    testEval(
      run {
        val x = Expr.Var("x")
        val om = Expr.Lam("x", Expr.App(x, x))
        Expr.App(om, om)
      },
      Discrete.Btm,
    )

  /*
   * Example 5: Hard-coded 0-CFA interpreter of lambda-calculus with mutable states
   */

  private data class State<out X>(
    val store: PersistentMap<String, UnboundedSet<Expr>>?,
    val value: X,
  )

  private val storeLattice = Lattice.pointWise<String, UnboundedSet<Expr>>(possibilityLattice())

  private val stateLattice =
    Lattice.product(
      ::State,
      State<UnboundedSet<Expr>>::store,
      State<UnboundedSet<Expr>>::value,
      storeLattice,
      possibilityLattice(),
    )

  // The abstract interpreter terminates on all programs.
  // For example, it finitely over-approximates unbounded closures as (spurious) cycles through
  // indirection in the store.
  private val monovariantLcInterpreter =
    object :
      Monotone<State<Expr>, State<UnboundedSet<Expr>>>,
      Lattice<State<UnboundedSet<Expr>>> by stateLattice {
      override fun invoke(
        rec: (State<Expr>) -> State<UnboundedSet<Expr>>,
        state: State<Expr>,
      ): State<UnboundedSet<Expr>> {
        val (store, expr) = state
        if (store == null) return top
        return when (expr) {
          is Expr.Var -> State(store, store[expr.name])
          is Expr.App -> {
            val (store1, func) = invoke(rec, State(store, expr.fn))
            val (store2, arg) = invoke(rec, State(store1, expr.arg))
            if (func == null) return top
            func.joinedOver { f ->
              when (f) {
                is Expr.Lam -> {
                  val alloc = persistentMapOf(f.param to arg)
                  rec(State(storeLattice.joinOf(store2, alloc), f.body))
                }
                else -> bottom
              }
            }
          }
          is Expr.Lam,
          is Expr.Const -> State(store, unboundedSetOf(expr))
          is Expr.Set -> {
            val (store1, rhs) = invoke(rec, State(store, expr.rhs))
            val update = persistentMapOf(expr.lhs to rhs)
            State(storeLattice.joinOf(store1, update), rhs)
          }
          is Expr.If -> {
            val (store1, scrutiny) = invoke(rec, State(store, expr.scrutiny))
            if (scrutiny == null) return top
            scrutiny.joinedOver { v ->
              invoke(rec, State(store1, if (v is Expr.Lam) expr.ifFun else expr.ifConst))
            }
          }
        }
      }
    }

  private fun testAbstractEval(e: Expr, resultState: State<UnboundedSet<Expr>>) {
    with(monovariantLcInterpreter) {
      val initState = State(persistentMapOf(), e)
      val result = leastFixPoint(initState)
      assertThat(resultState precedes (result[initState] ?: bottom)).isTrue()
    }
  }

  @Test
  fun `(λx, 42) 43 ⊑ {42}`() =
    testAbstractEval(
      Expr.App(Expr.Lam("x", Expr.Const(42)), Expr.Const(43)),
      State(persistentMapOf(/* ignored */ ), unboundedSetOf(Expr.Const(42))),
    )

  @Test
  fun `(λx, x) 13 ⊑ {13}`() =
    testAbstractEval(
      Expr.App(Expr.Lam("x", Expr.Var("x")), Expr.Const(13)),
      State(persistentMapOf(/* ignored */ ), unboundedSetOf(Expr.Const(13))),
    )

  @Test
  fun `(λx, x x) (λx, x x) ⊑ ⊥`() =
    testAbstractEval(
      run {
        val x = Expr.Var("x")
        val om = Expr.Lam("x", Expr.App(x, x))
        Expr.App(om, om)
      },
      stateLattice.bottom,
    )

  @Test
  fun `let f = (λx, x) in ((f f) 42) ⊑ {42, (λx, x)}`() {
    val id = Expr.Lam("x", Expr.Var("x"))
    val f = Expr.Var("f")
    testAbstractEval(
      Expr.Let("f", id, Expr.App(Expr.App(f, f), Expr.Const(42))),
      State(persistentMapOf(/* ignored */ ), unboundedSetOf(Expr.Const(42), id)),
    )
  }

  @Test
  fun `let (v = 42, x = 0) in (if isFun(v) x ← 1 else x ← 2, x) ⊑ {0, 2}`() {
    val v = Expr.Var("v")
    val a = Expr.Var("a")
    testAbstractEval(
      Expr.Let(
        "v",
        Expr.Const(42),
        Expr.Let(
          "a",
          Expr.Const(0),
          Expr.Sequence(Expr.If(v, Expr.Set("a", Expr.Const(1)), Expr.Set("a", Expr.Const(2))), a),
        ),
      ),
      State(persistentMapOf(/* ignored */ ), unboundedSetOf(Expr.Const(0), Expr.Const(2))),
    )
  }

  @Test
  fun `let (v = ●, x = 0) in (if isFun(v) x ← 1 else x ← 2, x) ⊑ {0, 1, 2}`() {
    val v = Expr.Var("v")
    val a = Expr.Var("a")
    val `●` = run {
      // ● can be either function or base constant, thanks to 0CFA's imprecision
      val id = Expr.Lam("x", Expr.Var("x"))
      val f = Expr.Var("f")
      Expr.Let("f", id, Expr.App(Expr.App(f, f), Expr.Const(42)))
    }
    testAbstractEval(
      Expr.Let(
        "v",
        `●`,
        Expr.Let(
          "a",
          Expr.Const(0),
          Expr.Sequence(Expr.If(v, Expr.Set("a", Expr.Const(1)), Expr.Set("a", Expr.Const(2))), a),
        ),
      ),
      State(
        persistentMapOf(/* ignored */ ),
        unboundedSetOf(Expr.Const(0), Expr.Const(1), Expr.Const(2)),
      ),
    )
  }
}
