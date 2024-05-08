/*
 * Copyright (C) 2020 The Android Open Source Project
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

package com.android.tools.lint.checks.androidx

import com.android.tools.lint.client.api.IssueRegistry
import com.android.tools.lint.detector.api.Issue

class ComposeDesktopIssueRegistry : IssueRegistry() {

    override val api: Int = com.android.tools.lint.detector.api.CURRENT_API

    override val issues: List<Issue> = (androidx.compose.runtime.lint.RuntimeIssueRegistry().issues
            + androidx.compose.animation.core.lint.AnimationCoreIssueRegistry().issues
            + androidx.compose.animation.lint.AnimationIssueRegistry().issues
            + androidx.compose.foundation.lint.FoundationIssueRegistry().issues
            + androidx.compose.material.lint.MaterialIssueRegistry().issues
            + androidx.compose.material3.lint.Material3IssueRegistry().issues
            + androidx.compose.runtime.saveable.lint.RuntimeSaveableIssueRegistry().issues
            + androidx.compose.ui.graphics.lint.UiGraphicsIssueRegistry().issues
            + androidx.compose.ui.lint.UiIssueRegistry().issues
            + androidx.compose.ui.test.manifest.lint.TestManifestIssueRegistry().issues
            ).filter { !it.isAndroidSpecific() }
}