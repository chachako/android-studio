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

package com.android.tools.render.configuration

import com.android.tools.configurations.ConfigurationFileState
import com.android.tools.configurations.ConfigurationProjectState
import com.android.tools.configurations.ConfigurationStateManager
import com.intellij.openapi.vfs.VirtualFile

/**
 * Since we are not saving the configuration state in the standalone rendering this is essentially
 * a stub [ConfigurationStateManager].
 */
internal class StandaloneConfigurationStateManager : ConfigurationStateManager {
    private val projState = ConfigurationProjectState()
    private val fileState = ConfigurationFileState()
    private val state = ConfigurationStateManager.State().also { it.projectState = projState }
    override fun getState(): ConfigurationStateManager.State = state

    override fun loadState(state: ConfigurationStateManager.State) {}
    override fun getConfigurationState(file: VirtualFile): ConfigurationFileState = fileState
    override fun setConfigurationState(file: VirtualFile, state: ConfigurationFileState) { }
    override fun getProjectState(): ConfigurationProjectState = projState
}
