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

package com.android.tools.preview.screenshot.report

import org.gradle.reporting.ReportRenderer
import java.io.IOException

class TabsRenderer<T> : ReportRenderer<T, SimpleHtmlWriter>() {

    private val tabs: MutableList<TabDefinition> = ArrayList()
    fun add(title: String, contentRenderer: ReportRenderer<T, SimpleHtmlWriter>) {
        tabs.add(TabDefinition(title, contentRenderer))
    }

    fun clear() {
        tabs.clear()
    }

    @Throws(IOException::class)
    override fun render(model: T, htmlWriterWriter: SimpleHtmlWriter) {
        htmlWriterWriter.startElement("div").attribute("id", "tabs")
        htmlWriterWriter.startElement("ul").attribute("class", "tabLinks")
        for (i in tabs.indices) {
            val tab: TabDefinition = tabs[i]
            val tabId = String.format("tab%s", i)
            htmlWriterWriter.startElement("li")
            htmlWriterWriter.startElement("a")
                .attribute("href", "#$tabId")
                .characters(tab.title)
                .endElement()
            htmlWriterWriter.endElement()
        }
        htmlWriterWriter.endElement()
        for (i in tabs.indices) {
            val tab: TabDefinition = tabs[i]
            val tabId = String.format("tab%s", i)
            htmlWriterWriter.startElement("div").attribute("id", tabId).attribute("class", "tab")
            htmlWriterWriter.startElement("h2").characters(tab.title).endElement()
            tab.renderer.render(model, htmlWriterWriter)
            htmlWriterWriter.endElement()
        }
        htmlWriterWriter.endElement()
    }

    private inner class TabDefinition (
        val title: String,
        val renderer: ReportRenderer<T, SimpleHtmlWriter>
    )
}
