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

package com.android.tools.idea.wizard.template.impl.activities.genAiActivity

import com.android.tools.idea.wizard.template.Category
import com.android.tools.idea.wizard.template.Constraint.CLASS
import com.android.tools.idea.wizard.template.Constraint.NONEMPTY
import com.android.tools.idea.wizard.template.Constraint.UNIQUE
import com.android.tools.idea.wizard.template.FormFactor
import com.android.tools.idea.wizard.template.LanguageWidget
import com.android.tools.idea.wizard.template.ModuleTemplateData
import com.android.tools.idea.wizard.template.PackageNameWidget
import com.android.tools.idea.wizard.template.TemplateConstraint
import com.android.tools.idea.wizard.template.TemplateData
import com.android.tools.idea.wizard.template.TextFieldWidget
import com.android.tools.idea.wizard.template.UrlLinkWidget
import com.android.tools.idea.wizard.template.WizardUiContext
import com.android.tools.idea.wizard.template.impl.defaultPackageNameParameter
import com.android.tools.idea.wizard.template.stringParameter
import com.android.tools.idea.wizard.template.template
import java.io.File

val genAiActivityTemplate
  get() = template {
    name = "Gemini API Starter"
    description = "Creates a starter app for using the Gemini API"
    minApi = 26
    constraints =
      listOf(
        TemplateConstraint.AndroidX,
        TemplateConstraint.Kotlin,
        TemplateConstraint.Material3,
        TemplateConstraint.Compose
      )

    // TODO(b/312755227): Should probably be Category.Google, but needs to be Compose to use
    // Compose.
    // Doesn't actually matter since it doesn't appear in the menu.
    category = Category.Compose
    formFactor = FormFactor.Mobile
    screens = listOf(WizardUiContext.NewProject, WizardUiContext.NewProjectExtraDetail)

    val activityClass = stringParameter {
      name = "Activity Name"
      default = "MainActivity"
      visible = { false }
      help = "The name of the activity class to create"
      constraints = listOf(CLASS, UNIQUE, NONEMPTY)
    }

    val packageName = defaultPackageNameParameter

    val apiKey = stringParameter {
      name = "API Key"
      default = ""
      help = "Add API key here to be used as a build config variable"
      constraints = listOf(NONEMPTY)
    }

    widgets(
      TextFieldWidget(activityClass),
      PackageNameWidget(packageName),
      TextFieldWidget(apiKey),
      UrlLinkWidget(
        "Generate API key with Google AI Studio",
        "https://makersuite.google.com/app/apikey"
      ),
      LanguageWidget()
    )

    thumb { File("genai-activity").resolve("template_genai_activity.png") }

    recipe = { data: TemplateData ->
      genAiActivityRecipe(
        data as ModuleTemplateData,
        activityClass.value,
        packageName.value,
        apiKey.value
      )
    }
  }
