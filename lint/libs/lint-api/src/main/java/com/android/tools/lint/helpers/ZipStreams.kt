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
package com.android.tools.lint.helpers

import java.io.EOFException
import java.io.InputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipInputStream

/**
 * Reads all the bytes for ZipEntry
 *
 * Method to avoid allocating temporary buffer, and byte array cloning when uncompressed size is
 * known and reasonably small.
 *
 * Inspired by java.util.jar.JarFile#getBytes
 *
 * @throws EOFException if stream length don't match ZipEntry size
 */
fun ZipFile.readAllBytes(entry: ZipEntry): ByteArray {
  this.getInputStream(entry).use { stream ->
    if (entry.size != -1L && entry.size <= 65535L) {
      return toByteArray(stream, entry.size.toInt())
    } else {
      return stream.readBytes()
    }
  }
}

/**
 * Reads all the bytes for ZipEntry
 *
 * @throws EOFException if stream length doesn't match ZipEntry size
 */
fun ZipInputStream.readAllBytes(entry: ZipEntry): ByteArray {
  return if (entry.size != -1L && entry.size <= 65535L) {
    toByteArray(this, entry.size.toInt())
  } else {
    this.readBytes()
  }
}

private fun toByteArray(stream: InputStream, expectedSize: Int): ByteArray {
  val bytes = ByteArray(expectedSize)
  val bytesRead = stream.readNBytes(bytes, 0, expectedSize)
  if (expectedSize != bytesRead) {
    throw EOFException("Expected:$expectedSize, read:$bytesRead")
  }
  if (stream.read() != -1) {
    throw EOFException("Expected:$expectedSize, but stream have extra bytes")
  }
  return bytes
}
