/*
 * Copyright 2020 The Android Open Source Project
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

package com.android.tools.appinspection.database

import android.os.Build
import androidx.inspection.InspectorEnvironment
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.SQLiteMode

/**
 * This test just checks that we have reasonable defaults (e.g. no crash) if Room is not available
 * in the classpath.
 */
@RunWith(RobolectricTestRunner::class)
@Config(
  manifest = Config.NONE,
  minSdk = Build.VERSION_CODES.O,
  maxSdk = Build.VERSION_CODES.UPSIDE_DOWN_CAKE,
)
@SQLiteMode(SQLiteMode.Mode.NATIVE)
class RoomInvalidationRegistryWithoutRoomTest {
  @Test
  fun noOpTest() {
    // this does not really assert anything, we just want to make sure it does not crash and
    // never makes a call to the environment if Room is not available.
    val env = InspectorEnvironment { throw AssertionError("should never call environment") }
    val tracker = RoomInvalidationRegistry(env)
    tracker.triggerInvalidations()
    tracker.invalidateCache()
    tracker.triggerInvalidations()
  }
}
