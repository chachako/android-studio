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

import com.android.jdwppacket.Cmd
import com.android.jdwppacket.EventKind
import com.android.jdwppacket.Location
import com.android.jdwppacket.MessageReader
import com.android.jdwppacket.TaggedObjectID
import com.android.jdwppacket.TaggedValue
import com.android.jdwppacket.Writer
import com.android.jdwppacket.getTagValue
import com.android.jdwppacket.putTaggedValue

data class CompositeCmd(val suspendPolicy: Byte, val events: List<Event>) :
  Cmd(com.android.jdwppacket.Event.Composite) {

  interface Event {
    val kind: EventKind
    val requestID: Int
  }

  data class EventClassPrepare(
    override val kind: EventKind = EventKind.CLASS_PREPARE,
    override val requestID: Int,
    val threadID: Long,
    val typeTag: Byte,
    val referenceTypeID: Long,
    val signature: String,
    val status: Int
  ) : Event

  data class EventClassUnload(
    override val kind: EventKind = EventKind.CLASS_UNLOAD,
    override val requestID: Int,
    val signature: String,
  ) : Event

  data class EventThreadLocation(
    override val kind: EventKind,
    override val requestID: Int,
    val threadID: Long,
    val location: Location,
  ) : Event

  data class EventMethodExitReturnValue(
    override val kind: EventKind,
    override val requestID: Int,
    val threadID: Long,
    val location: Location,
    val taggedValue: TaggedValue
  ) : Event

  data class EventMonitorContended(
    override val kind: EventKind,
    override val requestID: Int,
    val threadID: Long,
    val taggedObjectID: TaggedObjectID,
    val location: Location
  ) : Event

  data class EventMonitorWait(
    override val kind: EventKind,
    override val requestID: Int,
    val threadID: Long,
    val taggedObjectID: TaggedObjectID,
    val location: Location,
    val timeout: Long
  ) : Event

  data class EventMonitorWaited(
    override val kind: EventKind,
    override val requestID: Int,
    val threadID: Long,
    val taggedObjectID: TaggedObjectID,
    val location: Location,
    val timedOut: Boolean
  ) : Event

  data class EventException(
    override val kind: EventKind,
    override val requestID: Int,
    val threadID: Long,
    val location: Location,
    val taggedObjectID: TaggedObjectID,
    val catchLocation: Location
  ) : Event

  data class EventLifeCycle(
    override val kind: EventKind,
    override val requestID: Int,
    val threadID: Long
  ) : Event

  data class EventFieldAccess(
    override val kind: EventKind,
    override val requestID: Int,
    val threadID: Long,
    val location: Location,
    val refType: Byte,
    val typeID: Long,
    val fieldID: Long,
    val taggedObjectID: TaggedObjectID
  ) : Event

  data class EventFieldModification(
    override val kind: EventKind,
    override val requestID: Int,
    val threadID: Long,
    val location: Location,
    val refType: Byte,
    val typeID: Long,
    val fieldID: Long,
    val taggedObjectID: TaggedObjectID,
    val value: TaggedValue
  ) : Event

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
            events.add(
              EventThreadLocation(kind, requestID, reader.getThreadID(), reader.getLocation())
            )
          }
          EventKind.METHOD_EXIT_WITH_RETURN_VALUE -> {
            events.add(
              EventMethodExitReturnValue(
                kind,
                requestID,
                reader.getThreadID(),
                reader.getLocation(),
                reader.getTagValue()
              )
            )
          }
          EventKind.MONITOR_CONTENDED_ENTER,
          EventKind.MONITOR_CONTENDED_ENTERED -> {
            events.add(
              EventMonitorContended(
                kind,
                requestID,
                reader.getThreadID(),
                reader.getTaggedObjectID(),
                reader.getLocation()
              )
            )
          }
          EventKind.MONITOR_WAIT -> {
            events.add(
              EventMonitorWait(
                kind,
                requestID,
                reader.getThreadID(),
                reader.getTaggedObjectID(),
                reader.getLocation(),
                reader.getLong()
              )
            )
          }
          EventKind.MONITOR_WAITED -> {
            events.add(
              EventMonitorWaited(
                kind,
                requestID,
                reader.getThreadID(),
                reader.getTaggedObjectID(),
                reader.getLocation(),
                reader.getBoolean()
              )
            )
          }
          EventKind.EXCEPTION -> {
            events.add(
              EventException(
                kind,
                requestID,
                reader.getThreadID(),
                reader.getLocation(),
                reader.getTaggedObjectID(),
                reader.getLocation()
              )
            )
          }
          EventKind.VM_START,
          EventKind.THREAD_START,
          EventKind.THREAD_DEATH -> {
            events.add(EventLifeCycle(kind, requestID, reader.getThreadID()))
          }
          EventKind.CLASS_PREPARE -> {
            val threadID = reader.getThreadID()
            val typeTag = reader.getTypeTag()
            val referenceTypeID = reader.getReferenceTypeID()
            val signature = reader.getString()
            val status = reader.getInt()
            events.add(
              EventClassPrepare(
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
            val signature = reader.getString() // signature
            events.add(EventClassUnload(kind, requestID, signature))
          }
          EventKind.FIELD_ACCESS -> {
            events.add(
              EventFieldAccess(
                kind,
                requestID,
                reader.getThreadID(),
                reader.getLocation(),
                reader.getByte(),
                reader.getReferenceTypeID(),
                reader.getFieldID(),
                reader.getTaggedObjectID()
              )
            )
          }
          EventKind.FIELD_MODIFICATION -> {
            events.add(
              EventFieldModification(
                kind,
                requestID,
                reader.getThreadID(),
                reader.getLocation(),
                reader.getByte(),
                reader.getObjectID(),
                reader.getFieldID(),
                reader.getTaggedObjectID(),
                reader.getTagValue()
              )
            )
          }
          else -> throw IllegalStateException("Unprocessed Kind" + kind.name)
        }
      }
      return CompositeCmd(suspendPolicy, events)
    }
  }

  override fun paramsKey(): String {
    return ""
  }

  override fun writePayload(writer: Writer) {
    writer.putByte(suspendPolicy)
    writer.putInt(events.size)
    events.forEach {
      writer.putByte(it.kind.id.toByte())
      writer.putInt(it.requestID)
      when (it.kind) {
        EventKind.SINGLE_STEP,
        EventKind.BREAKPOINT,
        EventKind.METHOD_ENTRY,
        EventKind.METHOD_EXIT -> {
          it as EventThreadLocation
          writer.putThreadID(it.threadID)
          writer.putLocation(it.location)
        }
        EventKind.METHOD_EXIT_WITH_RETURN_VALUE -> {
          it as EventMethodExitReturnValue
          writer.putThreadID(it.threadID)
          writer.putLocation(it.location)
          writer.putTaggedValue(it.taggedValue)
        }
        EventKind.MONITOR_CONTENDED_ENTER,
        EventKind.MONITOR_CONTENDED_ENTERED -> {
          it as EventMonitorContended
          writer.putThreadID(it.threadID)
          writer.putTaggedObjectID(it.taggedObjectID)
          writer.putLocation(it.location)
        }
        EventKind.MONITOR_WAIT -> {
          it as EventMonitorWait
          writer.putThreadID(it.threadID)
          writer.putTaggedObjectID(it.taggedObjectID)
          writer.putLocation(it.location)
          writer.putLong(it.timeout)
        }
        EventKind.MONITOR_WAITED -> {
          it as EventMonitorWaited
          writer.putThreadID(it.threadID)
          writer.putTaggedObjectID(it.taggedObjectID)
          writer.putLocation(it.location)
          writer.putBoolean(it.timedOut)
        }
        EventKind.EXCEPTION -> {
          it as EventException
          writer.putThreadID(it.threadID)
          writer.putLocation(it.location)
          writer.putTaggedObjectID(it.taggedObjectID)
          writer.putLocation(it.catchLocation)
        }
        EventKind.VM_START,
        EventKind.THREAD_START,
        EventKind.THREAD_DEATH -> {
          it as EventLifeCycle
          writer.putThreadID(it.threadID)
        }
        EventKind.CLASS_PREPARE -> {
          it as EventClassPrepare
          writer.putThreadID(it.threadID)
          writer.putTypeTag(it.typeTag)
          writer.putReferenceTypeID(it.referenceTypeID)
          writer.putString(it.signature)
          writer.putInt(it.status)
        }
        EventKind.CLASS_UNLOAD -> {
          it as EventClassUnload
          writer.putString(it.signature)
        }
        EventKind.FIELD_ACCESS -> {
          it as EventFieldAccess
          writer.putThreadID(it.threadID)
          writer.putLocation(it.location)
          writer.putByte(it.refType)
          writer.putObjectID(it.typeID)
          writer.putLong(it.fieldID)
          writer.putTaggedObjectID(it.taggedObjectID)
        }
        EventKind.FIELD_MODIFICATION -> {
          it as EventFieldModification
          writer.putThreadID(it.threadID)
          writer.putLocation(it.location)
          writer.putByte(it.refType)
          writer.putObjectID(it.typeID)
          writer.putLong(it.fieldID)
          writer.putTaggedObjectID(it.taggedObjectID)
          writer.putTaggedValue(it.value)
        }
      }
    }
  }
}
