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

package com.android.tools.appinspection.network

import com.android.tools.appinspection.network.httpurl.HttpURLConnectionWrapper
import com.android.tools.appinspection.network.httpurl.HttpsURLConnectionWrapper
import com.android.tools.appinspection.network.rules.InterceptionRule
import com.android.tools.appinspection.network.rules.InterceptionRuleService
import com.android.tools.appinspection.network.rules.NetworkConnection
import com.android.tools.appinspection.network.rules.NetworkInterceptionMetrics
import com.android.tools.appinspection.network.rules.NetworkResponse
import com.android.tools.appinspection.network.trackers.HttpConnectionTracker
import com.google.common.truth.Truth.assertThat
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.cert.Certificate
import javax.net.ssl.HttpsURLConnection
import org.junit.Before
import org.junit.Test
import studio.network.inspection.NetworkInspectorProtocol.HttpConnectionEvent.HttpTransport

class WrappedUrlConnectionTest {

  private lateinit var factory: HttpTrackerFactory

  @Before
  fun setup() {
    factory =
      object : HttpTrackerFactory {
        override fun trackConnection(url: String, callstack: String): HttpConnectionTracker {
          return object : HttpConnectionTracker {
            override fun disconnect() = Unit

            override fun error(message: String) = Unit

            override fun trackRequestBody(stream: OutputStream) = stream

            override fun trackRequest(
              method: String,
              headers: Map<String, List<String>>,
              transport: HttpTransport,
            ) = Unit

            override fun trackResponseHeaders(
              responseCode: Int,
              headers: Map<String?, List<String>>,
            ) = Unit

            override fun trackResponseBody(stream: InputStream) = stream

            override fun trackResponseInterception(interception: NetworkInterceptionMetrics) = Unit
          }
        }
      }
  }

  // Checks the handcrafted Kotlin wrapper around HttpUrlConnection and HttpsUrlConnection
  // returns null without throwing an exception (only for certain APIs).
  @Test
  fun verifyNullableHttpUrlConnectionApi() {
    // Setup
    val interceptionService =
      object : InterceptionRuleService {
        override fun interceptResponse(connection: NetworkConnection, response: NetworkResponse) =
          response

        override fun addRule(ruleId: Int, rule: InterceptionRule) = Unit

        override fun removeRule(ruleId: Int) = Unit

        override fun reorderRules(ruleIdList: List<Int>) = Unit
      }

    // Test WrappedHttpsUrlConnection
    val httpsConnection =
      object : HttpsURLConnection(URL("https://www.google.com")) {
        override fun connect() = Unit

        override fun getLocalPrincipal() = null

        override fun getResponseMessage() = null

        override fun getHeaderFieldDate(name: String?, Default: Long) = 0L

        override fun getRequestMethod() = "GET"

        override fun getHeaderFields() = mutableMapOf<String, MutableList<String>>()

        override fun getInputStream() = ByteArrayInputStream(byteArrayOf())

        override fun getRequestProperty(key: String?) = null

        override fun disconnect() = Unit

        override fun usingProxy() = false

        override fun getCipherSuite() = ""

        override fun getLocalCertificates(): Array<Certificate>? = null

        override fun getServerCertificates(): Array<Certificate> = emptyArray()

        override fun getHeaderFieldKey(n: Int) = null

        override fun getHeaderField(name: String?) = null

        override fun getErrorStream() = null

        override fun getHeaderField(n: Int) = null

        override fun getContentType() = null

        override fun getContentEncoding() = null

        override fun getContent(classes: Array<out Class<*>>?): Any? = null
      }
    val httpsWrapper = HttpsURLConnectionWrapper(httpsConnection, "", factory, interceptionService)
    assertThat(httpsWrapper.localCertificates).isNull()
    assertThat(httpsWrapper.localPrincipal).isNull()
    assertThat(httpsWrapper.getHeaderFieldDate(null, 0)).isEqualTo(0)
    assertNullableApi(httpsConnection)

    // Test WrappedHttpUrlConnection
    val httpConnection =
      object : HttpURLConnection(URL("https://www.google.com")) {
        override fun getHeaderFieldKey(n: Int) = null

        override fun getResponseMessage() = null

        override fun getHeaderFieldDate(name: String?, Default: Long) = 0L

        override fun getRequestMethod() = "GET"

        override fun getHeaderFields() = mutableMapOf<String, MutableList<String>>()

        override fun getInputStream() = ByteArrayInputStream(byteArrayOf())

        override fun getRequestProperty(key: String?) = null

        override fun disconnect() = Unit

        override fun usingProxy() = false

        override fun getHeaderField(name: String?) = null

        override fun getErrorStream() = null

        override fun getHeaderField(n: Int) = null

        override fun connect() = Unit

        override fun getContentType() = null

        override fun getContentEncoding() = null

        override fun getContent(classes: Array<out Class<*>>?) = null
      }
    val httpWrapper = HttpURLConnectionWrapper(httpConnection, "", factory, interceptionService)
    assertNullableApi(httpWrapper)
  }

  @Test
  fun verifyInterceptionService() {
    // Setup
    val newResponse =
      NetworkResponse(
        200,
        mapOf(
          null to listOf("HTTP/1.0 200 OK"),
          "content-type" to listOf("text/html"),
          "myKey" to listOf("myValue1", "myValue2"),
          "myInt" to listOf("12345"),
          "myLong" to listOf("123123123123123"),
          "myData" to listOf("Wed, 21 Oct 2015 07:28:00 GMT"),
        ),
        "myBody".byteInputStream(),
      )
    val interceptionService =
      object : InterceptionRuleService {
        override fun interceptResponse(connection: NetworkConnection, response: NetworkResponse) =
          newResponse

        override fun addRule(ruleId: Int, rule: InterceptionRule) = Unit

        override fun removeRule(ruleId: Int) = Unit

        override fun reorderRules(ruleIdList: List<Int>) = Unit
      }

    // Test WrappedHttpsUrlConnection
    val httpsConnection =
      object : HttpsURLConnection(URL("https://www.google.com")) {
        override fun connect() = Unit

        override fun getLocalPrincipal() = null

        override fun getResponseMessage() = null

        override fun getHeaderFieldDate(name: String?, Default: Long) = 0L

        override fun getRequestMethod() = "GET"

        override fun getHeaderFields() = mutableMapOf<String, MutableList<String>>()

        override fun getInputStream() = ByteArrayInputStream(byteArrayOf())

        override fun getRequestProperty(key: String?) = null

        override fun disconnect() = Unit

        override fun usingProxy() = false

        override fun getCipherSuite() = ""

        override fun getLocalCertificates(): Array<Certificate>? = null

        override fun getServerCertificates(): Array<Certificate> = emptyArray()

        override fun getHeaderFieldKey(n: Int) = null

        override fun getHeaderField(name: String?) = null

        override fun getErrorStream() = null

        override fun getHeaderField(n: Int) = null

        override fun getContentType() = null

        override fun getContentEncoding() = null

        override fun getContent(classes: Array<out Class<*>>?) = null
      }
    val httpsWrapper = HttpsURLConnectionWrapper(httpsConnection, "", factory, interceptionService)
    assertThat(httpsWrapper.headerFields.size).isEqualTo(6)
    assertThat(httpsWrapper.getHeaderField(0)).isEqualTo("HTTP/1.0 200 OK")
    assertThat(httpsWrapper.getHeaderField(1)).isEqualTo("text/html")
    assertThat(httpsWrapper.getHeaderField(2)).isEqualTo("myValue1")
    assertThat(httpsWrapper.getHeaderField(3)).isEqualTo("myValue2")
    assertThat(httpsWrapper.getHeaderField(4)).isEqualTo("12345")
    assertThat(httpsWrapper.getHeaderField(null)).isEqualTo("HTTP/1.0 200 OK")
    assertThat(httpsWrapper.getHeaderField("content-type")).isEqualTo("text/html")
    assertThat(httpsWrapper.getHeaderField("myKey")).isEqualTo("myValue1")
    assertThat(httpsWrapper.getHeaderField("myInt")).isEqualTo("12345")
    assertThat(httpsWrapper.getHeaderFieldInt("myInt", 0)).isEqualTo(12345)
    assertThat(httpsWrapper.getHeaderFieldLong("myLong", 0)).isEqualTo(123123123123123L)
    assertThat(httpsWrapper.getHeaderFieldDate("myData", 0)).isEqualTo(1445412480000L)
    assertThat(httpsWrapper.inputStream.reader().use { it.readText() }).isEqualTo("myBody")
    assertThat(httpsWrapper.responseCode).isEqualTo(200)
    assertThat(httpsWrapper.responseMessage).isEqualTo("OK")
  }

  private fun assertNullableApi(urlConnection: HttpURLConnection) {
    assertThat(urlConnection.responseMessage).isNull()
    assertThat(urlConnection.getRequestProperty(null)).isNull()
    assertThat(urlConnection.getHeaderFieldKey(1)).isNull()
    assertThat(urlConnection.getHeaderField("")).isNull()
    assertThat(urlConnection.getHeaderField(1)).isNull()
    assertThat(urlConnection.errorStream).isNull()
    assertThat(urlConnection.contentType).isNull()
    assertThat(urlConnection.contentEncoding).isNull()
    assertThat(urlConnection.getContent(arrayOf(WrappedUrlConnectionTest::class.java))).isNull()
  }
}
