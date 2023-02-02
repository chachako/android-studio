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
package com.android.jdwppacket.event

import com.android.jdwppacket.EventKind
import com.android.jdwppacket.MessageReader

// This is the event type, usually issued from the VM. We don't cache them and we don't
// generate them. So not really a point in making them inherit from Cmd for now.
data class CompositeCmd(val suspendPolicy: Byte, val events: List<Event>) {

  open class Event(val kind: EventKind, val requestID: Int)
  class ClassPrepareEvent(
    kind: EventKind,
    requestID: Int,
    val threadID: Long,
    val typeTag: Byte,
    val referenceTypeID: Long,
    val signature: String,
    val status: Int
  ) : Event(kind, requestID)

  companion object {

    @JvmStatic
    fun parse(reader: MessageReader): CompositeCmd {
      val suspendPolicy = reader.getByte()
      val numEvents = reader.getInt()
      val events: List<Event>

      events = mutableListOf()
      repeat(numEvents) {
        val eventKind = reader.getByte()
        val kind = EventKind.fromID(eventKind.toInt())
        val requestID = reader.getInt()

        when (kind) {
          EventKind.SINGLE_STEP,
          EventKind.BREAKPOINT,
          EventKind.METHOD_ENTRY,
          EventKind.METHOD_EXIT -> {
            reader.getThreadID()
            reader.getLocation()
            events.add(Event(kind, requestID))
          }
          EventKind.METHOD_EXIT_WITH_RETURN_VALUE -> {
            reader.getThreadID()
            reader.getLocation()
            reader.getTagValue()
            events.add(Event(kind, requestID))
          }
          EventKind.MONITOR_CONTENDED_ENTER, EventKind.MONITOR_CONTENDED_ENTERED -> {
            reader.getThreadID()
            reader.getTaggedObjectID()
            reader.getLocation()
            events.add(Event(kind, requestID))
          }
          EventKind.MONITOR_WAIT -> {
            reader.getThreadID()
            reader.getTaggedObjectID()
            reader.getLocation()
            reader.getLong() // timeout
            events.add(Event(kind, requestID))
          }
          EventKind.MONITOR_WAITED -> {
            reader.getThreadID()
            reader.getTaggedObjectID()
            reader.getLocation()
            reader.getBoolean() // timeout
            events.add(Event(kind, requestID))
          }
          EventKind.EXCEPTION -> {
            reader.getThreadID()
            reader.getLocation() // throw location
            reader.getTaggedObjectID()
            reader.getLocation() // catch location
            events.add(Event(kind, requestID))
          }
          EventKind.VM_START, EventKind.THREAD_START, EventKind.THREAD_DEATH -> {
            reader.getThreadID()
            events.add(Event(kind, requestID))
          }
          EventKind.CLASS_PREPARE -> {
            val threadID = reader.getThreadID()
            val typeTag = reader.getTypeTag()
            val referenceTypeID = reader.getReferenceTypeID()
            val signature = reader.getString()
            val status = reader.getInt()
            events.add(
              ClassPrepareEvent(
                kind,
                requestID,
                threadID,
                typeTag,
                referenceTypeID,
                signature,
                status
              )
            )
          }
          EventKind.CLASS_UNLOAD -> {
            reader.getString() // signature
            events.add(Event(kind, requestID))
          }
          EventKind.FIELD_ACCESS -> {
            reader.getThreadID()
            reader.getLocation()
            reader.getByte() // refTypeTag
            reader.getReferenceTypeID() // referenceID
            reader.getFieldID()
            reader.getTaggedObjectID()
            events.add(Event(kind, requestID))
          }
          EventKind.FIELD_MODIFICATION -> {
            reader.getThreadID()
            reader.getLocation()
            reader.getByte() // refTypeTag
            reader.getObjectID() // referenceID
            reader.getFieldID()
            reader.getTaggedObjectID()
            reader.getTagValue()
            events.add(Event(kind, requestID))
          }
          else -> throw IllegalStateException("Unprocessed Kind" + kind.name)
        }
      }
      return CompositeCmd(suspendPolicy, events)
    }
  }
}
