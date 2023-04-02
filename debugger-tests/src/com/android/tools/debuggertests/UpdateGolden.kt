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

import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout

/**
 * Updates golden files.
 *
 * Run with `bazel run //tools/base/debugger-tests:update-golden`
 *
 * When running from Intellij, add to VM options:
 * ```
 * --add-opens=jdk.jdi/com.sun.tools.jdi=ALL-UNNAMED
 * ```
 */
fun main() {
  Resources.findTestClasses().forEach {
    println("Test $it")
    val actual = runBlocking { withTimeout(30.seconds) { Engine.runTest(it) } }
    Resources.writeGolden(it, actual)
  }
}
