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

package com.android.build.api

import com.android.Version
import com.android.build.api.dsl.Lint
import com.android.ide.common.repository.AgpVersion
import com.android.testutils.ApiTester
import com.google.common.io.Resources
import com.google.common.reflect.ClassPath
import org.junit.Test

class DeprecatedApiTest {

    @Test
    fun `deprecated and removed APIs need to be announced publicly`() {
        getApiTester().checkApiElements()
    }

    companion object {
        private val snapshotFileUrl =
            Resources.getResource(DeprecatedApiTest::class.java, "deprecated-api.txt")
        private val currentAgpVersion = Version.ANDROID_GRADLE_PLUGIN_VERSION.removeSuffix("-dev")

        private val keyOrdering: Comparator<String> =
            Comparator.comparing<String, AgpVersion> { if (it == "UNKNOWN_VERSION") AgpVersion.parse("0.0.0") else AgpVersion.parse(it) }.reversed()

        internal fun getApiTester(): ApiTester {
            val classes = ClassPath.from(
                Lint::class.java.classLoader
            ).getTopLevelClassesRecursive("com.android.build.api")
                .filter(::filterNonApiClasses)
            return ApiTester(
                "Deprecated Android Gradle Plugin API.",
                classes,
                ApiTester.Filter.DEPRECATED_ONLY,
                """
                The deprecated API has changed, if you're removing a previously deprecated API or
                deprecating an API, make sure that you announce these updates on http://go/agp-api-updates
                to be added on https://developer.android.com/studio/releases/gradle-plugin-api-updates
                then run
                    gradlew :base:gradle-api:updateDeprecatedApi

                To update all the API expectation files, run
                    gradlew updateApi

                DeprecatedApiUpdater will apply the following changes if run:

                """.trimIndent(),
                snapshotFileUrl,
                { content -> transformFinalFileContent(content, snapshotFileUrl,
                    currentKey =  currentAgpVersion,
                    keyPrefix = "Deprecated from AGP ",
                    keyOrdering = keyOrdering) },
                ApiTester.Flag.OMIT_HASH
            )
        }
    }
}
