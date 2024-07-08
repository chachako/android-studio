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

package kexter.core

private fun UInt.toIntThrowing(): Int {
  if (this < UInt.MIN_VALUE || this > UInt.MAX_VALUE) {
    throw RuntimeException("Cannot convert $this to int")
  }

  return this.toInt()
}

private fun ByteArray.getUIntAt(index: UInt): UInt {
  val idx = index.toIntThrowing()
  return ((this[idx + 3].toUInt() and 0xFFu) shl 24) or
    ((this[idx + 2].toUInt() and 0xFFu) shl 16) or
    ((this[idx + 1].toUInt() and 0xFFu) shl 8) or
    (this[idx + 0].toUInt() and 0xFFu)
}

private fun ByteArray.getIntAt(index: UInt): Int {
  val idx = index.toIntThrowing()
  return ((this[idx + 3].toInt() and 0xFF) shl 24) or
    ((this[idx + 2].toInt() and 0xFF) shl 16) or
    ((this[idx + 1].toInt() and 0xFF) shl 8) or
    (this[idx + 0].toInt() and 0xFF)
}

private fun ByteArray.getUShortAt(index: UInt): UShort {
  val idx = index.toIntThrowing()
  return (((this[idx + 1].toUInt() and 0xFFu) shl 8) or ((this[idx + 0].toUInt() and 0xFFu) shl 0))
    .toUShort()
}

class DexReader(private val bytes: ByteArray, var position: UInt = 0u) {
  fun uLeb128(): UInt {
    val mask = 0x7Fu
    var result = ubyte().toUInt()
    if (result > mask) {
      var cur = ubyte().toUInt()
      result = (result and mask) or ((cur and mask) shl 7)
      if (cur > mask) {
        cur = ubyte().toUInt()
        result = result or ((cur and mask) shl 14)
        if (cur > mask) {
          cur = ubyte().toUInt()
          result = result or ((cur and mask) shl 21)
          if (cur > mask) {
            cur = ubyte().toUInt()
            result = result or ((cur and mask) shl 28)
          }
        }
      }
    }
    return result
  }

  fun span(): Span {
    return Span(uint(), uint())
  }

  fun bytes(size: UInt): ByteArray {
    val start = position.toIntThrowing()
    val end = start + size.toIntThrowing()
    position += size
    return bytes.sliceArray(start..<end)
  }

  fun ubyte(): UByte {
    val index = position.toInt()
    position++
    return bytes[index].toUByte()
  }

  fun ushort(): UShort {
    val value = bytes.getUShortAt(position)
    position += 2u
    return value
  }

  fun uint(): UInt {
    val value = bytes.getUIntAt(position)
    position += 4u
    return value
  }

  fun int(): Int {
    val value = bytes.getIntAt(position)
    position += 4u
    return value
  }

  fun skip(length: UInt) {
    position += length
  }
}
