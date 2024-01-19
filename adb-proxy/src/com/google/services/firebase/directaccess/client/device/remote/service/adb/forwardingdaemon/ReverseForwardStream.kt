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
import com.android.adblib.AdbInputChannel
import com.android.adblib.AdbOutputChannel
import com.android.adblib.AdbSession
import com.android.adblib.DeviceSelector
import com.android.adblib.RemoteFileMode
import com.android.adblib.read
import com.android.adblib.shellCommand
import com.android.adblib.syncSend
import com.android.adblib.withInputChannelCollector
import com.google.services.firebase.directaccess.client.device.remote.service.adb.forwardingdaemon.reverse.MessageParseException
import com.google.services.firebase.directaccess.client.device.remote.service.adb.forwardingdaemon.reverse.MessageType
import com.google.services.firebase.directaccess.client.device.remote.service.adb.forwardingdaemon.reverse.StreamDataHeader
import java.io.EOFException
import java.io.IOException
import java.net.Inet4Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.URI
import java.nio.ByteBuffer
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.concurrent.CancellationException
import java.util.logging.Level
import java.util.logging.Logger
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull

/**
 * The ReverseForwardStream streams data to and from the ReverseDaemon on the device.
 *
 * The ReverseForwardStream has a few responsibilities:
 * 1. Push the reverse daemon to the device
 * 2. Start the ReverseDaemon
 * 3. Read the stdin and write to the stdout of the daemon
 *
 * @param sendsDaemon a flag to control whether to send the daemon and is disabled only for tests
 * @param socketFactory a method to create a Socket connecting to a specific host and port
 */
internal class ReverseForwardStream(
  val devicePort: String,
  localPort: String,
  private val streamId: Int,
  private val deviceId: String,
  private val adbSession: AdbSession,
  private val responseWriter: ResponseWriter,
  private val scope: CoroutineScope,
  private val sendsDaemon: Boolean = true,
  private val socketFactory: suspend (InetSocketAddress) -> AdbChannel = { address ->
    adbSession.channelFactory.connectSocket(address)
  },
) : Stream {
  private val openSockets = mutableMapOf<Int, AdbChannel>()
  var localPort: String = localPort
    private set

  private var streamReader: StreamReader? = null
  private val outputLock = Mutex()
  private val reverseDaemonReady = CompletableDeferred<Unit>()

  suspend fun run() {
    val device = DeviceSelector.fromSerialNumber(deviceId)
    if (sendsDaemon) {
      adbSession.deviceServices.syncSend(
        device,
        daemonPath,
        "/data/local/tmp/reverse_daemon.dex",
        RemoteFileMode.DEFAULT,
      )
    }
    scope.launch(Dispatchers.IO) {
      val stdinInputChannel = adbSession.channelFactory.createPipedChannel()

      try {
        adbSession.deviceServices
          .shellCommand(
            device,
            "CLASSPATH=/data/local/tmp/reverse_daemon.dex app_process " +
              "/data/local/tmp/ " +
              "com.google.services.firebase.directaccess.client.device.remote.service.adb.forwardingdaemon.reverse.daemon." +
              "ReverseDaemon $devicePort",
          )
          .withInputChannelCollector()
          .withStdin(stdinInputChannel)
          .executeAsSingleOutput { inputChannelOutput ->
            StreamReader(inputChannelOutput.stdout, stdinInputChannel.pipeSource).apply {
              streamReader = this
              run()
            }
          }
      } catch (e: EOFException) {
        // Remote channel may end unexpectedly when closing the forwarding daemon.
        // TODO (b/317130053): Expose IOException in a better way
        if (!scope.coroutineContext.job.isCancelled) {
          throw e
        }
      }
    }

    waitForReverseDaemonReady()
    // For "tcp:...", we need to output the port. For anything else, don't output anything after
    // "OKAY"
    // TODO: Get the real port from the daemon
    val output = devicePort.substringAfter("tcp:", "")

    responseWriter.writeOkayResponse(streamId, output)
  }

  /**
   * Sometimes screen sharing agent starts up before ReverseDaemon has set up sockets causing screen
   * sharing agent to fail because the socket doesn't exist yet. This is likely because app_process
   * launcher takes some time to actually execute the binary. We wait for a ready signal after which
   * we send OKAY back to Android Studio ensuring screen sharing agent starts after everything is
   * set up.
   *
   * We only wait for 1 second since this process is usually pretty fast. If we do not receive the
   * ready signal because something went wrong on ReverseDaemon,the behavior remains the same as
   * receiving the signal.
   */
  private suspend fun waitForReverseDaemonReady() =
    withTimeoutOrNull(1000) {
      reverseDaemonReady.await()
      logger.info("Reverse daemon ready")
    } ?: run { logger.warning("Timeout waiting for ReverseDaemon READY signal. Proceeding.") }

  /** Redirect new reverse forward connections to a different target port. */
  fun rebind(outbound: String) {
    localPort = outbound
  }

  /**
   * Kill the reverse forward.
   *
   * Note that this does not kill active connections, in order to mimic what Android does already.
   * It simply sends a signal ot the device that it should close the server socket of whatever is
   * currently listening.
   */
  suspend fun kill() {
    logger.info("Killing reverse forward")
    streamReader?.kill()
  }

  override fun sendWrite(command: WriteCommand) {
    logger.warning("Unexpected command: $command")
  }

  override fun sendClose() {
    logger.warning("Unexpected close")
  }

  override suspend fun receiveCommand(command: StreamCommand) {
    // We shouldn't receive anything from the device, because we're not sending anything to the
    // device.
    logger.warning("Unexpected command: $command")
  }

  /** A reader class that reads and processes data from [shellCommandInput]. */
  private inner class StreamReader(
    private val shellCommandInput: AdbInputChannel,
    private val shellCommandOutput: AdbOutputChannel,
  ) {
    private val buffer = ByteBuffer.allocate(1024 * 1024)
    private val headerBuffer = ByteBuffer.allocate(12)

    suspend fun kill() {
      try {
        outputLock.withLock {
          shellCommandOutput.writeExactly(StreamDataHeader(MessageType.KILL, -1, 0).toByteBuffer())
          logger.info("Sent kill message")
        }
      } catch (e: IOException) {
        logger.log(Level.WARNING, "Socket already closed", e)
      }
    }

    suspend fun run() {
      while (true) {
        headerBuffer.clear()
        try {
          shellCommandInput.readExactly(headerBuffer)
        } catch (e: Throwable) {
          // CoroutineScope in which run() was called might have been cancelled.
          // Don't log in that case
          if (e !is CancellationException) {
            logger.log(Level.WARNING, "Reverse daemon exited. Closing stream.", e)
          }
          shellCommandOutput.close()
          shellCommandInput.close()
          return
        }

        try {
          val header = StreamDataHeader(headerBuffer.flip())
          when (header.type) {
            MessageType.OPEN -> handleOpen(header)
            MessageType.DATA -> handleData(header)
            MessageType.CLSE -> handleClose(header)
            MessageType.REDY -> handleReady()
            MessageType.KILL -> logger.warning("Unexpected KILL message from daemon")
          }
        } catch (e: MessageParseException) {
          logger.warning("Failed to parse message. Got: ${String(headerBuffer.array())}")
        }
      }
    }

    private fun handleReady() {
      reverseDaemonReady.complete(Unit)
      logger.info("Reverse Forward stream is ready on the device")
    }

    private suspend fun handleOpen(header: StreamDataHeader) {
      if (openSockets.containsKey(header.streamId)) return
      logger.info("Opening new port (stream ${header.streamId}) at localhost:$localPort")
      val newSocket =
        socketFactory(
          InetSocketAddress(
            localhost,
            Integer.parseInt(localPort.substringAfter("tcp:").substringBefore('\u0000')),
          )
        )
      openSockets[header.streamId] = newSocket
      scope.launch { SocketReader(header.streamId, newSocket, shellCommandOutput).run() }
    }

    private suspend fun handleData(header: StreamDataHeader) {
      val socket = openSockets[header.streamId]
      if (socket == null) {
        logger.info("Received data for unknown stream ${header.streamId}")
        return
      }
      buffer.position(0)
      buffer.limit(header.len)
      shellCommandInput.readExactly(buffer)
      socket.writeExactly(buffer.flip())
    }

    private fun handleClose(header: StreamDataHeader) {
      val socket = openSockets.remove(header.streamId)
      if (socket == null) {
        logger.info("Received close for unknown stream ${header.streamId}")
        return
      }
      logger.info("Reverse Daemon closed stream ${header.streamId}")
      socket.close()
    }
  }

  /**
   * A reader class that reads data from [socketChannel], wraps the data to [StreamDataHeader] and
   * forwards them to [shellCommandOutput].
   */
  private inner class SocketReader(
    private val streamId: Int,
    private val socketChannel: AdbInputChannel,
    private val shellCommandOutput: AdbOutputChannel,
  ) {
    private val buffer = ByteBuffer.allocate(1024 * 1024)

    suspend fun run() {
      while (true) {
        buffer.clear()
        val bytesRead =
          try {
            socketChannel.read(buffer)
          } catch (e: IOException) {
            -1
          }
        if (bytesRead == -1) break

        outputLock.withLock {
          shellCommandOutput.writeExactly(
            StreamDataHeader(MessageType.DATA, streamId, bytesRead).toByteBuffer()
          )
          shellCommandOutput.writeExactly(buffer.flip())
        }
      }

      try {
        outputLock.withLock {
          shellCommandOutput.writeExactly(
            StreamDataHeader(MessageType.CLSE, streamId, 0).toByteBuffer()
          )
        }
      } catch (e: IOException) {
        // Output channel might be closed while writing CLSE message
        if (!scope.coroutineContext.job.isCancelled) {
          throw e
        }
      }
    }
  }

  companion object {
    private val logger = Logger.getLogger(ReverseForwardStream::class.qualifiedName)
    /** The localhost address, preferring IPv4 if it is present on the system. */
    private val localhost: InetAddress by lazy {
      val localhostAddresses = InetAddress.getAllByName("localhost")
      localhostAddresses.filterIsInstance<Inet4Address>().firstOrNull()
        ?: localhostAddresses.first()
    }

    private val daemonPath: Path by lazy {
      // TODO: this all needs to be handled by studio, ideally using DeployableFile
      val devRoot = ClassLoader.getSystemResource(".")
      var result: Path? = null
      if (devRoot != null) {
        val pluginDir = Paths.get(devRoot.toURI())
        val devPath =
          pluginDir.resolve(
            "../../../../../../bazel-bin/tools/base/adb-proxy/reverse-daemon/reverse_daemon.dex"
          )
        if (Files.exists(devPath)) {
          result = devPath
        }
      }
      if (result == null) {
        val resource = ReverseForwardStream::class.java.getResource("ReverseForwardStream.class")
        if (resource != null) {
          // we're just looking for the jar location, so remove the path within the jar.
          // (after the !). Also on Windows there can be a leading / before the drive letter, which
          // is invalid. Strip it off.
          val path =
            URI(resource.path).path.substringBefore("!").replaceFirst(Regex("^/(?=[a-zA-Z]:/)"), "")
          val proxyRoot = Paths.get(path).parent
          val builtRoot = proxyRoot.parent
          result = builtRoot.resolve("resources/reverse_daemon.dex")
          if (result == null || !Files.exists(result)) {
            // bazel run CLI case
            result = proxyRoot.resolve("reverse-daemon/reverse_daemon.dex")
          }
        }
      }
      if (result == null || !Files.exists(result)) {
        throw Exception("Couldn't find reverse_daemon.dex")
      }
      result
    }
  }
}
