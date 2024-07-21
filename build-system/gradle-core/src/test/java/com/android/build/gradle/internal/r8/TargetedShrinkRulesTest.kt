/*
 * Copyright (C) 2024 The Android Open Source Project
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

package com.android.build.gradle.internal.r8

import com.android.build.gradle.internal.r8.TargetedShrinkRulesReadWriter.createJarContents
import com.android.build.gradle.internal.r8.TargetedShrinkRulesReadWriter.readFromJar
import com.android.testutils.ZipContents
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import kotlin.test.assertEquals

/** Unit test for [TargetedShrinkRules]. */
class TargetedShrinkRulesTest {

    @get:Rule
    val tmpDir = TemporaryFolder()

    private fun exampleShrinkRules(): Map<String, String> {
        return mapOf(
            "META-INF/com.android.tools/r8-from-8.2.0/r8-from-8.2.0.ext" to "# R8-from-8.2.0 rules",
            "META-INF/com.android.tools/r8-from-8.0.0-upto-8.2.0/r8-from-8.0.0-upto-8.2.0.ext" to "# R8-from-8.0.0-upto-8.2.0 rules",
            "META-INF/com.android.tools/r8-upto-8.0.0/r8-upto-8.0.0.ext" to "# R8-upto-8.0.0 rules",
            "META-INF/com.android.tools/proguard/proguard.ext" to "# Proguard rules",
            "META-INF/proguard/proguard.pro" to "# Legacy Proguard rules"
        )
    }

    @Test
    fun `test producing and consuming targeted shrinking rules`() {
        val shrinkRulesContentsAtProducer: Map<String, String> = exampleShrinkRules()
        val jarFile = tmpDir.root.resolve("lib.jar")
        ZipContents(shrinkRulesContentsAtProducer.mapValues { it.value.toByteArray() })
            .writeToFile(jarFile)

        val shrinkRulesAtConsumer: TargetedShrinkRules = readFromJar(jarFile)
        val shrinkRulesContentsAtConsumer: Map<String, String> =
            shrinkRulesAtConsumer.createJarContents().mapValues { it.value.decodeToString() }

        assertEquals(shrinkRulesContentsAtProducer, shrinkRulesContentsAtConsumer)
    }

}
