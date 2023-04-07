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
internal class DebuggerImpl private constructor(private val vm: VirtualMachine) : Debugger {

  private val requestManager = vm.eventRequestManager()
  private val eventChannel = EventChannel(vm.eventQueue())

  override suspend fun waitForStart(): Debugger {
    eventChannel.receive(VMStartEvent::class.java)
    return this
  }

  override fun suspend() {
    vm.suspend()
  }

  /** Resume execution and return the next event. */
  override suspend fun <T : Event> resume(eventClass: Class<T>): T {
    vm.resume()
    return eventChannel.receive(eventClass)
  }

  /** Set a breakpoint */
  override suspend fun setBreakpoint(className: String, line: Int) {
    requestManager.createClassPrepareRequest().apply {
      addClassFilter(className)
      addCountFilter(1)
      enable()
    }
    val event = resume(ClassPrepareEvent::class.java)

    requestManager
      .createBreakpointRequest(event.referenceType().locationsOfLine(line).first())
      .apply { enable() }
  }

  override fun close() {}

  companion object {

    fun launch(mainClass: String, classpath: String): Debugger {
      val connector = virtualMachineManager().launchingConnectors().named(LAUNCH_CONNECTOR)
      val arguments = connector.defaultArguments()
      arguments["main"]?.setValue("MainKt $mainClass")
      arguments["options"]?.setValue("-classpath $classpath")
      return DebuggerImpl(connector.launch(arguments))
    }

    fun attachToProcess(hostname: String, port: Int): Debugger {
      val connector = virtualMachineManager().attachingConnectors().named(ATTACH_CONNECTOR)
      val arguments = connector.defaultArguments()
      arguments["hostname"]?.setValue(hostname)
      (arguments["port"] as? Connector.IntegerArgument)?.setValue(port)
      return DebuggerImpl(connector.attach(arguments))
    }
  }
}

private inline fun <reified T : Connector> List<T>.named(name: String): T = first {
  it.name() == name
}
