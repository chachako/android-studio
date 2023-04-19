/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.fakeadbserver.devicecommandhandlers

import com.android.fakeadbserver.ClientState
import com.android.fakeadbserver.DeviceState
import com.android.fakeadbserver.FakeAdbServer
import com.android.fakeadbserver.devicecommandhandlers.ddmsHandlers.DdmPacketHandler
import com.android.fakeadbserver.devicecommandhandlers.ddmsHandlers.DdmPacket
import com.android.fakeadbserver.devicecommandhandlers.ddmsHandlers.DdmPacket.Companion.chunkTypeToString
import com.android.fakeadbserver.devicecommandhandlers.ddmsHandlers.DdmPacket.Companion.fromJdwpPacket
import com.android.fakeadbserver.devicecommandhandlers.ddmsHandlers.DdmPacket.Companion.isDdmPacket
import com.android.fakeadbserver.devicecommandhandlers.ddmsHandlers.ExitHandler
import com.android.fakeadbserver.devicecommandhandlers.ddmsHandlers.FeatHandler
import com.android.fakeadbserver.devicecommandhandlers.ddmsHandlers.JdwpHandlerOutput
import com.android.fakeadbserver.devicecommandhandlers.ddmsHandlers.HeloHandler
import com.android.fakeadbserver.devicecommandhandlers.ddmsHandlers.HpgcHandler
import com.android.fakeadbserver.devicecommandhandlers.ddmsHandlers.JdwpCommandId
import com.android.fakeadbserver.devicecommandhandlers.ddmsHandlers.JdwpPacket
import com.android.fakeadbserver.devicecommandhandlers.ddmsHandlers.JdwpPacket.Companion.readFrom
import com.android.fakeadbserver.devicecommandhandlers.ddmsHandlers.JdwpPacketHandler
import com.android.fakeadbserver.devicecommandhandlers.ddmsHandlers.JdwpVmExitHandler
import com.android.fakeadbserver.devicecommandhandlers.ddmsHandlers.JdwpVmIdSizesHandler
import com.android.fakeadbserver.devicecommandhandlers.ddmsHandlers.JdwpVmVersionHandler
import com.android.fakeadbserver.devicecommandhandlers.ddmsHandlers.MprqHandler
import com.android.fakeadbserver.devicecommandhandlers.ddmsHandlers.MpseHandler
import com.android.fakeadbserver.devicecommandhandlers.ddmsHandlers.MpssHandler
import com.android.fakeadbserver.devicecommandhandlers.ddmsHandlers.ReaeHandler
import com.android.fakeadbserver.devicecommandhandlers.ddmsHandlers.RealHandler
import com.android.fakeadbserver.devicecommandhandlers.ddmsHandlers.ReaqHandler
import com.android.fakeadbserver.devicecommandhandlers.ddmsHandlers.SpseHandler
import com.android.fakeadbserver.devicecommandhandlers.ddmsHandlers.SpssHandler
import com.android.fakeadbserver.devicecommandhandlers.ddmsHandlers.VulwHandler
import com.android.fakeadbserver.devicecommandhandlers.ddmsHandlers.VuopHandler
import com.android.fakeadbserver.devicecommandhandlers.ddmsHandlers.VurtHandler
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.Socket
import java.nio.charset.StandardCharsets

/**
 * jdwp:pid changes the connection to communicate with the pid's (required: Client) JDWP interface.
 */
class JdwpCommandHandler : DeviceCommandHandler("jdwp") {

    private val ddmPacketHandlers: MutableMap<Int, DdmPacketHandler> = HashMap()
    private val jdwpPacketHandlers: MutableMap<JdwpCommandId, JdwpPacketHandler> = HashMap()

    init {
        addDdmPacketHandler(HeloHandler.CHUNK_TYPE, HeloHandler())
        addDdmPacketHandler(HpgcHandler.CHUNK_TYPE, HpgcHandler())
        addDdmPacketHandler(ExitHandler.CHUNK_TYPE, ExitHandler())
        addDdmPacketHandler(FeatHandler.CHUNK_TYPE, FeatHandler())
        addDdmPacketHandler(ReaeHandler.CHUNK_TYPE, ReaeHandler())
        addDdmPacketHandler(RealHandler.CHUNK_TYPE, RealHandler())
        addDdmPacketHandler(ReaqHandler.CHUNK_TYPE, ReaqHandler())
        addDdmPacketHandler(SpssHandler.CHUNK_TYPE, SpssHandler())
        addDdmPacketHandler(SpseHandler.CHUNK_TYPE, SpseHandler())
        addDdmPacketHandler(MpssHandler.CHUNK_TYPE, MpssHandler())
        addDdmPacketHandler(MpseHandler.CHUNK_TYPE, MpseHandler())
        addDdmPacketHandler(VulwHandler.CHUNK_TYPE, VulwHandler())
        addDdmPacketHandler(VuopHandler.CHUNK_TYPE, VuopHandler())
        addDdmPacketHandler(VurtHandler.CHUNK_TYPE, VurtHandler())
        addDdmPacketHandler(MprqHandler.CHUNK_TYPE, MprqHandler())
        addJdwpPacketHandler(JdwpVmExitHandler.commandId, JdwpVmExitHandler())
        addJdwpPacketHandler(
            JdwpVmVersionHandler.commandId, JdwpVmVersionHandler()
        )
        addJdwpPacketHandler(
            JdwpVmIdSizesHandler.commandId, JdwpVmIdSizesHandler()
        )
    }

    fun addDdmPacketHandler(chunkType: Int, packetHandler: DdmPacketHandler) {
        ddmPacketHandlers[chunkType] = packetHandler
    }

    fun addJdwpPacketHandler(commandId: JdwpCommandId, packetHandler: JdwpPacketHandler) {
        jdwpPacketHandlers[commandId] = packetHandler
    }

    override fun invoke(
        server: FakeAdbServer,
        socket: Socket,
        device: DeviceState,
        args: String
    ) {
        val oStream: OutputStream
        val iStream: InputStream
        try {
            oStream = socket.getOutputStream()
            iStream = socket.getInputStream()
        } catch (ignored: IOException) {
            return
        }
        val pid: Int
        pid = try {
            args.toInt()
        } catch (ignored: NumberFormatException) {
            writeFailResponse(oStream, "Invalid pid specified: $args")
            return
        }
        val client = device.getClient(pid)
        if (client == null) {
            writeFailResponse(oStream, "No client exists for pid: $pid")
            return
        }

        // Make sure there is only one JDWP session for this process
        while (!client.startJdwpSession(socket)) {
            // There is one active JDWP session.
            // On API < 28, we return EOF right away.
            // On API >= 28, we wait until the previous session is released
            if (device.apiLevel < 28) {
                writeFailResponse(oStream, "JDWP Session already opened for pid: $pid")
                return
            } else {
                try {
                    Thread.sleep(50)
                } catch (e: InterruptedException) {
                    throw RuntimeException(e)
                }
            }
        }
        try {
            jdwpLoop(device, client, iStream, oStream)
        } finally {
            client.stopJdwpSession()
        }
    }

    private fun jdwpLoop(
        device: DeviceState,
        client: ClientState,
        iStream: InputStream,
        oStream: OutputStream
    ) {
        try {
            writeOkay(oStream)
        } catch (ignored: IOException) {
            return
        }
        val handshake = ByteArray(14)
        try {
            val readCount = iStream.read(handshake)
            if (handshake.size != readCount) {
                writeFailResponse(oStream, "Could not read full handshake.")
                return
            }
        } catch (ignored: IOException) {
            writeFailResponse(oStream, "Could not read handshake.")
            return
        }
        if (HANDSHAKE_STRING != String(handshake, StandardCharsets.US_ASCII)) {
            return
        }
        try {
            writeString(oStream, HANDSHAKE_STRING)
        } catch (ignored: IOException) {
            return
        }

        // default - ignore the packet and keep listening
        val defaultDdmHandler =
            DdmPacketHandler { device1: DeviceState, client1: ClientState, packet1: DdmPacket, jdwpHandlerOutput: JdwpHandlerOutput ->
                handleUnknownDdmsPacket(
                    device1,
                    client1,
                    packet1,
                    jdwpHandlerOutput
                )
            }
        val defaultJdwpHandler =
            JdwpPacketHandler { state: DeviceState, clientState: ClientState, packet1: JdwpPacket, jdwpHandlerOutput: JdwpHandlerOutput ->
                handleUnknownJdwpPacket(
                    state,
                    clientState,
                    packet1,
                    jdwpHandlerOutput
                )
            }

        var running = true
        val jdwpHandlerOutput = JdwpHandlerOutput(oStream)
        while (running) {
            running = try {
                val packet = readFrom(iStream)
                if (isDdmPacket(packet)) {
                    val ddmPacket = fromJdwpPacket(packet)
                    ddmPacketHandlers
                        .getOrDefault(ddmPacket.chunkType, defaultDdmHandler)
                        .handlePacket(device, client, ddmPacket, jdwpHandlerOutput)
                } else {
                    val commandId = JdwpCommandId(packet.cmdSet, packet.cmd)
                    jdwpPacketHandlers
                        .getOrDefault(commandId, defaultJdwpHandler)
                        .handlePacket(device, client, packet, jdwpHandlerOutput)
                }
            } catch (e: IOException) {
                writeFailResponse(oStream, "Could not read packet.")
                return
            }
        }
    }

    private fun handleUnknownJdwpPacket(
        state: DeviceState,
        clientState: ClientState,
        packet: JdwpPacket,
        jdwpHandlerOutput: JdwpHandlerOutput
    ): Boolean {
        System.err.printf(
            "FakeAdbServer: Unsupported JDWP packet: id=%d, cmdSet=%d, cmd=%d%n",
            packet.id, packet.cmdSet, packet.cmd
        )
        return true
    }

    private fun handleUnknownDdmsPacket(
        device: DeviceState,
        client: ClientState,
        packet: DdmPacket,
        jdwpHandlerOutput: JdwpHandlerOutput
    ): Boolean {
        System.err.printf(
            "FakeAdbServer: Unsupported DDMS command: '%s'%n",
            chunkTypeToString(packet.chunkType)
        )
        return true
    }

    companion object {

        private const val HANDSHAKE_STRING = "JDWP-Handshake"
    }
}
