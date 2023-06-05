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

import com.android.adblib.AdbChannel
import com.android.adblib.AdbServerSocket
import com.android.adblib.AdbSession
import com.android.adblib.DeviceAddress
import com.android.adblib.DeviceSelector
import com.android.adblib.shellCommand
import com.android.adblib.withInputChannelCollector
import java.nio.ByteBuffer
import java.time.Duration
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicBoolean
import java.util.logging.Level
import java.util.logging.Logger
import kotlin.system.measureTimeMillis
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.takeWhile
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

/**
 * An implementation of the ADB server to device protocol that forwards all commands to a remote
 * server via gRPC.
 */
internal class ForwardingDaemonImpl(
  private val streamOpener: StreamOpener,
  private val scope: CoroutineScope,
  private val adbSession: AdbSession,
  private val serverSocketProvider: suspend () -> AdbServerSocket = {
    adbSession.channelFactory.createServerSocket()
  }
) : ForwardingDaemon {

  private val streams = mutableMapOf<Int, Stream>()
  private val startedLatch = Mutex(locked = true)
  private val started = AtomicBoolean(false)

  private var reverseService: ReverseService? = null
  private var features: String = ""
  override var devicePort: Int = -1
  private val serialNumber: String
    get() = "localhost:$devicePort"

  // The socket the local ADB server sends ADB commands to. Bytes, in this case ADB commands,
  // coming to this socket are forwarded to the remote device.
  private lateinit var localAdbChannel: AdbChannel
  private val deviceState = MutableStateFlow(DeviceState.MISSING)
  private val onlineStates = setOf(DeviceState.DEVICE, DeviceState.RECOVERY, DeviceState.RESCUE)
  private lateinit var adbCommandHandler: Job

  override val roundTripLatencyMsFlow: Flow<Long> =
    flow {
        deviceState.takeWhile { it !in onlineStates }.collect()

        val device = DeviceSelector.fromSerialNumber(serialNumber)
        val stdinInputChannel = adbSession.channelFactory.createPipedChannel()
        val byteArray = "Foo".toByteArray()
        adbSession.deviceServices
          .shellCommand(device, "cat")
          .withInputChannelCollector()
          .withStdin(stdinInputChannel)
          .executeAsSingleOutput { result ->
            val input = result.stdout
            val output = stdinInputChannel.pipeSource
            while (true) {
              try {
                val buffer = ByteBuffer.wrap(byteArray)
                withTimeout(ROUND_TRIP_LATENCY_LIMIT.toMillis()) {
                  emit(
                    measureTimeMillis {
                      output.writeExactly(buffer)
                      buffer.flip()
                      input.readExactly(buffer)
                    }
                  )
                }
              } catch (e: Exception) {
                emit(ROUND_TRIP_LATENCY_LIMIT.toMillis())
                continue
              }
              delay(LATENCY_COLLECTION_INTERVAL.toMillis())
            }
          }
      }
      .flowOn(Dispatchers.IO)
      .shareIn(scope, SharingStarted.WhileSubscribed(), 1)

  private suspend fun run() {
    streamOpener.connect(this)

    withContext(Dispatchers.IO) {
      serverSocketProvider().use { serverSocket ->
        devicePort = serverSocket.bind().port
        startedLatch.unlock()
        while (true) {
          try {
            logger.info("Waiting for remote device to come online.")
            deviceState.takeWhile { it !in onlineStates }.collect()
            logger.info("Device is online at port: $devicePort!")
            // Connect this "device" on the given port to the local ADB server using `adb connect`.
            adbSession.hostServices.connect(DeviceAddress(serialNumber))
            // A mutex is added to the wrapped AdbChannel for concurrent write calling.
            localAdbChannel =
              serverSocket.accept().let { adbChannel ->
                object : AdbChannel by adbChannel {
                  val writeMutex = Mutex()
                  override suspend fun write(
                    buffer: ByteBuffer,
                    timeout: Long,
                    unit: TimeUnit
                  ): Int = writeMutex.withLock { adbChannel.write(buffer, timeout, unit) }
                }
              }
            reverseService =
              ReverseService(serialNumber, scope, ResponseWriter(localAdbChannel), adbSession)

            while (true) {
              ensureActive()
              val command = Command.readFrom(localAdbChannel)

              logger.fine("Local Server -> Device: $command")

              when (command) {
                is ConnectCommand -> handleConnect()
                is OpenCommand -> handleOpen(command)
                is WriteCommand -> handleWrite(command)
                is OkayCommand -> handleOkay()
                is CloseCommand -> handleClose(command)
                else -> logger.warning("Unexpected command: $command")
              }
            }
          } catch (e: CancellationException) {
            throw e
          } catch (e: Exception) {
            // The socket likely closed because we closed it ourselves. This is done intentionally
            // to kick the device from online to offline, so we can block until the device is online
            // again. We don't break or throw here so the ADB server can reconnect.
            logger.log(Level.INFO, "Error reading from socket.", e)
          } finally {
            // We need to clean up the reverse service, so we don't leak any threads.
            scope.launch { reverseService?.killAll() }
            logger.info("Connection to fake device closed.")
          }
        }
      }
    }
  }

  override suspend fun start(timeout: Duration) {
    if (started.getAndSet(true)) return

    adbCommandHandler = scope.launch { run() }
    try {
      withTimeout(timeout.toMillis()) { startedLatch.lock() }
    } catch (ignored: TimeoutCancellationException) {
      throw TimeoutException("Device not started after $timeout")
    }
    scope.launch {
      var consecutiveConnectionLostCount = 0
      roundTripLatencyMsFlow.collect {
        if (it < ROUND_TRIP_LATENCY_LIMIT.toMillis()) {
          consecutiveConnectionLostCount = 0
        } else {
          // Close connection if latency exceed ROUND_TRIP_LATENCY_LIMIT for more than 3 times.
          if (++consecutiveConnectionLostCount >= 3) {
            close()
          }
        }
      }
    }
  }

  override fun close() {
    if (started.get()) {
      adbCommandHandler.cancel()
      scope.cancel()
      streams.values.forEach { it.sendClose() }
      runBlocking {
        onStateChanged(DeviceState.OFFLINE)
        if (adbSession.hostServices.devices().any { it.serialNumber == serialNumber }) {
          adbSession.hostServices.disconnect(DeviceAddress(serialNumber))
        }
      }
    }
  }

  override suspend fun onStateChanged(newState: DeviceState, features: String?) {
    if (features != null) this.features = features
    deviceState.update { newState }
    if (this::localAdbChannel.isInitialized) localAdbChannel.close()
  }

  private suspend fun handleConnect() {
    // We ignore the information in the connect request coming from the local ADB server and
    // respond with device information we gathered when waiting for the device to come online.
    val response = ConnectCommand(banner = "${deviceState.value.adbState}::features=$features")
    response.writeTo(localAdbChannel)
  }

  private suspend fun handleOpen(header: OpenCommand) {
    if (header.service.startsWith("reverse:")) {
      reverseService!!.handleReverse(header.service, header.localId)
    } else {
      streams[header.localId] = streamOpener.open(header.service, header.localId, localAdbChannel)

      OkayCommand(header.localId, header.localId).writeTo(localAdbChannel)
    }
  }

  private suspend fun handleWrite(command: WriteCommand) {
    streams[command.remoteId]?.sendWrite(command)

    OkayCommand(command.remoteId, command.remoteId).writeTo(localAdbChannel)
  }

  private fun handleOkay() {
    // OKAY from local ADB server. Nothing to do here.
  }

  private fun handleClose(command: CloseCommand) {
    streams[command.remoteId]?.sendClose()

    streams.remove(command.remoteId)
  }

  /** Receive a command from the remote ADB server. */
  override suspend fun receiveRemoteCommand(command: StreamCommand) {
    streams[command.remoteId]?.receiveCommand(command)
  }

  companion object {
    private val logger = Logger.getLogger(ForwardingDaemonImpl::class.qualifiedName)
  }
}
