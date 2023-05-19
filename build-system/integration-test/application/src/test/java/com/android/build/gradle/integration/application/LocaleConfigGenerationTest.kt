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

import com.android.SdkConstants
import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.android.build.gradle.integration.common.fixture.GradleTestProject.Companion.builder
import com.android.build.gradle.integration.common.fixture.app.MinimalSubProject
import com.android.build.gradle.integration.common.fixture.app.MultiModuleTestProject
import com.android.build.gradle.integration.common.truth.ScannerSubject
import com.android.build.gradle.integration.common.truth.TruthHelper
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
private val ES = LocaleFolderToStandard("es-rES", "es-ES")
private val ZH_HANT_TW = LocaleFolderToStandard("b+zh+Hant+TW", "zh-Hant-TW")
private val ZH_HANT = LocaleFolderToStandard("b+zh+Hant", "zh-Hant")
private val EN_GB = LocaleFolderToStandard("en-rGB", "en-GB")
private val RU = LocaleFolderToStandard("ru-rRU", "ru-RU")
private val PT = LocaleFolderToStandard("pt-rBR", "pt-BR")
private val CHR_US = LocaleFolderToStandard("b+chr+US", "chr-US")
private val CHR = LocaleFolderToStandard("b+chr", "chr")
private val ES_419 = LocaleFolderToStandard("b+es+419", "es-419")
private val DE = LocaleFolderToStandard("de", "de")
private val DE_DE = LocaleFolderToStandard("de-rDE", "de-DE")

private data class LocaleFolderToStandard(
    val folderName: String,
    val standardName: String
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
                        <locale android:name="zh-Hant-TW"/>
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
        locale: LocaleFolderToStandard?,
        variantDimension: String = "main",
        addResFile: Boolean = true
    ) {
        val file =
            if (locale == DEFAULT) {
                project.file("src/${variantDimension}/res/values/strings.xml")
            } else {
                project.file(
                    "src/${variantDimension}/res/values-${locale.folderName}/strings.xml")
            }
        val text = locale?.standardName ?: "en-US"

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
        appLocales: List<LocaleFolderToStandard?>,
        lib1Locales: List<LocaleFolderToStandard?>,
        lib2Locales: List<LocaleFolderToStandard?>
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
        addResourcesProperties: Boolean = true,
        pseudoLocalesEnabled: Boolean = false
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
                        cherokee {
                            dimension "language"
                        }
                        london {
                            dimension "language"
                        }
                }
                """.trimIndent()
        }
        if (pseudoLocalesEnabled) {
            dslString += """
                android.buildTypes.debug.pseudoLocalesEnabled true
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
                project.getSubproject(subproject).file("$localeListPath/$variant/extract" +
                        variant.replaceFirstChar { it.uppercase() } + "SupportedLocales/supported_locales.txt"))
        )
    }

    private fun assertLocaleConfig(variant: String): IterableSubject {
        return Truth.assertThat(
            localeListFromXml(
                project.getSubproject("app").file("$localeConfigPath/$variant/xml/$LOCALE_CONFIG_FILE_NAME.xml"))
        )
    }

    private fun validateLocalesInLocaleConfigAndApk(
        expectedLocaleList: List<String>,
        defaultLocale: String? = "en-US"
    ) {
        assertLocaleConfig("debug").isEqualTo(expectedLocaleList)

        // The APK locale list will return the default locale as "--_--" in this method
        val apkLocaleList = expectedLocaleList.map { if (it == defaultLocale) "--_--" else it }
        TruthHelper.assertThat(
            project.getSubproject("app").getApk(GradleTestProject.ApkType.DEBUG))
            .locales()
            .containsExactlyElementsIn(apkLocaleList)
    }

    private fun getExpectedLocaleList(
        nonDefaultLocales: List<String>,
        defaultLocale: String
    ): List<String> {
        val combinedList = mutableListOf("*$defaultLocale")
        combinedList.addAll(nonDefaultLocales)
        return combinedList
    }

    @Test
    fun `Test with default behavior locale config is not generated`() {
        // no DSL

        val result = project.withLocales(
            appLocales = listOf(DEFAULT, ES, ZH_HANT_TW, EN_GB),
            lib1Locales = listOf(DEFAULT, ES, RU),
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
            .file("$mergedManifestPath/debug/processDebugMainManifest/AndroidManifest.xml"))
            .doesNotContain("android:localeConfig")
    }

    @Test
    fun `Test with feature off user locale config is not changed and locale config is not generated`() {
        buildDsl(generateLocaleConfig = false)

        val result = project.withLocales(
            appLocales = listOf(DEFAULT, ES, ZH_HANT_TW, EN_GB),
            lib1Locales = listOf(DEFAULT, ES, RU),
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
            .file("$mergedManifestPath/debug/processDebugMainManifest/AndroidManifest.xml")
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
            appLocales = listOf(DEFAULT, ES, ZH_HANT_TW, EN_GB),
            lib1Locales = listOf(DEFAULT, ES, RU),
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
            .file("$mergedManifestPath/debug/processDebugMainManifest/AndroidManifest.xml")
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
            appLocales = listOf(DEFAULT, ES, ZH_HANT_TW, EN_GB),
            lib1Locales = listOf(RU),
            lib2Locales = listOf(PT)
        ).execute("assembleDebug")

        assertLocaleList("app", "debug").isEqualTo(
            getExpectedLocaleList(listOf("b+zh+Hant+TW", "en-rGB", "es-rES"), "de-DE"))
        assertLocaleList("lib1", "debug").isEqualTo(listOf("ru-rRU"))
        assertLocaleList("lib2", "debug").isEqualTo(listOf("pt-rBR"))
        // Final xml adds default locale (de-DE)
        assertLocaleConfig("debug")
            .isEqualTo(listOf("de-DE", "zh-Hant-TW", "en-GB", "es-ES", "ru-RU", "pt-BR"))
    }

    @Test
    fun `Test locales are not duplicated`() {
        buildDsl(generateLocaleConfig = true)

        project.withLocales(
            appLocales = listOf(DEFAULT, ES, ZH_HANT_TW, EN_GB),
            lib1Locales = listOf(ES, RU), // Duplicate ES
            lib2Locales = listOf(PT, EN_GB) // Duplicate EN_GB
        ).execute("assembleDebug")

        assertLocaleList("app", "debug").isEqualTo(
            getExpectedLocaleList(listOf("b+zh+Hant+TW", "en-rGB", "es-rES"), "en-US"))
        assertLocaleList("lib1", "debug").isEqualTo(listOf("es-rES", "ru-rRU"))
        assertLocaleList("lib2", "debug").isEqualTo(listOf("en-rGB", "pt-rBR"))
        assertLocaleConfig("debug")
            .isEqualTo(listOf("en-US", "zh-Hant-TW", "en-GB", "es-ES", "ru-RU", "pt-BR"))
    }

    @Test
    fun `Test user locale config produces conflict`() {
        buildDsl(generateLocaleConfig = true)

        val result = project.withLocales(
            appLocales = listOf(DEFAULT, ES, ZH_HANT_TW, EN_GB),
            lib1Locales = listOf(DEFAULT, ES, RU),
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
    fun `Test locales filtered by resource configurations`() {
        buildDsl(generateLocaleConfig = true)

        // Validate non-locale resource configuration does not filter locales
        project.getSubproject("app").buildFile.appendText("""
            android.defaultConfig.resourceConfigurations += ["mdpi"]
        """.trimIndent())

        project.withLocales(
            appLocales = listOf(DEFAULT, ES, ZH_HANT_TW, EN_GB, DE),
            lib1Locales = listOf(RU),
            lib2Locales = listOf(PT)
        ).execute("assembleDebug")

        validateLocalesInLocaleConfigAndApk(
            listOf("en-US", "zh-Hant-TW", "de", "en-GB", "es-ES", "ru-RU", "pt-BR")
        )

        // Validate resConfig locale without matching any strings locale
        TestFileUtils.searchAndReplace(project.getSubproject("app").buildFile,
            "android.defaultConfig.resourceConfigurations += [\"mdpi\"]",
            "android.defaultConfig.resourceConfigurations += [\"fr\"]")

        project.withLocales(
            appLocales = listOf(DEFAULT, ES, ZH_HANT_TW, EN_GB, DE),
            lib1Locales = listOf(RU, DEFAULT),
            lib2Locales = listOf(PT)
        ).execute("assembleDebug")

        // The default locale should not be removed
        validateLocalesInLocaleConfigAndApk(listOf("en-US"))

        // "de", "es-ES", "zh-Hant", "chr", "es-419" should match
        TestFileUtils.searchAndReplace(project.getSubproject("app").buildFile,
            "android.defaultConfig.resourceConfigurations += [\"fr\"]",
            "android.defaultConfig.resourceConfigurations += " +
                "[\"mdpi\", \"de\", \"en\", \"b+zh+Hant\", \"es-rES\", \"b+chr\", \"b+es+419\"]"
        )

        project.withLocales(
            appLocales = listOf(DEFAULT, ES, ZH_HANT_TW, EN_GB, DE),
            lib1Locales = listOf(RU, DEFAULT, CHR),
            lib2Locales = listOf(PT, ZH_HANT, ES_419)
        ).execute("assembleDebug")

        validateLocalesInLocaleConfigAndApk(
            listOf("en-US", "de", "es-ES", "chr", "es-419", "zh-Hant"))
    }

    @Test
    fun `Test resource configuration filtering with different default locale`() {
        buildDsl(generateLocaleConfig = true, defaultLocale = RU.standardName)

        project.getSubproject("app").buildFile.appendText("""
            android.defaultConfig.resourceConfigurations += ["b+zh+Hant+TW", "en", "es", "de-rDE"]
        """.trimIndent())

        project.withLocales(
            appLocales = listOf(DEFAULT, ZH_HANT, ZH_HANT_TW),
            lib1Locales = listOf(EN_GB, DE_DE),
            lib2Locales = listOf(ES_419, DE)
        ).execute("assembleDebug")

        // Having "b+zh+Hant+TW" in resConfigs should match both "zh-Hant" and "zh-Hant-TW"
        // Having "de-rDE" in resConfigs should match both "de" and "de-DE"
        validateLocalesInLocaleConfigAndApk(
            listOf("ru-RU", "zh-Hant", "zh-Hant-TW", "de-DE", "de"), defaultLocale = RU.standardName)
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

        addLocaleForVariantDimension(project.getSubproject("app"), CHR_US, "cherokee")
        addLocaleForVariantDimension(project.getSubproject("app"), EN_GB, "london")

        project.withLocales(
            appLocales = listOf(DEFAULT),
            lib1Locales = listOf(ES),
            lib2Locales = listOf(PT)
        ).execute("assembleDebug")

        // london flavor res should not be included
        assertLocaleList("app", "cherokeeDebug")
            .isEqualTo(getExpectedLocaleList(listOf("b+chr+US"), "en-US"))
        assertLocaleConfig("cherokee/debug").isEqualTo(listOf("en-US", "chr-US", "es-ES", "pt-BR"))
    }

    @Test
    fun `Test emptyResFoldersAreIgnored`() {
        buildDsl(generateLocaleConfig = true)

        addLocaleForVariantDimension(project.getSubproject("app"), CHR_US, addResFile = false)
        project.withLocales(
            appLocales = listOf(DEFAULT),
            lib1Locales = listOf(ES),
            lib2Locales = listOf(PT)
        ).execute("assembleDebug")

        assertLocaleList("app", "debug")
            .isEqualTo(getExpectedLocaleList(emptyList(), "en-US"))
        assertLocaleConfig("debug").isEqualTo(listOf("en-US", "es-ES", "pt-BR"))
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

        assertLocaleConfig("debug").isEqualTo(listOf("en-US"))
    }

    @Test
    fun `Test pseudolocales are present`() {
        buildDsl(generateLocaleConfig = true, pseudoLocalesEnabled = true)

        project.withLocales(
            appLocales = listOf(DEFAULT),
            lib1Locales = listOf(),
            lib2Locales = listOf()
        ).execute("assembleDebug")

        assertLocaleList("app", "debug").isEqualTo(
        getExpectedLocaleList(listOf(SdkConstants.EN_XA, SdkConstants.AR_XB), "en-US"))
        assertLocaleList("lib1", "debug").isEmpty()
        assertLocaleList("lib2", "debug").isEmpty()
        validateLocalesInLocaleConfigAndApk(listOf("en-US", "en-XA", "ar-XB"))
    }
}
