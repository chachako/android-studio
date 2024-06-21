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

package com.example.rubidumconsumer.test

import android.graphics.Color
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.rubidumconsumer.MainActivity
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MainActivityTest {

    @get:Rule var rule = ActivityScenarioRule(MainActivity::class.java)

    @Test
    fun viewIsUpdatedBySdk() {
        val numRetries = 30
        val sleepDuration = 1000L
        repeat(numRetries) {
            val bitmap = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
            if (bitmap == null) {
                return@repeat // Continue looping in case the screenshot fails
            }
            if (bitmap.getPixel(bitmap.width / 2, bitmap.height / 2) == Color.RED) {
                return@viewIsUpdatedBySdk
            }
            Thread.sleep(sleepDuration)
        }
        throw Exception("View not updated after $numRetries attempts.")
    }
}
