/*
 * Copyright (C) 2021 The Android Open Source Project
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

package com.android.tools.idea.wizard.template.impl.activities.composeWearActivity

import com.android.tools.idea.wizard.template.ModuleTemplateData
import com.android.tools.idea.wizard.template.RecipeExecutor
import com.android.tools.idea.wizard.template.impl.activities.common.COMPOSE_BOM_VERSION
import com.android.tools.idea.wizard.template.impl.activities.common.COMPOSE_KOTLIN_COMPILER_VERSION
import com.android.tools.idea.wizard.template.impl.activities.common.addAllKotlinDependencies
import com.android.tools.idea.wizard.template.impl.activities.common.generateManifest
import com.android.tools.idea.wizard.template.impl.activities.composeWearActivity.complication.complicationServiceKt
import com.android.tools.idea.wizard.template.impl.activities.composeWearActivity.res.values.complicationStringsXml
import com.android.tools.idea.wizard.template.impl.activities.composeWearActivity.res.values.stringsXml
import com.android.tools.idea.wizard.template.impl.activities.composeWearActivity.res.values.stylesXml
import com.android.tools.idea.wizard.template.impl.activities.composeWearActivity.res.values.tileStringsXml
import com.android.tools.idea.wizard.template.impl.activities.composeWearActivity.src.app_package.mainActivityKt
import com.android.tools.idea.wizard.template.impl.activities.composeWearActivity.src.app_package.theme.themeKt
import com.android.tools.idea.wizard.template.impl.activities.composeWearActivity.src.app_package.tile.tileServiceKt
import java.io.File
import com.android.tools.idea.wizard.template.impl.activities.composeWearActivity.res.values_round.stringsXml as stringsRoundXml

private fun RecipeExecutor.commonComposeRecipe(
    moduleData: ModuleTemplateData,
    activityClass: String,
    packageName: String,
    isLauncher: Boolean,
    greeting: String,
    wearAppName: String,
    defaultPreview: String,
    composeBomVersion: String = COMPOSE_BOM_VERSION
) {
    addAllKotlinDependencies(moduleData)

    // Add Compose dependencies, using the BOM to set versions
    addPlatformDependency(mavenCoordinate = "androidx.compose:compose-bom:$composeBomVersion")
    addPlatformDependency(mavenCoordinate = "androidx.compose:compose-bom:$composeBomVersion", "androidTestImplementation")

    addDependency(mavenCoordinate = "androidx.compose.ui:ui")
    addDependency(mavenCoordinate = "androidx.compose.ui:ui-tooling-preview")
    addDependency(mavenCoordinate = "androidx.compose.ui:ui-tooling", configuration = "debugImplementation")
    addDependency(mavenCoordinate = "androidx.compose.ui:ui-test-manifest", configuration = "debugImplementation")
    addDependency(mavenCoordinate = "androidx.compose.ui:ui-test-junit4", configuration = "androidTestImplementation")

    // Add Compose Wear dependencies; the Compose BOM doesn't include Wear.
    val wearComposeVersionVarName =
        getDependencyVarName("androidx.wear.compose:compose-material", "wear_compose_version")
    val wearComposeVersion = getExtVar(wearComposeVersionVarName, "1.2.1")
    addDependency(mavenCoordinate = "androidx.wear.compose:compose-material:$wearComposeVersion")
    addDependency(mavenCoordinate = "androidx.wear.compose:compose-foundation:$wearComposeVersion")

    addDependency(mavenCoordinate = "androidx.activity:activity-compose:1.7.2")

    addDependency(mavenCoordinate = "androidx.core:core-splashscreen:1.0.1")

    val splashScreenTheme = "${activityClass}Theme.Starting"
    generateManifest(
        moduleData = moduleData,
        activityClass = "presentation.${activityClass}",
        activityThemeName = splashScreenTheme,
        packageName = packageName,
        isLauncher = isLauncher,
        hasNoActionBar = true,
        generateActivityTitle = false,
        taskAffinity = "",
    )

    val (_, srcOut, resOut, manifestOut) = moduleData
    mergeXml(androidManifestWearOsAdditions(), manifestOut.resolve("AndroidManifest.xml"))
    mergeXml(
        stringsXml(
            activityClass,
            moduleData.isNewModule
        ), resOut.resolve("values/strings.xml")
    )
    mergeXml(stringsRoundXml(), resOut.resolve("values-round/strings.xml"))

    val themeName = "${moduleData.themesData.appName}Theme"
    save(
        mainActivityKt(
            // when a new project is being created, there will not be an applicationPackage
            moduleData.projectTemplateData.applicationPackage ?: packageName,
            activityClass,
            defaultPreview,
            greeting,
            wearAppName,
            packageName,
            themeName
        ),
        srcOut.resolve("presentation/${activityClass}.kt")
    )
    val uiThemeFolder = "presentation/theme"
    save(themeKt(packageName, themeName), srcOut.resolve("$uiThemeFolder/Theme.kt"))

    mergeXml(
        stylesXml(splashScreenTheme),
        resOut.resolve("values/styles.xml")
    )

    mergeXml(
        lintXml(),
        moduleData.rootDir.resolve("lint.xml")
    )

    requireJavaVersion("1.8", true)
    setBuildFeature("compose", true)
    // Note: kotlinCompilerVersion default is declared in TaskManager.COMPOSE_KOTLIN_COMPILER_VERSION
    setComposeOptions(kotlinCompilerExtensionVersion = COMPOSE_KOTLIN_COMPILER_VERSION)
}

fun RecipeExecutor.composeWearActivityRecipe(
    moduleData: ModuleTemplateData,
    activityClass: String,
    packageName: String,
    isLauncher: Boolean,
    greeting: String,
    wearAppName: String,
    defaultPreview: String
) {
    commonComposeRecipe(
        moduleData,
        activityClass,
        packageName,
        isLauncher,
        greeting,
        wearAppName,
        defaultPreview
    )

    val (_, srcOut, resOut, _) = moduleData
    open(srcOut.resolve("${activityClass}.kt"))

    copy(File("wear-app").resolve("drawable/splash_icon.xml"), resOut.resolve("drawable/splash_icon.xml"))
}

fun RecipeExecutor.composeWearActivityWithTileAndComplicationRecipe(
    moduleData: ModuleTemplateData,
    activityClass: String,
    tileServiceClass: String,
    tilePreviewName: String,
    complicationServiceClass: String,
    packageName: String,
    isLauncher: Boolean,
    greeting: String,
    wearAppName: String,
    defaultPreview: String
) {
    commonComposeRecipe(
        moduleData,
        activityClass,
        packageName,
        isLauncher,
        greeting,
        wearAppName,
        defaultPreview
    )

    val wearTilesVersionVarName =
        getDependencyVarName("androidx.wear.tiles:tiles", "wear_tiles_version")
    val wearTilesVersion = getExtVar(wearTilesVersionVarName, "1.1.0")
    addDependency(mavenCoordinate = "androidx.wear.tiles:tiles:$wearTilesVersion")
    addDependency(mavenCoordinate = "androidx.wear.tiles:tiles-material:$wearTilesVersion")

    val horologistVersionVarName =
        getDependencyVarName(
            "com.google.android.horologist:horologist-compose-tools",
            "horologist_version"
        )
    val horologistVersion = getExtVar(horologistVersionVarName, "0.4.8")
    addDependency(mavenCoordinate = "com.google.android.horologist:horologist-compose-tools:$horologistVersion")
    addDependency(mavenCoordinate = "com.google.android.horologist:horologist-tiles:$horologistVersion")

    addDependency(mavenCoordinate = "androidx.wear.watchface:watchface-complications-data-source-ktx:1.1.1")

    val (_, srcOut, resOut, manifestOut) = moduleData
    save(
        tileServiceKt(
            tileServiceClass,
            tilePreviewName,
            packageName,
        ),
        srcOut.resolve("tile/${tileServiceClass}.kt")
    )
    mergeXml(
        tileStringsXml(), resOut.resolve("values/strings.xml")
    )
    mergeXml(
        tileServiceManifestXml(tileServiceClass, packageName),
        manifestOut.resolve("AndroidManifest.xml")
    )
    copy(File("wear-app").resolve("drawable"), resOut.resolve("drawable"))
    copy(File("wear-app").resolve("drawable-round"), resOut.resolve("drawable-round"))

    save(
        complicationServiceKt(
            complicationServiceClass,
            packageName,
        ),
        srcOut.resolve("complication/${complicationServiceClass}.kt")
    )
    mergeXml(
        complicationStringsXml(), resOut.resolve("values/strings.xml")
    )
    mergeXml(
        complicationServiceManifestXml(complicationServiceClass, packageName),
        manifestOut.resolve("AndroidManifest.xml")
    )
}
