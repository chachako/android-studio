/*
 * Copyright (C) 2024 The Android Open Source Project
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

package com.android.tools.fakeandroid

import java.io.BufferedReader
import java.io.File
import java.io.IOException
import java.io.InputStreamReader
import java.net.URL
import kotlin.test.fail
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue

private val BOOT_CLASSPATH_JARS =
  listOf(
    "bouncycastle-hostdex.jar",
    "conscrypt-hostdex.jar",
    "core-icu4j-hostdex.jar",
    "core-libart-hostdex.jar",
    "core-oj-hostdex.jar",
    "okhttp-hostdex.jar",
  )

class FakeAndroidDriver(
  private val address: String,
  debuggerPort: Int,
  extraArtFlag: String?,
  env: Array<String>,
) : ProcessRunner(getArgs(debuggerPort, extraArtFlag), env) {
  var communicationPort: Int = 0
    private set

  constructor(address: String, env: Array<String>) : this(address, -1, null, env)

  override fun start() {
    super.start()
    val regex = Regex("(.*)($APP_LISTENING)(?<result>.*)")
    communicationPort = waitForInput(regex, LONG_TIMEOUT_MS)?.toInt() ?: fail("Port not found")
  }

  fun loadDex(dexPath: String) {
    sendRequest("load-dex", dexPath)
  }

  fun launchActivity(activityClass: String) {
    sendRequest("launch-activity", activityClass)

    // Block until we verify the activity has been created.
    assertTrue(waitForInput("ActivityThread Created"))
  }

  fun triggerMethod(activityClass: String?, methodName: String?) {
    sendRequest("trigger-method", "$activityClass,$methodName")
  }

  @Suppress("unused")
  @JvmOverloads
  fun attachAgent(loc: String, shouldSucceed: Boolean = true) {
    sendRequest("attach-agent", loc)
    if (shouldSucceed) {
      waitForInput("attach-agent $loc")
    } else {
      waitForError("Failed to attach agent", SHORT_TIMEOUT_MS)
    }
  }

  @Suppress("unused")
  fun setProperty(propertyKey: String?, propertyValue: String?) {
    sendRequest("set-property", "$propertyKey,$propertyValue")
    waitForInput("$propertyKey=$propertyValue")
  }

  private fun sendRequest(request: String, value: String) {
    try {
      val url = URL("http://$address:$communicationPort?$request=$value&")
      val conn = url.openConnection()
      val reader = BufferedReader(InputStreamReader(conn.getInputStream()))
      assertEquals("SUCCESS", reader.readLine())
      reader.close()
    } catch (ex: IOException) {
      fail("Failed to send request ($request): $ex")
    }
  }

  companion object {
    private const val APP_LISTENING = "Test Framework Server Listening: "
    private val ART_PATH = getProcessPath("art.location")

    /**
     * Given a property which evaluates to zero or more relative paths to files (separated by ':'),
     * return the absolute paths in a list.
     */
    @Suppress("SameParameterValue")
    private fun resolvePropertyPaths(propertyKey: String): List<String> {
      val relativePaths = System.getProperty(propertyKey) ?: return emptyList()
      return relativePaths.split(":").map { File(getProcessPathRoot() + it).parent }
    }

    private fun getArgs(debuggerPort: Int, extraArtFlag: String?): Array<String> {
      val args = buildList {
        add("bash")
        add(ART_PATH)
        add("--64")
        add("--verbose")

        val artLib64 = getProcessPath("art.lib64.location")
        val artDeps = getProcessPath("art.deps.location")
        val libs = resolvePropertyPaths("app.libs") + artLib64
        add("-Djava.library.path=${libs.joinToString(":") { it }}")

        add("-cp")
        add(getProcessPath("perfa.dex.location"))

        val mockAndroid = getProcessPath("android-mock.dex.location")
        val bootClassPath = BOOT_CLASSPATH_JARS.map { "$artDeps$it" } + mockAndroid
        add("-Xbootclasspath:${bootClassPath.joinToString(":") { it }}")

        add("-Xcompiler-option")
        add("--debuggable")
        add("-Ximage:${getProcessPath("art.boot.location")}core-core-libart-hostdex.art")

        if (debuggerPort > 0) {
          add("-Xplugin:${"$artLib64/libopenjdkjvmti.so"}")
          val jdwpArgs = "transport=dt_socket,server=y,suspend=n,address=$debuggerPort"
          add("-agentpath:$artLib64/libjdwp.so=$jdwpArgs")
        }

        if (extraArtFlag != null) {
          add(extraArtFlag)
        }

        add("com.android.tools.applauncher.FakeAndroid")
      }

      return args.toTypedArray<String>()
    }
  }
}
