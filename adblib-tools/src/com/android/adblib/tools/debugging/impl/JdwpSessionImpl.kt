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
package com.android.adblib.tools.debugging.impl

import com.android.adblib.AdbChannel
import com.android.adblib.AdbInputChannel
import com.android.adblib.AdbLogger
import com.android.adblib.AdbOutputChannel
import com.android.adblib.AdbSession
import com.android.adblib.ConnectedDevice
import com.android.adblib.readNBytes
import com.android.adblib.serialNumber
import com.android.adblib.thisLogger
import com.android.adblib.tools.debugging.JdwpSession
import com.android.adblib.tools.debugging.packets.JdwpPacketConstants.PACKET_BYTE_ORDER
import com.android.adblib.tools.debugging.packets.JdwpPacketConstants.PACKET_HEADER_LENGTH
import com.android.adblib.tools.debugging.packets.JdwpPacketView
import com.android.adblib.tools.debugging.packets.impl.EphemeralJdwpPacket
import com.android.adblib.tools.debugging.packets.impl.MutableJdwpPacket
import com.android.adblib.tools.debugging.packets.impl.PayloadProvider
import com.android.adblib.tools.debugging.packets.impl.PayloadProviderFactory
import com.android.adblib.tools.debugging.packets.impl.parseHeader
import com.android.adblib.tools.debugging.packets.writeToChannel
import com.android.adblib.utils.ResizableBuffer
import com.android.adblib.withPrefix
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.nio.ByteBuffer
import java.util.concurrent.atomic.AtomicInteger

internal class JdwpSessionImpl(
    override val device: ConnectedDevice,
    private val channel: AdbChannel,
    pid: Int,
    peerName: String,
    nextPacketIdBase: Int?
) : JdwpSession {
    private val session: AdbSession
        get() = device.session

    private val logger = thisLogger(session)
        .withPrefix("(hash=${hashCode()}, '$peerName') device='${device.serialNumber}' pid=$pid: ")

    private val inputChannel = session.channelFactory.createReadAheadChannel(channel)

    private val outputChannel = session.channelFactory.createWriteBackChannel(channel)

    private val handshakeHandler = HandshakeHandler(peerName, logger, inputChannel, outputChannel)

    private val sender = Sender(outputChannel)

    private val receiver = Receiver(session, inputChannel)

    private val sendMutex = Mutex()

    private val receiveMutex = Mutex()

    private val atomicPacketId: AtomicInteger? = nextPacketIdBase?.let { AtomicInteger(it) }

    init {
        logger.debug { "Opening JDWP session" }
    }

    override suspend fun shutdown() {
        logger.debug { "Shutting down JDWP session" }
        outputChannel.shutdown()
        inputChannel.close()
        channel.close()
    }

    override fun close() {
        logger.debug { "Closing JDWP session (and underlying channel)" }
        inputChannel.close()
        outputChannel.close()
        channel.close()
    }

    override suspend fun sendPacket(packet: JdwpPacketView) {
        // We serialize sending packets to the channel so that we always send fully formed packets
        sendMutex.withLock {
            sendHandshake()
            logger.verbose { "Sending JDWP packet: $packet" }
            sender.sendPacket(packet)
        }
    }

    override suspend fun receivePacket(): JdwpPacketView {
        // We serialize reading packets from the channel so that we always read fully formed packets
        receiveMutex.withLock {
            waitForHandshake()
            logger.verbose { "Waiting for next JDWP packet from channel" }
            val packet = receiver.receivePacket()
            logger.verbose { "Received JDWP packet: $packet" }
            return packet
        }
    }

    override fun nextPacketId(): Int {
        return atomicPacketId?.getAndIncrement()
            ?: throw UnsupportedOperationException("JDWP session does not support custom packet (IDs)")
    }

    private suspend fun sendHandshake() {
        handshakeHandler.sendHandshake()
    }

    private suspend fun waitForHandshake() {
        handshakeHandler.waitForHandshake()
    }

    class HandshakeHandler(
        private val peerName: String,
        parentLogger: AdbLogger,
        private val inputChannel: AdbInputChannel,
        private val outputChannel: AdbOutputChannel
    ) {

        private val logger = parentLogger.withPrefix("HandshakeHandler: ")

        private val channelMutex = Mutex()
        private var handshakeSent: Boolean = false
        private var handshakeReceived: Boolean = false

        suspend fun sendHandshake() {
            // Short-circuit: We don't need synchronization here, as the field is only set
            // once the handshake has already been sent
            if (handshakeSent) {
                return
            }

            channelMutex.withLock {
                // Execution is serialized here
                val workBuffer = ResizableBuffer().order(PACKET_BYTE_ORDER)
                sendHandshakeWorker(workBuffer)
            }
        }

        suspend fun waitForHandshake() {
            // Short-circuit: We don't need synchronization here, as the field is only set
            // once the handshake has been fully received
            if (handshakeReceived) {
                return
            }

            channelMutex.withLock {
                // Execution is serialized here
                if (!handshakeReceived) {
                    val workBuffer = ResizableBuffer().order(PACKET_BYTE_ORDER)
                    sendHandshakeWorker(workBuffer)

                    logger.debug { "Waiting for JDWP handshake from $peerName" }
                    receiveJdwpHandshake(workBuffer)
                    logger.debug { "JDWP handshake received from $peerName" }

                    handshakeReceived = true
                }
            }
        }

        private suspend fun sendHandshakeWorker(workBuffer: ResizableBuffer) {
            if (!handshakeSent) {
                logger.debug { "Sending JDWP handshake to $peerName" }
                workBuffer.clear()
                workBuffer.appendBytes(HANDSHAKE)
                val data = workBuffer.forChannelWrite()
                outputChannel.writeExactly(data)
                handshakeSent = true
            }
        }

        private suspend fun receiveJdwpHandshake(workBuffer: ResizableBuffer) {
            //TODO: This could be more efficient
            val bytesSoFar = ArrayList<Byte>()
            while (true) {
                bytesSoFar.add(readOneByte(workBuffer))

                if (isJdwpHandshake(bytesSoFar)) {
                    return
                }
                processEarlyJdwpPacket(bytesSoFar)
            }
        }

        private fun processEarlyJdwpPacket(bytesSoFar: MutableList<Byte>) {
            // See bug 178655046: There was a race condition in JDWP connection handling
            // for many years that resulted in APNM packets sometimes being sent before
            // the JDWP handshake.
            // This was eventually fixed in https://android-review.googlesource.com/c/platform/art/+/1569323
            // by making sure such packets are not sent until the handshake is sent.
            // Given the "APNM" packet is redundant with the "HELO" packet, we simply ignore
            // such pre-handshake packets.
            if (bytesSoFar.size >= PACKET_HEADER_LENGTH) {
                val buffer = ByteBuffer.wrap(bytesSoFar.toByteArray()).order(PACKET_BYTE_ORDER)
                val packet = MutableJdwpPacket()
                packet.parseHeader(buffer)
                if (packet.length - PACKET_HEADER_LENGTH <= buffer.remaining()) {
                    //TODO: We don't really need this, as we don't use this packet
                    packet.payloadProvider = PayloadProvider.forByteBuffer(buffer)
                    logger.debug { "Skipping JDWP packet received before JDWP handshake: $packet" }
                    bytesSoFar.clear()
                }
            }
        }

        private fun isJdwpHandshake(bytesSoFar: List<Byte>): Boolean {
            //TODO: This could be more efficient
            val bytesSoFarIndex = bytesSoFar.size - HANDSHAKE.size
            if (bytesSoFarIndex < 0) {
                return false
            }

            for (i in HANDSHAKE.indices) {
                if (bytesSoFar[bytesSoFarIndex + i] != HANDSHAKE[i]) {
                    return false
                }
            }
            return true
        }

        private suspend fun readOneByte(workBuffer: ResizableBuffer): Byte {
            workBuffer.clear()
            inputChannel.readExactly(workBuffer.forChannelRead(1))
            return workBuffer.afterChannelRead().get()
        }
    }

    class Sender(private val channel: AdbOutputChannel) {

        private val workBuffer = ResizableBuffer().order(PACKET_BYTE_ORDER)

        suspend fun sendPacket(packet: JdwpPacketView) {
            packet.writeToChannel(channel, workBuffer)
        }
    }

    class Receiver(
        session: AdbSession,
        private val channel: AdbInputChannel
    ) {

        /**
         * The [ResizableBuffer] we re-use during each call to [receivePacket] to temporarily
         * store data read from [channel].
         */
        private val workBuffer = ResizableBuffer().order(PACKET_BYTE_ORDER)

        private val payloadProviderFactory = PayloadProviderFactory(session)

        /**
         * The [MutableJdwpPacket] we re-use during each call to [receivePacket] to temporarily
         * parse and store JDWP packet headers.
         */
        private val workJdwpPacket = MutableJdwpPacket()

        /**
         * The [EphemeralJdwpPacket] returned from the last call to [receivePacket]
         */
        private var previousEphemeralJdwpPacket: EphemeralJdwpPacket? = null

        suspend fun receivePacket(): JdwpPacketView {
            // Note: The code below is not thread-safe by design, because JdwpSession is
            // not supposed to be thread-safe. This means we don't have to worry about
            // concurrent usages of "previousEphemeralJdwpPacket", for example.

            // Ensure we consume all bytes from the previous packet payload
            previousEphemeralJdwpPacket?.shutdown(workBuffer)
            previousEphemeralJdwpPacket = null

            // Read next packet
            return readOnePacket(workBuffer).also {
                previousEphemeralJdwpPacket = it
            }
        }

        private suspend fun readOnePacket(workBuffer: ResizableBuffer): EphemeralJdwpPacket {
            workBuffer.clear()

            // Read and parse header
            channel.readNBytes(workBuffer, PACKET_HEADER_LENGTH)
            workJdwpPacket.parseHeader(workBuffer.afterChannelRead())

            // Wrap payload
            val payloadProvider = payloadProviderFactory.create(workJdwpPacket, channel)

            // Return new JDWP packet
            return EphemeralJdwpPacket.fromPacket(workJdwpPacket, payloadProvider)
        }

    }

    companion object {

        private val HANDSHAKE = "JDWP-Handshake".toByteArray(Charsets.US_ASCII)
    }

}
