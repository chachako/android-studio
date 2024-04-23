/*
 * Copyright (C) 2015 The Android Open Source Project
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
package com.android.tools.lint.checks

import com.android.SdkConstants
import com.android.SdkConstants.ANDROID_URI
import com.android.SdkConstants.ATTR_HOST
import com.android.SdkConstants.ATTR_PACKAGE
import com.android.SdkConstants.ATTR_SCHEME
import com.android.SdkConstants.TAG_ACTIVITY
import com.android.SdkConstants.TAG_ACTIVITY_ALIAS
import com.android.SdkConstants.TAG_APPLICATION
import com.android.SdkConstants.TAG_INTENT_FILTER
import com.android.SdkConstants.VALUE_TRUE
import com.android.tools.lint.client.api.LintClient
import com.android.tools.lint.client.api.LintClient.Companion.isUnitTest
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Context
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Incident
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.Issue.Companion.create
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlScanner
import com.android.utils.iterator
import com.android.utils.subtag
import com.android.xml.AndroidManifest.ATTRIBUTE_NAME
import com.android.xml.AndroidManifest.NODE_ACTION
import com.android.xml.AndroidManifest.NODE_CATEGORY
import com.android.xml.AndroidManifest.NODE_DATA
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.google.gson.JsonSyntaxException
import java.io.FileNotFoundException
import java.io.IOException
import java.net.HttpURLConnection
import java.net.MalformedURLException
import java.net.SocketTimeoutException
import java.net.URL
import java.net.UnknownHostException
import java.util.concurrent.Executors
import java.util.concurrent.Future
import kotlin.text.Charsets.UTF_8
import org.w3c.dom.Attr
import org.w3c.dom.Element
import org.w3c.dom.Node

/**
 * Reports Android App Links for which the remote (HTTPS) Digital Asset Links JSON file is broken in
 * some way. Note that this Detector performs network (HTTPS) requests.
 */
class AppLinksAutoVerifyDetector : Detector(), XmlScanner {

  override fun checkMergedProject(context: Context) {
    if (context.mainProject.isLibrary) return

    // Only run in batch mode. That is, only run from the command line or via
    // "Inspect Code..." in the IDE.
    if (!context.scope.contains(Scope.ALL_JAVA_FILES)) {
      return
    }
    // TODO: Enable running on-the-fly in the IDE, and add caching.
    // TODO: When running on-the-fly, we will still only run on app modules.
    //  But if the warnings are from other manifests, we can't show them,
    //  so we should perhaps show the warning on the application tag, instead?

    val manifest = context.mainProject.mergedManifest?.documentElement ?: return
    val application = manifest.subtag(TAG_APPLICATION) ?: return

    // We need the application id to be able to check if it is correctly
    // included in the Digital Asset Links JSON file.
    val applicationId = manifest.getAttribute(ATTR_PACKAGE)
    if (applicationId.isBlank()) return

    val activities =
      application.iterator().asSequence().filter {
        it.tagName in arrayOf(TAG_ACTIVITY, TAG_ACTIVITY_ALIAS)
      }

    val intentFilters =
      activities.flatMap { activity ->
        activity.iterator().asSequence().filter { it.tagName == TAG_INTENT_FILTER }
      }

    // At the time of writing, the documentation at:
    // https://developer.android.com/training/app-links/verify-android-applinks#add-intent-filters
    // suggests that the tags inside the intent-filter need to follow a very specific format:
    //
    // <!-- Make sure you explicitly set android:autoVerify to "true". -->
    // <intent-filter android:autoVerify="true">
    //     <action android:name="android.intent.action.VIEW" />
    //     <category android:name="android.intent.category.DEFAULT" />
    //     <category android:name="android.intent.category.BROWSABLE" />
    //
    //     <!-- If a user clicks on a shared link that uses the "http" scheme, your
    //          app should be able to delegate that traffic to "https". -->
    //     <data android:scheme="http" />
    //     <data android:scheme="https" />
    //
    //     <!-- Include one or more domains that should be verified. -->
    //     <data android:host="..." />
    // </intent-filter>
    //
    // We should be somewhat strict about the format before doing network requests, and
    // there should be other checks/warnings (without network requests) when the format is not
    // followed.

    val intentFiltersWithRequiredSubTags =
      intentFilters.filter { intentFilter ->
        intentFilter.getAttributeNS(ANDROID_URI, ATTRIBUTE_AUTO_VERIFY) == VALUE_TRUE &&
          intentFilter.hasSubTagWithNameAttr(NODE_ACTION, "android.intent.action.VIEW") &&
          intentFilter.hasSubTagWithNameAttr(NODE_CATEGORY, "android.intent.category.BROWSABLE") &&
          intentFilter.hasSubTagWithNameAttr(NODE_CATEGORY, "android.intent.category.DEFAULT") &&
          intentFilter.hasHttpOrHttpsSchemeSubTag()
      }

    val dataTags =
      intentFiltersWithRequiredSubTags.flatMap { intentFilter ->
        intentFilter.iterator().asSequence().filter { it.tagName == NODE_DATA }
      }

    val hostAttrs = dataTags.mapNotNull { it.getAttributeNodeNS(ANDROID_URI, ATTR_HOST) }

    fun Attr.toHostRequest(): HostRequest? {
      // `this` is a host attribute like android:host="*.example.com"
      var host = this.value ?: return null
      // Remove wildcard, if present.
      host = host.removePrefix("*.")
      if (host.isBlank()) return null
      // We should not see placeholders because we have the merged manifest,
      // but we ignore hosts with them, just in case.
      if (host.contains(SdkConstants.MANIFEST_PLACEHOLDER_PREFIX)) return null

      // TODO: We should handle resource references. For example:
      //  <data android:host="@string/foo_domain" />

      // The JSON file must be available via HTTPS.
      return HostRequest(hostAttr = this, url = "https://${host}${JSON_RELATIVE_PATH}")
    }

    val hostRequests = hostAttrs.mapNotNull { it.toHostRequest() }.distinctBy { it.url }

    val results = hostRequests.getJsonFilesAsync(context.client)

    for (result in results) {
      when (result) {
        // TODO: We could ignore network failures, like unknown host, which are most likely
        //  transient failures?
        is HostResult.HttpResponse -> {
          val packageNames = getPackageNameFromJson(result.json)
          if (!packageNames.contains(applicationId)) {
            reportError(
              context,
              result.request.hostAttr,
              "This host does not support app links to your app. Checks the Digital Asset Links JSON file: ${result.request.url}",
            )
          }
        }
        is HostResult.HttpConnectFail,
        is HostResult.OtherException -> {
          reportWarning(
            context,
            result.request.hostAttr,
            "Connection to Digital Asset Links JSON file ${result.request.url} fails",
          )
        }
        is HostResult.MalformedUrl -> {
          reportError(
            context,
            result.request.hostAttr,
            "Malformed URL of Digital Asset Links JSON file: ${result.request.url}. An unknown protocol is specified",
          )
        }
        is HostResult.UnknownHost -> {
          reportWarning(
            context,
            result.request.hostAttr,
            "Unknown host: ${result.request.url.removeSuffix(JSON_RELATIVE_PATH)}. Check if the host exists, and check your network connection",
          )
        }
        is HostResult.NotFound -> {
          reportError(
            context,
            result.request.hostAttr,
            "Digital Asset Links JSON file ${result.request.url} is not found on the host",
          )
        }
        is HostResult.JsonSyntaxEx -> {
          reportError(
            context,
            result.request.hostAttr,
            "${result.request.url} has incorrect JSON syntax",
          )
        }
        is HostResult.JsonParseFail -> {
          reportError(
            context,
            result.request.hostAttr,
            "Parsing JSON file ${result.request.url} fails",
          )
        }
        is HostResult.HttpResponseBad -> {
          reportWarning(
            context,
            result.request.hostAttr,
            "HTTP request for Digital Asset Links JSON file ${result.request.url} fails. HTTP response code: ${result.httpStatus}",
          )
        }
        is HostResult.Timeout -> {
          // Ignore for now.
        }
        is HostResult.HttpResponseBadContentType -> {
          reportWarning(
            context,
            result.request.hostAttr,
            "HTTP response for Digital Asset Links JSON file ${result.request.url} should have Content-Type application/json, but has ${result.contentType}",
          )
        }
      }
    }
  }

  companion object {
    private val IMPLEMENTATION =
      Implementation(AppLinksAutoVerifyDetector::class.java, Scope.MANIFEST_SCOPE)

    @JvmField
    val ISSUE: Issue =
      create(
          "AppLinksAutoVerify",
          "App Links Auto Verification Failure",
          "Ensures that app links are correctly set and associated with website.",
          Category.CORRECTNESS,
          5,
          Severity.ERROR,
          IMPLEMENTATION,
        )
        .addMoreInfo("https://g.co/appindexing/applinks")
        .setAliases(mutableListOf("AppLinksAutoVerifyError", "AppLinksAutoVerifyWarning"))
        .setEnabledByDefault(false)

    private const val ATTRIBUTE_AUTO_VERIFY = "autoVerify"
    private const val JSON_RELATIVE_PATH = "/.well-known/assetlinks.json"

    /** Gets all the Digital Asset Links JSON files asynchronously. */
    private fun Sequence<HostRequest>.getJsonFilesAsync(client: LintClient): List<HostResult> {
      // TODO: this code was converted from Java; could perhaps be improved.
      //  Should we be using Executors?
      val executorService = Executors.newCachedThreadPool()
      val futures = ArrayList<Future<HostResult>>()
      for (hostRequest in this) {
        futures.add(executorService.submit<HostResult> { getJson(client, hostRequest) })
      }
      executorService.shutdown()

      val result = ArrayList<HostResult>()
      for (future in futures) {
        try {
          result.add(future.get())
        } catch (e: Exception) {
          if (isUnitTest) {
            val cause = e.cause
            if (cause is Error) {
              throw cause
            } else {
              throw IllegalArgumentException("Network failure", cause)
            }
          }
          // Fallthrough.
        }
      }
      return result
    }

    private class HostRequest(val hostAttr: Attr, val url: String)

    private sealed interface HostResult {
      val request: HostRequest

      class HttpResponse(override val request: HostRequest, val json: JsonElement) : HostResult

      class HttpResponseBad(
        override val request: HostRequest,
        val httpStatus: Int,
        val response: String? = null,
      ) : HostResult

      class HttpResponseBadContentType(
        override val request: HostRequest,
        val httpStatus: Int,
        val contentType: String?,
        val response: String? = null,
      ) : HostResult

      class HttpConnectFail(override val request: HostRequest) : HostResult

      class Timeout(override val request: HostRequest, val exception: SocketTimeoutException) :
        HostResult

      class MalformedUrl(override val request: HostRequest, val exception: MalformedURLException) :
        HostResult

      class UnknownHost(override val request: HostRequest, exception: UnknownHostException) :
        HostResult

      class NotFound(override val request: HostRequest, exception: FileNotFoundException) :
        HostResult

      class OtherException(override val request: HostRequest, exception: IOException) : HostResult

      class JsonSyntaxEx(
        override val request: HostRequest,
        val exception: JsonSyntaxException,
        val response: String,
      ) : HostResult

      class JsonParseFail(
        override val request: HostRequest,
        val exception: RuntimeException,
        val response: String,
      ) : HostResult
    }

    private fun reportWarning(context: Context, node: Node, message: String) {
      val incident = Incident(ISSUE, node, context.getLocation(node), message)
      incident.overrideSeverity(Severity.WARNING)
      context.report(incident)
    }

    private fun reportError(context: Context, node: Node, message: String) {
      val incident = Incident(ISSUE, node, context.getLocation(node), message)
      context.report(incident)
    }

    private fun Element.hasSubTagWithNameAttr(tagName: String, nameAttrValue: String) =
      this.iterator().asSequence().any {
        it.tagName == tagName && it.getAttributeNS(ANDROID_URI, ATTRIBUTE_NAME) == nameAttrValue
      }

    private fun Element.hasHttpOrHttpsSchemeSubTag() =
      this.iterator().asSequence().any {
        it.tagName == NODE_DATA &&
          it.getAttributeNS(ANDROID_URI, ATTR_SCHEME) in listOf("http", "https")
      }

    /** Gets the Digital Asset Links JSON file from the host. */
    private fun getJson(client: LintClient, hostRequest: HostRequest): HostResult {
      try {
        val httpConnection =
          client.openConnection(URL(hostRequest.url), 3000) as? HttpURLConnection
            ?: return HostResult.HttpConnectFail(hostRequest)
        try {
          // There must not be any redirects.
          httpConnection.instanceFollowRedirects = false
          val status = httpConnection.responseCode

          val inputStream =
            httpConnection.inputStream ?: return HostResult.HttpResponseBad(hostRequest, status)
          val response = inputStream.use { String(inputStream.readAllBytes(), UTF_8) }

          // Check the status.
          if (status != HttpURLConnection.HTTP_OK) {
            return HostResult.HttpResponseBad(hostRequest, status, response)
          }

          // Check the Content-Type.
          val contentType = httpConnection.getHeaderField("Content-Type")
          if (contentType != "application/json") {
            return HostResult.HttpResponseBadContentType(hostRequest, status, contentType, response)
          }

          // Try to parse JSON.
          try {
            val json = JsonParser.parseString(response)
            return HostResult.HttpResponse(hostRequest, json)
          } catch (e: JsonSyntaxException) {
            return HostResult.JsonSyntaxEx(hostRequest, e, response)
          } catch (e: RuntimeException) {
            return HostResult.JsonParseFail(hostRequest, e, response)
          }
        } finally {
          httpConnection.disconnect()
        }
      } catch (e: SocketTimeoutException) {
        return HostResult.Timeout(hostRequest, e)
      } catch (e: MalformedURLException) {
        return HostResult.MalformedUrl(hostRequest, e)
      } catch (e: UnknownHostException) {
        return HostResult.UnknownHost(hostRequest, e)
      } catch (e: FileNotFoundException) {
        return HostResult.NotFound(hostRequest, e)
      } catch (e: IOException) {
        return HostResult.OtherException(hostRequest, e)
      }
    }

    /**
     * Gets the package names of all the apps from the Digital Asset Links JSON file.
     *
     * @param element The JsonElement of the json file.
     * @return All the package names.
     */
    private fun getPackageNameFromJson(element: JsonElement): List<String> {
      val packageNames = mutableListOf<String>()
      if (element is JsonArray) {
        for (i in 0 until element.size()) {
          val app = element[i]
          if (app is JsonObject) {
            val target = app.getAsJsonObject("target")
            if (target != null) {
              // Checks namespace to ensure it is an app statement.
              val namespace = target["namespace"]
              val packageName = target["package_name"]
              if (namespace != null && namespace.asString == "android_app" && packageName != null) {
                packageNames.add(packageName.asString)
              }
            }
          }
        }
      }
      return packageNames
    }
  }
}
