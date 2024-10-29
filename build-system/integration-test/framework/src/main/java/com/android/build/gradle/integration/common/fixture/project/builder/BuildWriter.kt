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
    /** Affectation: a = b */
    fun set(name: String, value: RawString): BuildWriter
    /** Affectation: a = null */
    fun setNull(name: String): BuildWriter

    /** Calls a method with the given parameters */
    fun method(name: String, vararg params: Any)
    /** Calls a method with named parameters */
    fun method(name: String, params: List<Pair<String, Any>>)
    fun pluginId(id: String, version: String?, apply: Boolean = true)
    fun dependency(scope: String, value:Any)

    /** Build a [RawString] from a string. */
    fun rawString(value: String): RawString
    /** Build a [RawString] from a method call. */
    fun rawMethod(name: String, vararg params: Any): RawString
    /** Build a [RawString] from a method call. */
    fun rawMethod(name: String, params: List<Pair<String, Any>>): RawString

    /** Writes a block with a sub item */
    fun <T> block(name: String, item: T, action: BuildWriter.(T) -> Unit): BuildWriter
    fun block(name: String, action: BuildWriter.() -> Unit): BuildWriter

    /** Returns the file name of the build file for this writer */
    val buildFileName: String
    /** Returns the file name of the settings file for this writer */
    val settingsFileName: String

    /**
     * A String that is not quoted when written into the file
     */
    data class RawString(val value: String)
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

    fun put(value: Char): IndentHandler {
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

    override fun set(name: String, value: BuildWriter.RawString): BuildWriter {
        indent().put(name).put(" = ").put(value.value).endLine()
        return this
    }

    override fun setNull(name: String): BuildWriter {
        indent().put(name).put(" = null").endLine()
        return this
    }

    override fun method(name: String, vararg params: Any) {
        indent()
            .put(name)
            .put('(').put(concatMethodParams(*params)).put(')')
            .endLine()
    }

    override fun method(name: String, params: List<Pair<String, Any>>) {
        val namedParams = toNamedParams(params)
        indent()
            .put(name)
            .put('(').put(namedParams.joinToString(separator = ", ")).put(')')
            .endLine()
    }

    override fun pluginId(id: String, version: String?, apply: Boolean) {
        val writer = indent()
            .put("id(")
            .put(quoteString(id))
            .put(')')

        version?.let {
            writer.put(" version ").put(quoteString(it))
        }

        if (!apply) {
            writer.put(" apply false")
        }

        writer.endLine()
    }

    override fun dependency(scope: String, value: Any) {
        method(scope, value)
    }

    override fun rawString(value: String) = BuildWriter.RawString(value)

    override fun rawMethod(name: String, vararg params: Any): BuildWriter.RawString {
        val sb = StringBuilder()
        sb.append(name).append('(').append(concatMethodParams(*params)).append(')')
        return BuildWriter.RawString(sb.toString())
    }

    override fun rawMethod(name: String, params: List<Pair<String, Any>>): BuildWriter.RawString {
        val namedParams = toNamedParams(params)

        val sb = StringBuilder()
        sb.append(name).append('(').append(namedParams.joinToString(separator = ", ")).append(')')
        return BuildWriter.RawString(sb.toString())
    }

    private fun concatMethodParams(vararg params: Any): String {
        return params.joinToString(separator = ", ") { it ->
            handleParam(it)
        }
    }

    private fun handleParam(it: Any) = when (it) {
        is String -> quoteString(it)
        is BuildWriter.RawString -> it.value
        else -> it.toString()
    }

    abstract fun namedParam(name: String): String

    private fun toNamedParams(params: List<Pair<String, Any>>): List<String> {
        return params.map { it ->
            namedParam(it.first) + handleParam(it.second)
        }
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

        indent().put('}').endLine()
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

        indent().put('}').endLine()
        return this
    }
}

internal class KtsBuildWriter(indentLevel: Int = 0): BaseBuildWriter(indentLevel) {
    override fun newBuilder(indentLevel: Int): BaseBuildWriter = KtsBuildWriter(indentLevel)

    override fun quoteString(value: String): String {
        return "\"$value\""
    }

    override fun namedParam(name: String): String = "$name = "

    override val buildFileName: String
        get() = "build.gradle.kts"
    override val settingsFileName: String
        get() = "settings.gradle.kts"
}

internal class GroovyBuildWriter(indentLevel: Int = 0): BaseBuildWriter(indentLevel) {
    override fun newBuilder(indentLevel: Int): BaseBuildWriter = GroovyBuildWriter(indentLevel)

    override fun quoteString(value: String): String {
        return "'$value'"
    }

    /**
     * This is actually a map notation. This is used only(?) for the project() call
     * in dependencies where it's a named param in KTS and a map in groovy.
     */
    override fun namedParam(name: String): String = "$name: "

    override val buildFileName: String
        get() = "build.gradle"
    override val settingsFileName: String
        get() = "settings.gradle"

}
