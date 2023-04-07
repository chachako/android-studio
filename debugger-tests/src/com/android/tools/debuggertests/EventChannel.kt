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

import com.sun.jdi.event.Event
import com.sun.jdi.event.EventQueue
import com.sun.jdi.event.VMDeathEvent
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.runBlocking

/** Converts an [EventQueue] into a [Flow<Event>] */
internal class EventChannel(queue: EventQueue) {

  private val channel = Channel<Event>(10)
  private var thread = EventQueueThread(queue, channel)

  init {
    thread.start()
  }

  suspend fun <T : Event> receive(eventClass: Class<T>): T {
    val event: Event = channel.receive()
    @Suppress("UNCHECKED_CAST")
    when {
      eventClass.isAssignableFrom(event.javaClass) -> return event as T
      else -> throw IllegalStateException("Unexpected event: $event")
    }
  }

  private class EventQueueThread(
    private val queue: EventQueue,
    private val channel: Channel<Event>
  ) : Thread("Event Queue") {

    override fun run() {
      var done = false
      while (!done) {
        val events =
          try {
            queue.remove()
          } catch (e: InterruptedException) {
            return
          }
        events.forEach {
          runBlocking {
            channel.send(it)
            if (it is VMDeathEvent) {
              done = true
            }
          }
        }
      }
    }
  }
}
