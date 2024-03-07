/*
 * Copyright (C) 2021 The Android Open Source Project
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

package com.android.tools.appinspection.network.reporters

import androidx.annotation.VisibleForTesting
import androidx.inspection.Connection
import com.android.tools.appinspection.network.utils.Logger
import com.android.tools.appinspection.network.utils.sendHttpConnectionEvent
import com.android.tools.idea.protobuf.ByteString
import com.android.tools.idea.protobuf.ByteString.Output
import java.nio.charset.Charset
import studio.network.inspection.NetworkInspectorProtocol

/**
 * The initial capacity of the buffer that stores payload data. It is automatically expanded when
 * capacity is reached.
 */
private const val INITIAL_BUFFER_SIZE = 1024
private const val MAX_BUFFER_SIZE = 10 * 1024 * 1024

/**
 * A class that reports on [java.io.InputStream] and [java.io.OutputStream]. It records the payload
 * that is sent/received in a temporary buffer before reporting it to Studio.
 */
internal abstract class StreamReporter
@VisibleForTesting
constructor(
  private val connection: Connection,
  threadReporter: ThreadReporter,
  private val connectionId: Long,
  maxBufferSize: Int?,
  bufferHelper: BufferHelper?,
) : ThreadReporter by threadReporter {

  private val maxBufferSize = maxBufferSize ?: MAX_BUFFER_SIZE
  private val bufferHelper = bufferHelper ?: BufferHelperImpl()

  private val buffer = ByteString.newOutput(INITIAL_BUFFER_SIZE)
  private var isClosed = false

  protected abstract fun onClosed(data: ByteString)

  fun addOneByte(byte: Int) {
    addBytes(ByteArray(1) { byte.toByte() }, 0, 1)
  }

  fun addBytes(bytes: ByteArray, offset: Int, len: Int) {
    if (buffer.size() + len > maxBufferSize) {
      Logger.error("Payload size exceeded max size (${buffer.size() + len})")
      return
    }
    try {
      bufferHelper.write(buffer, bytes, offset, len)
    } catch (e: OutOfMemoryError) {
      Logger.error("Payload too large (${buffer.size()})", e)
      buffer.reset()
      buffer.write("Payload omitted because it was too large".toByteArray())
    }
  }

  fun onStreamClose() {
    // This is to prevent the double reporting of stream closed events
    // because this is reachable by both calling disconnect() on the
    // HttpUrlConnection, and calling close() on the stream.
    if (!isClosed) {
      isClosed = true
      val data =
        try {
          bufferHelper.toByteString(buffer)
        } catch (e: OutOfMemoryError) {
          Logger.error("Payload too large (${buffer.size()})", e)
          ByteString.copyFrom("Payload omitted because it was too large", Charset.defaultCharset())
        }
      onClosed(data)
    }
  }

  protected fun sendHttpConnectionEvent(
    builder: NetworkInspectorProtocol.HttpConnectionEvent.Builder
  ) {
    connection.sendHttpConnectionEvent(builder.setConnectionId(connectionId))
  }

  interface BufferHelper {
    fun write(buffer: Output, bytes: ByteArray, offset: Int, len: Int)

    fun toByteString(buffer: Output): ByteString
  }

  internal class InputStreamReporter(
    connection: Connection,
    connectionId: Long,
    threadReporter: ThreadReporter,
    maxBufferSize: Int? = null,
    bufferHelper: BufferHelper? = null,
  ) : StreamReporter(connection, threadReporter, connectionId, maxBufferSize, bufferHelper) {

    override fun onClosed(data: ByteString) {
      sendHttpConnectionEvent(
        NetworkInspectorProtocol.HttpConnectionEvent.newBuilder()
          .setResponsePayload(
            NetworkInspectorProtocol.HttpConnectionEvent.Payload.newBuilder().setPayload(data)
          )
      )
      sendHttpConnectionEvent(
        NetworkInspectorProtocol.HttpConnectionEvent.newBuilder()
          .setHttpResponseCompleted(
            NetworkInspectorProtocol.HttpConnectionEvent.ResponseCompleted.getDefaultInstance()
          )
      )
      sendHttpConnectionEvent(
        NetworkInspectorProtocol.HttpConnectionEvent.newBuilder()
          .setHttpClosed(
            NetworkInspectorProtocol.HttpConnectionEvent.Closed.newBuilder().setCompleted(true)
          )
      )
    }
  }

  internal class OutputStreamReporter(
    connection: Connection,
    connectionId: Long,
    threadReporter: ThreadReporter,
    maxBufferSize: Int? = null,
    bufferHelper: BufferHelper? = null,
  ) : StreamReporter(connection, threadReporter, connectionId, maxBufferSize, bufferHelper) {

    override fun onClosed(data: ByteString) {
      sendHttpConnectionEvent(
        NetworkInspectorProtocol.HttpConnectionEvent.newBuilder()
          .setRequestPayload(
            NetworkInspectorProtocol.HttpConnectionEvent.Payload.newBuilder().setPayload(data)
          )
      )
      sendHttpConnectionEvent(
        NetworkInspectorProtocol.HttpConnectionEvent.newBuilder()
          .setHttpRequestCompleted(
            NetworkInspectorProtocol.HttpConnectionEvent.RequestCompleted.getDefaultInstance()
          )
      )
    }
  }

  private class BufferHelperImpl : BufferHelper {

    override fun write(buffer: Output, bytes: ByteArray, offset: Int, len: Int) =
      buffer.write(bytes, offset, len)

    override fun toByteString(buffer: Output): ByteString = buffer.toByteString()
  }
}
