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

package com.android.build.gradle.integration.common.fixture.project.builder

import com.google.common.truth.Truth
import org.junit.Test

class BuildWriterTest {

    @Test
    fun setString() {
        Truth.assertThat(kts.set("foo", "bar").toString()).isEqualTo("foo = \"bar\"\n")
    }

    data class Person(
        val name: String,
        val surname: String
    ) {
        fun writeBlock(writer: BuildWriter): BuildWriter {
            writer.block("Person", this) { it->
                set("name", it.name)
                set("surname", it.surname)
            }

            return writer
        }
    }

    @Test
    fun ktsStrings() {
        val p = Person("John", "Doe")
        val writer =  p.writeBlock(kts)

        Truth.assertThat(writer.toString()).isEqualTo("""
            Person {
              name = "John"
              surname = "Doe"
            }

        """.trimIndent())
    }

    @Test
    fun groovyStrings() {
        val p = Person("John", "Doe")
        val writer =  p.writeBlock(groovy)

        Truth.assertThat(writer.toString()).isEqualTo("""
            Person {
              name = 'John'
              surname = 'Doe'
            }

        """.trimIndent())
    }

    @Test
    fun testPlugins() {
        testWriterOutput(expected = """
            id("plugin1") version "3.2"
            id("plugin2")
            id("plugin3") version "1.3" apply false
            id("plugin4") apply false

        """.trimIndent()) {
            pluginId("plugin1", "3.2", true)
            pluginId("plugin2", null)
            pluginId("plugin3", "1.3", false)
            pluginId("plugin4", null, false)
        }
    }

    @Test
    fun testRawString() {
        testWriterOutput(expected = """
            a = b
            b = foo(12)

        """.trimIndent()) {
            set("a", rawString("b"))
            set("b", rawMethod("foo", 12))
        }
    }

    @Test
    fun testMethods() {
        testWriterOutput(expected = """
            foo("bar")
            foo("bar", 12, false)
            foo(bar(12, false))

        """.trimIndent()) {
            method("foo", "bar")
            method("foo", "bar", 12, false)
            method("foo", rawMethod("bar", 12, false))
        }
    }

    @Test
    fun testNamedMethodsInKTS() {
        testWriterOutput(expected = """
            foo(bar = 12, something = false)
            foo(bar = 12, something = bar(value = 12))

        """.trimIndent(), kts) {
            method("foo", listOf("bar" to 12, "something" to false))
            method("foo", listOf("bar" to 12, "something" to rawMethod("bar", listOf("value" to 12))))
        }
    }

    @Test
    fun testNamedMethodsInGroovy() {
        testWriterOutput(expected = """
            foo(bar: 12, something: false)
            foo(bar: 12, something: bar(value: 12))

        """.trimIndent(), groovy) {
            method("foo", listOf("bar" to 12, "something" to false))
            method("foo", listOf("bar" to 12, "something" to rawMethod("bar", listOf("value" to 12))))
        }
    }

    private fun testWriterOutput(expected: String, writer: BuildWriter = kts, action: BuildWriter.() -> Unit) {
        action(writer)
        Truth.assertThat(writer.toString()).isEqualTo(expected)
    }

    private val kts: BuildWriter
        get() = KtsBuildWriter()
    private val groovy: BuildWriter
        get() = GroovyBuildWriter()

}
