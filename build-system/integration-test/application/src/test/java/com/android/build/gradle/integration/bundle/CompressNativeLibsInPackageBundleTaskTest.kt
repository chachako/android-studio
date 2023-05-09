/*
 * Copyright (C) 2018 The Android Open Source Project
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

package com.android.build.gradle.integration.bundle

import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.android.build.gradle.integration.common.fixture.app.MinimalSubProject
import com.android.build.gradle.integration.common.fixture.app.MultiModuleTestProject
import com.android.build.gradle.integration.common.runner.FilterableParameterized
import com.android.testutils.truth.PathSubject.assertThat
import com.android.tools.build.bundletool.model.AppBundle
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import java.util.zip.ZipFile

@RunWith(FilterableParameterized::class)
class CompressNativeLibsInPackageBundleTaskTest(
    private val useLegacyPackaging: Boolean?,
    private val useLegacyPackagingFromBundle: Boolean?
) {

    companion object {
        @Parameterized.Parameters(
            name = "useLegacyPackaging_{0}_useLegacyPackagingFromBundle_{1}"
        )
        @JvmStatic
        fun params() =
            listOf(
                arrayOf(true, true),
                arrayOf(true, false),
                arrayOf(true, null),
                arrayOf(false, true),
                arrayOf(false, false),
                arrayOf(false, null),
                arrayOf(null, true),
                arrayOf(null, false),
                arrayOf<Boolean?>(null, null)
            )
    }

    private val app = MinimalSubProject.app("com.example.test")

    @get:Rule
    val project =
        GradleTestProject.builder()
            .fromTestApp(MultiModuleTestProject.builder().subproject(":app", app).build())
            .create()

    @Test()
    fun testNativeLibsCompression() {
        useLegacyPackaging?.also {
            project.getSubproject(":app")
                .buildFile.appendText(
                    "\nandroid.packagingOptions.jniLibs.useLegacyPackaging = $it\n"
                )
        }
        useLegacyPackagingFromBundle?.also {
            project.getSubproject(":app")
                .buildFile.appendText(
                    """
                        androidComponents {
                            onVariants(selector().all(), {
                                packaging.jniLibs.useLegacyPackagingFromBundle.set($it)
                            })
                        }
                    """.trimIndent()
                )
        }

        project.executor().run(":app:bundleDebug")
        val bundleFile = project.locateBundleFileViaModel("debug", ":app")
        assertThat(bundleFile).isNotNull()
        assertThat(bundleFile).exists()
        ZipFile(bundleFile!!).use { zip ->
            val appBundle = AppBundle.buildFromZip(zip)
            val expectedUncompressNativeLibsEnabledValue =
                when {
                    useLegacyPackagingFromBundle == true -> false
                    useLegacyPackagingFromBundle == false -> true
                    useLegacyPackaging == true -> false
                    else -> true
                }
            assertThat(appBundle.bundleConfig.optimizations.uncompressNativeLibraries.enabled)
                .isEqualTo(expectedUncompressNativeLibsEnabledValue)
        }
    }
}
