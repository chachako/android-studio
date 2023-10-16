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
package com.android.tools.utp.plugins.host.emulatorcontrol

import com.google.common.annotations.VisibleForTesting
import org.apache.commons.io.FileUtils
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.InvalidPathException
import java.nio.file.Path
import java.nio.file.Paths
import java.util.Locale
import java.util.logging.Logger
import java.util.regex.Pattern
import kotlin.streams.asSequence

// The Default emulator gRPC port, the usual port where the emulator gRPC endpoint lives.
@VisibleForTesting
const val DEFAULT_EMULATOR_GRPC_PORT = "8554"

private val LOG = Logger.getLogger("EmulatorControlConfigUtils")

/** Returns the Emulator registration directory.
 * See go/embedded-emulator-design for details.
 */
fun computeRegistrationDirectoryContainer(): Path? {
    val os = System.getProperty("os.name").lowercase(Locale.ROOT)
    when {
        os.startsWith("mac") -> {
            return Paths.get(
                System.getenv("HOME") ?: "/",
                "Library", "Caches", "TemporaryItems"
            )
        }

        os.startsWith("win") -> {
            return Paths.get(System.getenv("LOCALAPPDATA") ?: "/", "Temp")
        }

        else -> { // Linux and Chrome OS.
            for (dirstr in arrayOf(
                System.getenv("XDG_RUNTIME_DIR"),
                "/run/user/${getUid()}",
                System.getenv("ANDROID_EMULATOR_HOME"),
                System.getenv("ANDROID_PREFS_ROOT"),
                System.getenv("ANDROID_SDK_HOME"),
                (System.getenv("HOME") ?: "/") + ".android",
                // For integration tests, we use java property.
                System.getProperty("android.emulator.home")
            )) {
                if (dirstr == null) {
                    continue
                }
                try {
                    val dir = Paths.get(dirstr)
                    if (Files.isDirectory(dir)) {
                        return dir
                    }
                } catch (exception: InvalidPathException) {
                    LOG.finer("Failed to parse dir ${dirstr}, exception $exception")
                }
            }

            return Paths.get(
                FileUtils.getTempDirectory().absolutePath, "android-" +
                        System.getProperty("USER")
            )
        }
    }
}

private fun getUid(): String? {
    try {
        val userName = System.getProperty("user.name")
        val command = "id -u $userName"
        val process = Runtime.getRuntime().exec(command)
        process.inputStream.use {
            val result = String(it.readBytes(), StandardCharsets.UTF_8).trim()
            if (result.isEmpty()) {
                return null
            }
            return result
        }
    } catch (e: IOException) {
        return null
    }
}

/**
 * Represents information about an emulator gRPC service that was acquired from parsing
 * the emulator discovery file.
 *
 * @property port The port of the emulator gRPC service.
 * @property token The required token, if any.
 * @property serverCert Server certificates that need to be present, if any.
 * @property jwks Discovery directory where the emulator expects JSON Web Key sets.
 *                This is the location where the generated public web keys need to be written to.
 * @property allowlist Directory where the emulator allows list can be found.
 */
data class EmulatorGrpcInfo(
    val port: Int,
    val token: String? = null,
    val serverCert: String? = null,
    val jwks: String? = null,
    val allowlist: String? = null
)

/**
 * Finds and returns information about an emulator gRPC service from the given discovery file.
 *
 * @param deviceSerial The serial number of the emulator device.
 * @param file The discovery file to parse.
 * @return An `EmulatorGrpcInfo` object if the discovery file contains information about the
 *         given emulator, or `null` otherwise.
 */
fun findGrpcInfo(deviceSerial: String, file: Path): EmulatorGrpcInfo? {
    var discovered = mutableMapOf<String, String>()
    Files.readAllLines(file).forEach { line ->
        val keyValuePair = line.split("=", limit = 2)
        discovered.put(keyValuePair[0], keyValuePair[1])
    }
    val serial = discovered.getOrDefault("port.serial", "")
    val matchedAvd = ("emulator-" + serial == deviceSerial)

    if (matchedAvd) {
        return EmulatorGrpcInfo(
            discovered.getOrDefault("grpc.port", DEFAULT_EMULATOR_GRPC_PORT).toInt(),
            discovered.getOrDefault("grpc.token", ""),
            discovered.getOrDefault("grpc.server_cert", ""),
            discovered.getOrDefault("grpc.jwks", ""),
            discovered.getOrDefault("grpc.allowlist", "")
        )
    } else {
        return null
    }
}

/**
 * Finds and returns information about an emulator gRPC service. It will find all discovery
 * files and return the discovered emulator with the given serial number
 *
 * @param deviceSerial The serial number of the emulator device.
 * @return An `EmulatorGrpcInfo` object if the discovery file contains information about the
 *         given emulator.
 */
fun findGrpcInfo(deviceSerial: String): EmulatorGrpcInfo {
    try {
        val fileNamePattern = Pattern.compile("pid_\\d+.ini")
        val directory = computeRegistrationDirectoryContainer()?.resolve("avd/running")
        return Files.list(directory).asSequence().map { file ->
            if (fileNamePattern.matcher(file.fileName.toString()).matches()) {
                findGrpcInfo(deviceSerial, file)
            } else {
                null
            }
        }.filterNotNull().firstOrNull() ?: EmulatorGrpcInfo(DEFAULT_EMULATOR_GRPC_PORT.toInt())
    } catch (exception: Throwable) {
        LOG.fine(
            "Failed to parse emulator gRPC port, fallback to default, exception $exception"
        )
        return EmulatorGrpcInfo(DEFAULT_EMULATOR_GRPC_PORT.toInt())
    }
}
