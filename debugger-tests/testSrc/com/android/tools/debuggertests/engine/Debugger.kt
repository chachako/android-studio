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
package com.android.tools.debuggertests.engine

import com.sun.jdi.Bootstrap
import com.sun.jdi.VirtualMachine
import com.sun.jdi.event.ClassPrepareEvent
import com.sun.jdi.event.Event
import com.sun.jdi.event.VMStartEvent
import com.sun.jdi.request.EventRequestManager
import kotlinx.coroutines.runBlocking

/** A simple JDI client that can set a breakpoint */
internal class Debugger(mainClass: String, classpath: String) {

  private val vm: VirtualMachine
  private val requestManager: EventRequestManager
  private val eventChannel: EventChannel

  init {
    val launcher = Bootstrap.virtualMachineManager().defaultConnector()
    val arguments = launcher.defaultArguments()
    arguments["main"]?.setValue(mainClass)
    arguments["options"]?.setValue("-classpath $classpath")

    vm = launcher.launch(arguments)
    requestManager = vm.eventRequestManager()

    eventChannel = EventChannel(vm.eventQueue())
    runBlocking { eventChannel.receive<VMStartEvent>() }
  }

  /** Resume execution and return the next event. */
  suspend inline fun <reified T : Event> resume(): T {
    vm.resume()
    return eventChannel.receive()
  }

  /** Set a breakpoint */
  suspend fun setBreakpoint(className: String, line: Int) {
    requestManager.createClassPrepareRequest().apply {
      addClassFilter(className)
      addCountFilter(1)
      enable()
    }
    val event = resume<ClassPrepareEvent>()

    requestManager
      .createBreakpointRequest(event.referenceType().locationsOfLine(line).first())
      .apply { enable() }
  }
}
