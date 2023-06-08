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
package com.android.tools.lint.checks

import com.android.SdkConstants.ATTR_INPUT_TYPE
import com.android.SdkConstants.EDIT_TEXT
import com.android.resources.ResourceFolderType
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Incident
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import com.android.tools.lint.detector.api.XmlScanner
import org.w3c.dom.Attr

class WearPasswordInputDetector : WearDetector(), XmlScanner {

  companion object Issues {
    private val IMPLEMENTATION =
      Implementation(WearPasswordInputDetector::class.java, Scope.RESOURCE_FILE_SCOPE)

    @JvmField
    val ISSUE =
      Issue.create(
          id = "WearPasswordInput",
          briefDescription = "Wear OS: Using password input",
          explanation =
            """
          Your app must not ask the user to input password directly on the Wear device.
        """,
          category = Category.USABILITY,
          severity = Severity.ERROR,
          implementation = IMPLEMENTATION,
          enabledByDefault = true
        )
        .addMoreInfo("https://developer.android.com/training/wearables/apps/auth-wear")

    private val PASSWORD_INPUT_TYPES =
      listOf("numberPassword", "textPassword", "textVisiblePassword", "textWebPassword")
  }

  override fun appliesTo(folderType: ResourceFolderType) = ResourceFolderType.LAYOUT == folderType

  override fun getApplicableAttributes() = listOf(ATTR_INPUT_TYPE)

  override fun visitAttribute(context: XmlContext, attribute: Attr) {
    if (
      isWearProject &&
        attribute.ownerElement.tagName == EDIT_TEXT &&
        attribute.value.findAnyOf(PASSWORD_INPUT_TYPES) != null
    ) {
      context.report(
        Incident(ISSUE, context.getLocation(attribute), "Don't ask Wear OS users for a password")
      )
    }
  }
}
