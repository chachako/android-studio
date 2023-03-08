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

package com.android.tools.profgen

import com.android.testutils.TestUtils
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.io.InputStreamReader
import kotlin.test.fail

class WildcardExpansionTest {

    @Test
    fun testClassFile() {
        val classFileResource = object : ClassFileResource {
            override fun getByteStream(): InputStream =
                TestUtils.resolveWorkspacePath(ClassFilePath).toFile().inputStream()
        }
        runTests(listOf(classFileResource))
    }

    @Test
    fun testJarArchive() {
        val archive = TestUtils.resolveWorkspacePath(JarArchivePath)
        ArchiveClassFileResourceProvider(archive).use {
            runTests(it.getClassFileResources())
        }
    }

    private fun runTests(classFileResources: Collection<ClassFileResource>) {
        runTest("LHello;", "LHello;", classFileResources)
        runTest("LHell?;", "LHello;", classFileResources)
        runTest("L*;", "LHello;", classFileResources)
        runTest("L**;", "LHello;", classFileResources)
        runTest("HSPLHello;->voidMethod()V", "HSPLHello;->voidMethod()V", classFileResources)
        runTest("HSPLHello;->voidMetho?()V", "HSPLHello;->voidMethod()V", classFileResources)
        runTest("HSPLHello;->void*()V", "HSPLHello;->voidMethod()V", classFileResources)
        runTest("HSPLHello;->void**()V", "HSPLHello;->voidMethod()V", classFileResources)
        runTest(
            "HSP**->**(**)**",
            """
                HSPLHello;-><init>()V
                HSPLHello;->method(Ljava/lang/String;)I
                HSPLHello;->voidMethod()V
            """.trimIndent(),
            classFileResources)
        runTest(
            """
                H**;->**(**)**
                S**;->**(**)**
                P**;->**(**)**
            """.trimIndent(),
            """
                HSPLHello;-><init>()V
                HSPLHello;->method(Ljava/lang/String;)I
                HSPLHello;->voidMethod()V
            """.trimIndent(),
            classFileResources)
    }

    private fun runTest(
            profile: String,
            expectedProfile: String,
            classFileResources: Collection<ClassFileResource>) {
        val reader = InputStreamReader(ByteArrayInputStream(profile.toByteArray()))
        val hrp = HumanReadableProfile(reader) { _, _, _ -> fail("Unable to parse HRF") }
        require(hrp != null)
        val expandedHrp = hrp.expandWildcards(classFileResources)
        val expandedProfile = StringBuilder()
        expandedHrp.printExact(expandedProfile)
        assertThat(expandedProfile.toString()).isEqualTo(expectedProfile.plus('\n'))
    }

    companion object {
        private const val ClassFilePath = "tools/base/profgen/profgen/testData/Hello.class"
        private const val JarArchivePath = "tools/base/profgen/profgen/testData/hello.jar"
    }
}
