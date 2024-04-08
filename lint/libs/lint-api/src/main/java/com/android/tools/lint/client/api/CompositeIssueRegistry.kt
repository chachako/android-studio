/*
 * Copyright (C) 2013 The Android Open Source Project
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
package com.android.tools.lint.client.api

import com.android.tools.lint.detector.api.CURRENT_API

/** Registry which merges many issue registries into one, and presents a unified list of issues. */
open class CompositeIssueRegistry(val registries: List<IssueRegistry>) : IssueRegistry() {
  override val issues by
    lazy(LazyThreadSafetyMode.NONE) { registries.flatMap(IssueRegistry::issues) }
  override val deletedIssues
    get() = registries.flatMap(IssueRegistry::deletedIssues)

  override val api: Int = CURRENT_API
  override val isUpToDate
    get() = registries.all(IssueRegistry::isUpToDate)
}
