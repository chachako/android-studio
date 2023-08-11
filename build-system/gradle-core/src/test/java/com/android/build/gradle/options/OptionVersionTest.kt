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

package com.android.build.gradle.options

import com.android.Version.ANDROID_GRADLE_PLUGIN_VERSION
import com.android.ide.common.repository.AgpVersion
import org.junit.Test

/** Tests the validity of the Android Gradle plugin versions associated with the [Option]s. */
class OptionVersionTest {

    companion object {

        /**
         * The AGP stable version that is going to be published (ignoring dot releases for the
         * purpose of this test).
         */
        private val AGP_STABLE_VERSION: AgpVersion = getStableAgpVersionIgnoringDotReleases(ANDROID_GRADLE_PLUGIN_VERSION)

        /**
         * Deprecated [Option]s that have invalid target removal versions.
         *
         * We should fix these issues as soon as possible, ideally before the beta release.
         *
         * @ RELEASE TEAM: If you update this list when upgrading AGP, be sure to file a new bug
         * assigned to the AGP team and blocking beta release (example bug: b/277803353).
         */
        private val INVALID_DEPRECATED_OPTIONS: List<Option<*>> = listOf(
        )

        private fun getStableAgpVersionIgnoringDotReleases(versionString: String): AgpVersion {
            // Normalize the version string first (e.g., "7.0" => "7.0.0")
            val normalizedVersionString = if (versionString.count { it=='.' }==1) {
                "$versionString.0"
            } else {
                versionString
            }
            val gradleVersion = AgpVersion.parse(normalizedVersionString)
            return AgpVersion(gradleVersion.major, gradleVersion.minor, 0)
        }
    }

    @Test
    fun `check deprecated options have deprecation versions in the future`() {
        val violatingOptions = getAllOptions()
                .filter { it.status is Option.Status.Deprecated }
                .filter {
                    val deprecationVersion = getStableAgpVersionIgnoringDotReleases(
                            (it.status as Option.Status.Deprecated).deprecationTarget.removalTarget.versionString!!)
                    deprecationVersion <= AGP_STABLE_VERSION
                }

        checkViolatingProjectOptions(
                violatingOptions = violatingOptions,
                ignoreList = INVALID_DEPRECATED_OPTIONS,
                requirement = "Deprecated options must have target removal versions in the future. (@ RELEASE TEAM: To handle this error, please read the full error message.) ",
                suggestion = "@ RELEASE TEAM: This error usually happens when we upgrade AGP version.\n" +
                        "We don't have to fix this issue immediately, but we should fix it before the beta release.\n" +
                        "To do that:\n" +
                        "  - Please copy the invalid options shown above to `OptionVersionTest.INVALID_DEPRECATED_OPTIONS`.\n" +
                        "  - Please file a bug for the AGP team and mark it as blocking the beta release (example bug: b/277803353)."
        )
    }

    @Test
    fun `check removed options do not have removed versions in the future`() {
        val violatingOptions = getAllOptions()
                .filter { it.status is Option.Status.Removed }
                .filter { option ->
                    val removedVersion = (option.status as Option.Status.Removed).removedVersion.versionString?.let {
                        getStableAgpVersionIgnoringDotReleases(it)
                    }
                    removedVersion?.let { removedVersion > AGP_STABLE_VERSION } ?: false
                }

        checkViolatingProjectOptions(
                violatingOptions = violatingOptions,
                requirement = "Removed options must not have removed versions in the future."
        )
    }

    private fun getAllOptions(): List<Option<Any>> =
            (BooleanOption.values().toList() as List<Option<Boolean>>) +
                    OptionalBooleanOption.values() +
                    StringOption.values() +
                    IntegerOption.values()
}

fun checkViolatingProjectOptions(
        violatingOptions: List<Option<*>>,
        ignoreList: List<Option<*>> = emptyList(),
        requirement: String,
        suggestion: String? = null) {
    val newViolations = violatingOptions - ignoreList.toSet()
    check(newViolations.isEmpty()) {
        "$requirement\n" +
                "The following options do not meet that requirement:\n" +
                "```\n" +
                newViolations.joinToString(",\n") { "${it.javaClass.simpleName}.$it" } + "\n" +
                "```\n" +
                (suggestion
                    ?: "If this is intended, copy the above code snippet to the ignore list of this test.")
    }

    val fixedViolations = ignoreList - violatingOptions.toSet()
    check(fixedViolations.isEmpty()) {
        "$requirement\n" +
                "The following options have met that requirement:\n" +
                "```\n" +
                fixedViolations.joinToString(",\n") { "${it.javaClass.simpleName}.$it" } + "\n" +
                "```\n" +
                "Remove them from the ignore list of this test."
    }
}
