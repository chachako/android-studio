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
package com.android.adblib.tools.debugging

import com.android.adblib.AdbChannel
import com.android.adblib.AdbSession
import com.android.adblib.ConnectedDevice
import com.android.adblib.CoroutineScopeCache
import com.android.adblib.getOrPutSynchronized
import com.android.adblib.tools.debugging.SharedJdwpSessionFilter.FilterId
import com.android.adblib.tools.debugging.impl.SharedJdwpSessionImpl
import com.android.adblib.tools.debugging.packets.JdwpPacketView
import com.android.adblib.tools.debugging.packets.withPayload
import com.android.adblib.tools.debugging.utils.NoDdmsPacketFilterFactory
import com.android.adblib.utils.ResizableBuffer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.takeWhile
import java.io.EOFException
import kotlin.coroutines.cancellation.CancellationException

/**
 * A thread-safe version of [JdwpSession] that consumes packets on-demand via [newPacketReceiver]
 *
 * * For sending packets, the [sendPacket] methods works the same way as the underlying
 *   [JdwpSession.sendPacket], i.e. it is thread-safe and automatically handle the JDWP
 *   handshake. One difference is that writing to the underlying [socket][AdbChannel]
 *   is performed in a custom [CoroutineScope] so that cancellation of the caller
 *   (e.g. timeout) does not close the underlying socket. Closing the underlying socket
 *   would mean all other consumers of this JDWP session would be unable to perform
 *   any other operation on it.
 *
 * * For receiving packets, the [newPacketReceiver] method allows callers to register a
 *   [JdwpPacketReceiver] that exposes a [Flow] of [JdwpPacketView] for collecting *all*
 *   packets received from the JDWP session. Receivers are called sequentially and should
 *   handle their exceptions. Similar to [sendPacket] behavior, any I/O performed on the
 *   underlying [AdbChannel] is executed in a custom [CoroutineScope] so that cancellation
 *   of receivers coroutine does not close the underlying [socket][AdbChannel].
 */
interface SharedJdwpSession {

    /**
     * The [ConnectedDevice] this [SharedJdwpSession] is connected to.
     */
    val device: ConnectedDevice

    /**
     * The process ID this [SharedJdwpSession] handles
     */
    val pid: Int

    /**
     * Sends a [JdwpPacketView] to the underlying [JdwpSession].
     *
     * Note this method can block until the underlying communication channel
     * "send" buffer has enough room to store the packet.
     */
    suspend fun sendPacket(packet: JdwpPacketView)

    /**
     * Creates a [JdwpPacketReceiver] to collect [JdwpPacketView] coming from the
     * underlying [JdwpSession].
     *
     * ### Usage
     *
     *       session.newReceiver()
     *          .withName("Foo") // An arbitrary name used for debugging
     *          .onActivation {
     *              // Receiver has been activated and is guaranteed to receive all
     *              // received packets from this point on
     *          }
     *          .receive { packet ->
     *              // Receiver is active and a packet has been received.
     *              // The receiver has exclusive access to the packet until this block
     *              // ends.
     *          }
     *
     * ### Notes
     *
     * A [JdwpPacketReceiver] is initially **inactive**, i.e. does not collect packets and
     * does not make this [SharedJdwpSession] start consuming packets from the underlying
     * [JdwpSession]. A [JdwpPacketReceiver] is **activated** by calling [JdwpPacketReceiver.flow]
     * (or the [JdwpPacketReceiver.receive] shortcut).
     *
     * * All active [JdwpPacketReceiver]s are guaranteed to be invoked sequentially, meaning
     *   they can freely use any field of the [JdwpPacketView], as well as consume the
     *   [JdwpPacketView.withPayload] without any explicit synchronization. This also
     *   implies that **receivers should process packets quickly** to prevent blocking
     *   other receivers. Conceptually, the [SharedJdwpSession] works like this
     *
     *
     *    while(!EOF) {
     *      val packet = collect one JdwpPacketView from the JdwpSession
     *      activeReceiverFlowCollectors.forEach {
     *        it.emit(packet)
     *      }
     *    }
     *
     * * Active [JdwpPacketReceiver]s are cancelled if this session is closed.
     *
     * * Active [JdwpPacketReceiver]s are notified of the termination of the underlying [JdwpSession]
     *   with a [Throwable]. A "normal" termination is an [EOFException].
     *
     *   @see JdwpPacketReceiver
     */
    suspend fun newPacketReceiver(): JdwpPacketReceiver

    /**
     * Returns a unique [JDWP packet ID][JdwpPacketView.id] to use for sending
     * a [JdwpPacketView], typically a [command packet][JdwpPacketView.isCommand],
     * in this session. Each call returns a new unique value.
     *
     * See [JdwpSession.nextPacketId]
     */
    fun nextPacketId(): Int

    /**
     * Add a [JdwpPacketView] to the list of "replay packets", i.e. the list of [JdwpPacketView]
     * that each new [receiver][newPacketReceiver] receives before any other packet from the
     * underlying [JdwpSession].
     */
    suspend fun addReplayPacket(packet: JdwpPacketView)

    companion object {

        private val addSharedJdwpSessionFilterFactoryKey = CoroutineScopeCache.Key<Unit>(
            "SharedJdwpSession.addSharedJdwpSessionFilterFactory"
        )

        internal fun create(
            device: ConnectedDevice,
            pid: Int,
            jdwpSessionFactory: suspend (ConnectedDevice) -> JdwpSession
        ): SharedJdwpSessionImpl {
            // Add the DdmsPacketFilterFactory to the list of active filters of the AdbSession
            // (in a very round-about way to make sure the filter is added only once per
            // AdbSession instance).
            //TODO: Make this configurable
            val session = device.session
            session.cache.getOrPutSynchronized(addSharedJdwpSessionFilterFactoryKey) {
                session.addSharedJdwpSessionFilterFactory(NoDdmsPacketFilterFactory())
            }
            return SharedJdwpSessionImpl(device, jdwpSessionFactory, pid)
        }
    }
}

/**
 * Provides access to a [Flow] of [JdwpPacketView]
 *
 * @see SharedJdwpSession.newPacketReceiver
 */
interface JdwpPacketReceiver {

    /**
     * Sets an arbitrary name for this receiver
     */
    fun withName(name: String): JdwpPacketReceiver

    /**
     * Applies a [FilterId] to this [JdwpPacketReceiver] so its [flow] does not emit
     * [JdwpPacketView] instances filtered by the corresponding [SharedJdwpSessionFilter].
     */
    fun withFilter(filterId: FilterId): JdwpPacketReceiver

    /**
     * Sets a [activationBlock] that is invoked when this receiver is activated, but before
     * any [JdwpPacketView] is received.
     *
     * Note: [activationBlock] is executed on the [AdbSession.ioDispatcher] dispatcher
     */
    fun withActivation(activationBlock: suspend () -> Unit): JdwpPacketReceiver

    /**
     * Starts receiving [packets][JdwpPacketView] from the underlying [JdwpSession] and invokes
     * [receiver] on [AdbSession.ioDispatcher] for each received packet.
     *
     * ### Detailed behavior
     *
     * * First, [withActivation] is launched concurrently and is guaranteed to be invoked **after**
     * [receiver] is ready to be invoked. This means [withActivation] can use
     * [SharedJdwpSession.sendPacket] that will be processed in [receiver].
     * * Then, all replay packets from [SharedJdwpSession.addReplayPacket] are sent to [receiver]
     * * Then, the underlying [SharedJdwpSession] is activated if needed, i.e. [JdwpPacketView]
     * are read from the underlying [JdwpSession], and [receiver] is invoked on
     * [AdbSession.ioDispatcher] for each [JdwpPacketView] received from the underlying
     * [JdwpSession], except for the packets filtered by the (optional) [SharedJdwpSessionFilter]
     * specified in [withFilter].
     *
     * ### Accessing packet payload
     *
     * The payload of the [JdwpPacketView] passed to [receiver] is guaranteed to be valid only
     * for the duration of the [receiver] call. This is because, most of the time, the
     * [JdwpPacketView] payload is directly connected to the underlying network socket and
     * will be invalidated once the next [JdwpPacketView] is read from that socket.
     *
     * ### Exceptions
     *
     * * This function returns when the underlying [SharedJdwpSession] reaches EOF.
     * * This function re-throws any other exception from the underlying [SharedJdwpSession].
     */
    suspend fun receive(receiver: suspend (JdwpPacketView) -> Unit)

}

/**
 * Wraps [JdwpPacketReceiver.receive] into a [Flow] of [JdwpPacketView].
 *
 * ### Performance
 *
 * To ensure all [JdwpPacketView] instances of the flow are guaranteed to be valid in
 * downstream flows (e.g. [`take(n)`][Flow.take] or [`buffer(n)`][Flow.buffer]), as well as
 * after the flow completes, [JdwpPacketView.toOffline] is invoked on each packet of the flow,
 * so there is a cost in terms of memory usage versus using the [JdwpPacketReceiver.receive] method.
 */
fun JdwpPacketReceiver.flow(): Flow<JdwpPacketView> {
    return channelFlow {
        val workBuffer = ResizableBuffer()
        receive { packet ->
            // Make the packet "offline" (i.e. read payload in memory if needed) to
            // ensure it is safe to use in downstream flows (e.g. filtering,
            // buffering, etc)
            send(packet.toOffline(workBuffer))
        }
    }
}

/**
 * Stores the result of the mapping function passed to [JdwpPacketReceiver.receiveMapFirst]
 * or [JdwpPacketReceiver.receiveMapFirstOrNull]. `null` values ar supported.
 */
class ReceiveResult<R> {
    private var result: Any? = NOT_SET

    val isCompleted: Boolean
        get() = result !== NOT_SET

    fun complete(value: R) {
        result = value
    }

    fun getOrThrow(): R {
        return if (result === NOT_SET) {
            throw NoSuchElementException()
        } else {
            @Suppress("UNCHECKED_CAST")
            result as R
        }
    }

    fun getOrNull(): R? {
        return if (result === NOT_SET) {
            null
        } else {
            @Suppress("UNCHECKED_CAST")
            result as R?
        }
    }

    companion object {
        private val NOT_SET = Any()
    }
}

private class AbortReceive(
    val receiveResult: ReceiveResult<*>
) : CancellationException("Receive cancelled")

/**
 * Returns the first packet that [block] transforms into a value stored in [ReceiveResult]
 */
private suspend fun <R> JdwpPacketReceiver.receiveMapFirstResult(
    block: suspend ReceiveResult<R>.(JdwpPacketView) -> Unit
): ReceiveResult<R> {
    val receiveResult = ReceiveResult<R>()
    return try {
        receive { livePacket ->
            receiveResult.block(livePacket)
            if (receiveResult.isCompleted) {
                throw AbortReceive(receiveResult)
            }
        }
        receiveResult
    } catch(e: AbortReceive) {
        if (e.receiveResult === receiveResult) {
            receiveResult
        } else {
            throw e
        }
    }
}

/**
 * Returns the first packet that [block] transforms into a value stored in [ReceiveResult]
 */
suspend fun <R> JdwpPacketReceiver.receiveMapFirst(
    block: suspend ReceiveResult<R>.(JdwpPacketView) -> Unit
): R {
    return receiveMapFirstResult(block).getOrThrow()
}

/**
 * Returns the first packet that [block] transforms into a value stored in [ReceiveResult],
 * or `null` if no value was generated.
 */
suspend fun <R> JdwpPacketReceiver.receiveMapFirstOrNull(
    block: suspend ReceiveResult<R>.(JdwpPacketView) -> Unit
): R? {
    return receiveMapFirstResult(block).getOrNull()
}

/**
 * Returns the first [JdwpPacketView] received by the [JdwpPacketReceiver].
 * Throws [NoSuchElementException] if EOF was reached.
 *
 * @see [Flow.first]
 */
suspend inline fun JdwpPacketReceiver.receiveFirst(): JdwpPacketView {
    return receiveFirstOrNull() ?: throw NoSuchElementException()
}

/**
 * Returns the first [JdwpPacketView] received by the [JdwpPacketReceiver], or `null`
 * if EOF was reached.
 *
 * @see [Flow.firstOrNull]
 */
suspend inline fun JdwpPacketReceiver.receiveFirstOrNull(): JdwpPacketView? {
    return receiveFirstOrNull { true }
}

/**
 * Returns the first [JdwpPacketView] that matches the given [predicate].
 * Throws [NoSuchElementException] if no packet matched the [predicate].
 *
 * @see [Flow.first]
 */
suspend inline fun JdwpPacketReceiver.receiveFirst(
    crossinline predicate: suspend (JdwpPacketView) -> Boolean
): JdwpPacketView {
    return receiveFirstOrNull(predicate) ?: throw NoSuchElementException()
}

/**
 * Returns the first [JdwpPacketView] that matches the given [predicate], or `null` if no packet
 * matches the [predicate].
 *
 * @see [Flow.firstOrNull]
 */
suspend inline fun JdwpPacketReceiver.receiveFirstOrNull(
    crossinline predicate: suspend (JdwpPacketView) -> Boolean
): JdwpPacketView? {
    return receiveMapFirstOrNull { livePacket ->
        if (predicate(livePacket)) {
            // Make the packet offline so that it can be safely used outside
            // the `receive` scope.
            this@receiveMapFirstOrNull.complete(livePacket.toOffline())
        }
    }
}

/**
 * Calls [predicate] on each received [JdwpPacketView] as long as [predicate] returns `true`
 * or EOF is reached.
 *
 * @see [Flow.takeWhile]
 */
suspend fun JdwpPacketReceiver.receiveWhile(
    predicate: suspend (JdwpPacketView) -> Boolean
) {
    receiveMapFirstResult<Unit> { livePacket ->
        if (!predicate(livePacket)) {
            this@receiveMapFirstResult.complete(Unit)
        }
    }
}

/**
 * Calls [predicate] on each received [JdwpPacketView] until [predicate] returns `true`
 * or EOF is reached.
 *
 * @see [Flow.takeWhile]
 */
suspend inline fun JdwpPacketReceiver.receiveUntil(
    crossinline predicate: suspend (JdwpPacketView) -> Boolean
) {
    return receiveWhile { livePacket -> !predicate(livePacket) }
}
