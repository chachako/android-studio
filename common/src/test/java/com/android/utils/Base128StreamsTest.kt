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
package com.android.utils

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import kotlin.test.assertFailsWith

/** Tests the [Base128OutputStream] class. */
@RunWith(JUnit4::class)
class Base128StreamsTest {
  private val byteStream = ByteArrayOutputStream()
  private val output = Base128OutputStream(byteStream)

  @Test
  fun readWriteInt() {
    val ints = buildList {
      (7..28 step 7).forEach { add((1 shl it) - 1) }
      add((1 shl 31) - 1)
      add(-42)
    }
    ints.forEach { output.writeInt(it) }
    output.flush()

    assertThat(byteStream.size()).isEqualTo(1 + 2 + 3 + 4 + 5 + 5)

    val input = getInputStream()
    ints.forEach { assertThat(input.readInt()).isEqualTo(it) }
  }

  @Test
  fun readWriteChar() {
    val str = "this is a great test! ¯\\_(ツ)_/¯"
    str.toList().forEach { output.writeChar(it) }
    output.flush()

    assertThat(byteStream.size()).isEqualTo(str.length + 3)  // 3 nonstd chars: ¯, ツ, ¯

    val input = getInputStream()
    str.forEach { assertThat(input.readChar()).isEqualTo(it) }
  }

  @Test
  fun readWriteLong() {
    val longs = buildList {
      (7..63 step 7).forEach { add((1L shl it) - 1) }
      add(-42L)
    }
    longs.forEach { output.writeLong(it) }
    output.flush()

    assertThat(byteStream.size()).isEqualTo(1 + 2 + 3 + 4 + 5 + 6 + 7 + 8 + 9 + 10)

    val input = getInputStream()
    longs.forEach { assertThat(input.readLong()).isEqualTo(it) }
  }

  @Test
  fun readWriteFloat() {
    val floats = listOf(2.71828f, 3.14159f, 1.61803f, 1.41421f)
    floats.forEach { output.writeFloat(it) }
    output.flush()

    assertThat(byteStream.size()).isEqualTo(floats.size * 4)  // These are 4 bytes each

    val input = getInputStream()
    floats.forEach { assertThat(input.readFloat()).isEqualTo(it) }
  }

  @Test
  fun readWriteFixed32() {
    val values = listOf(
      0b10101010101010101010101010101010u,
      0b01010101010101010101010101010101u,
      0b11111111111111111111111111111111u,
      )
    values.forEach { output.writeFixed32(it.toInt()) }
    output.flush()

    assertThat(byteStream.size()).isEqualTo(values.size * 4)  // These are 4 bytes each

    val input = getInputStream()
    values.forEach { assertThat(input.readFixed32().toUInt()).isEqualTo(it) }
  }

  @Test
  fun readWriteString() {
    val strings = listOf("I", "said", "come", "on", "fhqwhgads")
    strings.forEach { output.writeString(it) }
    output.flush()

    // All short strings of "normal" chars so 1 byte to encode the length plus 1 byte per char.
    assertThat(byteStream.size()).isEqualTo(strings.size + strings.sumOf { it.length })

    val input = getInputStream()
    strings.forEach { assertThat(input.readString()).isEqualTo(it) }
  }

  @Test
  fun readStringCache_enabled() {
    val strings = listOf("I", "I", "often", "repeat", "repeat", "repeat", "myself", "myself")
    strings.forEach { output.writeString(it) }
    output.flush()

    val cache: MutableMap<String, String> = mutableMapOf()
    val input = getInputStream()
    input.setStringCache(cache)

    val readStrings = buildList(strings.size) {
      repeat(strings.size) { add(input.readString()) }
    }

    assertThat(cache).hasSize(4)
    readStrings.forEach { assertThat(it).isSameAs(cache[it]) }
  }

  @Test
  fun readStringCache_disabled() {
    val strings = listOf("I", "I", "often", "repeat", "repeat", "repeat", "myself", "myself")
    strings.forEach { output.writeString(it) }
    output.flush()

    val input = getInputStream()

    val readStrings = buildList(strings.size) {
      repeat(strings.size) { add(input.readString()) }
    }

    // Pairwise compare all strings. The only ones that should be equal are the self-comparisons.
    assertThat(readStrings.zip(readStrings).count { it.first == it.second }).isEqualTo(strings.size)
  }

  @Test
  fun readWriteByte() {
    val bytes = "gobbledygook".toByteArray().toList()
    bytes.forEach { output.writeByte(it) }
    output.flush()

    assertThat(byteStream.size()).isEqualTo(bytes.size)

    val input = getInputStream()
    bytes.forEach { assertThat(input.readByte()).isEqualTo(it) }
  }

  @Test
  fun readWriteBytes() {
    val byteArrays = listOf("Zoom", "Schwartz", "Profigliano").map { it.toByteArray() }
    byteArrays.forEach { output.writeBytes(it) }
    output.flush()

    // One byte for length, plus the bytes themselves.
    assertThat(byteStream.size()).isEqualTo(byteArrays.size + byteArrays.sumOf { it.size })

    val input = getInputStream()
    byteArrays.forEach { assertThat(input.readBytes()).isEqualTo(it) }
  }

  @Test
  fun readWriteBoolean() {
    val booleans = listOf(true, false, false, true, false)
    booleans.forEach { output.writeBoolean(it) }
    output.flush()

    assertThat(byteStream.size()).isEqualTo(booleans.size)

    val input = getInputStream()
    booleans.forEach { assertThat(input.readBoolean()).isEqualTo(it) }
  }

  @Test
  fun writeThrowsUnsupportedOperationException() {
    assertFailsWith<UnsupportedOperationException> {
      output.write(3)
    }
  }

  private fun getInputStream() = Base128InputStream(ByteArrayInputStream(byteStream.toByteArray()))
}
