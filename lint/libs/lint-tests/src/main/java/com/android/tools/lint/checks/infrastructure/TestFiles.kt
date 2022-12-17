/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.tools.lint.checks.infrastructure

import com.android.SdkConstants.ANDROID_MANIFEST_XML
import com.android.SdkConstants.DOT_JAVA
import com.android.SdkConstants.DOT_KT
import com.android.SdkConstants.DOT_XML
import com.android.SdkConstants.FN_BUILD_GRADLE
import com.android.resources.ResourceType
import com.android.resources.ResourceUrl
import com.android.tools.lint.checks.infrastructure.TestFile.BinaryTestFile
import com.android.tools.lint.checks.infrastructure.TestFile.ByteProducer
import com.android.tools.lint.checks.infrastructure.TestFile.BytecodeProducer
import com.android.tools.lint.checks.infrastructure.TestFile.GradleTestFile
import com.android.tools.lint.checks.infrastructure.TestFile.ImageTestFile
import com.android.tools.lint.checks.infrastructure.TestFile.JarTestFile
import com.android.tools.lint.checks.infrastructure.TestFile.JavaTestFile
import com.android.tools.lint.checks.infrastructure.TestFile.KotlinTestFile
import com.android.tools.lint.checks.infrastructure.TestFile.ManifestTestFile
import com.android.tools.lint.checks.infrastructure.TestFile.PropertyTestFile
import com.android.tools.lint.checks.infrastructure.TestFile.XmlTestFile
import com.android.tools.lint.client.api.JavaEvaluator
import com.google.common.base.Joiner
import com.google.common.base.Splitter
import com.google.common.io.ByteStreams
import org.intellij.lang.annotations.Language
import org.jetbrains.kotlin.util.capitalizeDecapitalize.toUpperCaseAsciiOnly
import org.junit.Assert.assertNotNull
import org.junit.Assert.fail
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.Base64
import java.util.EnumMap
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream
import java.util.zip.ZipException

/** A utility class which provides unit test file descriptions. */
object TestFiles {
    @JvmStatic
    fun file(): TestFile {
        return TestFile()
    }

    @JvmStatic
    fun source(to: String, source: String): TestFile {
        return file().to(to).withSource(source)
    }

    @JvmStatic
    fun java(to: String, @Language("JAVA") source: String): TestFile {
        return JavaTestFile.create(to, source)
    }

    @JvmStatic
    fun java(@Language("JAVA") source: String): TestFile {
        return JavaTestFile.create(source)
    }

    @JvmStatic
    fun kt(@Language("kotlin") source: String): TestFile {
        return kotlin(source)
    }

    @JvmStatic
    fun kt(to: String, @Language("kotlin") source: String): TestFile {
        return kotlin(to, source)
    }

    @JvmStatic
    fun kotlin(@Language("kotlin") source: String): TestFile {
        return KotlinTestFile.create(source)
    }

    @JvmStatic
    fun kotlin(to: String, @Language("kotlin") source: String): TestFile {
        return KotlinTestFile.create(to, source)
    }

    @JvmStatic
    fun xml(to: String, @Language("XML") source: String): TestFile {
        require(to.endsWith(DOT_XML)) { "Expected .xml suffix for XML test file" }
        return XmlTestFile.create(to, source)
    }

    @JvmStatic
    fun copy(from: String, resourceProvider: TestResourceProvider): TestFile {
        return file().from(from, resourceProvider).to(from)
    }

    @JvmStatic
    fun copy(from: String, to: String, resourceProvider: TestResourceProvider): TestFile {
        return file().from(from, resourceProvider).to(to)
    }

    @JvmStatic
    fun gradle(to: String, @Language("Groovy") source: String): GradleTestFile {
        return GradleTestFile(to, source)
    }

    @JvmStatic
    fun gradle(@Language("Groovy") source: String): GradleTestFile {
        return GradleTestFile(FN_BUILD_GRADLE, source)
    }

    @JvmStatic
    fun manifest(): ManifestTestFile {
        return ManifestTestFile()
    }

    @JvmStatic
    fun manifest(@Language("XML") source: String): TestFile {
        return source(ANDROID_MANIFEST_XML, source)
    }

    @JvmStatic
    fun projectProperties(): PropertyTestFile {
        return PropertyTestFile()
    }

    @JvmStatic
    fun bytecode(to: String, producer: BytecodeProducer): BinaryTestFile {
        return BinaryTestFile(to, producer)
    }

    @JvmStatic
    fun rClass(pkg: String, vararg urls: String): TestFile {
        if (ResourceUrl.parse(pkg) != null) {
            fail("The argument in rClass should be a package! (was $pkg)")
        }
        var id = 0x7f040000
        val sb = StringBuilder()
        sb.append("package ").append(pkg).append(";\n")
        sb.append("public final class R {\n")
        val map: MutableMap<ResourceType, MutableList<ResourceUrl?>> = EnumMap(ResourceType::class.java)
        for (url in urls) {
            val reference = ResourceUrl.parse(url)
            assertNotNull("Resource reference was not a valid URL: $url", reference)
            val list = map.computeIfAbsent(reference!!.type) { _: ResourceType? -> ArrayList() }
            list.add(reference)
        }
        for (type in ResourceType.values()) {
            val resources = map[type] ?: continue
            sb.append("    public static final class ").append(type).append(" {\n")
            for (resource in resources) {
                sb.append("        public static final int ")
                    .append(resource!!.name)
                    .append(" = 0x")
                    .append(Integer.toHexString(id++))
                    .append(";\n")
            }
            sb.append("    }\n")
        }
        sb.append("}")
        return java(sb.toString())
    }

    @JvmStatic
    fun bytes(to: String, bytes: ByteArray): BinaryTestFile {
        val producer: BytecodeProducer = object : BytecodeProducer() {
            override fun produce(): ByteArray {
                return bytes
            }
        }
        return BinaryTestFile(to, producer)
    }

    @JvmStatic
    fun toBase64(bytes: ByteArray): String {
        val base64 = Base64.getEncoder().encodeToString(bytes)
        return "\"\"\n+ \"" +
            Joiner.on("\"\n+ \"").join(Splitter.fixedLength(60).split(base64)) +
            "\""
    }

    // Backwards compat: default to Java formatting
    @JvmStatic
    fun toBase64gzip(bytes: ByteArray): String {
        return toBase64gzipJava(bytes, 0, indentStart = false, includeEmptyPrefix = true)
    }

    @JvmStatic
    fun toBase64gzipJava(
        bytes: ByteArray,
        indent: Int,
        indentStart: Boolean,
        includeEmptyPrefix: Boolean
    ): String {
        val base64 = toBase64gzipString(bytes)
        val indentString = StringBuilder()
        for (i in 0 until indent) {
            indentString.append(' ')
        }
        val lines = Splitter.fixedLength(60).split(base64)
        val result = StringBuilder()
        if (indentStart) {
            result.append(indentString)
        }
        if (includeEmptyPrefix) {
            result.append("\"\" +\n")
            result.append(indentString)
        }
        result.append("\"")
        val separator = "\" +\n$indentString\""
        result.append(Joiner.on(separator).join(lines))
        result.append("\"")
        return result.toString()
    }

    @JvmStatic
    fun toBase64gzipKotlin(
        bytes: ByteArray,
        indent: Int,
        indentStart: Boolean,
        includeQuotes: Boolean
    ): String {
        val base64 = toBase64gzipString(bytes).replace('$', '＄')
        val indentString = StringBuilder()
        for (i in 0 until indent) {
            indentString.append(' ')
        }
        val lines = Splitter.fixedLength(60).split(base64)
        val result = StringBuilder()
        if (indentStart) {
            result.append(indentString)
        }
        if (includeQuotes) {
            result.append("\"\"\"\n")
            result.append(indentString)
        }
        result.append(Joiner.on("\n" + indentString.toString()).join(lines))
        if (includeQuotes) {
            result.append("\"\"\"")
        }
        result.append("\n")
        return result.toString()
    }

    private fun toBase64gzipString(bytes: ByteArray): String {
        val out = ByteArrayOutputStream()
        GZIPOutputStream(out).use { stream -> stream.write(bytes) }
        val gzipped = out.toByteArray()
        return Base64.getEncoder().encodeToString(gzipped).replace('$', '＄')
    }

    @JvmStatic
    fun toBase64(file: File): String {
        return toBase64(file.readBytes())
    }

    @JvmStatic
    fun toBase64gzip(file: File): String {
        return toBase64gzip(file.readBytes())
    }

    @JvmStatic
    fun toHexBytes(file: File): String {
        return toHexBytes(file.readBytes())
    }

    /**
     * Creates the string to initialize a [.hexBytes] test file with.
     */
    @JvmStatic
    fun toHexBytes(bytes: ByteArray): String {
        val sb = StringBuilder()
        var column = 0
        sb.append('"')
        for (b in bytes) {
            val i = b.toInt() and 0xFF
            var hex = Integer.toHexString(i)
            hex = hex.toUpperCaseAsciiOnly()
            if (hex.length == 1) {
                sb.append('0')
            }
            sb.append(hex)
            column += 2
            if (column > 60) {
                sb.append("\\n\" +\n\"")
                column = 0
            }
        }
        sb.append('"')
        return sb.toString()
    }

    /**
     * Creates a test file from the given base64 data. To create this
     * data, use [ ][.toBase64] or [.toBase64], for example via
     *
     * ```
     * `assertEquals("", toBase64(new File("path/to/your.class")));`
     * ```
     *
     * @param to the file to write as
     * @param encoded the encoded data
     * @return the new test file
     */
    @JvmStatic
    @Deprecated("Use {@link #base64gzip(String, String)} instead")
    fun base64(to: String, encoded: String): BinaryTestFile {
        val escaped = encoded.replace('＄', '$')
        val bytes = Base64.getDecoder().decode(escaped)
        return BinaryTestFile(
            to,
            object : BytecodeProducer() {
                override fun produce(): ByteArray {
                    return bytes
                }
            }
        )
    }

    /**
     * Decodes base64 strings into gzip data, then decodes that into
     * a data file. To create this data, use [.toBase64gzip] or
     * [.toBase64gzip], for example via
     *
     * ```
     * `assertEquals("", toBase64gzip(new File("path/to/your.class")));`
     * ```
     */
    @JvmStatic
    fun base64gzip(to: String, encoded: String): BinaryTestFile {
        return BinaryTestFile(to, getByteProducerForBase64gzip(encoded))
    }

    /**
     * Creates a bytecode producer which takes an encoded base64gzip
     * string and returns the uncompressed de-base64'ed byte array.
     */
    @JvmStatic
    fun getByteProducerForBase64gzip(encoded: String): ByteProducer {
        val escaped = encoded // Recover any $'s we've converted to ＄ to better handle Kotlin raw strings
            .replace('＄', '$') // Whitespace is not significant in base64 but isn't handled properly by
            // the base64 decoder
            .replace(" ", "")
            .replace("\n", "")
            .replace("\t", "")
        val gzipBytes = Base64.getDecoder().decode(escaped)
        try {
            val stream = GZIPInputStream(ByteArrayInputStream(gzipBytes))
            val bytes = ByteStreams.toByteArray(stream)
            return object : BytecodeProducer() {
                override fun produce(): ByteArray {
                    return bytes
                }
            }
        } catch (e: ZipException) {
            val message = "The unit test data is not in gzip format. Perhaps this was\n" +
                "encoded using base64() instead of base64gzip? If so, the base64gzip data\n" +
                "should have been:\n${toBase64gzip(gzipBytes)}"
            error(message)
        }
    }

    /**
     * Decodes hex byte strings into the original byte array. To create
     * this data, use [ ][.toHexBytes] or [.toHexBytes], for example via
     *
     * ```
     * `assertEquals("", toHexBytes(new File("path/to/your.class")));`
     * ```
     *
     * Normally you'll be using [.base64gzip] test files, since these
     * are much more compact. The main use case for hex byte files is
     * very clearly seeing the binary contents in the test description,
     * and perhaps modifying these slightly (for example, to
     * deliberately change a field in a file format like a class file.)
     */
    @JvmStatic
    fun hexBytes(to: String, encoded: String): BinaryTestFile {
        return BinaryTestFile(to, getByteProducerForHexBytes(encoded))
    }

    /**
     * Creates a bytecode producer which takes an encoded hex bytes
     * string and returns the decoded byte array.
     */
    @JvmStatic
    fun getByteProducerForHexBytes(encoded: String): ByteProducer {
        val escaped = encoded.replace(" ", "").replace("\n", "").replace("\t", "")
        val out = ByteArrayOutputStream()
        var i = 0
        while (i < escaped.length) {
            val b = escaped.substring(i, i + 2).toInt(16)
            out.write(b)
            i += 2
        }
        val finalBytes = out.toByteArray()
        return object : BytecodeProducer() {
            override fun produce(): ByteArray {
                return finalBytes
            }
        }
    }

    @JvmStatic
    fun classpath(vararg extraLibraries: String?): TestFile {
        //language=TEXT
        val newline = "\n"
        val source =
            //language=XML
            "" +
                "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                "<classpath>\n" +
                "    <classpathentry kind=\"src\" path=\"src\"/>\n" +
                "    <classpathentry kind=\"src\" path=\"gen\"/>\n" +
                "    <classpathentry kind=\"con\" path=\"com.android.ide.eclipse.adt.ANDROID_FRAMEWORK\"/>\n" +
                "    <classpathentry kind=\"con\" path=\"com.android.ide.eclipse.adt.LIBRARIES\"/>\n" +
                "    <classpathentry kind=\"output\" path=\"bin/classes\"/>\n" +
                "    <classpathentry kind=\"output\" path=\"build/intermediates/javac/debug/classes\"/>\n" +
                extraLibraries.joinToString(newline) { "    <classpathentry kind=\"lib\" path=\"$it\"/>" } +
                "\n</classpath>"
        return source(".classpath", source)
    }

    @JvmStatic
    fun jar(to: String): JarTestFile {
        return JarTestFile(to)
    }

    @JvmStatic
    fun binaryStub(
        into: String,
        vararg source: TestFile,
        byteOnly: Boolean = true
    ): TestFile {
        return binaryStub(into, source.toList(), emptyList(), byteOnly)
    }

    /**
     * Creates a **class file** from a simple stub source file which
     * gets interpreted by lint's test infrastructure and "compiled"
     * into an actual class file. This lets unit tests more accurately
     * test what happens at runtime (for example, parameter names are
     * available when you resolve calls into source, but not into class
     * files), without having to actually compile and maintain binary
     * test files.
     *
     * The [stubSources] are the source files to be used for the
     * library. The [compileOnly] sources are ones that may define APIs
     * referenced by the [stubSources], but which should not be packaged
     * in the jar. If [byteOnly] is false, it will run this test both
     * with the source code available, and without.
     */
    @JvmStatic
    fun binaryStub(
        into: String,
        /** The test source files to be stubbed */
        stubSources: List<TestFile>,
        /**
         * Any library-only (needed for compilation, but not to be
         * packaged) dependencies
         */
        compileOnly: List<TestFile> = emptyList(),
        byteOnly: Boolean = true
    ): TestFile {
        val default = if (byteOnly) BytecodeTestFile.Type.BYTECODE_ONLY else BytecodeTestFile.Type.SOURCE_AND_BYTECODE
        val type = getCompileType(default, *stubSources.toTypedArray())
        return StubClassFile(into, type, stubSources, compileOnly)
    }

    /**
     * Creates a simple binary "Maven jar library artifact". Given some
     * simple Java stubs for the APIs the library should contain, and
     * an artifact address, this will perform simple "compilation" of
     * the stub APIs into a binary jar, and will locate this jar file
     * in the right place for lint to discover it and associate it
     * (via [JavaEvaluator.findOwnerLibrary]) with the right artifact.
     *
     * The [stubSources] are the source files to be used for the
     * library. The [compileOnly] sources are ones that may define APIs
     * referenced by the [stubSources], but which should not be packaged
     * in the jar. If [byteOnly] is false, it will run this test both
     * with the source code available, and without.
     */
    @JvmStatic
    fun mavenLibrary(
        artifact: String,
        /** The test source files to be stubbed */
        stubSources: List<TestFile>,
        /**
         * Any library-only (needed for compilation, but not to be
         * packaged) dependencies
         */
        compileOnly: List<TestFile> = emptyList(),
        byteOnly: Boolean = true
        // TODO: Preserve artifact name, and then in test infrastructure, make sure
        // all exploded-aar files are accounted for in the dependency graph!
        // Maybe even build dependency graph here with a PomBuilder?
    ): TestFile {
        val default = if (byteOnly) BytecodeTestFile.Type.BYTECODE_ONLY else BytecodeTestFile.Type.SOURCE_AND_BYTECODE
        val type = getCompileType(default, *stubSources.toTypedArray())
        return MavenLibrary(artifact, type, stubSources, compileOnly)
    }

    /**
     * Creates a simple binary "Maven jar library artifact". Given some
     * simple Java stubs for the APIs the library should contain, and
     * an artifact address, this will perform simple "compilation" of
     * the stub APIs into a binary jar, and will locate this jar file
     * in the right place for lint to discover it and associate it
     * (via [JavaEvaluator.findOwnerLibrary]) with the right artifact.
     */
    @JvmStatic
    fun mavenLibrary(
        artifact: String,
        /** The test source files to be stubbed */
        vararg files: TestFile,
        byteOnly: Boolean = true
    ): TestFile {
        val default = if (byteOnly) BytecodeTestFile.Type.BYTECODE_ONLY else BytecodeTestFile.Type.SOURCE_AND_BYTECODE
        val type = getCompileType(default, *files)
        return MavenLibrary(artifact, type, listOf(*files), emptyList())
    }

    @Deprecated("") // Use the method with the checksum instead
    @JvmStatic
    fun compiled(
        into: String,
        source: TestFile,
        vararg encoded: String
    ): TestFile {
        val type = getCompileType(BytecodeTestFile.Type.SOURCE_AND_BYTECODE, source)
        return CompiledSourceFile(into, type, source, null, encoded)
    }

    @JvmStatic
    fun compiled(
        into: String,
        source: TestFile,
        checksum: Long,
        vararg encoded: String
    ): TestFile {
        val type = getCompileType(BytecodeTestFile.Type.SOURCE_AND_BYTECODE, source)
        return CompiledSourceFile(into, type, source, checksum, encoded)
    }

    @Deprecated("") // Use the method with the checksum instead
    @JvmStatic
    fun bytecode(
        into: String,
        source: TestFile,
        vararg encoded: String
    ): TestFile {
        val type = getCompileType(BytecodeTestFile.Type.BYTECODE_ONLY, source)
        return CompiledSourceFile(into, type, source, null, encoded)
    }

    @JvmStatic
    fun bytecode(
        into: String,
        source: TestFile,
        checksum: Long,
        vararg encoded: String
    ): TestFile {
        val type = getCompileType(BytecodeTestFile.Type.BYTECODE_ONLY, source)
        return CompiledSourceFile(into, type, source, checksum, encoded)
    }

    private fun getCompileType(default: BytecodeTestFile.Type, vararg sources: TestFile): BytecodeTestFile.Type {
        for (source in sources) {
            val targetRelativePath = source.targetRelativePath
            if (targetRelativePath.endsWith(DOT_JAVA) || targetRelativePath.endsWith(DOT_KT)) {
                return default
            }
        }
        return BytecodeTestFile.Type.RESOURCE
    }

    @JvmStatic
    fun jar(to: String, vararg files: TestFile): JarTestFile {
        // don't insist on .jar since we're also supporting .srcjar etc
        require(to.endsWith("jar") || to.endsWith("zip")) { "Expected .jar suffix for jar test file" }
        val jar = JarTestFile(to)
        jar.files(*files)
        return jar
    }

    @JvmStatic
    fun image(to: String, width: Int, height: Int): ImageTestFile {
        return ImageTestFile(to, width, height)
    }

    @JvmStatic
    fun getLintClassPath(): Array<TestFile> {
        val paths: MutableList<TestFile> = ArrayList()
        val libraries = findFromRuntimeClassPath { isLintJar(it) }
        for (file in libraries) {
            val testFile: TestFile = LibraryReferenceTestFile(file)
            paths.add(testFile)
        }
        return paths.toTypedArray()
    }

    private fun isLintJar(file: File): Boolean {
        val name = file.name
        return (
            (
                name.startsWith("lint-") ||
                    name.startsWith("kotlin-compiler") ||
                    name.startsWith("uast-") ||
                    name.startsWith("intellij-core") ||
                    name.endsWith("uast.jar") || // bazel
                    name.startsWith("android.sdktools.lint") || // IJ ADT
                    name.endsWith(".lint-api-base") || // IJ BASE
                    name.endsWith("lint-api.jar") || // bazel
                    name.endsWith(".lint.checks-base") || // IJ
                    name.endsWith("lint-checks.jar") || // bazel
                    name.endsWith(".lint-model-base") || // IJ
                    name.endsWith("lint-model.jar") || // bazel
                    name.startsWith("lint-model") || // Gradle
                    name.endsWith(".testutils") ||
                    name.endsWith("testutils.jar") ||
                    name.startsWith("testutils-") ||
                    name.endsWith(".lint.tests") ||
                    name.endsWith("lint-tests.jar") || name == "main" && file.path.contains("lint-tests")
                ) || // Gradle
                name.endsWith(".lint.cli")
            )
    }

    class LibraryReferenceTestFile(to: String, file: File) : TestFile() {
        val file: File

        constructor(file: File) : this(file.path, file)

        init {
            targetRelativePath = to
            this.file = file
        }
    }
}
