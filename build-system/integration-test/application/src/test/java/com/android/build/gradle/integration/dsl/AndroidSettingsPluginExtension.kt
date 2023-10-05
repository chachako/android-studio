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

package com.android.build.gradle.integration.dsl

import com.android.Version
import com.android.build.gradle.integration.common.fixture.testprojects.createGradleProject
import com.google.common.truth.Truth
import org.junit.Rule
import org.junit.Test

class AndroidSettingsPluginExtension {

  @get:Rule
  val project =
    createGradleProject {
      rootProject {
        addFile("settings.gradle.kts",
                """
                  pluginManagement {
                      apply(from="../commonLocalRepo.gradle", to=pluginManagement)
                  }

                  plugins {
                      id("com.android.settings") version "${Version.ANDROID_GRADLE_PLUGIN_VERSION}"
                  }

                  android {
                      println(compileSdk)
                      execution {
                          println(defaultProfile)
                      }
                  }
                """.trimIndent()
        )
      }
    }

  @Test
  fun testConfigures() {
    val result = project.executor().run("tasks")
    Truth.assertThat(result.failureMessage).isNull()
  }
}
