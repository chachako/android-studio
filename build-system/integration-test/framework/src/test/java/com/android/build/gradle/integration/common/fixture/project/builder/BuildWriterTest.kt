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
    fun basicBlock() {
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
    fun groovyBlock() {
        val p = Person("John", "Doe")
        val writer =  p.writeBlock(groovy)

        Truth.assertThat(writer.toString()).isEqualTo("""
            Person {
              name = 'John'
              surname = 'Doe'
            }

        """.trimIndent())
    }

    private val kts: BuildWriter
        get() = KtsBuildWriter()
    private val groovy: BuildWriter
        get() = GroovyBuildWriter()

}
