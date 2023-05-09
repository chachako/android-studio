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

package com.android.build.gradle.integration.application

import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.android.build.gradle.integration.common.fixture.GradleTestProject.Companion.builder
import com.android.build.gradle.integration.common.fixture.app.MinimalSubProject
import com.android.build.gradle.integration.common.fixture.app.MultiModuleTestProject
import com.android.build.gradle.integration.common.truth.ScannerSubject
import com.android.build.gradle.integration.common.utils.TestFileUtils
import com.android.build.gradle.tasks.LOCALE_CONFIG_FILE_NAME
import com.android.testutils.truth.PathSubject
import com.android.testutils.truth.ZipFileSubject
import com.android.utils.FileUtils
import com.google.common.truth.IterableSubject
import com.google.common.truth.Truth
import org.junit.Rule
import org.junit.Test
import java.io.File

private val DEFAULT = null // default locale
private val ES = Locale("b+es+ES", "es-ES")
private val ZH = Locale("b+zh+Hans+SG", "zh-Hans-SG")
private val EN_GB = Locale("en-rGB", "en-GB")
private val EN_US = Locale("b+en+US", "en-US")
private val RU = Locale("b+ru+RU", "ru-RU")
private val PT = Locale("b+pt+BR", "pt-BR")
private val JP = Locale("b+jp+JP", "jp-JP")

private data class Locale(
    val code: String,
    val text: String
)

class LocaleConfigGenerationTest {

    private val localeListPath = "build/intermediates/supported_locale_list"
    private val localeConfigPath = "build/generated/res/localeConfig"
    private val mergedManifestPath = "build/intermediates/merged_manifest"

    private val app =
        MinimalSubProject.app("com.example.app")
            .withFile(
                "src/main/res/xml/user_locale_config.xml",
                // language=xml
                """
                    <locale-config xmlns:android="http://schemas.android.com/apk/res/android">
                        <locale android:name="en-US"/>
                        <locale android:name="zh-TW"/>
                        <locale android:name="pt"/>
                        <locale android:name="fr"/>
                        <locale android:name="zh-Hans-SG"/>
                    </locale-config>
                """.trimIndent()
            )

    private val lib1 =
        MinimalSubProject.lib("com.example.lib1")

    private val lib2 =
        MinimalSubProject.lib("com.example.lib2")

    @get:Rule
    val project = builder()
        .fromTestApp(
            MultiModuleTestProject.builder()
                .subproject(":app", app)
                .subproject(":lib1", lib1)
                .subproject(":lib2", lib2)
                .dependency(app, lib1)
                .dependency(lib1, lib2)
                .build()
        ).create()

    private fun addLocaleForVariantDimension(
        project: GradleTestProject,
        locale: Locale?,
        variantDimension: String = "main",
        addResFile: Boolean = true
    ) {
        val file =
            if (locale == DEFAULT) {
                project.file("src/${variantDimension}/res/values/strings.xml")
            } else {
                project.file("src/${variantDimension}/res/values-${locale.code}/strings.xml")
            }
        val text = locale?.text ?: "en-US"

        file.parentFile.mkdirs()
        if (addResFile) {
            file.writeText(
                // language=xml
                """
                <?xml version="1.0" encoding="utf-8"?>
                <resources>
                    <string name="test">"Hello $text"</string>
                </resources>
                """.trimIndent()
            )
        }
    }

    private fun GradleTestProject.withUserLocaleConfig(): GradleTestProject {
        TestFileUtils.searchAndReplace(
            this.getSubproject("app").file("src/main/AndroidManifest.xml"),
            "<application />",
            "<application android:localeConfig=\"@xml/user_locale_config\"/>"
        )
        return this
    }

    private fun GradleTestProject.withLocales(
        appLocales: List<Locale?>,
        lib1Locales: List<Locale?>,
        lib2Locales: List<Locale?>
    ): GradleTestProject {
        appLocales.forEach {
            addLocaleForVariantDimension(this.getSubproject("app"), it)
        }
        lib1Locales.forEach {
            addLocaleForVariantDimension(this.getSubproject("lib1"), it)
        }
        lib2Locales.forEach {
            addLocaleForVariantDimension(this.getSubproject("lib2"), it)
        }
        return this
    }

    private fun buildDsl(
        generateLocaleConfig: Boolean,
        defaultLocale: String? = "en-US",
        addFlavor: Boolean = false,
        addResourcesProperties: Boolean = true
    ) {
        var dslString =
            // language=groovy
            """

                android {
                    androidResources {
                        generateLocaleConfig $generateLocaleConfig
                    }
                }

            """.trimIndent()
        if (addFlavor)  {
            // language=groovy
            dslString += """
                android.flavorDimensions "language"
                android.productFlavors {
                        tokyo {
                            dimension "language"
                        }
                        london {
                            dimension "language"
                        }
                }
                """.trimIndent()
        }

        project.getSubproject("app").buildFile.appendText(dslString)

        if (addResourcesProperties) {
            var fileText = ""
            if (defaultLocale != null) {
                fileText =
                    """
                        unqualifiedResLocale=$defaultLocale
                    """.trimIndent()
            }

            project.getSubproject("app").file("src/main/res/resources.properties")
                .writeText(fileText)
        }
    }

    private fun localeListFromFile(file: File): List<String> {
        return file.readLines()
    }

    private fun localeListFromXml(xml: File): List<String> {
        val locales = mutableListOf<String>()
        xml.readLines().forEach {
            if (it.trim().startsWith("<locale android:name")) locales.add(it.split("\"")[1])
        }
        return locales
    }

    private fun assertLocaleList(subproject: String, variant: String): IterableSubject {
        return Truth.assertThat(
            localeListFromFile(
                project.getSubproject(subproject).file("$localeListPath/$variant/supported_locales.txt"))
        )
    }

    private fun assertLocaleConfig(variant: String): IterableSubject {
        return Truth.assertThat(
            localeListFromXml(
                project.getSubproject("app").file("$localeConfigPath/$variant/xml/$LOCALE_CONFIG_FILE_NAME.xml"))
        )
    }

    @Test
    fun `Test with default behavior locale config is not generated`() {
        // no DSL

        val result = project.withLocales(
            appLocales = listOf(DEFAULT, ES, ZH, EN_GB),
            lib1Locales = listOf(EN_US, ES, RU),
            lib2Locales = listOf(PT)
        ).executor().run("assembleDebug")

        Truth.assertThat(result.didWorkTasks).doesNotContain(":app:generateDebugLocaleConfig")

        PathSubject.assertThat(project.getSubproject("app").file(localeConfigPath))
            .doesNotExist()
        PathSubject.assertThat(project.getSubproject("app").file(localeListPath))
            .doesNotExist()
        PathSubject.assertThat(project.getSubproject("lib1").file(localeListPath))
            .doesNotExist()
        PathSubject.assertThat(project.getSubproject("lib2").file(localeListPath))
            .doesNotExist()
        PathSubject.assertThat(project.getSubproject("app")
            .file("$mergedManifestPath/debug/AndroidManifest.xml"))
            .doesNotContain("android:localeConfig")
    }

    @Test
    fun `Test with feature off user locale config is not changed and locale config is not generated`() {
        buildDsl(generateLocaleConfig = false)

        val result = project.withLocales(
            appLocales = listOf(DEFAULT, ES, ZH, EN_GB),
            lib1Locales = listOf(EN_US, ES, RU),
            lib2Locales = listOf()
        ).withUserLocaleConfig().executor().run("assembleDebug")

        Truth.assertThat(result.didWorkTasks).doesNotContain(":app:generateDebugLocaleConfig")
        PathSubject.assertThat(project.getSubproject("app").file(localeConfigPath))
            .doesNotExist()
        PathSubject.assertThat(project.getSubproject("app").file(localeListPath))
            .doesNotExist()
        PathSubject.assertThat(project.getSubproject("lib1").file(localeListPath))
            .doesNotExist()
        PathSubject.assertThat(project.getSubproject("lib2").file(localeListPath))
            .doesNotExist()
        val mergedManifest = project.getSubproject("app")
            .file("$mergedManifestPath/debug/AndroidManifest.xml")
        PathSubject.assertThat(mergedManifest)
            .contains("""android:localeConfig="@xml/user_locale_config"""")
        PathSubject.assertThat(mergedManifest)
            .doesNotContain(LOCALE_CONFIG_FILE_NAME)
    }

    // Test that locale lists are created in app and lib
    // Test that locale config xml is created in app only
    // Test that generated locale config with no user generated alternative is present in manifest
    @Test
    fun `Test task runs and xml and locale lists are generated`() {
        buildDsl(generateLocaleConfig = true)

        val result = project.withLocales(
            appLocales = listOf(DEFAULT, ES, ZH, EN_GB),
            lib1Locales = listOf(EN_US, ES, RU),
            lib2Locales = listOf(PT)
        ).executor().run("assembleDebug")

        Truth.assertThat(result.didWorkTasks).contains(":app:generateDebugLocaleConfig")
        // Test that all expected files are generated
        PathSubject.assertThat(project.getSubproject("app").file(localeConfigPath)).exists()
        PathSubject.assertThat(project.getSubproject("app").file(localeListPath)).exists()
        PathSubject.assertThat(project.getSubproject("lib1").file(localeListPath)).exists()
        PathSubject.assertThat(project.getSubproject("lib2").file(localeListPath)).exists()

        // Test that manifest change is present
        val mergedManifest = project.getSubproject("app")
            .file("$mergedManifestPath/debug/AndroidManifest.xml")
        PathSubject.assertThat(mergedManifest)
            .contains("""android:localeConfig="@xml/$LOCALE_CONFIG_FILE_NAME""")
        PathSubject.assertThat(mergedManifest)
            .doesNotContain("""android:localeConfig="@xml/user_locale_config""")

        // Test that xml is not generated in libs
        PathSubject.assertThat(project.getSubproject("lib1")
            .file("build/generated/res/localeConfig")).doesNotExist()
        PathSubject.assertThat(project.getSubproject("lib2")
            .file("build/generated/res/localeConfig")).doesNotExist()
    }

    // Test that all locales are present in final xml (including non-overlapping fallback locale)
    @Test
    fun `Test locales contain all supported locales`() {
        buildDsl(generateLocaleConfig = true, defaultLocale = "de-DE")

        project.withLocales(
            appLocales = listOf(DEFAULT, ES, ZH, EN_GB),
            lib1Locales = listOf(RU),
            lib2Locales = listOf(PT)
        ).execute("assembleDebug")

        assertLocaleList("app", "debug")
            .containsExactly("de-DE", "es-ES","zh-Hans-SG","en-GB")
        assertLocaleList("lib1", "debug")
            .containsExactly("ru-RU")
        assertLocaleList("lib2", "debug")
            .containsExactly("pt-BR")
        // Final xml adds default locale (de-DE)
        assertLocaleConfig("debug")
            .containsExactly("de-DE", "es-ES","zh-Hans-SG","en-GB", "ru-RU", "pt-BR")
    }

    @Test
    fun `Test locales are not duplicated`() {
        buildDsl(generateLocaleConfig = true)

        project.withLocales(
            appLocales = listOf(DEFAULT, ES, ZH, EN_GB),
            lib1Locales = listOf(ES, RU), // Duplicate ES
            lib2Locales = listOf(PT, EN_US) // Duplicate EN_US (default)
        ).execute("assembleDebug")

        assertLocaleList("app", "debug")
            .containsExactly("en-US", "es-ES","zh-Hans-SG","en-GB")
        assertLocaleList("lib1", "debug")
            .containsExactly("es-ES", "ru-RU")
        assertLocaleList("lib2", "debug")
            .containsExactly("pt-BR", "en-US")
        assertLocaleConfig("debug")
            .containsExactly("en-US", "es-ES", "zh-Hans-SG","en-GB", "ru-RU", "pt-BR")
    }

    @Test
    fun `Test user locale config produces conflict`() {
        buildDsl(generateLocaleConfig = true)

        val result = project.withLocales(
            appLocales = listOf(DEFAULT, ES, ZH, EN_GB),
            lib1Locales = listOf(EN_US, ES, RU),
            lib2Locales = listOf()
        ).withUserLocaleConfig().executor().expectFailure().run("assembleDebug")

        ScannerSubject.assertThat(result.stderr).contains(
            "Locale config generation was requested but user locale config is present in manifest"
        )
    }

    @Test
    fun `Test invalid default locale`() {
        buildDsl(generateLocaleConfig = true, defaultLocale = "testLocale")

        val result = project.withLocales(
            appLocales = listOf(DEFAULT),
            lib1Locales = listOf(),
            lib2Locales = listOf()
        ).executor().expectFailure().run("assembleDebug")

        ScannerSubject.assertThat(result.stderr).contains(
            "The default locale \"testLocale\" from the file " +
            "\"${project.getSubproject("app").file("src/main/res/resources.properties").path}\" " +
            "is invalid."
        )
    }

    @Test
    fun `Test fallback locale is mandatory`() {
        buildDsl(generateLocaleConfig = true, defaultLocale = null)

        val result = project.withLocales(
            appLocales = listOf(DEFAULT),
            lib1Locales = listOf(),
            lib2Locales = listOf()
        ).executor().expectFailure().run("assembleDebug")

        ScannerSubject.assertThat(result.stderr).contains("No locale is set for unqualified res.")
    }

    @Test
    fun `Test missing resources properties file`() {
        buildDsl(generateLocaleConfig = true, addResourcesProperties = false)

        val result = project.withLocales(
            appLocales = listOf(DEFAULT),
            lib1Locales = listOf(),
            lib2Locales = listOf()
        ).executor().expectFailure().run("assembleDebug")

        ScannerSubject.assertThat(result.stderr).contains("No resources.properties file found.")
    }

    @Test
    fun `Test multiple resources properties files error`() {
        buildDsl(generateLocaleConfig = true)

        project.getSubproject("app").buildFile.appendText("""
            android.sourceSets.main.res.srcDirs = ['src/main/res', 'src/main/res1']
        """.trimIndent())

        val newResourcesPropertiesFile =
            project.getSubproject("app").file("src/main/res1/resources.properties")

        FileUtils.createFile(newResourcesPropertiesFile,
            """
                unqualifiedResLocale=es-ES
            """.trimIndent()
        )

        val result = project.withLocales(
            appLocales = listOf(DEFAULT),
            lib1Locales = listOf(DEFAULT),
            lib2Locales = listOf(DEFAULT)
        ).executor().expectFailure().run("assembleDebug")

        ScannerSubject.assertThat(result.stderr).contains(
            "Multiple resources.properties files found with different unqualifiedResLocale values.")
    }

    @Test
    fun `Test error when using resource configurations`() {
        buildDsl(generateLocaleConfig = true)

        project.getSubproject("app").buildFile.appendText("""
            android.defaultConfig.resourceConfigurations += ["en"]
        """.trimIndent())

        val result = project.executor().expectFailure().run("assembleDebug")

        ScannerSubject.assertThat(result.stderr).contains(
            "You cannot specify languages in resource configurations when " +
            "automatic locale generation is enabled. To use resource configurations, " +
            "please provide the locale config manually: " +
            "https://d.android.com/r/tools/locale-config")
    }

    @Test
    fun `Test that locale config xml is in apk res`() {
        buildDsl(generateLocaleConfig = true)

        project.withLocales(
            appLocales = listOf(DEFAULT),
            lib1Locales = listOf(DEFAULT),
            lib2Locales = listOf(DEFAULT)
        ).execute("assembleDebug")

        ZipFileSubject.assertThat(
            project.getSubproject("app").file("build/outputs/apk/debug/app-debug.apk")) {
            it.contains("res/xml/$LOCALE_CONFIG_FILE_NAME.xml")
        }
    }

    @Test
    fun `Test that locale config xml is in bundle res`() {
        buildDsl(generateLocaleConfig = true)

        project.withLocales(
            appLocales = listOf(DEFAULT),
            lib1Locales = listOf(DEFAULT),
            lib2Locales = listOf(DEFAULT)
        ).execute("bundleDebug")

        ZipFileSubject.assertThat(
            project.getSubproject("app").file("build/outputs/bundle/debug/app-debug.aab")) {
            it.contains("base/res/xml/$LOCALE_CONFIG_FILE_NAME.xml")
        }
    }

    @Test
    fun `Test build flavor res are included`() {
        buildDsl(generateLocaleConfig = true, addFlavor = true)

        addLocaleForVariantDimension(project.getSubproject("app"), JP, "tokyo")
        addLocaleForVariantDimension(project.getSubproject("app"), EN_GB, "london")

        project.withLocales(
            appLocales = listOf(DEFAULT),
            lib1Locales = listOf(ES),
            lib2Locales = listOf(PT)
        ).execute("assembleDebug")

        // london flavor res should not be included
        assertLocaleList("app", "tokyoDebug").containsExactly("en-US", "jp-JP")
        assertLocaleConfig("tokyo/debug").containsExactly("en-US", "es-ES", "pt-BR", "jp-JP")
    }

    @Test
    fun `Test emptyResFoldersAreIgnored`() {
        buildDsl(generateLocaleConfig = true)

        addLocaleForVariantDimension(project.getSubproject("app"), JP, addResFile = false)
        project.withLocales(
            appLocales = listOf(DEFAULT),
            lib1Locales = listOf(ES),
            lib2Locales = listOf(PT)
        ).execute("assembleDebug")

        assertLocaleList("app", "debug").containsExactly("en-US")
        assertLocaleConfig("debug").containsExactly("en-US", "es-ES", "pt-BR")
    }

    @Test
    fun `Test AAR dependency locales not present in app locale config`() {
        buildDsl(generateLocaleConfig = true)

        val libAarDir = File(project.projectDir, "lib1-aar").also { it.mkdirs() }
        File(libAarDir, "build.gradle").writeText(
            """
                configurations.maybeCreate("default")
                artifacts.add("default", file('lib1.aar'))
            """.trimIndent()
        )
        project.withLocales(
            appLocales = listOf(DEFAULT),
            lib1Locales = listOf(ES),
            lib2Locales = listOf(PT)
        ).executor().run(":lib1:assembleDebug")

        project.getSubproject("lib1")
            .getAar("debug") { aar ->
                FileUtils.copyFile(aar.file.toFile(), File(libAarDir, "lib1.aar"))
            }

        TestFileUtils.searchAndReplace(
            project.getSubproject("app").buildFile,
            "implementation project(':lib1')",
            "implementation project(':lib1-aar')",
        )
        TestFileUtils.appendToFile(project.settingsFile, "include ':lib1-aar'")

        project.withLocales(
            appLocales = listOf(DEFAULT),
            lib1Locales = listOf(ES),
            lib2Locales = listOf(PT)
        ).execute("assembleDebug")

        assertLocaleConfig("debug").containsExactly("en-US")
    }
}
