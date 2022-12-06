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

import com.android.tools.idea.wizard.template.Category
import com.android.tools.idea.wizard.template.CheckBoxWidget
import com.android.tools.idea.wizard.template.Constraint
import com.android.tools.idea.wizard.template.Constraint.CLASS
import com.android.tools.idea.wizard.template.Constraint.NONEMPTY
import com.android.tools.idea.wizard.template.Constraint.UNIQUE
import com.android.tools.idea.wizard.template.FormFactor
import com.android.tools.idea.wizard.template.ModuleTemplateData
import com.android.tools.idea.wizard.template.PackageNameWidget
import com.android.tools.idea.wizard.template.TemplateConstraint
import com.android.tools.idea.wizard.template.TemplateData
import com.android.tools.idea.wizard.template.TextFieldWidget
import com.android.tools.idea.wizard.template.WizardUiContext
import com.android.tools.idea.wizard.template.booleanParameter
import com.android.tools.idea.wizard.template.impl.defaultPackageNameParameter
import com.android.tools.idea.wizard.template.stringParameter
import com.android.tools.idea.wizard.template.template
import java.io.File

val composeWearActivityTemplate
    get() = template {
        name = "Empty Wear App"
        minApi = 25
        description = "Creates an empty app using Compose for Wear OS"

        constraints = listOf(
                TemplateConstraint.AndroidX,
                TemplateConstraint.Kotlin,
                TemplateConstraint.Compose)
        category = Category.Wear
        formFactor = FormFactor.Wear
        screens =
                listOf(WizardUiContext.MenuEntry,
                        WizardUiContext.NewProject,
                        WizardUiContext.NewModule)

        val activityClass = stringParameter {
            name = "Activity Name"
            default = "MainActivity"
            help = "The name of the activity class to create"
            constraints = listOf(CLASS, UNIQUE, NONEMPTY)
        }

        val packageName = defaultPackageNameParameter

        val isLauncher = booleanParameter {
            name = "Launcher Activity"
            default = false
            help =
                    "If true, this activity will have a CATEGORY_LAUNCHER intent filter, making it visible in the launcher"
        }

        val greeting = stringParameter {
            name = "Greeting function name"
            default = "Greeting"
            help = "Used for deduplication"
            visible = { false }
            constraints = listOf(UNIQUE, Constraint.KOTLIN_FUNCTION)
        }

        val wearAppName = stringParameter {
            name = "WearApp function name"
            default = "WearApp"
            help = "Used for deduplication"
            visible = { false }
            constraints = listOf(UNIQUE, Constraint.KOTLIN_FUNCTION)
        }

        val defaultPreview = stringParameter {
            name = "Default Preview function name"
            default = "DefaultPreview"
            help = "Used for deduplication"
            visible = { false }
            constraints = listOf(UNIQUE, Constraint.KOTLIN_FUNCTION)
        }

        widgets(
                TextFieldWidget(activityClass),
                PackageNameWidget(packageName),
                CheckBoxWidget(isLauncher),
                // Invisible widgets to pass data
                TextFieldWidget(greeting),
                TextFieldWidget(defaultPreview),
        )

        thumb { File("compose-wear-activity").resolve("templates-wear-app.png") }

        recipe = { data: TemplateData ->
            composeWearActivityRecipe(
                    data as ModuleTemplateData,
                    activityClass.value,
                    packageName.value,
                    isLauncher.value,
                    greeting.value,
                    wearAppName.value,
                    defaultPreview.value
            )
        }
    }

val composeWearActivityWithTileAndComplicationTemplate
    get() = template {
        name = "Empty Wear App With Tile And Complication"
        minApi = 25
        description = "Creates an empty app using Compose for Wear OS, including a Tile and Complication"

        constraints = listOf(
            TemplateConstraint.AndroidX,
            TemplateConstraint.Kotlin,
            TemplateConstraint.Compose)
        category = Category.Wear
        formFactor = FormFactor.Wear
        screens =
            listOf(WizardUiContext.MenuEntry,
                   WizardUiContext.NewProject,
                   WizardUiContext.NewModule)

        val activityClass = stringParameter {
            name = "Activity Name"
            default = "MainActivity"
            help = "The name of the activity class to create"
            constraints = listOf(CLASS, UNIQUE, NONEMPTY)
        }
        val tileServiceClass = stringParameter {
            name = "Tile Service Name"
            default = "MainTileService"
            help = "The name of the tile service class to create"
            constraints = listOf(CLASS, UNIQUE, NONEMPTY)
        }
        val complciationServiceClass = stringParameter {
            name = "Tile Service Name"
            default = "MainComplicationService"
            help = "The name of the complication service class to create"
            constraints = listOf(CLASS, UNIQUE, NONEMPTY)
        }

        val packageName = defaultPackageNameParameter

        val isLauncher = booleanParameter {
            name = "Launcher Activity"
            default = false
            help =
                "If true, this activity will have a CATEGORY_LAUNCHER intent filter, making it visible in the launcher"
        }

        val greeting = stringParameter {
            name = "Greeting function name"
            default = "Greeting"
            help = "Used for deduplication"
            visible = { false }
            constraints = listOf(UNIQUE, Constraint.KOTLIN_FUNCTION)
        }

        val wearAppName = stringParameter {
            name = "WearApp function name"
            default = "WearApp"
            help = "Used for deduplication"
            visible = { false }
            constraints = listOf(UNIQUE, Constraint.KOTLIN_FUNCTION)
        }

        val defaultPreview = stringParameter {
            name = "Default Preview function name"
            default = "DefaultPreview"
            help = "Used for deduplication"
            visible = { false }
            constraints = listOf(UNIQUE, Constraint.KOTLIN_FUNCTION)
        }
        val tilePreview = stringParameter {
            name = "Tile Default Preview function name"
            default = "TilePreview"
            help = "Used for deduplication"
            visible = { false }
            constraints = listOf(UNIQUE, Constraint.KOTLIN_FUNCTION)
        }

        widgets(
            TextFieldWidget(activityClass),
            TextFieldWidget(tileServiceClass),
            TextFieldWidget(complciationServiceClass),
            PackageNameWidget(packageName),
            CheckBoxWidget(isLauncher),
            // Invisible widgets to pass data
            TextFieldWidget(greeting),
            TextFieldWidget(defaultPreview),
            TextFieldWidget(tilePreview),
        )

        thumb { File("compose-wear-activity").resolve("templates-wear-app-with-tile-complication.png") }

        recipe = { data: TemplateData ->
            composeWearActivityWithTileAndComplicationRecipe(
                data as ModuleTemplateData,
                activityClass.value,
                tileServiceClass.value,
                tilePreview.value,
                complciationServiceClass.value,
                packageName.value,
                isLauncher.value,
                greeting.value,
                wearAppName.value,
                defaultPreview.value
            )
        }
    }
