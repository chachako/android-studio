/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.ide.common.resources.localeGen

import com.android.ide.common.resources.configuration.FolderConfiguration
import com.android.ide.common.resources.generateFolderLocaleSet
import com.android.ide.common.resources.writeLocaleConfig
import com.android.ide.common.resources.generateLocaleConfigManifestAttribute
import com.android.ide.common.resources.generateLocaleString
import com.android.ide.common.resources.validateLocale
import com.android.ide.common.resources.readSupportedLocales
import com.android.ide.common.resources.writeSupportedLocales
import com.google.common.truth.Truth
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class LocaleGenerationTest {
    @get:Rule
    var temporaryFolder = TemporaryFolder()

    private fun addResFolder(root: File, name: String, empty: Boolean) {
        val resFolder = File(root, name)

        resFolder.mkdir()

        if (!empty) {
            File(resFolder, "strings.xml").createNewFile()
        }
    }
    @Test
    fun `Test that manifest attribute is correctly generated`() {
        Truth.assertThat(generateLocaleConfigManifestAttribute("_gradle_res_locale_config"))
            .isEqualTo("@xml/_gradle_res_locale_config")
    }

    @Test
    fun `Test that locale string is correctly generated`() {
        Truth.assertThat(generateLocaleString(
            FolderConfiguration.getConfig("values-en-rUS".split("-"))!!.localeQualifier)
        ).isEqualTo("en-US")
        Truth.assertThat(generateLocaleString(
            FolderConfiguration.getConfig("values-b+zh+Hans+SG".split("-"))!!.localeQualifier)
        ).isEqualTo("zh-Hans-SG")
    }

    @Test
    fun `Test that locale list is correctly generated from a collection of res folders`() {
        val res1 = temporaryFolder.newFolder("res1")
        addResFolder(res1, "values-en-rUS", false)
        addResFolder(res1, "values-ru-rRU", false)
        addResFolder(res1, "values-b+es+ES", false)
        val res2 = temporaryFolder.newFolder("res2")
        addResFolder(res2, "values-pt-rBR", false)
        val res3 = temporaryFolder.newFolder("res3")
        addResFolder(res3, "values-b+zh+Hans+SG", false)

        Truth.assertThat(generateFolderLocaleSet(listOf(res1, res2, res3)))
            .isEqualTo(setOf("b+es+ES", "en-rUS", "ru-rRU", "pt-rBR", "b+zh+Hans+SG"))
    }

    @Test
    fun `Test that locale list generator ignores empty folders`() {
        val res1 = temporaryFolder.newFolder("res1")
        addResFolder(res1, "values-en-rUS", false)
        addResFolder(res1, "values-ru-rRU", true)

        Truth.assertThat(generateFolderLocaleSet(listOf(res1))).isEqualTo(setOf("en-rUS"))
    }

    @Test
    fun `Test that locale config xml is correctly generated`() {
        val outfile = temporaryFolder.newFile("locale_config.xml")
        writeLocaleConfig(
            output = outfile,
            locales = setOf("en-US", "ru-RU", "es-ES", "pt-BR", "zh-Hans-SG", "en-GB")
        )
        Truth.assertThat(
            listOf(
                """<locale-config xmlns:android="http://schemas.android.com/apk/res/android">""",
                """    <locale android:name="en-US"/>""",
                """    <locale android:name="ru-RU"/>""",
                """    <locale android:name="es-ES"/>""",
                """    <locale android:name="pt-BR"/>""",
                """    <locale android:name="zh-Hans-SG"/>""",
                """    <locale android:name="en-GB"/>""",
                """</locale-config>""")).isEqualTo(outfile.readLines())
    }

    @Test
    fun `Test supported locales read and write`() {
        val jsonFile = temporaryFolder.newFile("locales.txt")
        val locales = listOf("*en-US", "es-ES", "pt-BR", "ru-RU", "zh-Hans-SG")
        // Write file to json and read it back to make sure it results in the same list
        Truth.assertThat(locales).isEqualTo(
            run {
                writeSupportedLocales(jsonFile, locales.drop(1), defaultLocale = "en-US")
                jsonFile.readLines()
            }
        )
    }

    @Test
    fun `Test locale validation`() {
        // Make sure invalid locales are not accepted
        Truth.assertThat(validateLocale("invalid-locale")).isNull()
        // Only locales in the same format as the locales config file are accepted
        // (en-US, and not b+en+US, for instance)
        Truth.assertThat(validateLocale("b+pt+BR")).isNull()
        // Make sure valid locales are supported
        Truth.assertThat(validateLocale("en-US")).isEqualTo("en-US")
    }

    @Test
    fun `Test read supported locales`() {
        val supportedLocales = temporaryFolder.newFile("supported_locales.txt")
        supportedLocales.writeText(
            """
                *chr
                zh
                es-rES
                pt-rBR
                b+zh+Hant
                b+zh+Hant+TW
            """.trimIndent()
        )
        val supportedLocalesObject = readSupportedLocales(supportedLocales)
        Truth.assertThat(supportedLocalesObject.defaultLocale).isEqualTo("chr")
        Truth.assertThat(supportedLocalesObject.folderLocales)
            .containsExactly("zh", "es-rES", "pt-rBR", "b+zh+Hant", "b+zh+Hant+TW")
    }
}
