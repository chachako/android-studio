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
import com.google.testing.platform.api.config.ProtoConfig
import com.google.testing.platform.api.context.Context
import com.google.testing.platform.api.context.events
import com.google.testing.platform.api.device.DeviceController
import com.google.testing.platform.api.event.send
import com.google.testing.platform.api.plugin.HostPlugin
import com.google.testing.platform.lib.logging.jvm.getLogger
import com.google.testing.platform.plugin.android.proto.instrumentationTestOptionsProvided
import com.google.testing.platform.proto.api.core.TestArtifactProto
import com.google.testing.platform.proto.api.core.TestCaseProto.TestCase
import com.google.testing.platform.proto.api.core.TestResultProto.TestResult
import com.google.testing.platform.proto.api.core.TestSuiteResultProto.TestSuiteResult
import com.google.testing.platform.runtime.android.controller.ext.delete
import java.io.File
import com.android.tools.utp.plugins.host.emulatorcontrol.proto.EmulatorControlPluginProto.EmulatorControlPlugin as EmulatorControlPluginConfig

/**
 * The EmulatorAccessPlugin configures the gRPC access point such that it can become
 * accessible from the running instrumentation tests.
 *
 * This is done by providing a set of well known parameters that can be used by
 * the instrumentation tests to safely access the gRPC endpoint of the emulator.
 *
 * The plugin will make certificate files (if any) available in a well known location.
 */
class EmulatorControlPlugin : HostPlugin {

    private companion object {

        @JvmStatic
        val logger = getLogger()
    }

    @VisibleForTesting
    lateinit var emulatorControlPluginConfig: EmulatorControlPluginConfig

    private var jwtConfig: JwtConfig = INVALID_JWT_CONFIG
    private lateinit var context: Context

    override fun configure(context: Context) {
        this.context = context
        val config = context[Context.CONFIG_KEY] as ProtoConfig
        emulatorControlPluginConfig =
            EmulatorControlPluginConfig.parseFrom(config.configProto!!.value)
        jwtConfig =
            JwtConfig(emulatorControlPluginConfig.token, emulatorControlPluginConfig.jwkFile)
    }

    private fun pushSourceIfExists(
        source: String, dest: String, deviceController: DeviceController
    ): Unit {
        if (source.isNullOrEmpty()) return

        val artifact = TestArtifactProto.Artifact.newBuilder().apply {
            sourcePathBuilder.path = source
            destinationPathBuilder.path = dest
        }.build()
        deviceController.push(artifact)
        logger.fine("Pushed $source to $dest")
    }

    override fun beforeEach(testCase: TestCase?, deviceController: DeviceController) {
        logger.fine("Updating timestamp of ${emulatorControlPluginConfig.jwkFile}")

        // The emulator is allowed to delete expired jwk files. We make sure to update the
        // modified timestamp to prevent the emulator from expiring the key
        if (!File(jwtConfig.jwkPath).setLastModified(System.currentTimeMillis())) {
            logger.warning(
                "Unable to update timestamp of ${jwtConfig.jwkPath}" +
                        ", the emulator might delete the key!"
            )
        }
    }

    override fun beforeAll(deviceController: DeviceController) {

        // Let's check to see if we have a configured device
        if (emulatorControlPluginConfig.token.isNullOrEmpty()) {
            // We need to configure the device
            val grpcInfo = getGrpcInfo(deviceController)
            jwtConfig = createTokenConfig(
                emulatorControlPluginConfig.allowedEndpointsList.toSet(),
                emulatorControlPluginConfig.secondsValid,
                "gradle-utp-emulator-control",
                grpcInfo
            )

            if (jwtConfig == INVALID_JWT_CONFIG) {
                logger.warning(
                    "Control of the emulator is not supported for emulators without " +
                            "security features enabled. Please upgrade to a " +
                            "later version of the emulator."
                )
            } else {
                // Now we will fire an event to make these options available
                // to the device runner:
                this.context.events.send(
                    instrumentationTestOptionsProvided {
                        testOptions.putAll(
                            mapOf(
                                "grpc.port" to grpcInfo.port.toString(),
                                "grpc.token" to jwtConfig.token
                            )
                        )
                    }
                )
            }
        }

        // Push all the keyfiles if they exist.
        pushSourceIfExists(
            emulatorControlPluginConfig.emulatorClientCaFilePath,
            emulatorControlPluginConfig.tlsCfgPrefix + ".cer",
            deviceController
        )
        pushSourceIfExists(
            emulatorControlPluginConfig.emulatorClientPrivateKeyFilePath,
            emulatorControlPluginConfig.tlsCfgPrefix + ".key",
            deviceController
        )
        pushSourceIfExists(
            emulatorControlPluginConfig.trustedCollectionRootPath,
            emulatorControlPluginConfig.tlsCfgPrefix + ".ca",
            deviceController
        )
    }

    override fun afterAll(
        testSuiteResult: TestSuiteResult,
        deviceController: DeviceController,
        cancelled: Boolean /* = compiled code */
    ) {
        // Delete the JWK on the host if it is there..
        logger.fine("Deleting ${jwtConfig.jwkPath}")
        File(jwtConfig.jwkPath).delete()

        // Clean up tls configuration files
        deviceController.delete(
            listOf(
                emulatorControlPluginConfig.tlsCfgPrefix + ".cer",
                emulatorControlPluginConfig.tlsCfgPrefix + ".cer",
                emulatorControlPluginConfig.tlsCfgPrefix + ".ca"
            )
        )

    }

    override fun afterEach(
        testResult: TestResult, deviceController: DeviceController, cancelled: Boolean
    ) = Unit

    override fun canRun(): Boolean = true

    /**
     * Returns the gRPC info for the attached device.
     *
     * First attempts to find the gRPC info from the provided configuration proto.
     * If not specified, attempts to determine the gRPC info from the device serial.
     *
     * @param deviceController The device controller to use.
     * @return The gRPC info for the attached device.
     */
    private fun getGrpcInfo(deviceController: DeviceController): EmulatorGrpcInfo {
        if (emulatorControlPluginConfig.emulatorGrpcPort != 0) {
            return EmulatorGrpcInfo(
                emulatorControlPluginConfig.emulatorGrpcPort,
                emulatorControlPluginConfig.token,
                "",
                "",
                ""
            )
        }
        return findGrpcInfo(deviceController.getDevice().serial)
    }
}
