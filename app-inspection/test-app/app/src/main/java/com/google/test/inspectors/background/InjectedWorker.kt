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

package com.google.test.inspectors.background

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.Data
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.google.test.inspectors.Logger
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

@HiltWorker
internal class InjectedWorker
@AssistedInject
constructor(@Assisted context: Context, @Assisted private val params: WorkerParameters) :
  Worker(context, params) {

  override fun doWork(): Result {
    Logger.info("doWork1")
    return Result.success(
      Data.Builder().putString(MESSAGE_KEY, "Hello from InjectedWorker ${params.id}").build()
    )
  }

  companion object {

    const val MESSAGE_KEY = "message"
  }
}
