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
import com.android.ide.common.resources.writeLocaleConfig
import com.android.ide.common.resources.generateLocaleConfigManifestAttribute
import com.android.ide.common.resources.generateLocaleList
import com.android.ide.common.resources.generateLocaleString
import com.android.ide.common.resources.mergeLocaleLists
import com.android.ide.common.resources.validateLocale
import com.android.ide.common.resources.writeSupportedLocales
import com.google.common.truth.Truth.assertThat
import junit.framework.Assert.assertEquals
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
        assertEquals("@xml/_gradle_res_locale_config",
            generateLocaleConfigManifestAttribute("_gradle_res_locale_config"))
    }

    @Test
    fun `Test that locale string is correctly generated`() {
        assertEquals("en-US", generateLocaleString(
            FolderConfiguration.getConfig("values-en-rUS".split("-")).localeQualifier))
        assertEquals("zh-Hans-SG", generateLocaleString(
            FolderConfiguration.getConfig("values-b+zh+Hans+SG".split("-")).localeQualifier))
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

        assertThat(generateLocaleList(listOf(res1, res2, res3)))
            .isEqualTo(listOf("es-ES", "en-US", "ru-RU", "pt-BR", "zh-Hans-SG"))
    }

    @Test
    fun `Test that locale list generator ignores empty folders`() {
        val res1 = temporaryFolder.newFolder("res1")
        addResFolder(res1, "values-en-rUS", false)
        addResFolder(res1, "values-ru-rRU", true)

        assertThat(generateLocaleList(listOf(res1))).isEqualTo(listOf("en-US"))
    }

    @Test
    fun `Test that locale config xml is correctly generated`() {
        val outfile = temporaryFolder.newFile("locale_config.xml")
        writeLocaleConfig(
            output = outfile,
            locales = setOf("en-US", "ru-RU", "es-ES", "pt-BR", "zh-Hans-SG", "en-GB")
        )
        assertThat(
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
    fun `Test that locale lists are correctly merged`() {
        assertThat(listOf("en-US", "en-GB", "es-ES", "pt-BR")).containsExactlyElementsIn(
            mergeLocaleLists(
                listOf(
                    listOf("en-US", "en-GB", "en-GB"),
                    listOf("es-ES", "pt-BR"),
                    listOf()
                )
            )
        ).inOrder()
    }

    @Test
    fun `Test supported locales read and write`() {
        val jsonFile = temporaryFolder.newFile("locales.txt")
        val locales = listOf("en-US", "es-ES", "pt-BR", "ru-RU", "zh-Hans-SG")
        // Write file to json and read it back to make sure it results in the same list
        assertThat(locales).isEqualTo(
            run {
                writeSupportedLocales(jsonFile, locales.drop(1), defaultLocale = "en-US")
                jsonFile.readLines()
            }
        )
    }

    @Test
    fun `Test locale validation`() {
        // Make sure invalid locales are not accepted
        assertEquals(null, validateLocale("invalid-locale"))
        // Only locales in the same format as the locales config file are accepted
        // (en-US, and not b+en+US, for instance)
        assertEquals(null, validateLocale("b+pt+BR"))

        // Make sure valid locales are supported
        assertEquals("en-US", validateLocale("en-US"))
    }
}
