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

package com.android.tools.utp.plugins.deviceprovider.gradle

import com.google.testing.platform.lib.logging.jvm.getLogger
import com.google.testing.platform.lib.process.Handle
import com.google.testing.platform.lib.process.inject.SubprocessComponent
import java.util.logging.Logger

/**
 * Component of the [GradleManagedAndroidDeviceLauncher] to handle the emulator instance
 */
class EmulatorHandleImpl(private val subprocessComponent: SubprocessComponent) : EmulatorHandle {
    companion object {
        private const val EMULATOR_NO_WINDOW = "-no-window"
        private const val EMULATOR_GPU = "-gpu"
        private const val EMULATOR_NO_AUDIO = "-no-audio"
        private const val EMULATOR_NO_BOOT_ANIM = "-no-boot-anim"
        private const val EMULATOR_READ_ONLY = "-read-only"
        private const val EMULATOR_NO_SNAPSHOT_SAVE = "-no-snapshot-save"
        private const val EMULATOR_VERBOSE = "-verbose"
        private const val EMULATOR_SHOW_KERNEL = "-show-kernel"
        private const val EMULATOR_DELAY_ADB = "-delay-adb"
    }

    private val logger: Logger = getLogger()

    private lateinit var emulatorPath: String

    private lateinit var emulatorGpuFlag: String

    private var showEmulatorKernelLogging: Boolean = false

    private var processHandle: Handle? = null

    override fun configure(
        emulatorPath: String, emulatorGpuFlag: String, showEmulatorKernelLogging: Boolean) {
        this.emulatorPath = emulatorPath
        this.emulatorGpuFlag = emulatorGpuFlag
        this.showEmulatorKernelLogging = showEmulatorKernelLogging
    }

    override fun isAlive() = processHandle?.isAlive() ?: false

    override fun exitCode(): Int? = processHandle?.exitCode()

    override fun launchInstance(
            avdName: String,
            avdFolder: String,
            avdId: String,
            enableDisplay: Boolean,
    ) {
        val args = mutableListOf(emulatorPath)
        args.add("@$avdName")
        if (!enableDisplay) {
            args.add(EMULATOR_NO_WINDOW)
            args.add(EMULATOR_NO_AUDIO)
        }
        args.add(EMULATOR_GPU)
        args.add(emulatorGpuFlag)
        args.add(EMULATOR_READ_ONLY)
        args.add(EMULATOR_NO_SNAPSHOT_SAVE)
        args.add(EMULATOR_NO_BOOT_ANIM)
        args.add(EMULATOR_DELAY_ADB)
        if (showEmulatorKernelLogging) {
            args.add(EMULATOR_VERBOSE)
            args.add(EMULATOR_SHOW_KERNEL)
        }
        args.add("-id")
        args.add(avdId)

        val emulatorEnvironment = System.getenv().toMutableMap()
        emulatorEnvironment["ANDROID_AVD_HOME"] = avdFolder

        // Stop the previously running command if exists. If the process was already
        // terminated, this will be a no-op.
        processHandle?.destroy()

        processHandle = subprocessComponent.subprocess()
            .executeAsync(args, emulatorEnvironment, logger::info, logger::info)
    }

    override fun closeInstance() {
        processHandle?.destroy()
    }
}
