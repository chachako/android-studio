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

import com.android.adblib.AdbSession
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.time.Duration
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicBoolean
import java.util.logging.Logger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.util.logging.Level

/**
 * An implementation of the ADB server to device protocol that forwards all commands to a remote
 * server via gRPC.
 */
internal class ForwardingDaemonImpl(
  private val streamOpener: StreamOpener,
  private val scope: CoroutineScope,
  private val adbSession: AdbSession
) : ForwardingDaemon {
  private val streams = mutableMapOf<Int, Stream>()
  private val deviceStateLatch = DeviceStateLatch()
  private val startedLatch = Mutex(locked = true)
  private val started = AtomicBoolean(false)

  private var reverseService: ReverseService? = null
  private var features: String = ""
  override var devicePort: Int = -1

  // The socket the local ADB server sends ADB commands to. Bytes, in this case ADB commands,
  // coming to this socket are forwarded to the remote device.
  private lateinit var adbdSocket: Socket
  private lateinit var deviceState: DeviceState
  private lateinit var adbCommandHandler: Job

  private suspend fun run() {
    streamOpener.connect(this)

    withContext(Dispatchers.IO) {
      // TODO(b/247398366): use adblib sockets
      ServerSocket(0, 1, InetAddress.getLoopbackAddress()).use { serverSocket ->
        devicePort = serverSocket.localPort
        startedLatch.unlock()
        while (true) {
          try {
            logger.info("Waiting for remote device to come online.")
            deviceStateLatch.waitForOnline()
            logger.info("Device is online at port: $devicePort!")
            adbdSocket = serverSocket.accept()
            reverseService =
              ReverseService(
                "localhost:$devicePort",
                scope,
                adbdSocket.getOutputStream(),
                adbSession
              )

            while (true) {
              val command = Command.readFrom(adbdSocket.getInputStream())

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
  }

  override fun close() {
    if (started.get()) {
      runBlocking(scope.coroutineContext) { onStateChanged(DeviceState.OFFLINE) }
      adbCommandHandler.cancel()
    }
  }

  override suspend fun onStateChanged(newState: DeviceState, features: String?) {
    if (features != null) this.features = features
    deviceState = newState
    deviceStateLatch.onState(newState)
    if (this::adbdSocket.isInitialized) adbdSocket.close()
  }

  private fun handleConnect() {
    // We ignore the information in the connect request coming from the local ADB server and
    // respond with device information we gathered when waiting for the device to come online.
    val response = ConnectCommand(banner = "${deviceState.adbState}::features=$features")
    response.writeTo(adbdSocket.getOutputStream())
  }

  private suspend fun handleOpen(header: OpenCommand) {
    if (header.service.startsWith("reverse:")) {
      reverseService!!.handleReverse(header.service, header.localId)
    } else {
      streams[header.localId] =
        streamOpener.open(header.service, header.localId, adbdSocket.getOutputStream())

      OkayCommand(header.localId, header.localId).writeTo(adbdSocket.getOutputStream())
    }
  }

  private fun handleWrite(command: WriteCommand) {
    streams[command.remoteId]?.sendWrite(command)

    OkayCommand(command.remoteId, command.remoteId).writeTo(adbdSocket.getOutputStream())
  }

  private fun handleOkay() {
    // OKAY from local ADB server. Nothing to do here.
  }

  private fun handleClose(command: CloseCommand) {
    streams[command.remoteId]?.sendClose()

    streams.remove(command.remoteId)
  }

  /** Receive a command from the remote ADB server. */
  override fun receiveRemoteCommand(command: StreamCommand) {
    streams[command.remoteId]?.receiveCommand(command)
  }

  private class DeviceStateLatch {
    private val onlineStates = setOf(DeviceState.DEVICE, DeviceState.RECOVERY, DeviceState.RESCUE)
    private val waiters = mutableSetOf<Mutex>()
    private val lock = Mutex()
    private var state = DeviceState.MISSING

    suspend fun waitForOnline() {
      val waiter = Mutex(locked = true)
      lock.withLock {
        if (state in onlineStates) {
          return
        }
        waiters.add(waiter)
      }
      waiter.lock()
      lock.withLock { waiters.remove(waiter) }
    }

    suspend fun onState(newState: DeviceState) {
      if (newState in onlineStates) {
        lock.withLock { waiters.forEach { it.unlock() } }
      }
    }
  }

  companion object {
    private val logger = Logger.getLogger(ForwardingDaemonImpl::class.qualifiedName)
  }
}
