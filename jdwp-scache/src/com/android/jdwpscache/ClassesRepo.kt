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

import com.android.jdwppacket.Key
import com.android.jdwppacket.event.CompositeCmd
import com.android.jdwppacket.vm.AllClassesReply
import com.android.jdwppacket.vm.AllClassesWithGenericsReply

/*
 * Repository of data we need when CLASS_UNLOAD is received. When a class is unloaded, we need
 * to evict all cached replies starting from the "signature" received in CLASS_UNLOAD. Here we
 * store a mapping [signature -> ReferenceTypeID] and a mapping [ReferenceTypeID -> List<CachedKey>]
 *
 */
class ClassesRepo {

  /**
   * We cache class information reply packets. If the class is unloaded, we must clear all its
   * cached data. This struct gives us all keys contains in `cache` for a classID.
   */
  private val classIdToKeys: MutableMap<Long, MutableList<CmdKey>> = mutableMapOf()

  private val classSignatureToID: MutableMap<String, Long> = mutableMapOf()

  fun declareSpeculation(classID: Long, key: CmdKey) {
    classIdToKeys.getOrPut(classID) { mutableListOf() }.add(key)
  }

  fun onClassUnload(eventClassUnload: CompositeCmd.EventClassUnload) {
    val id = classSignatureToID.remove(eventClassUnload.signature) ?: return
    classIdToKeys.remove(id)
  }

  fun onClassPrepare(eventClassPrepare: CompositeCmd.EventClassPrepare) {
    classSignatureToID[eventClassPrepare.signature] = eventClassPrepare.referenceTypeID
  }

  fun getSpeculatedFor(signature: String): List<Key> {
    val id = classSignatureToID[signature] ?: return emptyList()
    return classIdToKeys[id] ?: emptyList()
  }

  fun onAllClassesWithGenericReply(allClassesWithGeneric: AllClassesWithGenericsReply) {
    allClassesWithGeneric.classes.forEach { classSignatureToID[it.signature] = it.referenceTypeID }
  }

  fun onAllClassesReply(allClasses: AllClassesReply) {
    allClasses.classes.forEach { classSignatureToID[it.signature] = it.referenceTypeID }
  }
}
