/*
 * Copyright 2022 The Android Open Source Project
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

package com.google.services.firebase.directaccess.client.device.remote.service.adb.forwardingdaemon.reverse.daemon

import android.net.LocalServerSocket
import android.system.ErrnoException
import android.system.Os
import android.system.OsConstants
import android.util.Log
import com.google.services.firebase.directaccess.client.device.remote.service.adb.forwardingdaemon.reverse.MessageType
import com.google.services.firebase.directaccess.client.device.remote.service.adb.forwardingdaemon.reverse.StreamDataHeader
import java.io.InputStream
import java.io.OutputStream
import java.net.ServerSocket
import java.nio.ByteBuffer
import java.util.concurrent.Executor
import java.util.concurrent.Executors
import kotlin.system.exitProcess

/** Implementation of [InputStream.readNBytes] in Android, as it wasn't introduced until API 33. */
private fun InputStream.readNBytesAndroid(buffer: ByteArray, len: Int): Int {
  var bytesRead = 0
  while (bytesRead < len) {
    val read = read(buffer, bytesRead, len - bytesRead)
    if (read == -1) return if (bytesRead == 0) -1 else bytesRead
    bytesRead += read
  }
  return bytesRead
}

/**
 * The ReverseDaemon listens on a specific port provided in the args and creates new streams to the
 * ReverseForwardStream that created it.
 */
object ReverseDaemon {
  private const val TAG = "ReverseDaemon"
  private val writeLock = Any()
  private val openSockets = mutableMapOf<Int, SocketReader>()
  private val socketExecutor: Executor = Executors.newCachedThreadPool()

  // The ReverseDaemon is designed to write stream data to stdout which is read by the
  // ReverseForwardStream that created it. Writing to stdout is how data leaves the device.
  private val output = System.out

  @JvmStatic
  fun main(args: Array<String>) {
    try {
      Log.d(TAG, "Started reverse daemon with args: ${args.joinToString()}.")
      if (args.isEmpty()) {
        Log.d(TAG, "Args is empty!")
      }
      // TODO: better input validation.

      val target = args[0]
      val type = target.substringBefore(":")
      val targetPort = target.substringAfter(":")
      Log.d(TAG, "Type: '$type'. Target Port: '$targetPort'")
      val acceptor =
        when (type) {
          "tcp" -> TcpSocketAcceptor(Integer.parseInt(targetPort))
          "local",
          "localabstract" -> LocalSocketAcceptor(targetPort)
          else -> exitProcess(1)
        }

      Thread(StdinReader(acceptor)).start()

      // Send REDY message back to the ReverseForwardStream indicating this ReverseDaemon has
      // started listening for connections on the given targetPort
      output.write(StreamDataHeader(MessageType.REDY, 0, 0).toByteArray())

      var socketId = 1
      while (true) {
        Log.d(TAG, "Waiting for a socket. id: $socketId")
        val socket = acceptor.accept(socketId)
        openSockets[socketId] = socket
        // send the connect message synchronously to ensure ordering
        socket.init()
        socketExecutor.execute(socket)
        socketId += 1
      }
    } catch (t: Throwable) {
      Log.e(TAG, "Error running reverse daemon", t)
    }
  }

  private class StdinReader(private val acceptor: SocketAcceptor) : Runnable {
    private val buffer = ByteArray(1024 * 1024)
    private val input = System.`in`

    override fun run() {
      val byteBuffer = ByteBuffer.wrap(buffer)

      while (true) {
        val numBytesRead = input.readNBytesAndroid(buffer, 12)
        if (numBytesRead < 12) break

        val header = StreamDataHeader(byteBuffer)
        when (header.type) {
          MessageType.OPEN -> Log.w(TAG, "Unexpected OPEN message.")
          MessageType.REDY -> Log.w(TAG, "Unexpected REDY message.")
          MessageType.DATA -> handleData(header)
          MessageType.CLSE -> handleClose(header)
          MessageType.KILL -> handleKill()
        }
      }
      Log.i(TAG, "Stopping reverse forward. Stdin closed.")
      acceptor.stopAccepting()
    }

    private fun handleData(header: StreamDataHeader) {
      val socket = openSockets[header.streamId] ?: return
      input.readNBytesAndroid(buffer, header.len)
      socket.socketOutput.write(buffer, 0, header.len)
    }

    private fun handleClose(header: StreamDataHeader) {
      val socket = openSockets.remove(header.streamId) ?: return
      socket.close()
    }

    private fun handleKill() {
      Log.i(TAG, "Killing reverse forward.")
      acceptor.stopAccepting()
    }
  }

  private class SocketReader(
    private val streamId: Int,
    private val input: InputStream,
    val socketOutput: OutputStream,
    val close: () -> Unit,
  ) : Runnable {
    private val buffer = ByteArray(1024 * 1024)

    fun init() {
      synchronized(writeLock) {
        Log.d(TAG, "write open socket $streamId")
        output.write(StreamDataHeader(MessageType.OPEN, streamId, 0).toByteArray())
      }
    }

    override fun run() {
      while (true) {
        val bytesRead = input.read(buffer)
        if (bytesRead == -1) break

        synchronized(writeLock) {
          output.write(StreamDataHeader(MessageType.DATA, streamId, bytesRead).toByteArray())
          output.write(buffer, 0, bytesRead)
        }
      }

      synchronized(writeLock) {
        output.write(StreamDataHeader(MessageType.CLSE, streamId, 0).toByteArray())
      }
    }
  }

  private interface SocketAcceptor {
    fun accept(streamId: Int): SocketReader
    fun stopAccepting()
  }

  /** SocketAcceptor for standard TCP sockets. */
  private class TcpSocketAcceptor(
    port: Int,
  ) : SocketAcceptor {
    private val serverSocket = ServerSocket(port)

    override fun accept(streamId: Int): SocketReader {
      val socket = serverSocket.accept()
      return SocketReader(streamId, socket.getInputStream(), socket.getOutputStream()) {
        socket.close()
      }
    }

    override fun stopAccepting() {
      serverSocket.close()
    }
  }

  /** SocketAcceptor for UNIX domain sockets in Android. */
  private class LocalSocketAcceptor(
    name: String,
  ) : SocketAcceptor {
    private val serverSocket = LocalServerSocket(name)

    override fun accept(streamId: Int): SocketReader {
      val socket = serverSocket.accept()
      return SocketReader(streamId, socket.inputStream, socket.outputStream) { socket.close() }
    }

    override fun stopAccepting() {
      // Android has an issue where LocalServerSocket.close() doesn't actually stop accept(), so the
      // socket stays alive even after we call .close()
      // To work around this, we can directly kill the underlying FileDescriptor.
      serverSocket.close()
      try {
        Os.shutdown(serverSocket.fileDescriptor, OsConstants.SHUT_RDWR)
      } catch (e: Exception) {
        // Blaze complains if you catch ErrnoException directly: b/170716233
        if (e is ErrnoException) {
          if (e.errno != OsConstants.EBADF) throw e // suppress fd already closed
        } else {
          throw e
        }
      }
    }
  }
}
