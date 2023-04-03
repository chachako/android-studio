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

import com.sun.jdi.Bootstrap.virtualMachineManager
import com.sun.jdi.VirtualMachine
import com.sun.jdi.connect.Connector
import com.sun.jdi.event.ClassPrepareEvent
import com.sun.jdi.event.Event
import com.sun.jdi.event.VMStartEvent

private const val LAUNCH_CONNECTOR = "com.sun.jdi.CommandLineLaunch"
private const val ATTACH_CONNECTOR = "com.sun.jdi.SocketAttach"

/** A simple JDI client that can set a breakpoint */
internal class Debugger private constructor(private val vm: VirtualMachine) {

  private val requestManager = vm.eventRequestManager()
  private val eventChannel = EventChannel(vm.eventQueue())

  suspend fun start() {
    eventChannel.receive<VMStartEvent>()
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

  companion object {

    fun launch(mainClass: String, classpath: String): Debugger {
      val connector = virtualMachineManager().launchingConnectors().named(LAUNCH_CONNECTOR)
      val arguments = connector.defaultArguments()
      arguments["main"]?.setValue(mainClass)
      arguments["options"]?.setValue("-classpath $classpath")
      return Debugger(connector.launch(arguments))
    }

    fun attachToProcess(hostname: String, port: Int): Debugger {
      val connector = virtualMachineManager().attachingConnectors().named(ATTACH_CONNECTOR)
      val arguments = connector.defaultArguments()
      arguments["hostname"]?.setValue(hostname)
      (arguments["port"] as? Connector.IntegerArgument)?.setValue(port)
      return Debugger(connector.attach(arguments))
    }
  }
}

private inline fun <reified T : Connector> List<T>.named(name: String): T = first {
  it.name() == name
}
