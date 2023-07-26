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

package com.android.build.gradle.internal.fixtures

import org.gradle.api.artifacts.ModuleIdentifier
import org.gradle.api.artifacts.VersionConstraint
import org.gradle.api.artifacts.component.ComponentIdentifier
import org.gradle.api.artifacts.component.ModuleComponentSelector
import org.gradle.api.attributes.AttributeContainer
import org.gradle.api.capabilities.Capability

class FakeModuleComponentSelector(
    private val group: String,
    private val module: String,
    private val version: String,
) : ModuleComponentSelector {

    override fun getDisplayName(): String {
        TODO("Not yet implemented")
    }

    override fun matchesStrictly(identifier: ComponentIdentifier): Boolean {
        TODO("Not yet implemented")
    }

    override fun getAttributes(): AttributeContainer {
        TODO("Not yet implemented")
    }

    override fun getRequestedCapabilities(): MutableList<Capability> {
        TODO("Not yet implemented")
    }

    override fun getGroup(): String {
        return group
    }

    override fun getModule(): String {
        return module
    }

    override fun getVersion(): String {
        return version
    }

    override fun getVersionConstraint(): VersionConstraint {
        TODO("Not yet implemented")
    }

    override fun getModuleIdentifier(): ModuleIdentifier {
        TODO("Not yet implemented")
    }
}
