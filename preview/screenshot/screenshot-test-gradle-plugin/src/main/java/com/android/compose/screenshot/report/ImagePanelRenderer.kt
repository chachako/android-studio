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

package com.android.compose.screenshot.report

import com.android.tools.render.compose.ImagePathOrMessage
import org.gradle.reporting.ReportRenderer
import java.io.File
import java.util.Base64

class ImagePanelRenderer: ReportRenderer<ScreenshotTestImages, SimpleHtmlWriter>() {

    val ALT_TEXT_PREFIX = "Error displaying image at "
    val COLUMN_NAME_REFERENCE = "Reference Image"
    val COLUMN_NAME_ACTUAL = "Actual Image"
    val COLUMN_NAME_DIFF = "Diff Image"

    override fun render(ssImages: ScreenshotTestImages, htmlWriter: SimpleHtmlWriter) {

        // Wrap in a <span>, to work around CSS problem in IE
        htmlWriter.startElement("span")
            .startElement("table").attribute("style", "table-layout: fixed")
            .startElement("thead")
            .startElement("tr")

        addColumn(htmlWriter, COLUMN_NAME_REFERENCE)
        addColumn(htmlWriter, COLUMN_NAME_ACTUAL)
        addColumn(htmlWriter, COLUMN_NAME_DIFF)

        htmlWriter.endElement().endElement()
        htmlWriter.startElement("tbody").attribute("class", "grid").attribute("style", "width: 100%")  // this class will render a grid like background to better show the diff between png images with and without background
            .startElement("tr")
        val texts = getTexts(ssImages)
        renderImage(htmlWriter, ssImages.reference, texts.referenceImageText)
        renderImage(htmlWriter, ssImages.actual, texts.actualText)
        renderImage(htmlWriter, ssImages.diff, texts.diffText)
        htmlWriter.endElement().endElement().endElement().endElement()

    }

    private fun addColumn(htmlWriter: SimpleHtmlWriter, columnName: String) {
        htmlWriter.startElement("th").attribute("style", "width: 33.33%").characters(columnName).endElement()
    }

    private fun renderImage(htmlWriter: SimpleHtmlWriter, imagePath: ImagePathOrMessage?, text: String) {
        if (imagePath is ImagePathOrMessage.ImagePath && File(imagePath.path).exists()) {
            val base64String = Base64.getEncoder().encodeToString(File(imagePath.path).readBytes())
            htmlWriter.startElement("td").attribute("style", "width: 33.33%; padding: 1em")
                .startElement("img").attribute("src", "data:image/png;base64, $base64String").attribute("style", "max-width: 100%; height: auto;").attribute("alt", text).endElement()
                .endElement()
        } else {
            htmlWriter.startElement("td").attribute("style", "width: 33.33%").characters(text).endElement()
        }
    }

    private fun getTexts(ssImages: ScreenshotTestImages): ImageTexts {
        // alt texts when image was generated, but image display fails
        val referenceImageText = when (ssImages.reference) {
            is ImagePathOrMessage.ImagePath ->  "$ALT_TEXT_PREFIX${ssImages.reference.path}"
            is ImagePathOrMessage.ErrorMessage -> ssImages.reference.message
        }

        val actualText = when (ssImages.actual) {
            is ImagePathOrMessage.ImagePath -> "$ALT_TEXT_PREFIX${ssImages.actual.path}"
            is ImagePathOrMessage.ErrorMessage -> ssImages.actual.message
        }

        val diffText = when (ssImages.diff) {
            is ImagePathOrMessage.ImagePath -> "$ALT_TEXT_PREFIX${ssImages.diff.path}"
            is ImagePathOrMessage.ErrorMessage -> ssImages.diff.message
        }


        return ImageTexts(referenceImageText, actualText, diffText)
    }
}

data class ImageTexts(val referenceImageText: String, val actualText: String, val diffText: String)
