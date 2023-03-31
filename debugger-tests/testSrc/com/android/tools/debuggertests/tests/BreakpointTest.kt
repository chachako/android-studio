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
package com.android.tools.debuggertests.tests

import com.android.tools.debuggertests.engine.runBreakpointTest
import kotlinx.coroutines.test.runTest
import org.junit.Test

/** Runs breakpoint tests from `testResources` */
@Suppress("OPT_IN_USAGE")
class BreakpointTest {

  @Test fun inline() = runTest(dispatchTimeoutMs = 2_000) { runBreakpointTest("tests.Inline", 3) }
}
