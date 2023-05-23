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
package com.android.ide.common.gradle

import com.google.common.truth.Truth.assertThat
import org.junit.Assert.fail
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.ObjectInputStream
import java.io.ObjectOutputStream

class ComponentTest {

    @Test
    fun testParseAllPositions() {
        val letters = "abcdefghijklmnopqrstuvwxyz"
        for (i in 0..letters.length) {
            for (j in i..letters.length) {
                val id =
                    "${letters.substring(0, i)}:${letters.substring(i, j)}:${letters.substring(j)}"
                val component = Component.tryParse(id)
                assertThat(Component.parse(id)).isEqualTo(component)
                assertThat(component).isNotNull()
                assertThat(component?.group).isEqualTo(letters.substring(0, i))
                assertThat(component?.name).isEqualTo(letters.substring(i, j))
                assertThat(component?.version).isEqualTo(Version.parse(letters.substring(j)))
                assertThat(component?.toIdentifier()).isEqualTo(id)
                assertThat(component?.toString()).isEqualTo(id)
            }
        }
    }

    @Test
    fun testParseInvalidZeroColons() {
        val numbers = "0123456789"
        assertThat(Component.tryParse(numbers)).isNull()
        try {
            Component.parse(numbers)
            fail()
        } catch (_: IllegalArgumentException) {
        }
    }

    @Test
    fun testParseInvalidOneColon() {
        val numbers = "0123456789"
        for (i in 0..numbers.length) {
            val id = "${numbers.substring(0, i)}:${numbers.substring(i)}"
            assertThat(Component.tryParse(id)).isNull()
            try {
                Component.parse(id)
                fail()
            } catch (_: IllegalArgumentException) {
            }
        }
    }

    @Test
    fun testParseThreeColons() {
        val numbers = "0123456789"
        for (i in 0..numbers.length) {
            for (j in i..numbers.length) {
                for (k in j..numbers.length) {
                    val id =
                        numbers.run {
                            "${substring(0, i)}:${substring(i, j)}:${
                                substring(
                                    j,
                                    k
                                )
                            }:${substring(k)}"
                        }
                    val component = Component.tryParse(id)
                    assertThat(component).isNotNull()
                    assertThat(Component.parse(id)).isEqualTo(component)
                    assertThat(component?.group).isEqualTo(numbers.substring(0, i))
                    assertThat(component?.name).isEqualTo(numbers.substring(i, j))
                    assertThat(component?.version).isEqualTo(
                        Version.parse(
                            "${
                                numbers.substring(
                                    j,
                                    k
                                )
                            }:${numbers.substring(k)}"
                        )
                    )
                    assertThat(component?.toIdentifier()).isEqualTo(id)
                    assertThat(component?.toString()).isEqualTo(id)
                }
            }
        }
    }

    @Test
    fun testToStringInvalid() {
        val groups = listOf("abc", ":abc", "a:bc", "ab:c", "abc:")
        val names = listOf("123", ":123", "1:23", "12:3", "123:")
        for ((group, name) in groups.zip(names)) {
            if (!group.contains(':') && !name.contains(':')) continue
            val component = Component(Module(group, name), Version.parse("1.0"))
            assertThat(component.toIdentifier()).isNull()
            assertThat(component.toString()).matches("^Component\\(.*\\)$")
        }
        val component = Component("com.example", "foo", Version.prefixInfimum("1.0"))
        assertThat(component.toIdentifier()).isNull()
        assertThat(component.toString()).matches("^Component\\(.*\\)$")
    }

    @Test
    fun testSerialize() {
        val groups = listOf("abc", ":abc", "a:bc", "ab:c", "abc:")
        val names = listOf("123", ":123", "1:23", "12:3", "123:")
        for ((group, name) in groups.zip(names)) {
            val module = Module(group, name)
            val component = Component(module, Version.parse("1.0"))
            // Components with prefixInfima versions should never exist in practice, but
            // verify serialization nevertheless
            val prefixInfimumComponent = Component(module, Version.prefixInfimum("1.0"))

            val output = ByteArrayOutputStream()
            val objectOutput = ObjectOutputStream(output)
            objectOutput.writeObject(component)
            objectOutput.writeObject(prefixInfimumComponent)
            val byteArray = output.toByteArray()
            objectOutput.close()
            output.close()

            val input = ByteArrayInputStream(byteArray)
            val objectInput = ObjectInputStream(input)
            val deserializedComponent = objectInput.readObject()
            val deserializedPrefixInfimumComponent = objectInput.readObject()
            assertThat(deserializedComponent).isEqualTo(component)
            assertThat(deserializedPrefixInfimumComponent).isEqualTo(prefixInfimumComponent)
        }
    }
}
