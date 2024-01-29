package com.android.tools.appinspection.network.httpurl

import com.android.tools.appinspection.network.http.FakeHttpUrlConnection
import com.android.tools.appinspection.network.rules.InterceptionRule
import com.android.tools.appinspection.network.rules.InterceptionRuleService
import com.android.tools.appinspection.network.rules.NetworkConnection
import com.android.tools.appinspection.network.rules.NetworkInterceptionMetrics
import com.android.tools.appinspection.network.rules.NetworkResponse
import com.android.tools.appinspection.network.trackers.HttpConnectionTracker
import com.google.common.truth.Truth.assertThat
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.net.URL
import org.junit.Test
import studio.network.inspection.NetworkInspectorProtocol

/** Tests for [TrackedHttpURLConnection] */
class TrackedHttpURLConnectionTest {
  private val fakeTrackerFactory: (String, String) -> HttpConnectionTracker = { _, _ ->
    FakeHttpConnectionTracker()
  }
  private val fakeRuleService = FakeInterceptionRuleService()
  private val fakeConnection =
    object : FakeHttpUrlConnection(URL("http://fake.com")) {
      override fun getContent() = ""

      override fun getContent(classes: Array<out Class<*>>?) = ""
    }

  @Test
  fun getContentEncoding_startsTracking() {
    val trackedConnection =
      TrackedHttpURLConnection(fakeConnection, "stack", fakeTrackerFactory, fakeRuleService)

    trackedConnection.contentEncoding

    assertThat(trackedConnection.responseTracked).isTrue()
  }

  @Test
  fun getHeaderFieldInt_startsTracking() {
    val trackedConnection =
      TrackedHttpURLConnection(fakeConnection, "stack", fakeTrackerFactory, fakeRuleService)

    trackedConnection.getHeaderField(0)

    assertThat(trackedConnection.responseTracked).isTrue()
  }

  @Test
  fun getHeaderFieldString_startsTracking() {
    val trackedConnection =
      TrackedHttpURLConnection(fakeConnection, "stack", fakeTrackerFactory, fakeRuleService)

    trackedConnection.getHeaderField("")

    assertThat(trackedConnection.responseTracked).isTrue()
  }

  @Test
  fun getHeaderFieldKey_startsTracking() {
    val trackedConnection =
      TrackedHttpURLConnection(fakeConnection, "stack", fakeTrackerFactory, fakeRuleService)

    trackedConnection.getHeaderFieldKey(0)

    assertThat(trackedConnection.responseTracked).isTrue()
  }

  @Test
  fun getContent_startsTracking() {
    val trackedConnection =
      TrackedHttpURLConnection(fakeConnection, "stack", fakeTrackerFactory, fakeRuleService)

    trackedConnection.content

    assertThat(trackedConnection.responseTracked).isTrue()
  }

  @Test
  fun getContentClass_startsTracking() {
    val trackedConnection =
      TrackedHttpURLConnection(fakeConnection, "stack", fakeTrackerFactory, fakeRuleService)

    trackedConnection.getContent(arrayOf(String::class.java))

    assertThat(trackedConnection.responseTracked).isTrue()
  }

  @Test
  fun getContentLength_startsTracking() {
    val trackedConnection =
      TrackedHttpURLConnection(fakeConnection, "stack", fakeTrackerFactory, fakeRuleService)

    trackedConnection.contentLength

    assertThat(trackedConnection.responseTracked).isTrue()
  }

  @Test
  fun getContentLengthLong_startsTracking() {
    val trackedConnection =
      TrackedHttpURLConnection(fakeConnection, "stack", fakeTrackerFactory, fakeRuleService)

    trackedConnection.contentLengthLong

    assertThat(trackedConnection.responseTracked).isTrue()
  }

  @Test
  fun getContentType_startsTracking() {
    val trackedConnection =
      TrackedHttpURLConnection(fakeConnection, "stack", fakeTrackerFactory, fakeRuleService)

    trackedConnection.contentType

    assertThat(trackedConnection.responseTracked).isTrue()
  }

  @Test
  fun getHeaderFields_startsTracking() {
    val trackedConnection =
      TrackedHttpURLConnection(fakeConnection, "stack", fakeTrackerFactory, fakeRuleService)

    trackedConnection.headerFields

    assertThat(trackedConnection.responseTracked).isTrue()
  }

  private class FakeHttpConnectionTracker : HttpConnectionTracker {

    override fun disconnect() {}

    override fun error(message: String) {}

    override fun trackRequestBody(stream: OutputStream) = ByteArrayOutputStream()

    override fun trackRequest(
      method: String,
      headers: Map<String, List<String>>,
      transport: NetworkInspectorProtocol.HttpConnectionEvent.HttpTransport,
    ) {}

    override fun trackResponseHeaders(responseCode: Int, headers: Map<String?, List<String>>) {}

    override fun trackResponseBody(stream: InputStream) = ByteArrayInputStream("".toByteArray())

    override fun trackResponseInterception(interception: NetworkInterceptionMetrics) {}
  }

  private class FakeInterceptionRuleService : InterceptionRuleService {

    override fun interceptResponse(connection: NetworkConnection, response: NetworkResponse) =
      NetworkResponse(200, emptyMap(), "".byteInputStream())

    override fun addRule(ruleId: Int, rule: InterceptionRule) {}

    override fun removeRule(ruleId: Int) {}

    override fun reorderRules(ruleIdList: List<Int>) {}
  }
}
