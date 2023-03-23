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
package com.android.adblib.tools.debugging.packets.ddms.chunks

import com.android.adblib.testingutils.CoroutineTestUtils
import com.android.adblib.tools.debugging.packets.AdbBufferedInputChannel
import com.android.adblib.tools.debugging.packets.ddms.DdmsChunkTypes
import com.android.adblib.tools.debugging.packets.ddms.MutableDdmsChunk
import com.android.adblib.utils.ResizableBuffer
import org.junit.Assert
import org.junit.Test

class DdmsStagChunkTest {

    @Test
    fun testParsingWithAllFieldsWorks() = CoroutineTestUtils.runBlockingWithTimeout {
        // Prepare
        val payload = run {
            val buffer = ResizableBuffer()
            DdmsStagChunk.writePayload(
                buffer,
                stage = AppStage.DEBG
            )
            buffer.forChannelWrite()
        }
        val chunk = MutableDdmsChunk().apply {
            type = DdmsChunkTypes.WAIT
            length = payload.remaining()
            this.payload = AdbBufferedInputChannel.forByteBuffer(payload)
        }

        // Act
        val stagChunk = DdmsStagChunk.parse(chunk)

        // Assert
        Assert.assertEquals(AppStage.DEBG, stagChunk.stage)
    }
}
