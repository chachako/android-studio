package com.android.tools.appinspection.network.reporters

import android.os.Build
import androidx.inspection.Connection
import com.android.tools.appinspection.network.reporters.StreamReporter.BufferHelper
import com.android.tools.appinspection.network.reporters.StreamReporter.InputStreamReporter
import com.android.tools.appinspection.network.reporters.StreamReporter.OutputStreamReporter
import com.android.tools.appinspection.network.testing.FakeConnection
import com.android.tools.appinspection.network.testing.getLogLines
import com.android.tools.idea.protobuf.ByteString
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Tests for [StreamReporter] */
@RunWith(RobolectricTestRunner::class)
@Config(
  manifest = Config.NONE,
  minSdk = Build.VERSION_CODES.O,
  maxSdk = Build.VERSION_CODES.UPSIDE_DOWN_CAKE,
)
class StreamReporterTest {
  private val connection = FakeConnection()

  private val threadReporter =
    object : ThreadReporter {
      override fun reportCurrentThread() {}
    }

  @Test
  fun streamReporter_addOneByte() {
    val reporter = streamReporter()

    reporter.addOneByte('a'.code)
    reporter.addOneByte('b'.code)
    reporter.addOneByte('c'.code)
    reporter.onStreamClose()

    assertThat(reporter.data).isEqualTo("abc")
    assertThat(getLogLines()).isEmpty()
  }

  @Test
  fun streamReporter_addBytes() {
    val reporter = streamReporter()

    reporter.addBytes("-abc-".toByteArray(), 1, 3)
    reporter.onStreamClose()

    assertThat(reporter.data).isEqualTo("abc")
    assertThat(getLogLines()).isEmpty()
  }

  @Test
  fun streamReporter_addOneByte_exceedsSize() {
    val reporter = streamReporter(maxBufferSize = 1)

    reporter.addOneByte('a'.code)
    reporter.addOneByte('b'.code)
    reporter.onStreamClose()

    assertThat(reporter.data).isEqualTo("a")
    assertThat(getLogLines())
      .containsExactly("ERROR: Network Inspector: Payload size exceeded max size (2)")
  }

  @Test
  fun streamReporter_addBytes_exceedsSize() {
    val reporter = streamReporter(maxBufferSize = 5)

    reporter.addBytes("abc".toByteArray(), 0, 3)
    reporter.addBytes("efg".toByteArray(), 0, 3)
    reporter.onStreamClose()

    assertThat(reporter.data).isEqualTo("abc")
    assertThat(getLogLines())
      .containsExactly("ERROR: Network Inspector: Payload size exceeded max size (6)")
  }

  @Test
  fun streamReporter_writeThrows() {
    val reporter = streamReporter(bufferHelper = FakeBufferHelper(throwOnWrite = true))

    reporter.addBytes("abc".toByteArray(), 0, 3)
    reporter.onStreamClose()

    assertThat(reporter.data).isEqualTo("Payload omitted because it was too large")
    assertThat(getLogLines()).containsExactly("ERROR: Network Inspector: Payload too large (0)")
  }

  @Test
  fun streamReporter_toByteStringThrows() {
    val reporter = streamReporter(bufferHelper = FakeBufferHelper(throwOnToByteString = true))

    reporter.addBytes("abc".toByteArray(), 0, 3)
    reporter.onStreamClose()

    assertThat(reporter.data).isEqualTo("Payload omitted because it was too large")
    assertThat(getLogLines()).containsExactly("ERROR: Network Inspector: Payload too large (3)")
  }

  @Test
  fun inputStreamReporter_reportsResponse() {
    val reporter = inputStreamReporter(connection)

    reporter.addBytes("abc".toByteArray(), 0, 3)
    reporter.onStreamClose()

    assertThat(connection.httpData.first().responsePayload.payload.toStringUtf8()).isEqualTo("abc")
    assertThat(getLogLines()).isEmpty()
  }

  @Test
  fun outputStreamReporter_reportsRequest() {
    val reporter = outputStreamReporter(connection)

    reporter.addBytes("abc".toByteArray(), 0, 3)
    reporter.onStreamClose()

    assertThat(connection.httpData.first().requestPayload.payload.toStringUtf8()).isEqualTo("abc")
    assertThat(getLogLines()).isEmpty()
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
  ) : StreamReporter(connection, threadReporter, connectionId, maxBufferSize, bufferHelper) {
    var data: String? = null

    override fun onClosed(data: ByteString) {
      this.data = data.toStringUtf8()
    }
  }

  private fun streamReporter(
    connection: Connection = this.connection,
    maxBufferSize: Int = 10 * 1024 * 1024,
    bufferHelper: BufferHelper? = null,
  ) = TestStreamReporter(connection, threadReporter, 1, maxBufferSize, bufferHelper)

  private fun inputStreamReporter(
    connection: Connection,
    maxBufferSize: Int = 10 * 1024 * 1024,
    bufferHelper: BufferHelper? = null,
  ) = InputStreamReporter(connection, 1, threadReporter, maxBufferSize, bufferHelper)

  private fun outputStreamReporter(
    connection: Connection,
    maxBufferSize: Int = 10 * 1024 * 1024,
    bufferHelper: BufferHelper? = null,
  ) = OutputStreamReporter(connection, 1, threadReporter, maxBufferSize, bufferHelper)
}
