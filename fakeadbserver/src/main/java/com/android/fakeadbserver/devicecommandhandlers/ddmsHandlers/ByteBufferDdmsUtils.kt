/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.fakeadbserver.devicecommandhandlers.ddmsHandlers

import java.nio.ByteBuffer

internal fun ByteBuffer.readInt(): Int {
    return int
}

fun ByteBuffer.readLengthPrefixedString(): String {
    val length = int
    val chars = CharArray(length)
    for(index in 0 until length) {
        chars[index] = this.char
    }
    return String(chars)
}

internal fun ByteBuffer.readBooleanInt(): Boolean {
    return int != 0
}

internal fun ByteBuffer.putDdmString(value: String) {
    putInt(value.length)
    value.forEach { ch -> putChar(ch) }
}

internal fun String.ddmByteCount(): Int {
    return 4 + length * 2
}

internal fun List<String>.ddmByteCount(): Int {
    return this.fold(0) { acc, value -> acc + value.ddmByteCount() }
}

