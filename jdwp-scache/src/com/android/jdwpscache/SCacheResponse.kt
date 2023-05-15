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
package com.android.jdwpscache

import java.nio.ByteBuffer

/**
 * What problem is this edict/journal non-sense solving? Well, let me explain.
 *
 * SCache works like a collaborative look-aside cache. Upon receiving a cmd or an event, it returns
 * two list of packets, one to send upstream, the other to send downstream. These are the EDICTs.
 *
 * What if you have a jwpd tracing library? Can you not simply trace the outputs of scache? No, you
 * can't. I does not work upon cache hit. e.g.:
 *
 * 1 cmd arrives to scache 2 scache already speculated on this cmd. This is a cache hit. It returns
 * an empty upstream list and the speculatively cached response in the downstream list. 3 If we were
 * to send the two lists to a tracer, we would have a reply without a cmd (since scache prevented it
 * from being sent).
 *
 * This is what the Journal list is. It is meant to be consumed by JDWP tracer library. It gives a
 * cohesive view of the JDWP traffic, not what actually transited on the wire.
 */
class SCacheResponse {

  val edict = JdwpTraffic()
  val journal = JdwpTraffic()

  fun addToUpstream(buffer: ByteBuffer) {
    edict.upstreamList.add(buffer)
    journal.upstreamList.add(buffer)
  }

  fun addToDownstream(buffer: ByteBuffer) {
    edict.downstreamList.add(buffer)
    journal.downstreamList.add(buffer)
  }

  internal fun addToUpstreamJournal(buffer: ByteBuffer) {
    journal.upstreamList.add(buffer)
  }

  internal fun addToDownstreamJournal(buffer: ByteBuffer) {
    journal.downstreamList.add(buffer)
  }
}
