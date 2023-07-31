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

import com.android.tools.idea.wizard.template.impl.activities.common.collapseEmptyActivityTags
import com.android.tools.idea.wizard.template.renderIf

fun androidManifestXml(
  isNewModule: Boolean,
  packageName: String,
  activityClass: String,
  isLauncher: Boolean,
  activityThemeName: String,
): String {
  val activityDescription = activity(
    packageName = packageName,
    activityClass = activityClass,
    themeName = activityThemeName,
    isLauncherActivity = isLauncher || isNewModule,
  )
  return """
<manifest
    xmlns:android ="http://schemas.android.com/apk/res/android"
    xmlns:tools="http://schemas.android.com/tools">
    <uses-feature
        android:name="android.hardware.touchscreen"
        android:required="false" />
    <uses-feature android:name="android.software.leanback"
        android:required="false" />
    <application android:banner="@mipmap/ic_launcher" android:theme="@style/$activityThemeName">
        $activityDescription
    </application>
</manifest>
    """.collapseEmptyActivityTags()
}

private fun activity(
  packageName: String,
  activityClass: String,
  isLauncherActivity: Boolean,
  themeName: String,
) =
  """
<activity
    android:name ="${packageName}.${activityClass}"
    android:exported="$isLauncherActivity"
    ${theme(themeName)}>
        ${intentFilter(isLauncher = isLauncherActivity)}
</activity>
"""

private fun theme(themeName: String) =
  renderIf(themeName.startsWith("@android:style/")) {
    """android:theme = "$themeName""""
  }

private fun intentFilter(isLauncher: Boolean) =
  renderIf(isLauncher) {
    """
    <intent-filter>
        <action android:name="android.intent.action.MAIN" />
        <category android:name="android.intent.category.LAUNCHER" />
        <category android:name="android.intent.category.LEANBACK_LAUNCHER" />
    </intent-filter>
    """
  }
