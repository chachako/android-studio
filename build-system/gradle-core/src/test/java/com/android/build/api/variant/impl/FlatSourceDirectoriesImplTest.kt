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
package com.android.build.api.variant.impl

import com.android.build.gradle.internal.services.VariantServices
import com.google.common.truth.Truth
import org.gradle.api.file.Directory
import org.gradle.api.provider.Provider
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.mockito.Mock
import org.mockito.Mockito
import org.mockito.junit.MockitoJUnit
import org.mockito.junit.MockitoRule
import org.mockito.quality.Strictness
import kotlin.test.fail

class FlatSourceDirectoriesImplTest {

    @get:Rule
    val rule: MockitoRule = MockitoJUnit.rule().strictness(Strictness.STRICT_STUBS)

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Mock
    lateinit var variantServices: VariantServices
    @Test
    fun testAddingGeneratedEntryUsingStaticMethod(){
        val testTarget = FlatSourceDirectoriesImpl(
            "_for_test",
            variantServices,
            null,
        )

        try {
            @Suppress("UNCHECKED_CAST")
            testTarget.addStaticSource(
                TaskProviderBasedDirectoryEntryImpl(
                    "Generated",
                    Mockito.mock(Provider::class.java) as Provider<Directory>,
                    isGenerated = true
                ),
            )
        } catch(e: IllegalArgumentException) {
            Truth.assertThat(e.message).contains("The task Generated is generating code and should not be added as a Static source")
            return
        }
        fail("Expected Exception not generated")
    }
}
