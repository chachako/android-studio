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

package com.android.compose.screenshot.report

import java.io.IOException
import java.io.Writer
import java.util.LinkedList

/**
 *
 * A streaming markup writer. Encodes characters and CDATA. Provides only basic state validation, and some simple indentation.
 *
 *
 * This class also is-a [Writer], and any characters written to this writer will be encoded as appropriate. Note, however, that
 * calling [.close] on this object does not close the backing stream.
 *
 * This class is copied from org.gradle.internal.xml.SimpleMarkupWriter.
 *
 */
open class SimpleMarkupWriter (
    private val output: Writer,
    private val indent: String?
) : Writer() {

    private enum class Context {
        Outside,
        Text,
        CData,
        StartTag,
        ElementContent
    }

    private val elements = LinkedList<String>()
    private var context = Context.Outside
    private var squareBrackets = 0

    @Throws(IOException::class)
    override fun write(chars: CharArray, offset: Int, length: Int) {
        characters(chars, offset, length)
    }

    @Throws(IOException::class)
    override fun flush() {
        output.flush()
    }

    @Throws(IOException::class)
    override fun close() {
        // Does nothing
    }

    @Throws(IOException::class)
    fun characters(characters: CharArray): SimpleMarkupWriter {
        characters(characters, 0, characters.size)
        return this
    }

    @Throws(IOException::class)
    fun characters(characters: CharArray, start: Int, count: Int): SimpleMarkupWriter {
        if (context == Context.CData) {
            writeCDATA(characters, start, count)
        } else {
            maybeStartText()
            writeXmlEncoded(characters, start, count)
        }
        return this
    }

    @Throws(IOException::class)
    fun characters(characters: CharSequence): SimpleMarkupWriter {
        if (context == Context.CData) {
            writeCDATA(characters)
        } else {
            maybeStartText()
            writeXmlEncoded(characters)
        }
        return this
    }

    @Throws(IOException::class)
    private fun maybeStartText() {
        check(context != Context.Outside) { "Cannot write text, as there are no started elements." }
        if (context == Context.StartTag) {
            writeRaw(">")
        }
        context = Context.Text
    }

    @Throws(IOException::class)
    private fun maybeFinishStartTag() {
        if (context == Context.StartTag) {
            writeRaw(">")
            context = Context.ElementContent
        }
    }

    @Throws(IOException::class)
    fun startElement(name: String): SimpleMarkupWriter {
        require(isValidXmlName(name)) { String.format("Invalid element name: '%s'", name) }
        check(context != Context.CData) { "Cannot start element, as current CDATA node has not been closed." }
        maybeFinishStartTag()
        if (indent != null) {
            writeRaw(LINE_SEPARATOR)
            for (i in elements.indices) {
                writeRaw(indent)
            }
        }
        context = Context.StartTag
        elements.add(name)
        writeRaw("<")
        writeRaw(name)
        return this
    }

    @Throws(IOException::class)
    fun endElement(): SimpleMarkupWriter {
        check(context != Context.Outside) { "Cannot end element, as there are no started elements." }
        check(context != Context.CData) { "Cannot end element, as current CDATA node has not been closed." }
        if (context == Context.StartTag) {
            writeRaw("/>")
            elements.removeLast()
        } else {
            if (context != Context.Text && indent != null) {
                writeRaw(LINE_SEPARATOR)
                for (i in 1 until elements.size) {
                    writeRaw(indent)
                }
            }
            writeRaw("</")
            writeRaw(elements.removeLast())
            writeRaw(">")
        }
        context = if (elements.isEmpty()) {
            if (indent != null) {
                writeRaw(LINE_SEPARATOR)
            }
            output.flush()
            Context.Outside
        } else {
            Context.ElementContent
        }
        return this
    }

    @Throws(IOException::class)
    private fun writeCDATA(cdata: CharArray, offset: Int, count: Int) {
        val end = offset + count
        for (i in offset until end) {
            writeCDATA(cdata[i])
        }
    }

    @Throws(IOException::class)
    private fun writeCDATA(cdata: CharSequence) {
        val len = cdata.length
        for (i in 0 until len) {
            writeCDATA(cdata[i])
        }
    }

    @Throws(IOException::class)
    private fun writeCDATA(ch: Char) {
        if (needsCDATAEscaping(ch)) {
            writeRaw("]]><![CDATA[>")
        } else if (!isLegalCharacter(ch)) {
            writeRaw('?')
        } else if (isRestrictedCharacter(ch)) {
            writeRaw("]]>")
            writeCharacterReference(ch)
            writeRaw("<![CDATA[")
        } else {
            writeRaw(ch)
        }
    }

    @Throws(IOException::class)
    private fun writeCharacterReference(ch: Char) {
        writeRaw("&#x")
        writeRaw(Integer.toHexString(ch.code))
        writeRaw(";")
    }

    private fun needsCDATAEscaping(ch: Char): Boolean {
        return when (ch) {
            ']' -> {
                squareBrackets++
                false
            }

            '>' -> {
                if (squareBrackets >= 2) {
                    squareBrackets = 0
                    return true
                }
                false
            }

            else -> {
                squareBrackets = 0
                false
            }
        }
    }

    @Throws(IOException::class)
    fun attribute(name: String, value: String): SimpleMarkupWriter {
        require(isValidXmlName(name)) { String.format("Invalid attribute name: '%s'", name) }
        check(context == Context.StartTag) { "Cannot write attribute [$name:$value]. You should write start element first." }
        writeRaw(" ")
        writeRaw(name)
        writeRaw("=\"")
        writeXmlAttributeEncoded(value)
        writeRaw("\"")
        return this
    }

    @Throws(IOException::class)
    private fun writeRaw(c: Char) {
        output.write(c.code)
    }

    private fun isLegalCharacter(c: Char): Boolean {
        if (c.code == 0) {
            return false
        } else if (c.code <= 0xD7FF) {
            return true
        } else if (c.code < 0xE000) {
            return false
        } else if (c.code <= 0xFFFD) {
            return true
        }
        return false
    }

    private fun isRestrictedCharacter(c: Char): Boolean {
        if (c.code == 0x9 || c.code == 0xA || c.code == 0xD || c.code == 0x85) {
            return false
        } else if (c.code <= 0x1F) {
            return true
        } else if (c.code < 0x7F) {
            return false
        } else if (c.code <= 0x9F) {
            return true
        }
        return false
    }

    @Throws(IOException::class)
    protected fun writeRaw(message: String?) {
        output.write(message)
    }

    @Throws(IOException::class)
    private fun writeXmlEncoded(message: CharArray, offset: Int, count: Int) {
        val end = offset + count
        for (i in offset until end) {
            writeXmlEncoded(message[i])
        }
    }

    @Throws(IOException::class)
    private fun writeXmlAttributeEncoded(message: CharSequence?) {
        assert(message != null)
        val len = message!!.length
        for (i in 0 until len) {
            writeXmlAttributeEncoded(message[i])
        }
    }

    @Throws(IOException::class)
    private fun writeXmlAttributeEncoded(ch: Char) {
        if (ch.code == 9) {
            writeRaw("&#9;")
        } else if (ch.code == 10) {
            writeRaw("&#10;")
        } else if (ch.code == 13) {
            writeRaw("&#13;")
        } else {
            writeXmlEncoded(ch)
        }
    }

    @Throws(IOException::class)
    private fun writeXmlEncoded(message: CharSequence?) {
        assert(message != null)
        val len = message!!.length
        for (i in 0 until len) {
            writeXmlEncoded(message[i])
        }
    }

    @Throws(IOException::class)
    private fun writeXmlEncoded(ch: Char) {
        if (ch == '<') {
            writeRaw("&lt;")
        } else if (ch == '>') {
            writeRaw("&gt;")
        } else if (ch == '&') {
            writeRaw("&amp;")
        } else if (ch == '"') {
            writeRaw("&quot;")
        } else if (!isLegalCharacter(ch)) {
            writeRaw('?')
        } else if (isRestrictedCharacter(ch)) {
            writeCharacterReference(ch)
        } else {
            writeRaw(ch)
        }
    }

    companion object {

        private val LINE_SEPARATOR = System.getProperty("line.separator")
        private fun isValidXmlName(name: String): Boolean {
            val length = name.length
            if (length == 0) {
                return false
            }
            var ch = name[0]
            if (!isValidNameStartChar(ch)) {
                return false
            }
            for (i in 1 until length) {
                ch = name[i]
                if (!isValidNameChar(ch)) {
                    return false
                }
            }
            return true
        }

        private fun isValidNameChar(ch: Char): Boolean {
            if (isValidNameStartChar(ch)) {
                return true
            }
            if (ch in '0'..'9') {
                return true
            }
            if (ch == '-' || ch == '.' || ch == '\u00b7') {
                return true
            }
            if (ch in '\u0300'..'\u036f') {
                return true
            }
            return ch in '\u203f'..'\u2040'
        }

        private fun isValidNameStartChar(ch: Char): Boolean {
            if (ch in 'A'..'Z') {
                return true
            }
            if (ch in 'a'..'z') {
                return true
            }
            if (ch == ':' || ch == '_') {
                return true
            }
            if (ch in '\u00c0'..'\u00d6') {
                return true
            }
            if (ch in '\u00d8'..'\u00f6') {
                return true
            }
            if (ch in '\u00f8'..'\u02ff') {
                return true
            }
            if (ch in '\u0370'..'\u037d') {
                return true
            }
            if (ch in '\u037f'..'\u1fff') {
                return true
            }
            if (ch in '\u200c'..'\u200d') {
                return true
            }
            if (ch in '\u2070'..'\u218f') {
                return true
            }
            if (ch in '\u2c00'..'\u2fef') {
                return true
            }
            if (ch in '\u3001'..'\ud7ff') {
                return true
            }
            if (ch in '\uf900'..'\ufdcf') {
                return true
            }
            return ch in '\ufdf0'..'\ufffd'
        }
    }
}


