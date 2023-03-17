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

class ModuleTest {
    @Test
    fun testParseAscii() {
        val ascii = (32..126).map { Char(it) }.joinToString("")
        val module = Module.tryParse(ascii)
        assertThat(Module.parse(ascii)).isEqualTo(module)
        assertThat(module).isNotNull()
        assertThat(module?.group).isEqualTo(" !\"#$%&'()*+,-./0123456789")
        assertThat(module?.name).isEqualTo(";<=>?@ABCDEFGHIJKLMNOPQRSTUVWXYZ[\\]^_`abcdefghijklmnopqrstuvwxyz{|}~")
        assertThat(module?.toIdentifier()).isEqualTo(ascii)
        assertThat(module?.toString()).isEqualTo(ascii)
    }

    @Test
    fun testParseAllPositions() {
        val letters = "abcdefghijklmnopqrstuvwxyz"
        for (i in 0..letters.length) {
            val id = "${letters.substring(0, i)}:${letters.substring(i)}"
            val module = Module.tryParse(id)
            assertThat(Module.parse(id)).isEqualTo(module)
            assertThat(module).isNotNull()
            assertThat(module?.group).isEqualTo(letters.substring(0, i))
            assertThat(module?.name).isEqualTo(letters.substring(i))
            assertThat(module?.toIdentifier()).isEqualTo(id)
            assertThat(module?.toString()).isEqualTo(id)
        }
    }

    @Test
    fun testParseInvalidNoColons() {
        val numbers = "0123456789"
        val module = Module.tryParse(numbers)
        assertThat(module).isNull()
        try {
            Module.parse(numbers)
            fail()
        }
        catch (_: IllegalArgumentException) { }
    }

    @Test
    fun testParseInvalidTooManyColons() {
        val numbers = "0123456789"
        for (i in 0 .. numbers.length) {
            for (j in i .. numbers.length) {
                val invalid = "${numbers.substring(0, i)}:${numbers.substring(i, j)}:${numbers.substring(j)}"
                val module = Module.tryParse(invalid)
                assertThat(module).isNull()
                try {
                    Module.parse(invalid)
                    fail()
                }
                catch(_: IllegalArgumentException) { }
            }
        }
    }

    @Test
    fun testToStringInvalid() {
        val groups = listOf("abc", ":abc", "a:bc", "ab:c", "abc:")
        val names = listOf("123", ":123", "1:23", "12:3", "123:")
        for ((group, name) in groups.zip(names)) {
            if (!group.contains(':') && !name.contains(':')) continue
            val module = Module(group, name)
            assertThat(module.toIdentifier()).isNull()
            assertThat(module.toString()).matches("^Module\\(.*\\)$")
        }
    }
}
