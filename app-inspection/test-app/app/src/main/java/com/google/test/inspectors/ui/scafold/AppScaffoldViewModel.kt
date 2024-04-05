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

package com.google.test.inspectors.ui.scafold

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

private const val StopTimeoutMillis: Long = 5000

abstract class AppScaffoldViewModel : ViewModel() {
  private val snackFlow: MutableStateFlow<String?> = MutableStateFlow(null)
  val snackState: StateFlow<String?> = snackFlow.stateIn(viewModelScope, WhileUiSubscribed, null)

  fun setSnack(text: String) {
    snackFlow.value =
      when (text) {
        snackState.value -> "$text (repeated)"
        else -> text
      }
  }

  companion object {
    /**
     * A [SharingStarted] meant to be used with a [kotlinx.coroutines.flow.StateFlow] to expose data
     * to the UI.
     *
     * When the UI stops observing, upstream flows stay active for some time to allow the system to
     * come back from a short-lived configuration change (such as rotations). If the UI stops
     * observing for longer, the cache is kept but the upstream flows are stopped. When the UI comes
     * back, the latest value is replayed and the upstream flows are executed again. This is done to
     * save resources when the app is in the background but let users switch between apps quickly.
     */
    val WhileUiSubscribed: SharingStarted = SharingStarted.WhileSubscribed(StopTimeoutMillis)
  }
}
