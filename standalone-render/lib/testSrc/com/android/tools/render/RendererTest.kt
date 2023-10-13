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
import java.io.File
import javax.imageio.ImageIO
import kotlin.io.path.absolutePathString
import kotlin.math.abs
import kotlin.math.max

private const val TEST_DATA_DIR = "tools/base/standalone-render/lib/testData/rendered_images"
// This is to respect the different rounding on different platforms.
// TODO(): have different goldens for different platforms
private const val THRESHOLD = 1
class RendererTest {
    @JvmField @Rule
    val tmpFolder = TemporaryFolder()
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

        val fakeSdkFolder = tmpFolder.newFolder()
        prepareFakeSdkFolder(fakeSdkFolder)

        val layoutlibPath = TestUtils.resolveWorkspacePath("prebuilts/studio/layoutlib")

        var outputImage: BufferedImage? = null
        renderForTest(
            fakeSdkFolder.absolutePath,
            null,
            "",
            emptyList(),
            // Layoutlib path is expected strictly with the [File.separator] at the end
            layoutlibPath.absolutePathString() + File.separator,
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

    private fun prepareFakeSdkFolder(fakeSdkFolder: File) {
        val platforms = fakeSdkFolder.resolve("platforms")
        platforms.mkdirs()
    }
}
