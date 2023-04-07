package com.android.utils

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.io.Closeable

/**
 * Tests for CloseableUtils.kt
 *
 * The testing methodology uses a list of string as a log of events. Each entry in the log
 * represents an event such as an object creation, an object being closed etc. This allows us to
 * easily assert on the order of events as well as their existence.
 *
 * We use closable objects that depend on each other to be constructed. This is done to both
 * demonstrate this capability and to ensure it isn't lost if any future refactoring may take
 * place. A trivial test case of independent resources is also provided for completion.
 */
class CloseableUtilsKtTest {

    private val log = mutableListOf<String>()

    @Test
    fun twoResources_success() {
        withResources(A(7), { B(it) }) { a, b ->
            log.add("Action with $a and $b")
        }

        assertThat(log).containsExactly(
            "A created",
            "B created",
            "Action with a(7) and b(a(7))",
            "B closed",
            "A closed",
        ).inOrder()
    }

    @Test
    fun twoResources_firstThrows() {
        try {
            withResources(A(7, throwOnInit = true), { B(it) }) { a, b ->
                log.add("Action with $a and $b")
            }
        } catch (e: Exception) {
            log.add("Exception")
        }

        assertThat(log).containsExactly(
            "Exception",
        ).inOrder()
    }

    @Test
    fun twoResources_secondThrows() {
        try {
            withResources(A(7), { B(it, throwOnInit = true) }) { a, b ->
                log.add("Action with $a and $b")
            }
        } catch (e: Exception) {
            log.add("Exception")
        }

        assertThat(log).containsExactly(
            "A created",
            "A closed",
            "Exception",
        ).inOrder()
    }

    @Test
    fun twoResources_independent() {
        withResources(A(1), { A(2) }) { a1, a2 ->
            log.add("Action with $a1 and $a2")
        }

        assertThat(log).containsExactly(
            "A created",
            "A created",
            "Action with a(1) and a(2)",
            "A closed",
            "A closed",
        ).inOrder()
    }

    @Test
    fun threeResources_success() {
        withResources(A(7), { B(it) }, { (a, b) -> C(a, b) }) { a, b, c ->
            log.add("Action with $a, $b and $c")
        }

        assertThat(log).containsExactly(
            "A created",
            "B created",
            "C created",
            "Action with a(7), b(a(7)) and c(a(7), b(a(7)))",
            "C closed",
            "B closed",
            "A closed",
        ).inOrder()
    }

    @Test
    fun threeResources_firstThrows() {
        try {
            withResources(A(7, throwOnInit = true), { B(it) }, { (a, b) -> C(a, b) }) { a, b, c ->
                log.add("Action with $a, $b and $c")
            }
        } catch (e: Exception) {
            log.add("Exception")
        }

        assertThat(log).containsExactly(
            "Exception",
        ).inOrder()
    }

    @Test
    fun threeResources_secondThrows() {
        try {
            withResources(A(7), { B(it, throwOnInit = true) }, { (a, b) -> C(a, b) }) { a, b, c ->
                log.add("Action with $a, $b and $c")
            }
        } catch (e: Exception) {
            log.add("Exception")
        }

        assertThat(log).containsExactly(
            "A created",
            "A closed",
            "Exception",
        ).inOrder()
    }

    @Test
    fun threeResources_thirdThrows() {
        try {
            withResources(A(7), { B(it) }, { (a, b) -> C(a, b, throwOnInit = true) }) { a, b, c ->
                log.add("Action with $a, $b and $c")
            }
        } catch (e: Exception) {
            log.add("Exception")
        }

        assertThat(log).containsExactly(
            "A created",
            "B created",
            "B closed",
            "A closed",
            "Exception",
        ).inOrder()
    }

    @Test
    fun threeResources_independent() {
        withResources(A(1), { A(2) }, { A(3) }) { a1, a2, a3 ->
            log.add("Action with $a1, $a2 and $a3")
        }

        assertThat(log).containsExactly(
            "A created",
            "A created",
            "A created",
            "Action with a(1), a(2) and a(3)",
            "A closed",
            "A closed",
            "A closed",
        ).inOrder()
    }

    private inner class A(private val id: Int, throwOnInit: Boolean = false) : Closeable {
        init {
            if (throwOnInit) {
                throw RuntimeException()
            }
            log.add("A created")
        }

        override fun close() {
            log.add("A closed")
        }

        override fun toString(): String = "a($id)"
    }

    private inner class B(private val a: A, throwOnInit: Boolean = false) : Closeable {
        init {
            if (throwOnInit) {
                throw RuntimeException()
            }
            log.add("B created")
        }

        override fun close() {
            log.add("B closed")
        }

        override fun toString(): String = "b($a)"
    }

    private inner class C(
        private val a: A,
        private val b: B,
        throwOnInit: Boolean = false
    ) : Closeable {

        init {
            if (throwOnInit) {
                throw RuntimeException()
            }
            log.add("C created")
        }

        override fun close() {
            log.add("C closed")
        }

        override fun toString(): String = "c($a, $b)"
    }
}
