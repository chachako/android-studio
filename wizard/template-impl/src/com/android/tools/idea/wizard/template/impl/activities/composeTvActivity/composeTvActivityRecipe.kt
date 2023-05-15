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
package com.android.tools.idea.wizard.template.impl.activities.composeTvActivity

import com.android.tools.idea.wizard.template.ModuleTemplateData
import com.android.tools.idea.wizard.template.RecipeExecutor
import com.android.tools.idea.wizard.template.impl.activities.common.COMPOSE_BOM_VERSION
import com.android.tools.idea.wizard.template.impl.activities.common.COMPOSE_KOTLIN_COMPILER_VERSION
import com.android.tools.idea.wizard.template.impl.activities.common.addAllKotlinDependencies
import com.android.tools.idea.wizard.template.impl.activities.common.generateManifestStrings
import com.android.tools.idea.wizard.template.impl.activities.composeTvActivity.res.values.themesXml
import com.android.tools.idea.wizard.template.impl.activities.composeTvActivity.src.app_package.mainActivityKt
import com.android.tools.idea.wizard.template.impl.activities.composeTvActivity.src.app_package.ui.colorKt
import com.android.tools.idea.wizard.template.impl.activities.composeTvActivity.src.app_package.ui.themeKt
import com.android.tools.idea.wizard.template.impl.activities.composeTvActivity.src.app_package.ui.typeKt
import java.io.File

fun RecipeExecutor.composeTvActivityRecipe(
  moduleData: ModuleTemplateData,
  activityClass: String,
  packageName: String,
  isLauncher: Boolean,
  greeting: String,
  defaultPreview: String,
  composeBomVersion: String = COMPOSE_BOM_VERSION,
) {
  val (_, srcOut, resOut, _) = moduleData

  addAllKotlinDependencies(moduleData)

  // Add Compose dependencies, using the BOM to set versions
  addPlatformDependency(mavenCoordinate = "androidx.compose:compose-bom:$composeBomVersion")
  addPlatformDependency(mavenCoordinate = "androidx.compose:compose-bom:$composeBomVersion", "androidTestImplementation")

  addDependency(mavenCoordinate = "androidx.compose.ui:ui-tooling-preview")
  addDependency(mavenCoordinate = "androidx.compose.ui:ui-tooling", configuration = "debugImplementation")
  addDependency(mavenCoordinate = "androidx.compose.ui:ui-test-manifest", configuration = "debugImplementation")
  addDependency(mavenCoordinate = "androidx.compose.ui:ui-test-junit4", configuration = "androidTestImplementation")

  // Add Compose for TV dependencies; the Compose BOM does not include Compose for TV.
  addDependency(mavenCoordinate = "androidx.tv:tv-foundation:+")
  addDependency(mavenCoordinate = "androidx.tv:tv-material:+")

  addDependency(mavenCoordinate = "androidx.lifecycle:lifecycle-runtime-ktx:+")
  addDependency(mavenCoordinate = "androidx.activity:activity-compose:+")

  generateLeanbackEnabledManifest(
    moduleData = moduleData,
    activityClass = activityClass,
    activityThemeName = moduleData.themesData.main.name,
    packageName = packageName,
    isLauncher = isLauncher,
    generateActivityTitle = true
  )

  mergeXml(themesXml(themeName = moduleData.themesData.main.name), resOut.resolve("values/themes.xml"))

  val themeName = "${moduleData.themesData.appName}Theme"
  val mainActivity = "${activityClass}.kt"
  save(
    mainActivityKt(
      activityClass = activityClass,
      defaultPreview = defaultPreview,
      greeting = greeting,
      packageName = packageName,
      themeName = themeName,
    ),
    srcOut.resolve(mainActivity)
  )

  val uiThemeFolder = "ui/theme"
  save(colorKt(packageName), srcOut.resolve("$uiThemeFolder/Color.kt"))
  save(typeKt(packageName), srcOut.resolve("$uiThemeFolder/Type.kt"))
  save(themeKt(packageName, themeName), srcOut.resolve("$uiThemeFolder/Theme.kt"))

  requireJavaVersion("1.8", true)
  setBuildFeature("compose", true)
  setComposeOptions(kotlinCompilerExtensionVersion = COMPOSE_KOTLIN_COMPILER_VERSION)

  open(srcOut.resolve(mainActivity))
}

private fun RecipeExecutor.generateLeanbackEnabledManifest(
  moduleData: ModuleTemplateData,
  activityClass: String,
  packageName: String,
  isLauncher: Boolean,
  activityThemeName: String = moduleData.themesData.noActionBar.name,
  isNewModule: Boolean = moduleData.isNewModule,
  manifestOut: File = moduleData.manifestDir,
  baseFeatureResOut: File = moduleData.baseFeature?.resDir ?: moduleData.resDir,
  generateActivityTitle: Boolean,
) {
  generateManifestStrings(activityClass, baseFeatureResOut, isNewModule, generateActivityTitle)

  val manifest = androidManifestXml(
    activityClass = activityClass,
    packageName = packageName,
    isLauncher = isLauncher,
    activityThemeName = activityThemeName,
    isNewModule = isNewModule,
  )
  mergeXml(manifest, manifestOut.resolve("AndroidManifest.xml"))
}
