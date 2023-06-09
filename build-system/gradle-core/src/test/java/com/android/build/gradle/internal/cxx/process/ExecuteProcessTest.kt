/*
 * Copyright (C) 2021 The Android Open Source Project
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

package com.android.build.gradle.internal.cxx.process

import com.android.SdkConstants.CURRENT_PLATFORM
import com.android.SdkConstants.PLATFORM_WINDOWS
import com.android.build.gradle.internal.cxx.hashing.shortSha256Of
import com.android.utils.cxx.os.bat
import org.gradle.api.Action
import org.gradle.internal.file.PathToFileResolver
import org.gradle.process.ExecOperations
import org.gradle.process.ExecResult
import org.gradle.process.ExecSpec
import org.gradle.process.JavaExecSpec
import org.gradle.process.internal.DefaultExecSpec
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.ObjectInputStream
import java.io.ObjectOutputStream
import java.io.Serializable
import java.lang.System.err
import java.lang.management.ManagementFactory
import java.nio.file.Paths
import java.util.concurrent.TimeUnit.SECONDS

class ExecuteProcessTest {
    @Rule
    @JvmField
    val tempFolder = TemporaryFolder()

    /**
     * This test creates two scripts:
     * - 'my-script' to execute on Linux\Mac
     * - 'my-script.bat' to execute on Windows
     * It is meant to prove that these two scripts can coexist and the correct one is executed on
     * the current platform.
     */
    @Test
    fun `dual script and script dot bat works`() {
        with(createWorkingContext()) {
            val scriptBase = "dual-script-support-script"

            val script = createCallbackShellScripts(scriptBase) { args ->
                err.println("stderr with args: ${args.joinToString(" ") { "'$it'" }}")
            }

            createCommand(script, "command")
                .copy(useScript = true)
                .addArgs("arg1", "arg2", "arg3")
                .execute(ops())

            assertStderr("stderr with args: 'arg1' 'arg2' 'arg3'")
        }
    }

    @Test
    fun `check problematic strings in arguments can round trip`() {
        checkRoundtrip(":")
        checkRoundtrip("=")
        checkRoundtrip(" ")
        checkRoundtrip(",")
        checkRoundtrip("?")
        checkRoundtrip("*")
        checkRoundtrip("\"")
        checkRoundtrip("\\")
        checkRoundtrip("\\\\")
        checkRoundtrip(";")
        checkRoundtrip("{")
        checkRoundtrip("}")
        checkRoundtrip("<")
        checkRoundtrip(">")
        checkRoundtrip("%")
        checkRoundtrip("$")
        checkRoundtrip("~")
        checkRoundtrip("|")
        checkRoundtrip("/")
        checkRoundtrip("&")
        checkRoundtrip("!")
        checkRoundtrip("^")
        checkRoundtrip("[")
        checkRoundtrip("]")
        checkRoundtrip("'")
        checkRoundtrip("\u0008")
        checkRoundtrip(
            "\u0000",
            posix = "argument had embedded 0x0000",
            windows = "argument had embedded 0x0000")
        checkRoundtrip(
            "\r",
            windows = "argument had embedded line-feed (\\r)")
        checkRoundtrip(
            "\n",
            windows = "argument had embedded carriage-return (\\n)")
        checkRoundtrip(
            "`",
            windows = "argument had embedded tick-mark (`)")
    }

    /**
     * This is a continuation of `check problematic strings in arguments can round trip` test above.
     * It also checks that script file paths behave as expected when the script name has unusual
     * characters embedded in it.
     * See https://issuetracker.google.com/272534943 Gradle's prefab build fails on path with parentheses
     */
    @Test
    fun `check problematic strings in command file name`() {
        checkCommandNameRoundtrip("(")
        checkCommandNameRoundtrip(")")
        checkCommandNameRoundtrip("=")
        checkCommandNameRoundtrip(" ")
        checkCommandNameRoundtrip(",")
        checkCommandNameRoundtrip(";")
        checkCommandNameRoundtrip("{")
        checkCommandNameRoundtrip("}")
        checkCommandNameRoundtrip("%")
        checkCommandNameRoundtrip("$")
        checkCommandNameRoundtrip("~")
        checkCommandNameRoundtrip("/")
        checkCommandNameRoundtrip("&")
        checkCommandNameRoundtrip("!")
        checkCommandNameRoundtrip("^")
        checkCommandNameRoundtrip("[")
        checkCommandNameRoundtrip("]")
        checkCommandNameRoundtrip("'")
    }

    private fun checkRoundtrip(value : String, posix : String? = null, windows : String? = null) {
        println("Checking via script: [$value]")
        val scriptBase = "check-round-trip-script"
        val expectedException = if (CURRENT_PLATFORM == PLATFORM_WINDOWS) windows else posix

        with(createWorkingContext()) {

            val script = createCallbackShellScripts(scriptBase) { args ->
                err.println("stderr with args: ${args.joinToString(" ") { "'$it'" }}")
            }

            try {
                createCommand(script, "command").copy(useScript = true)
                    .addArgs(value, "$value$value", "\"$value\"")
                    .execute(ops())
            } catch (e: Exception) {
                if (e.message == expectedException) return
                throw (e)
            }

            if (expectedException != null) error("Expected exception: $expectedException")
            assertStderr("stderr with args: '$value' '$value$value' '\"$value\"'")
        }
    }

    private fun checkCommandNameRoundtrip(value : String, posix : String? = null, windows : String? = null) {
        val commandName = "my-$value-command"
        val scriptBase = "my-$value-script"
        with(createWorkingContext("scripts-$value-dir")) {
            val script = createCallbackShellScripts(scriptBase) { args ->
                err.println("stderr with args: ${args.joinToString(" ") { "'$it'" }}")
            }

            val commandFile = workingDir.resolve("$commandName$bat")
            println("Checking command file: [${commandFile.relativeTo(tempFolder.root)}]")
            val expectedException = (if (CURRENT_PLATFORM == PLATFORM_WINDOWS) windows else posix)
                ?.replace("<command>", commandFile.path)

            try {
                createCommand(script, commandName).copy(useScript = true)
                    .addArgs(value, "$value$value", "\"$value\"")
                    .execute(ops())
            } catch (e: Exception) {
                if (e.message == expectedException) return
                error("expected [$expectedException] but got [${e.message}]")
            }

            if (expectedException != null) error("Expected exception: $expectedException")
            assertStderr("stderr with args: '$value' '$value$value' '\"$value\"'")
        }
    }

    private fun WorkingContext.assertStderr(expectedStderr : String) {
        val actualStderr = stderr.readText().trim('\r', '\n')
        if (expectedStderr != actualStderr) {
            println("expected: $expectedStderr")
            println("  actual: $actualStderr")
            println(" working: $workingDir")
            error("")
        }
    }

    private data class WorkingContext(
        val workingDir: File,
        val stdout: File,
        val stderr: File
    )

    private fun createWorkingContext(scriptsBaseName : String = "scripts") = run {
        // Replace some chars that we know are unsupported. This is so that we can write tests
        // with unsupported chars and check the exceptions from shipping code rather than this
        // test code.
        val scriptsBaseNameCleaned = scriptsBaseName
            .replace("?", "_")
            .replace("*", "_")
            .replace("\u0000", "_")
            .replace('/', '_')
        val workingDir = tempFolder.newFolder().resolve(scriptsBaseNameCleaned).also { it.mkdir() }
        WorkingContext(
            workingDir,
            workingDir.resolve("stdout.txt"),
            workingDir.resolve("stderr.txt")
        )
    }

    /**
     * Create a [ExecuteProcessCommand] initialized with test defaults.
     */
    private fun WorkingContext.createCommand(script : File, commandName : String) : ExecuteProcessCommand =
        createCommand(script, workingDir.resolve("$commandName$bat"))

    private fun WorkingContext.createCommand(script : File, commandFile : File) = createExecuteProcessCommand(script).copy(
        commandFile = commandFile,
        stdout = stdout,
        stderr = stderr)

    /**
     * Implementation of [ExecOperations]
     */
    private fun WorkingContext.ops() = object : ExecOperations {
        override fun exec(setSpec: Action<in ExecSpec>): ExecResult {
            val spec = DefaultExecSpec(TestPathToFileResolver(workingDir))
            setSpec.execute(spec)
            val proc = ProcessBuilder(spec.commandLine)
                .directory(workingDir)
                .redirectOutput(stdout)
                .redirectError(stderr)
                .start()

            proc.waitFor(6, SECONDS)
            return object : ExecResult {
                override fun getExitValue() = proc.exitValue()
                override fun assertNormalExitValue() = this
                override fun rethrowFailure() = this
            }
        }
        override fun javaexec(p0: Action<in JavaExecSpec>?) = error("notimpl")
    }

    /**
     * Write a pair of shell scripts to call back into the main(...) function on T.
     * - The Linux and Mac script will be written to a filename based on [posixScriptBase]
     * - The Windows script will be written to the same file but with '.bat' extension.
     * When the script is invoked by the shell then [callback] will be called with the command-line
     * arguments.
     *
     * Usage,
     * ```
     *  createCallbackShellScripts(configureScript) { args ->
     *      System.err.println(args.joinToString(" "))
     *  }
     * ```
     *
     * The scripts generated will look like this:
     *
     * POSIX (file path/to/posix/script)
     * ```
     *      path/to/java
     *      --class-path {classpath:of:this:test}
     *      com.android.build.gradle.internal.cxx.process.ShellScriptCallback
     *      tmp/path/to//write-main-callback-shell-script.bin
     *      "$1" "$2" "$3"
     * ```
     *
     * WINDOWS (file path\to\posix\script.bat)
     * ```
     *      path\to\java.exe
     *      --class-path {classpath:of:this:test}
     *      com.android.build.gradle.internal.cxx.process.ShellScriptCallback
     *      tmp/path/to//write-main-callback-shell-script.bin
     *      %1 %2 %3
     * ```
     *
     * where,
     *   {classpath:of:this:test} -- is the JAVA class path of this test.
     *   com.android.build.gradle.internal.cxx.process.ShellScriptCallback -- is the java entry
     *      point to execute.
     *  tmp/path/to//write-main-callback-shell-script.bin -- is the serialization of the 'callback'
     *      passed to createCallbackShellScripts(...) written to disk. This file is the first
     *      parameter that 'ShellScriptCallback' will receive.
     *  "$1" "$2" "$3" and %1 %2 %3 -- are the additional parameters passed to ShellScriptCallback
     *      for Windows and Posix respectively.
     */
    private fun WorkingContext.createCallbackShellScripts(posixScriptBase: String, callback : (args:Array<String>) -> Unit) : File {
        // Replace some chars that we know are unsupported. This is so that we can write tests
        // with unsupported chars and check the exceptions from shipping code rather than this
        // test code.
        val posixScriptBaseCleaned = posixScriptBase
            .replace("?", "_")
            .replace("*", "_")
            .replace("\u0000", "_")
            .replace('/', '_')
        val context = callback as Serializable
        val contextFile = File.createTempFile("write-main-callback-shell-script", "bin")
        ObjectOutputStream(FileOutputStream(contextFile)).use { objects ->
            objects.writeObject(context)
        }
        val runtime = ManagementFactory.getRuntimeMXBean()
        val sb = StringBuilder()
        val java = File(runtime.systemProperties["java.home"]!!).resolve("bin/java")
        val classPath = runtime.systemProperties["java.class.path"]!!
            .split(File.pathSeparator)
            .map { path -> Paths.get(path).toAbsolutePath() }
            .joinToString(File.pathSeparator)
        val mainClass = ShellScriptCallback::class.java.name
        val isolated = workingDir.resolve(shortSha256Of(posixScriptBase.hashCode()))
        isolated.mkdirs()
        val posixScript = isolated.resolve(posixScriptBaseCleaned)
        val windowsScript = isolated.resolve("${posixScriptBaseCleaned}.bat")
        sb.append("$java --class-path ${classPath.replace("\\\\", "/")} ")
        sb.append("$mainClass $contextFile ")
        windowsScript.writeText("@echo off\r\n")
        windowsScript.appendText(sb.toString())
        windowsScript.appendText("%1 %2 %3")
        posixScript.writeText(sb.toString())
        posixScript.appendText("\"$1\" \"$2\" \"$3\"")
        posixScript.setExecutable(true)
        // POSIX script name is executable on both Windows and Posix because Windows will search
        // for a script with the same name but with '.bat' extension.
        return posixScript
    }

    private class TestPathToFileResolver(val working : File) : PathToFileResolver {
        override fun resolve(file: Any) = working.resolve("$file")
        override fun newResolver(working: File) = TestPathToFileResolver(working)
        override fun canResolveRelativePath() = true
    }
}

@Suppress("UNCHECKED_CAST")
class ShellScriptCallback {
    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            ObjectInputStream(FileInputStream(File(args[0]))).use { objects ->
                val callback = objects.readObject() as (args:Array<String>) -> Unit
                callback(args.drop(1).toTypedArray())
            }
        }
    }
}
