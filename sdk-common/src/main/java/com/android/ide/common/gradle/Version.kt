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
package com.android.ide.common.gradle

import com.google.common.annotations.Beta
import java.lang.IllegalArgumentException
import java.util.Locale
import java.util.Objects

/**
 * This class represents a single version with the semantics of version comparison as
 * specified by Gradle.  It explicitly does not represent:
 * - prefix includes (as designated by +) or prefix excludes (from an exclusive upper bound)
 * - ranges of any kind;
 * - the strictly, require, prefer (and reject) hierarchy of version constraints;
 * - any distinction between separator characters, except preserving them as constructed.
 *
 * Think of [Version] as representing a single point on the version line.
 */
@Beta
class Version: Comparable<Version> {
    // TODO:
    // - sentinel least-prefix version for prefix excludes (to allow range representation)
    // - restartable parser (for re-use in parsing version ranges etc.)
    // - base version extraction (for conflict resolution)
    private val parts: List<Part>
    private val separators: List<Separator>

    private constructor(parts: List<Part>, separators: List<Separator>) {
        if (parts.size != separators.size) throw IllegalArgumentException()
        this.parts = parts
        this.separators = separators
    }

    override fun compareTo(other: Version): Int {
        val partsComparisons = parts.zip(other.parts).map { it.first.compareTo(it.second) }
        partsComparisons.firstOrNull { it != 0 }?.let { return it }
        if (parts.size == other.parts.size) return 0
        return if (parts.size > other.parts.size) when (parts[other.parts.size]) {
            is Numeric -> 1
            else -> -1
        }
        else when (other.parts[parts.size]) {
            is Numeric -> -1
            else -> 1
        }
    }
    override fun equals(other: Any?) = when(other) {
        is Version -> this.parts == other.parts
        else -> false
    }
    override fun hashCode() = Objects.hash(this.parts)

    override fun toString() =
        parts.zip(separators) { part, separator -> "$part$separator" }.joinToString(separator = "")

    companion object {
        sealed interface ParseState {
            fun createPart(sb: StringBuffer): Part
            object EMPTY: ParseState {
                override fun createPart(sb: StringBuffer): Part {
                    return NONNUMERIC.createPart(sb)
                }
            }
            object NUMERIC: ParseState {
                override fun createPart(sb: StringBuffer): Part {
                    val string = sb.toString()
                    return Numeric(string, string.toInt())
                }
            }
            object NONNUMERIC: ParseState {
                override fun createPart(sb: StringBuffer): Part {
                    val string = sb.toString()
                    return when (string.lowercase(Locale.US)) {
                        "dev" -> DEV(string)
                        "rc" -> RC(string)
                        "snapshot" -> SNAPSHOT(string)
                        "final" -> FINAL(string)
                        "ga" -> GA(string)
                        "release" -> RELEASE(string)
                        "sp" -> SP(string)
                        else -> NonNumeric(string)
                    }
                }
            }
        }
        /**
         * Parse a string corresponding to an exact version (as defined by Gradle).  The result
         * is Comparable, implementing the ordering described in the Gradle user guide under
         * "Version ordering", compatible with determining whether a particular version is
         * included in a range (but not, directly, implementing the concept of "base version"
         * used in conflict resolution).
         */
        fun parse(string: String): Version {
            val sb = StringBuffer()
            val parts = mutableListOf<Part>()
            val separators = mutableListOf<Separator>()
            var parseState: ParseState = ParseState.EMPTY
            for (char in string) {
                val nextParseState = when {
                    char in '0'..'9' -> ParseState.NUMERIC
                    Separator.values().mapNotNull { it.char }.contains(char) -> ParseState.EMPTY
                    else -> ParseState.NONNUMERIC
                }
                if (nextParseState == ParseState.EMPTY) {
                    parts.add(parseState.createPart(sb))
                    separators.add(Separator.values().first { it.char == char })
                    sb.setLength(0)
                }
                else {
                    if (parseState == nextParseState || parseState == ParseState.EMPTY) {
                        sb.append(char)
                    }
                    else {
                        parts.add(parseState.createPart(sb))
                        separators.add(Separator.EMPTY)
                        sb.setLength(0)
                        sb.append(char)
                    }
                }
                parseState = nextParseState
            }
            parts.add(parseState.createPart(sb))
            separators.add(Separator.EMPTY)
            return Version(parts, separators)
        }
    }
}

enum class Separator(val char: Char?) {
    EMPTY(null),
    DOT('.'),
    DASH('-'),
    UNDERSCORE('_'),
    PLUS('+'),

    ;
    override fun toString() = char?.let { "$it" } ?: ""
}

sealed class Part(protected val string: String) : Comparable<Part> {
    override fun toString() = string
}

class DEV(string: String) : Part(string) {
    override fun compareTo(other: Part) = if (other is DEV) 0 else -1
    override fun equals(other: Any?) = other is DEV
    override fun hashCode() = Objects.hashCode("dev")
}

class NonNumeric(string: String) : Part(string) {
    override fun compareTo(other: Part) = when (other) {
        is DEV -> 1
        is Special, is Numeric -> -1
        is NonNumeric -> this.string.compareTo(other.string)
    }
    override fun equals(other: Any?) = when(other) {
        is NonNumeric -> this.string == other.string
        else -> false
    }
    override fun hashCode() = Objects.hash(this.string)
}

sealed class Special(string: String, val ordinal: Int) : Part(string) {
    override fun compareTo(other: Part) = when (other) {
        is Special -> this.ordinal.compareTo(other.ordinal)
        is Numeric -> -1
        is DEV, is NonNumeric -> 1
    }
    override fun equals(other: Any?) = when(other) {
        is Special -> this.ordinal == other.ordinal
        else -> false
    }
    override fun hashCode() = Objects.hash(this.ordinal)
}

class RC(string: String): Special(string, 0)
class SNAPSHOT(string: String): Special(string, 1)
class FINAL(string: String): Special(string, 2)
class GA(string: String): Special(string, 3)
class RELEASE(string: String): Special(string, 4)
class SP(string: String): Special(string, 5)

class Numeric(string: String, val number: Int) : Part(string) {
    override fun compareTo(other: Part) = when (other) {
        is Numeric -> this.number.compareTo(other.number)
        is DEV, is NonNumeric, is Special -> 1
    }
    override fun equals(other: Any?) = when (other) {
        is Numeric -> this.number == other.number
        else -> false
    }
    override fun hashCode() = Objects.hashCode(this.number)
}
