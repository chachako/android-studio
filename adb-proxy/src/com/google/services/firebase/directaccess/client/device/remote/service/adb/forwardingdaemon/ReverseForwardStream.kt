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
import com.android.adblib.AdbSession
import com.android.adblib.DeviceSelector
import com.android.adblib.RemoteFileMode
import com.android.adblib.shellCommand
import com.android.adblib.syncSend
import com.android.adblib.withInputChannelCollector
import com.google.services.firebase.directaccess.client.device.remote.service.adb.forwardingdaemon.reverse.MessageParseException
import com.google.services.firebase.directaccess.client.device.remote.service.adb.forwardingdaemon.reverse.MessageType
import com.google.services.firebase.directaccess.client.device.remote.service.adb.forwardingdaemon.reverse.StreamDataHeader
import java.io.IOException
import java.io.InputStream
import java.net.Socket
import java.net.URI
import java.nio.ByteBuffer
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.logging.Logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.logging.Level

/**
 * The ReverseForwardStream streams data to and from the ReverseDaemon on the device.
 *
 * The ReverseForwardStream has a few responsibilities:
 * 1. Push the reverse daemon to the device
 * 2. Start the ReverseDaemon
 * 3. Read the stdin and write to the stdout of the daemon
 */
internal class ReverseForwardStream(
  val devicePort: String,
  localPort: String,
  private val streamId: Int,
  private val deviceId: String,
  private val adbSession: AdbSession,
  private val responseWriter: ResponseWriter,
  private val scope: CoroutineScope,
) : Stream {
  // TODO(b/247398366): use adblib sockets
  private val openSockets = mutableMapOf<Int, Socket>()
  var localPort: String = localPort
    private set
  private var streamReader: StreamReader? = null
  private val outputLock = Mutex()
  private val reverseDaemonReadyLatch = CountDownLatch(1)

  suspend fun run() {
    val device = DeviceSelector.fromSerialNumber(deviceId)
    withContext(Dispatchers.IO) {
      adbSession.deviceServices.syncSend(
        device,
        daemonPath,
        "/data/local/tmp/reverse_daemon.dex",
        RemoteFileMode.DEFAULT
      )
    }
    scope.launch(Dispatchers.IO) {
      val stdinInputChannel = adbSession.channelFactory.createPipedChannel()

      adbSession
        .deviceServices
        .shellCommand(
          device,
          "CLASSPATH=/data/local/tmp/reverse_daemon.dex app_process " +
            "/data/local/tmp/ " +
            "com.google.services.firebase.directaccess.client.device.remote.service.adb.forwardingdaemon.reverse.daemon." +
            "ReverseDaemon $devicePort"
        )
        .withInputChannelCollector()
        .withStdin(stdinInputChannel)
        .executeAsSingleOutput { inputChannelOutput ->
          StreamReader(inputChannelOutput.stdout, stdinInputChannel.pipeSource).apply {
            streamReader = this
            run()
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
   */
  private fun waitForReverseDaemonReady() {
    // We only wait for 1 second since this process is usually pretty fast. If we do not receive the
    // ready signal because something went wrong on ReverseDaemon,the behavior remains the same as
    // earlier.
    val timedOut = !reverseDaemonReadyLatch.await(1, TimeUnit.SECONDS)
    if (timedOut) {
      logger.warning("Timeout waiting for ReverseDaemon READY signal. Proceeding.")
    } else {
      logger.info("Reverse daemon ready")
    }
  }

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

  override fun receiveCommand(command: StreamCommand) {
    // We shouldn't receive anything from the device, because we're not sending anything to the
    // device.
    logger.warning("Unexpected command: $command")
  }

  private inner class StreamReader(
    private val input: AdbInputChannel,
    val output: AdbOutputChannel
  ) {
    private val buffer = ByteArray(1024 * 1024)

    suspend fun kill() {
      try {
        outputLock.withLock {
          output.write(ByteBuffer.wrap(StreamDataHeader(MessageType.KILL, -1, 0).toByteArray()))
          logger.info("Sent kill message")
        }
      } catch (e: IOException) {
        logger.log(Level.WARNING, "Socket already closed", e)
      }
    }

    suspend fun run() {
      val byteBuffer = ByteBuffer.wrap(buffer)

      while (true) {
        byteBuffer.position(0)
        byteBuffer.limit(12)
        try {
          input.readExactly(byteBuffer)
        } catch (e: Throwable) {
          logger.log(Level.WARNING, "Reverse daemon exited. Closing stream.", e)
          output.close()
          input.close()
          return
        }

        try {
          val header = StreamDataHeader(byteBuffer)
          when (header.type) {
            MessageType.OPEN -> handleOpen(header)
            MessageType.DATA -> handleData(header)
            MessageType.CLSE -> handleClose(header)
            MessageType.REDY -> handleReady()
            MessageType.KILL -> logger.warning("Unexpected KILL message from daemon")
          }
        } catch (e: MessageParseException) {
          byteBuffer.position(0)
          byteBuffer.limit(byteBuffer.capacity())
          val output = String(buffer, 0, 12) + String(buffer, 0, input.read(byteBuffer))
          logger.warning("Failed to parse message. Got: $output")
        }
      }
    }

    private fun handleReady() {
      reverseDaemonReadyLatch.countDown()
      logger.info("Reverse Forward stream is ready on the device")
    }

    private suspend fun handleOpen(header: StreamDataHeader) {
      if (openSockets.containsKey(header.streamId)) return
      logger
        .info("Opening new port (stream ${header.streamId}) at localhost:$localPort")
      val newSocket =
        withContext(Dispatchers.IO) {
          Socket(
            "localhost",
            Integer.parseInt(localPort.substringAfter("tcp:").substringBefore('\u0000'))
          )
        }
      openSockets[header.streamId] = newSocket
      scope.launch { SocketReader(header.streamId, newSocket.getInputStream(), output).run() }
    }

    private suspend fun handleData(header: StreamDataHeader) {
      val socket = openSockets[header.streamId]
      if (socket == null) {
        logger.info("Received data for unknown stream ${header.streamId}")
        return
      }
      val byteBuffer = ByteBuffer.wrap(buffer)
      byteBuffer.position(0)
      byteBuffer.limit(header.len)
      input.readExactly(byteBuffer)
      withContext(Dispatchers.IO) { socket.getOutputStream().write(buffer, 0, header.len) }
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

  private inner class SocketReader(
    private val streamId: Int,
    private val input: InputStream,
    private val output: AdbOutputChannel,
  ) {
    private val buffer = ByteArray(1024 * 1024)

    suspend fun run() {
      while (true) {
        val bytesRead: Int
        try {
          bytesRead = input.read(buffer)
        } catch (e: IOException) {
          break
        }
        if (bytesRead == -1) break

        outputLock.withLock {
          val byteBuffer = ByteBuffer.wrap(buffer)
          byteBuffer.position(0)
          byteBuffer.limit(bytesRead)
          output.writeExactly(
            ByteBuffer.wrap(StreamDataHeader(MessageType.DATA, streamId, bytesRead).toByteArray())
          )
          output.writeExactly(byteBuffer)
        }
      }

      outputLock.withLock {
        output.writeExactly(
          ByteBuffer.wrap(StreamDataHeader(MessageType.CLSE, streamId, 0).toByteArray())
        )
      }
    }
  }

  companion object {
    private val logger = Logger.getLogger(ReverseForwardStream::class.qualifiedName)
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
          val path = URI(resource.path).path.substringBefore("!")
          val builtRoot = Paths.get(path).parent.parent
          result = builtRoot.resolve("resources/reverse_daemon.dex")
        }
      }
      if (result == null || !Files.exists(result)) {
        throw Exception("Couldn't find reverse_daemon.dex")
      }
      result
    }
  }
}
