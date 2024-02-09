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

package com.android.tools.render

import com.android.testutils.TestUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.awt.image.BufferedImage
import javax.imageio.ImageIO
import kotlin.io.path.absolutePathString
import kotlin.math.abs
import kotlin.math.max
import com.android.ide.common.rendering.api.Result
import com.android.tools.rendering.RenderResult
import com.android.tools.rendering.RenderService
import com.android.tools.sdk.AndroidTargetData
import com.android.tools.sdk.EmbeddedRenderTarget
import com.intellij.util.concurrency.AppExecutorUtil
import org.junit.AfterClass
import org.junit.Before
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit

private const val TEST_DATA_DIR = "tools/base/standalone-render/lib/testData/rendered_images"
// This is to respect the different rounding on different platforms.
// TODO(): have different goldens for different platforms
private const val THRESHOLD = 1
class RendererTest {
    @JvmField @Rule
    val tmpFolder = TemporaryFolder()

    companion object {
        @AfterClass
        @JvmStatic
        fun stopExecutor() {
            // Make sure the queue is empty
            AppExecutorUtil.getAppScheduledExecutorService().submit { }.get(60, TimeUnit.SECONDS)
            AppExecutorUtil.shutdownApplicationScheduledExecutorService()
        }
    }

    @Before
    fun setUp() {
        EmbeddedRenderTarget.resetRenderTarget()
        RenderService.initializeRenderExecutor()
        AndroidTargetData.clearCache()
    }

    @Test
    fun testSimpleLayoutRendering() {
        // language=xml
        val layout = """
            <?xml version="1.0" encoding="utf-8"?>
            <LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
                android:layout_width="wrap_content"
                android:layout_height="wrap_content"
                android:orientation="vertical" >
                <TextView
                    android:layout_width="match_parent"
                    android:layout_height="wrap_content"
                    android:gravity="top"
                    android:text="Hello!" />
                <Button
                    android:layout_width="100dp"
                    android:layout_height="wrap_content"
                    android:layout_gravity="end"
                    android:text="Press me!" />
            </LinearLayout>
        """.trimIndent()

        val request = RenderRequest({}) { sequenceOf(layout) }

        val layoutlibPath = TestUtils.resolveWorkspacePath("prebuilts/studio/layoutlib")

        var outputImage: BufferedImage? = null
        renderForTest(
            null,
            null,
            "",
            emptyList(),
            layoutlibPath.absolutePathString(),
            sequenceOf(request),
        ) { _, _, result ->
            assertNull("A single RenderResult is expected", outputImage)
            outputImage = result.renderedImage.copy
        }

        assertNotNull(outputImage)

        val goldenImagePath = TestUtils.resolveWorkspacePath("$TEST_DATA_DIR/img.png")
        val goldenImage = ImageIO.read(goldenImagePath.toFile())

        assertEquals(goldenImage.width, outputImage!!.width)
        assertEquals(goldenImage.height, outputImage!!.height)
        var lInfDiff = 0
        (0 until goldenImage.height).forEach {  j ->
            (0 until goldenImage.width).forEach { i ->
                val goldenCol = goldenImage.getRGB(i, j)
                val imgCol = outputImage!!.getRGB(i, j)
                lInfDiff = max(lInfDiff, abs((goldenCol and 0xFF) - (imgCol and 0xFF)))
                lInfDiff = max(lInfDiff, abs(((goldenCol shl 8) and 0xFF) - ((imgCol shl 8) and 0xFF)))
                lInfDiff = max(lInfDiff, abs(((goldenCol shl 16) and 0xFF) - ((imgCol shl 16) and 0xFF)))
            }
        }
        assertTrue("The L-infinity image diff is $lInfDiff, higher than the threshold $THRESHOLD", lInfDiff <= THRESHOLD)
    }

    @Test
    fun testIncorrectLayoutlibPath() {
        val renderResults = mutableListOf<RenderResult>()
        renderForTest(
            null,
            null,
            "",
            emptyList(),
            "",
            sequenceOf(RenderRequest({}) { sequenceOf("") }),
        ) { _, _, result ->
            renderResults.add(result)
        }

        assertEquals(1, renderResults.size)
        val renderResult = renderResults[0]
        assertEquals(Result.Status.ERROR_RENDER_TASK, renderResult.renderResult.status)
        assertTrue(renderResult.renderResult.exception is ExecutionException)
    }

    @Test
    fun testMissingResource() {
        // language=xml
        val layout = """
            <?xml version="1.0" encoding="utf-8"?>
            <LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
                android:layout_width="wrap_content"
                android:layout_height="wrap_content"
                android:orientation="vertical" >
                <TextView
                    android:layout_width="match_parent"
                    android:layout_height="wrap_content"
                    android:gravity="top"
                    android:text="@string/hello" />
            </LinearLayout>
        """.trimIndent()

        val layoutlibPath = TestUtils.resolveWorkspacePath("prebuilts/studio/layoutlib")

        val renderResults = mutableListOf<RenderResult>()
        renderForTest(
            null,
            null,
            "",
            emptyList(),
            layoutlibPath.absolutePathString(),
            sequenceOf(RenderRequest({}) { sequenceOf(layout) }),
        ) { _, _, result ->
            renderResults.add(result)
        }

        assertEquals(1, renderResults.size)
        val renderResult = renderResults[0]
        assertEquals(Result.Status.SUCCESS, renderResult.renderResult.status)
        val messages = renderResult.logger.messages
        assertEquals(1, messages.size)
        assertEquals("Couldn't resolve resource @string/hello", messages[0].html)
    }
}
