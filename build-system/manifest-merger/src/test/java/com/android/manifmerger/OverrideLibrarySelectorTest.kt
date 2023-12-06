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

package com.android.manifmerger

import com.android.testutils.MockitoKt
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito
import java.util.Optional

class OverrideLibrarySelectorTest {
    private val xmlElement = Mockito.mock<XmlElement>()
    private val xmlDocument = Mockito.mock<XmlDocument>()
    private val xmlAttribute = Mockito.mock<XmlAttribute>()

    @Before
    fun setUpMocks() {
        MockitoKt.whenever(xmlElement.document).thenReturn(xmlDocument)
        MockitoKt.whenever(xmlDocument.`package`).thenReturn(Optional.of(xmlAttribute))
    }

    @Test
    fun missingXmlAttribute_appliesTo() {
        val selector = OverrideLibrarySelector("com.example.foo")
        MockitoKt.whenever(xmlDocument.`package`).thenReturn(Optional.empty())
        assertFalse(selector.appliesTo(xmlElement))
    }

    @Test
    fun overrideSelector_appliesTo() {
        val selector = OverrideLibrarySelector("com.example.lib1")
        MockitoKt.whenever(xmlAttribute.value).thenReturn("com.example.lib1")
        assertTrue(selector.appliesTo(xmlElement))
    }
}
