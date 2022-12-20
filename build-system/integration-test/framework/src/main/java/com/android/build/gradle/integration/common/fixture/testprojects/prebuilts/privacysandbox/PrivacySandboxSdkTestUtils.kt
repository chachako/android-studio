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

package com.android.build.gradle.integration.common.fixture.testprojects.prebuilts.privacysandbox

import com.android.build.gradle.integration.common.fixture.testprojects.PluginType
import com.android.build.gradle.integration.common.fixture.testprojects.SubProjectBuilder
import com.android.build.gradle.integration.common.fixture.testprojects.TestProjectBuilder
import com.android.build.gradle.integration.common.utils.SdkHelper
import com.android.sdklib.BuildToolInfo
import com.android.testutils.TestUtils.KOTLIN_VERSION_FOR_TESTS

private val aidlPath = SdkHelper.getBuildTool(BuildToolInfo.PathId.AIDL).absolutePath
        .replace("""\""", """\\""")
const val androidxPrivacySandboxVersion = "1.0.0-alpha03"

fun TestProjectBuilder.privacySandboxSdkProject(path: String, action: SubProjectBuilder.() -> Unit) {
    subProject(path) {
        plugins.add(PluginType.PRIVACY_SANDBOX_SDK)
        android {
            defaultCompileSdk()
        }
        action()
    }
}

fun TestProjectBuilder.privacySandboxSdkLibraryProject(path: String, action: SubProjectBuilder.() -> Unit) {
    subProject(path) {
        useNewPluginsDsl = true
        plugins.add(PluginType.ANDROID_LIB)
        plugins.add(PluginType.KOTLIN_ANDROID)
        plugins.add(PluginType.KSP)
        android {
            defaultCompileSdk()
        }
        dependencies {
            implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8:1.8.10")
            implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.6.3")
            implementation("androidx.privacysandbox.tools:tools:$androidxPrivacySandboxVersion")
            implementation("androidx.privacysandbox.sdkruntime:sdkruntime-core:$androidxPrivacySandboxVersion")
            implementation("androidx.privacysandbox.sdkruntime:sdkruntime-client:$androidxPrivacySandboxVersion")

            ksp("androidx.privacysandbox.tools:tools-apicompiler:$androidxPrivacySandboxVersion")
            ksp("androidx.annotation:annotation:1.6.0")
        }
        appendToBuildFile {
            """
                   def aidlCompilerPath = '$aidlPath'
                   ksp { arg("aidl_compiler_path", aidlCompilerPath) }
                """
        }
        // Have an empty manifest as a regression test of b/237279793
        addFile("src/main/AndroidManifest.xml", """
                <?xml version="1.0" encoding="utf-8"?>
                <manifest xmlns:android="http://schemas.android.com/apk/res/android">
                </manifest>
                """.trimIndent()
        )
        action()
    }
}
