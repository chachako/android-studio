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

package com.android.tools.idea.wizard.template.impl.activities.googleWalletActivity

import com.android.tools.idea.wizard.template.Language
import com.android.tools.idea.wizard.template.ModuleTemplateData
import com.android.tools.idea.wizard.template.RecipeExecutor
import com.android.tools.idea.wizard.template.activityToLayout
import com.android.tools.idea.wizard.template.impl.activities.common.addAllKotlinDependencies
import com.android.tools.idea.wizard.template.impl.activities.common.addViewBindingSupport
import com.android.tools.idea.wizard.template.impl.activities.googleWalletActivity.app_package.samplePassJava
import com.android.tools.idea.wizard.template.impl.activities.googleWalletActivity.app_package.samplePassKt
import com.android.tools.idea.wizard.template.impl.activities.googleWalletActivity.app_package.walletActivityJava
import com.android.tools.idea.wizard.template.impl.activities.googleWalletActivity.app_package.walletActivityKt
import com.android.tools.idea.wizard.template.impl.activities.googleWalletActivity.res.layout.activityWalletXml
import com.android.tools.idea.wizard.template.impl.activities.googleWalletActivity.res.values.stringsXml
import java.io.File

fun RecipeExecutor.googleWalletActivityRecipe(
  moduleData: ModuleTemplateData,
  activityClass: String,
  layoutName: String,
  isLauncher: Boolean,
  packageName: String
) {
  val (projectData, srcOut, resOut, manifestOut) = moduleData

  // Dependencies and config
  addAllKotlinDependencies(moduleData)
  addViewBindingSupport(moduleData.viewBindingSupport, true)
  addDependency("com.google.android.gms:play-services-pay:+")
  addDependency("com.android.support:appcompat-v7:${moduleData.apis.appCompatVersion}.+")
  addDependency("androidx.lifecycle:lifecycle-viewmodel-ktx:+")
  addDependency("androidx.lifecycle:lifecycle-livedata-ktx:+")
  addDependency("androidx.constraintlayout:constraintlayout:+")
  if (projectData.language == Language.Kotlin) {
    addDependency("androidx.activity:activity-ktx:+")
  }

  // Generate either Java or Kotlin files.
  val ktOrJavaExt = projectData.language.extension

  // Create manifest
  val simpleName = activityToLayout(activityClass)
  mergeXml(
    androidManifestXml(
        activityClass, isLauncher, moduleData.isLibrary, packageName, simpleName, moduleData.isNewModule, moduleData.themesData),
    manifestOut.resolve("AndroidManifest.xml"))

  // Copy static resources
  val resLocation: File = if (moduleData.isDynamic) moduleData.baseFeature!!.resDir else resOut
  copy(File("google-wallet-activity"), resLocation)

  // Generated resources
  mergeXml(stringsXml(activityClass, simpleName), resLocation.resolve("values/strings.xml"))
  save(activityWalletXml(activityClass, packageName), resLocation.resolve("layout/$layoutName.xml"))

  //// Generate Constants class
  //val ktOrJavaExt = projectData.language.extension
  val samplePass = when (projectData.language) {
    Language.Java -> samplePassJava(packageName)
    Language.Kotlin -> samplePassKt(packageName)
  }
  val samplePassOut = srcOut.resolve("SamplePass.$ktOrJavaExt")
  save(samplePass, samplePassOut)


  // Add activity class
  val isViewBindingSupported = moduleData.viewBindingSupport.isViewBindingSupported()
  val checkoutActivity = when (projectData.language) {
    Language.Java -> walletActivityJava(
      activityClass = activityClass,
      layoutName = layoutName,
      packageName = packageName,
      applicationPackage = projectData.applicationPackage,
      isViewBindingSupported = isViewBindingSupported)
    Language.Kotlin -> walletActivityKt(
      activityClass = activityClass,
      layoutName = layoutName,
      packageName = packageName,
      applicationPackage = projectData.applicationPackage,
      isViewBindingSupported = isViewBindingSupported)
  }

  save(checkoutActivity, srcOut.resolve("$activityClass.$ktOrJavaExt"))
  open(srcOut.resolve("$activityClass.$ktOrJavaExt"))
}
