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
package com.android.jdwppacket.threadreference

import com.android.jdwppacket.Cmd
import com.android.jdwppacket.MessageReader
import com.android.jdwppacket.ThreadReference
import com.android.jdwppacket.Writer

data class ResumeCmd(val threadID: Long) : Cmd(ThreadReference.Resume) {

  override fun paramsKey(): String {
    return "$threadID"
  }

  override fun writePayload(writer: Writer) {
    writer.putThreadID(threadID)
  }

  companion object {
    @JvmStatic
    fun parse(reader: MessageReader): ResumeCmd {
      return ResumeCmd(reader.getThreadID())
    }
  }
}
