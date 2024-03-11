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

import androidx.inspection.InspectorEnvironment
import com.android.tools.appinspection.common.threadLocal
import com.android.tools.appinspection.database.EntryExitMatchingHookRegistry.OnExitCallback
import java.util.ArrayDeque
import java.util.Deque

/**
 * The class allows for observing method's EntryHook parameters in ExitHook.
 *
 * It works by registering both (entry and exit) hooks and keeping its own method frame stack. On
 * exit, it calls [OnExitCallback] provided by the user.
 *
 * TODO: handle cases when frames could be dropped (e.g. because of an Exception) causing internal
 *   state to be corrupted.
 *
 * Thread safe by using a [ThreadLocal].
 */
internal class EntryExitMatchingHookRegistry(private val environment: InspectorEnvironment) {
  private val frameStack: Deque<Frame> by threadLocal { ArrayDeque() }

  fun registerHook(originClass: Class<*>, originMethod: String, onExitCallback: OnExitCallback) {
    val artTooling = environment.artTooling()

    artTooling.registerEntryHook(originClass, originMethod) { thisObject, args ->
      frameStack.addLast(Frame(originMethod, thisObject, args))
    }

    artTooling.registerExitHook<Any>(originClass, originMethod) { result ->
      val entryFrame: Frame = frameStack.pollLast()
      // TODO: make more specific and handle
      check(originMethod == entryFrame.method)
      onExitCallback.onExit(entryFrame.copy(result = result))
      result
    }
  }

  internal data class Frame(
    val method: String,
    val thisObject: Any?,
    val args: List<Any?>,
    val result: Any? = null,
  )

  internal fun interface OnExitCallback {
    fun onExit(exitFrame: Frame)
  }
}
