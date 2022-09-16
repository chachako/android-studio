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
import com.google.testing.platform.api.device.DeviceController
import com.google.testing.platform.api.plugin.HostPlugin
import com.google.testing.platform.lib.logging.jvm.getLogger
import com.google.testing.platform.proto.api.core.TestArtifactProto
import com.google.testing.platform.proto.api.core.TestCaseProto.TestCase
import com.google.testing.platform.proto.api.core.TestResultProto.TestResult
import com.google.testing.platform.proto.api.core.TestSuiteResultProto.TestSuiteResult
import com.google.testing.platform.runtime.android.controller.ext.delete
import java.io.File
import com.android.tools.utp.plugins.host.emulatorcontrol.proto.EmulatorControlPluginProto.EmulatorControlPlugin as EmulatorControlPluginConfig

/**
 * The EmulatorAccessPlugin configures the gRPC access point such that it can become
 * accessible from within the emulator.
 *
 * This is done by writing a configuration file in a well known location that can be
 * used to properly configure a gRPC endpoint.
 */
class EmulatorControlPlugin : HostPlugin {

    private companion object {

        @JvmStatic
        val logger = getLogger()
    }

    private lateinit var deviceController: DeviceController

    @VisibleForTesting
    lateinit var emulatorControlPluginConfig: EmulatorControlPluginConfig

    override fun configure(context: Context) {
        val config = context[Context.CONFIG_KEY] as ProtoConfig
        emulatorControlPluginConfig =
            EmulatorControlPluginConfig.parseFrom((config as ProtoConfig).configProto!!.value)
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
        if (!File(emulatorControlPluginConfig.jwkFile).setLastModified(System.currentTimeMillis())) {
            logger.warning(
                "Unable to update timestamp of ${emulatorControlPluginConfig.jwkFile}, the emulator might delete the key!"
            )
        }
    }

    override fun beforeAll(deviceController: DeviceController) {
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
        cancelled: kotlin.Boolean /* = compiled code */
    ): TestSuiteResult {
        // Delete the JWK on the host if it is there..
        logger.fine("Deleting ${emulatorControlPluginConfig.jwkFile}")
        File(emulatorControlPluginConfig.jwkFile).delete()

        // Clean up tls configuration files
        deviceController.delete(
            listOf(
                emulatorControlPluginConfig.tlsCfgPrefix + ".cer",
                emulatorControlPluginConfig.tlsCfgPrefix + ".cer",
                emulatorControlPluginConfig.tlsCfgPrefix + ".ca"
            )
        )
        return testSuiteResult

    }

    override fun afterEach(
        testResult: TestResult, deviceController: DeviceController, cancelled: Boolean
    ): TestResult {
        return testResult
    }

    override fun canRun(): Boolean = true
}
