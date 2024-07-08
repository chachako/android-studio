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

import java.nio.charset.StandardCharsets

// count can either be bytes or number of elements
data class Span(val count: UInt, val offset: UInt)

internal enum class EndianTag(val value: UInt) {
  ENDIAN_CONSTANT(0x12345678u),
  REVERSE_ENDIAN_CONSTANT(0x78563412u);

  internal companion object {
    fun fromUInt(value: UInt) = entries.first { it.value == value }
  }
}

private fun ByteArray.toHexString() = joinToString(",") { "0x%02x".format(it) }

private fun ByteArray.toSha1String() = joinToString("") { "%02x".format(it) }

private fun UInt.nice() = "%,d".format(this.toInt())

private const val MAGIC_PREFIX = "dex\n" // 0x64 0x65 0x78 0x0a
private const val MAGIC_SUFFIX = "\u0000"

internal class DexHeader(reader: DexReader) {
  val magic: ByteArray
  val checksum: UInt
  val sha1Hash: ByteArray
  val fileSize: UInt
  val headerSize: UInt
  val endianTag: EndianTag
  val link: Span
  val mapOffset: UInt
  val stringIds: Span
  val typeIds: Span
  val protoIds: Span
  val fieldIds: Span
  val methodsIds: Span
  val classDefs: Span
  val data: Span

  init {
    magic = reader.bytes(8u)
    val magicString = String(magic, StandardCharsets.UTF_8)
    if (!magicString.startsWith(MAGIC_PREFIX) || !magicString.endsWith(MAGIC_SUFFIX)) {
      throw IllegalStateException("Bad dex magic number ('${magic.toHexString()}')!")
    }

    // TODO Allow checksum
    checksum = reader.uint()
    // TODO Allow sha1 checksum
    sha1Hash = reader.bytes(20u)
    fileSize = reader.uint()
    headerSize = reader.uint()
    endianTag = EndianTag.fromUInt(reader.uint())
    link = reader.span()
    mapOffset = reader.uint()
    stringIds = reader.span()
    typeIds = reader.span()
    protoIds = reader.span()
    fieldIds = reader.span()
    methodsIds = reader.span()
    classDefs = reader.span()
    data = reader.span()

    // Dex header grows with new features. Keep track of
    // what we may not have parsed.
    val extraBytes = headerSize - reader.position

    reader.skip(headerSize)

    // Logging
    // println("Magic      : '${magic.toHexString()}'")
    // println("Checksum   : $checksum")
    // println("Sha1 hash  : ${sha1Hash.toSha1String()}")
    // println("File size  : ${fileSize.nice()}")
    // println("Header size: ${fileSize.nice()}")
    // println("Endian Tag : $endianTag")
    // println("Link       : $link")
    // println("Map offset : $mapOffset")
    // println("String ids : $stringIds")
    // println("Type ids   : $typeIds")
    // println("Proto ids  : $protoIds")
    // println("Field ids  : $fieldIds")
    // println("Method ids : $methodsIds")
    // println("Class defs : $classDefs")
    // println("Data       : $data")
    // println("Not parsed : $extraBytes")
  }
}
