/*
 * Copyright (C) 2022 The Android Open Source Project
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

package com.google.services.firebase.directaccess.client.device.remote.service.adb.forwardingdaemon

import com.android.adblib.AdbInputChannel
import com.android.adblib.AdbOutputChannel
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.zip.CRC32

// Constants from
// https://cs.android.com/android/_/android/platform/packages/modules/adb/+/master:protocol.txt;l=210;drc=ebf09dd6e6cf295df224730b1551606c521e74a9
internal const val CNXN = 0x4E584E43
internal const val OPEN = 0x4E45504F
internal const val OKAY = 0x59414B4F
internal const val CLSE = 0x45534C43
internal const val WRTE = 0x45545257

/**
 * A command represents a single unit of data exchanged between an ADB server and an ADB daemon.
 *
 * Commands are outlined briefly in
 * [protocol.txt](https://cs.android.com/android/_/android/platform/packages/modules/adb/+/master:protocol.txt;drc=ebf09dd6e6cf295df224730b1551606c521e74a9).
 * In short, commands are a 24-byte packet followed by payload data (if applicable). ADB's packets
 * are laid out using six 32-bit integers (in little endian).
 *
 * The packets are laid out as follows:
 * 1. Command type (one of CNXN, OPEN, OKAY, CLSE, or WRTE). There are commands we don't support in
 *
 * ```
 *    this implementation, such as AUTH.
 * ```
 * 2. "first arg", which is commonly "local ID"
 * 3. "second arg", which is commonly "remote ID"
 * 4. payload length
 * 5. CRC32 of the payload
 * 6. "magic", which is defined as the command type xor'd with 0xFFFFFFFF
 *
 * Most packets have a notion of "local" and "remote" IDs. "local" is always local to the sender,
 * and "remote" is set by the other end of the connection.
 */
sealed class Command(
  private val type: Int,
  private val firstArg: Int,
  private val secondArg: Int,
  private val payload: ByteArray = ByteArray(0),
) {
  /** Write this command to the provided AdbOutputChannel. */
  suspend fun writeTo(adbOutputChannel: AdbOutputChannel) {
    val crc = CRC32()
    crc.update(payload, 0, payload.size)

    val buf =
      ByteBuffer.allocate(24 + payload.size)
        .order(ByteOrder.LITTLE_ENDIAN)
        .putInt(type)
        .putInt(firstArg)
        .putInt(secondArg)
        .putInt(payload.size)
        .putInt(crc.value.toInt()) // TODO: Determine if we still use CRC32.
        .putInt(type xor 0xFFFFFFFF.toInt()) // "magic"
        .put(payload)
        .flip()
    adbOutputChannel.write(buf)
  }

  companion object {
    /** Read the next Command from the provided InputStream. */
    suspend fun readFrom(adbInputChannel: AdbInputChannel): Command {
      val byteBuffer = ByteBuffer.allocate(24).order(ByteOrder.LITTLE_ENDIAN)

      adbInputChannel.readExactly(byteBuffer)

      val commandId = byteBuffer.getInt(0)
      val firstArg = byteBuffer.getInt(4)
      val secondArg = byteBuffer.getInt(8)
      val payloadSize = byteBuffer.getInt(12)

      val payloadBuffer = ByteBuffer.allocate(payloadSize).order(ByteOrder.LITTLE_ENDIAN)
      adbInputChannel.readExactly(payloadBuffer)
      payloadBuffer.flip()
      val payload = ByteArray(payloadSize)
      payloadBuffer.get(payload, 0, payloadSize)

      return when (commandId) {
        CNXN -> ConnectCommand(firstArg, secondArg, String(payload))
        OPEN -> OpenCommand(firstArg, String(payload))
        OKAY -> OkayCommand(firstArg, secondArg)
        CLSE -> CloseCommand(firstArg, secondArg)
        WRTE -> WriteCommand(firstArg, secondArg, payload)
        else -> throw UnknownCommandTypeException(commandId)
      }
    }
  }

  class UnknownCommandTypeException(commandType: Int) :
    Exception(String.format("Unexpected command type: '%08X'", commandType))
}

/**
 * The connect command (A_CNXN in protocol.txt) is exchanged between the ADB server and the device.
 *
 * The server sends a connect command to the device, containing information about itself, and the
 * device responds with a connect command describing itself. Unlike the other commands, connect
 * sends `adbVersion` and `maxData` as its first and second arguments. These are two constants that
 * are effectively hardcoded into ADB at this point. ADB version is defined to always be 0x01000000.
 * Max data is technically defined to be 256KiB, but the documentation is simply out of date.
 *
 * The banner format is perhaps the most interesting bit of this. The banner is formatted in three
 * sections separated by colons. The three sections are
 * 1. state (one of "device", "host", "offline", "unauthorized")
 * 2. The device ID (ignored for TCP devices)
 * 3. properties (e.g. "ro.device.model=hammerhead"), separated by semicolons
 */
class ConnectCommand(
  adbVersion: Int = 0x01000000,
  maxData: Int = 1024 * 1024,
  banner: String, // TODO: Describe banner format.
) : Command(CNXN, adbVersion, maxData, banner.toByteArray())

/**
 * A StreamCommand is an extraction of common properties of commands sent to streams.
 *
 * It just exposes the "remote ID" of the connection, so we can match this command to a map of
 * stream handlers.
 *
 * @see [Command] to explain local and remote IDs
 */
abstract class StreamCommand(
  type: Int,
  localId: Int,
  val remoteId: Int,
  payload: ByteArray = ByteArray(0),
) : Command(type, localId, remoteId, payload)

/**
 * The open command (A_OPEN in protocol.txt) is sent at the beginning of the stream.
 *
 * The server sends an open command when a new stream should be opened. The open command only
 * contains the local ID (its origin) and the service that it's trying to open (e.g. "shell:ls
 * /sdcard"). The "remote ID" that other commands use is simply set to zero, because it's not yet
 * known.
 *
 * @see [Command] to explain local ID
 */
class OpenCommand(
  val localId: Int,
  val service: String,
) : StreamCommand(OPEN, localId, 0, service.toByteArray())

/**
 * The okay command (A_OKAY in protocol.txt) is sent when the stream is ready for more data.
 *
 * @see [Command] to explain local and remote IDs
 */
class OkayCommand(localId: Int, remoteId: Int) : StreamCommand(OKAY, localId, remoteId)

/**
 * The close command (A_CLSE in protocol.txt) is sent when the stream is complete from the local
 * side.
 *
 * Once a stream has sent a close message, no more data should be sent from that side. However, data
 * may still be received.
 *
 * @see [Command] to explain local and remote IDs
 */
class CloseCommand(localId: Int, remoteId: Int) : StreamCommand(CLSE, localId, remoteId)

/**
 * The write command (A_WRTE in protocol.txt) is sent when there is data being written to the
 * stream.
 *
 * @see [Command] to explain local and remote IDs
 */
class WriteCommand(
  localId: Int,
  remoteId: Int,
  val payload: ByteArray,
) : StreamCommand(WRTE, localId, remoteId, payload)
