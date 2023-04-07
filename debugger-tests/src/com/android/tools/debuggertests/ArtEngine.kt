/*
 * Copyright (C) 2023 The Android Open Source Project
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
package com.android.tools.debuggertests

import kotlin.io.path.pathString

private val ART_ROOT = Resources.ROOT.resolve("prebuilts/tools/linux-x86_64/art")
private val ART = ART_ROOT.resolve("bin/art").pathString
private val FRAMEWORK = ART_ROOT.resolve("framework")
private val LIB64 = ART_ROOT.resolve("lib64")
private val JVMTI = LIB64.resolve("libopenjdkjvmti.so").pathString
private val JDWP = LIB64.resolve("libjdwp.so").pathString

private val X_BOOT_CLASSPATH =
  listOf(
      FRAMEWORK.resolve("core-libart-hostdex.jar").pathString,
      FRAMEWORK.resolve("core-oj-hostdex.jar").pathString,
      FRAMEWORK.resolve("core-icu4j-hostdex.jar").pathString,
    )
    .joinToString(":") { it }

/**
 * An [Engine] that launches and attaches to an ART process.
 *
 * TODO: While the engine runs to completion, there are a few errors and warnings emitted.
 */
internal class ArtEngine : AttachingEngine("dex") {

  /**
   * The command line is based on
   * https://cs.android.com/android-studio/platform/tools/base/+/mirror-goog-studio-main:fakeandroid/srcs/com/android/tools/fakeandroid/FakeAndroidDriver.java
   */
  override fun buildCommandLine(mainClass: String) =
    listOf(
      ART,
      "--64",
      "-Xbootclasspath:$X_BOOT_CLASSPATH",
      "-Xplugin:$JVMTI",
      "-agentpath:$JDWP=transport=dt_socket,server=y,suspend=y",
      "-classpath",
      Resources.TEST_CLASSES_DEX,
      "MainKt",
      mainClass,
    )
}
