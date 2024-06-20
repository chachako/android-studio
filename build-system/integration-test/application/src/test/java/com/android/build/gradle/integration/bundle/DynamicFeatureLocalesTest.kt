/*
 * Copyright (C) 2024 The Android Open Source Project
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

package com.android.build.gradle.integration.bundle

import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.android.build.gradle.integration.common.truth.ScannerSubject
import com.android.build.gradle.integration.common.utils.TestFileUtils
import com.android.utils.FileUtils
import com.google.common.collect.ImmutableSet
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import java.nio.file.Files

class DynamicFeatureLocalesTest {

    @get:Rule
    val project: GradleTestProject = GradleTestProject.builder()
        .fromTestProject("dynamicApp")
        .create()

    private fun addLocale(
        project: GradleTestProject,
        locale: String,
        feature: Boolean = false
    ) {
        val file = project.file("src/main/res/values-$locale/strings.xml")
        file.parentFile.mkdirs()
        val str = if (feature) {
            "<string name=\"unused_from_feature1\">Placeholder</string>"
        } else {
            "<string name=\"button_name\">String from base</string>"
        }
        file.writeText(
            // language=xml
            """
            <?xml version="1.0" encoding="utf-8"?>
            <resources>
                $str
            </resources>
            """.trimIndent()
        )
    }

    private fun GradleTestProject.withLocales(
        appLocales: List<String>,
        dynamicFeatureLocales: List<String>
    ): GradleTestProject {
        appLocales.forEach {
            addLocale(this.getSubproject("app"), it)
        }
        dynamicFeatureLocales.forEach {
            addLocale(this.getSubproject("feature1"), it, feature = true)
        }
        return this
    }

    @Test
    fun dynamicFeatureUsesAppLocaleFilters() {
        project.getSubproject("app").buildFile.appendText(
            """
                android.androidResources.localeFilters += ["de", "es-rES", "b+zh+Hant+TW"]
            """.trimIndent()
        )
        project.withLocales(
            appLocales = listOf("es", "zh"),
            dynamicFeatureLocales = listOf("de", "b+zh+Hant+TW", "es-rES", "ar", "pt-rBR")
        ).executor().run(":app:makeApkFromBundleForDebug")
        val apksFromBundle = FileUtils.createZipFilesystem(
            project.getSubproject("app").getIntermediateFile(
                "apks_from_bundle",
                "debug",
                "makeApkFromBundleForDebug",
                "bundle.apks"
            ).toPath()
        ).use { apks ->
            Files.list(apks.getPath("splits/")).use {
                it.map { file -> file.fileName.toString() }.collect(ImmutableSet.toImmutableSet())
            }
        }
        assertThat(apksFromBundle).containsExactly(
            // defaults
            "base-master.apk",
            "base-master_2.apk",
            "feature1-master.apk",
            "feature1-master_2.apk",
            "feature2-master.apk",
            "feature2-master_2.apk",
            // these are the locales specified in the localeFilters DSL
            "base-es.apk",
            "feature1-de.apk",
            "feature1-es.apk",
            "feature1-zh.apk"
        )

        TestFileUtils.searchAndReplace(
            project.getSubproject("app").buildFile,
            "android.androidResources.localeFilters += [\"de\", \"es-rES\", \"b+zh+Hant+TW\"]",
            "android.androidResources.localeFilters += [\"round\"]"
        )
        val result = project.executor().expectFailure().run(":feature1:assembleDebug")
        ScannerSubject.assertThat(result.stderr).contains("The locale in localeFilters \"round\" is invalid.")
    }
}
