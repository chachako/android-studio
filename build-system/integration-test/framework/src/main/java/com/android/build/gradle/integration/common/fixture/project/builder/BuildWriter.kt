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

/**
 * An object that can write a Gradle build file.
 *
 * It contains a list of methods that can be called to write specific instructions.
 *
 * The API is not meant to write arbitrary strings into the file.
 */
interface BuildWriter {
    /** Affectation: a = b */
    fun set(name: String, value: String): BuildWriter
    /** Affectation: a = b */
    fun set(name: String, value: Boolean): BuildWriter
    /** Affectation: a = b */
    fun set(name: String, value: Int): BuildWriter
    /** Affectation: a = null */
    fun setNull(name: String): BuildWriter

    fun method(name: String, vararg param: Any)

    /** Writes a block with a sub item */
    fun <T> block(name: String, item: T, action: BuildWriter.(T) -> Unit): BuildWriter
    fun block(name: String, action: BuildWriter.() -> Unit): BuildWriter
}

/**
 * A class that handles writing indented lines into a file.
 *
 * It's not meant to be used directly. See [BaseBuildWriter]
 */
internal abstract class IndentHandler(protected val indentLevel: Int) {

    private val buffer = StringBuilder()
    private var lineEnded = true

    protected fun indent(): IndentHandler {
        if (!lineEnded) throw RuntimeException("BuildWriter Line not ended")
        lineEnded = false
        for (i in 1..indentLevel) {
            buffer.append(' ')
        }
        return this
    }

    fun endLine() {
        buffer.append('\n')
        lineEnded = true
    }

    fun put(value: String): IndentHandler {
        buffer.append(value)
        return this
    }

    fun put(value: Boolean): IndentHandler {
        buffer.append(value)
        return this
    }

    fun put(value: Int): IndentHandler {
        buffer.append(value)
        return this
    }

    fun flatten(block: IndentHandler) {
        buffer.append(block.toString())
    }

    override fun toString(): String {
        if (!lineEnded) throw RuntimeException("BuildWriter Line not ended")
        return buffer.toString()
    }
}

/**
 * Base [BuildWriter] implementation over [IndentHandler].
 *
 * This is not meant to be used directly. see [KtsBuildWriter] or [GroovyBuildWriter].
 */
internal abstract class BaseBuildWriter(indentLevel: Int): IndentHandler(indentLevel), BuildWriter {

    protected abstract fun quoteString(value: String): String
    protected abstract fun newBuilder(indentLevel: Int): BaseBuildWriter

    override fun set(name: String, value: String): BuildWriter {
        indent().put(name).put(" = ").put(quoteString(value)).endLine()
        return this
    }

    override fun set(name: String, value: Boolean): BuildWriter {
        indent().put(name).put(" = ").put(value).endLine()
        return this
    }

    override fun set(name: String, value: Int): BuildWriter {
        indent().put(name).put(" = ").put(value).endLine()
        return this
    }

    override fun setNull(name: String): BuildWriter {
        indent().put(name).put(" = null").endLine()
        return this
    }

    override fun method(name: String, vararg param: Any) {
        val writer = indent().put(name).put("(")

        param.joinToString(separator = ", ") { it ->
            if (it is String) {
                quoteString(it)
            } else {
                it.toString()
            }
        }

        writer.put(")").endLine()
    }


    override fun <T> block(
        name: String,
        item: T,
        action: BuildWriter.(T) -> Unit
    ): BuildWriter {
        val blockBuilder = newBuilder(indentLevel + 2)

        indent().put(name).put(" {").endLine()
        action(blockBuilder, item)
        flatten(blockBuilder)

        indent().put("}").endLine()
        return this
    }

    override fun block(
        name: String,
        action: BuildWriter.() -> Unit
    ): BuildWriter {
        val blockBuilder = newBuilder(indentLevel + 2)

        indent().put(name).put(" {").endLine()
        action(blockBuilder)
        flatten(blockBuilder)

        indent().put("}").endLine()
        return this
    }
}

internal class KtsBuildWriter(indentLevel: Int = 0): BaseBuildWriter(indentLevel) {
    override fun newBuilder(indentLevel: Int): BaseBuildWriter = KtsBuildWriter(indentLevel)

    override fun quoteString(value: String): String {
        return "\"$value\""
    }
}

internal class GroovyBuildWriter(indentLevel: Int = 0): BaseBuildWriter(indentLevel) {
    override fun newBuilder(indentLevel: Int): BaseBuildWriter = GroovyBuildWriter(indentLevel)

    override fun quoteString(value: String): String {
        return "'$value'"
    }
}
