package com.android.tools.appinspection.network.reporters

import androidx.inspection.Connection
import com.android.tools.appinspection.network.FakeConnection
import com.android.tools.appinspection.network.reporters.StreamReporter.BufferHelper
import com.android.tools.appinspection.network.reporters.StreamReporter.InputStreamReporter
import com.android.tools.appinspection.network.reporters.StreamReporter.OutputStreamReporter
import com.android.tools.appinspection.network.utils.Logger
import com.android.tools.appinspection.network.utils.TestLogger
import com.android.tools.idea.protobuf.ByteString
import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** Tests for [StreamReporter] */
class StreamReporterTest {
  private val connection = FakeConnection()

  private val threadReporter =
    object : ThreadReporter {
      override fun reportCurrentThread() {}
    }

  private val logger = TestLogger()

  @Test
  fun streamReporter_addOneByte() {
    val reporter = streamReporter(logger)

    reporter.addOneByte('a'.code)
    reporter.addOneByte('b'.code)
    reporter.addOneByte('c'.code)
    reporter.onStreamClose()

    assertThat(reporter.data).isEqualTo("abc")
    assertThat(logger.messages).isEmpty()
  }

  @Test
  fun streamReporter_addBytes() {
    val reporter = streamReporter(logger)

    reporter.addBytes("-abc-".toByteArray(), 1, 3)
    reporter.onStreamClose()

    assertThat(reporter.data).isEqualTo("abc")
    assertThat(logger.messages).isEmpty()
  }

  @Test
  fun streamReporter_addOneByte_exceedsSize() {
    val reporter = streamReporter(logger, maxBufferSize = 1)

    reporter.addOneByte('a'.code)
    reporter.addOneByte('b'.code)
    reporter.onStreamClose()

    assertThat(reporter.data).isEqualTo("a")
    assertThat(logger.messages)
      .containsExactly("ERROR: Network Inspector: Payload size exceeded max size (2)")
  }

  @Test
  fun streamReporter_addBytes_exceedsSize() {
    val reporter = streamReporter(logger, maxBufferSize = 5)

    reporter.addBytes("abc".toByteArray(), 0, 3)
    reporter.addBytes("efg".toByteArray(), 0, 3)
    reporter.onStreamClose()

    assertThat(reporter.data).isEqualTo("abc")
    assertThat(logger.messages)
      .containsExactly("ERROR: Network Inspector: Payload size exceeded max size (6)")
  }

  @Test
  fun streamReporter_writeThrows() {
    val reporter = streamReporter(logger, bufferHelper = FakeBufferHelper(throwOnWrite = true))

    reporter.addBytes("abc".toByteArray(), 0, 3)
    reporter.onStreamClose()

    assertThat(reporter.data).isEqualTo("Payload omitted because it was too large")
    assertThat(logger.messages).containsExactly("ERROR: Network Inspector: Payload too large (0)")
  }

  @Test
  fun streamReporter_toByteStringThrows() {
    val reporter =
      streamReporter(logger, bufferHelper = FakeBufferHelper(throwOnToByteString = true))

    reporter.addBytes("abc".toByteArray(), 0, 3)
    reporter.onStreamClose()

    assertThat(reporter.data).isEqualTo("Payload omitted because it was too large")
    assertThat(logger.messages).containsExactly("ERROR: Network Inspector: Payload too large (3)")
  }

  @Test
  fun inputStreamReporter_reportsResponse() {
    val reporter = inputStreamReporter(connection, logger)

    reporter.addBytes("abc".toByteArray(), 0, 3)
    reporter.onStreamClose()

    assertThat(connection.httpData.first().responsePayload.payload.toStringUtf8()).isEqualTo("abc")
    assertThat(logger.messages).isEmpty()
  }

  @Test
  fun outputStreamReporter_reportsRequest() {
    val reporter = outputStreamReporter(connection, logger)

    reporter.addBytes("abc".toByteArray(), 0, 3)
    reporter.onStreamClose()

    assertThat(connection.httpData.first().requestPayload.payload.toStringUtf8()).isEqualTo("abc")
    assertThat(logger.messages).isEmpty()
  }

  private class FakeBufferHelper(
    private val throwOnWrite: Boolean = false,
    private val throwOnToByteString: Boolean = false,
  ) : BufferHelper {

    override fun write(buffer: ByteString.Output, bytes: ByteArray, offset: Int, len: Int) {
      if (throwOnWrite) {
        throw OutOfMemoryError()
      }
      buffer.write(bytes, offset, len)
    }

    override fun toByteString(buffer: ByteString.Output): ByteString {
      if (throwOnToByteString) {
        throw OutOfMemoryError()
      }
      return buffer.toByteString()
    }
  }

  private class TestStreamReporter(
    connection: Connection,
    threadReporter: ThreadReporter,
    connectionId: Long,
    maxBufferSize: Int?,
    bufferHelper: BufferHelper?,
    logger: Logger,
  ) :
    StreamReporter(connection, threadReporter, connectionId, maxBufferSize, bufferHelper, logger) {
    var data: String? = null

    override fun onClosed(data: ByteString) {
      this.data = data.toStringUtf8()
    }
  }

  private fun streamReporter(
    logger: Logger,
    connection: Connection = this.connection,
    maxBufferSize: Int = 10 * 1024 * 1024,
    bufferHelper: BufferHelper? = null,
  ) = TestStreamReporter(connection, threadReporter, 1, maxBufferSize, bufferHelper, logger)

  private fun inputStreamReporter(
    connection: Connection,
    logger: Logger,
    maxBufferSize: Int = 10 * 1024 * 1024,
    bufferHelper: BufferHelper? = null,
  ) = InputStreamReporter(connection, 1, threadReporter, maxBufferSize, bufferHelper, logger)

  private fun outputStreamReporter(
    connection: Connection,
    logger: Logger = this.logger,
    maxBufferSize: Int = 10 * 1024 * 1024,
    bufferHelper: BufferHelper? = null,
  ) = OutputStreamReporter(connection, 1, threadReporter, maxBufferSize, bufferHelper, logger)
}
