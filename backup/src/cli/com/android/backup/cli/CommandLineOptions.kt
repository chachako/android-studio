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
package com.android.backup.cli

import com.android.adblib.DeviceSelector
import com.android.backup.BackupProgressListener
import org.apache.commons.cli.CommandLine
import org.apache.commons.cli.Option
import org.apache.commons.cli.Options

object CommandLineOptions {
  val help = Option("h", "help", false, "Show this message.")

  private val verbose = Option("v", false, "Print progress.")

  private val usbDevice = Option("d", "Target usb device (error if multiple devices).")

  private val emulator = Option("e", "Target emulator device (error if multiple devices).")

  private val serial: Option =
    Option.builder()
      .option("s")
      .hasArg()
      .argName("SERIAL")
      .desc("Target device identified by SERIAL")
      .build()

  private val transport: Option =
    Option.builder()
      .option("t")
      .hasArg()
      .argName("TRANSPORT")
      .type(Integer::class.java)
      .desc("Target device identified by TRANSPORT")
      .build()

  fun createCommonOptions(): Options =
    Options()
      .addOption(help)
      .addOption(emulator)
      .addOption(usbDevice)
      .addOption(serial)
      .addOption(transport)
      .addOption(verbose)

  fun CommandLine.getDeviceSelector(): DeviceSelector {
    val deviceSelectorOptions = setOf(usbDevice, emulator, serial, transport)
    val count = options.count { it in deviceSelectorOptions }
    if (count > 1) {
      throw IllegalArgumentException(
        "Only one of [${deviceSelectorOptions.joinToString { "-${it.opt}" }}] can be specified"
      )
    }
    return when {
      hasOption(usbDevice) -> DeviceSelector.usb()
      hasOption(emulator) -> DeviceSelector.local()
      hasOption(serial) -> DeviceSelector.fromSerialNumber(getOptionValue(serial))
      hasOption(transport) -> DeviceSelector.fromTransportId(getParsedOptionValue(transport))
      else -> DeviceSelector.any()
    }
  }

  fun CommandLine.getBackupProgressListener() =
    when (hasOption(verbose)) {
      true -> BackupProgressListener { println("  ${it.text}") }
      false -> null
    }
}
