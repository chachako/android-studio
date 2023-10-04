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

package com.android.tools.render

import com.android.sdklib.AndroidVersion
import com.android.sdklib.BuildToolInfo
import com.android.sdklib.IAndroidTarget
import com.android.sdklib.OptionalLibrary
import java.nio.file.Path

/** Stub implementation of [IAndroidTarget]. */
internal class StandaloneAndroidTarget(
    private val androidVersion: AndroidVersion
) : IAndroidTarget {
    override fun compareTo(other: IAndroidTarget?): Int = 0
    override fun getLocation(): String = ""
    override fun getVendor(): String = ""
    override fun getName(): String = ""
    override fun getFullName(): String = ""
    override fun getClasspathName(): String = ""
    override fun getShortClasspathName(): String = ""
    override fun getVersion(): AndroidVersion = androidVersion
    override fun getVersionName(): String  = ""
    override fun getRevision(): Int = 0
    override fun isPlatform(): Boolean  = true
    override fun getParent(): IAndroidTarget? = this
    override fun getPath(pathId: Int): Path = when (pathId) {
        else -> throw NotImplementedError()
    }
    override fun getBuildToolInfo(): BuildToolInfo? = null
    override fun getBootClasspath(): MutableList<String> = mutableListOf()
    override fun getOptionalLibraries(): MutableList<OptionalLibrary> = mutableListOf()
    override fun getAdditionalLibraries(): MutableList<OptionalLibrary> = mutableListOf()
    override fun hasRenderingLibrary(): Boolean = true
    override fun getSkins(): Array<Path> = arrayOf()
    override fun getDefaultSkin(): Path? = null
    override fun getPlatformLibraries(): Array<String> = arrayOf()
    override fun getProperty(name: String?): String = ""
    override fun getProperties(): MutableMap<String, String> = mutableMapOf()
    override fun canRunOn(target: IAndroidTarget?): Boolean = true
    override fun hashString(): String = ""
}
