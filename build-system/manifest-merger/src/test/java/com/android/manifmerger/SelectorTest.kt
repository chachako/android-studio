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

import com.android.testutils.MockitoKt.whenever
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito.mock
import java.util.Optional

class SelectorTest {

    private val xmlElement = mock<XmlElement>()
    private val xmlDocument = mock<XmlDocument>()
    private val xmlAttribute = mock<XmlAttribute>()
    private val keyResolver = mock<KeyResolver<String>>()

    @Before
    fun setUpMocks() {
        whenever(xmlElement.document).thenReturn(xmlDocument)
        whenever(xmlDocument.`package`).thenReturn(Optional.of(xmlAttribute))
    }

    @Test
    fun missingXmlAttribute_appliesTo() {
        val selector = Selector("com.example.foo")
        whenever(xmlDocument.`package`).thenReturn(Optional.empty())
        assertFalse(selector.appliesTo(xmlElement))
    }

    @Test
    fun selectorWithSinglePackage_appliesTo() {
        val selector = Selector("com.example.lib1")
        whenever(xmlAttribute.value).thenReturn("com.example.lib1")
        assertTrue(selector.appliesTo(xmlElement))

        whenever(xmlAttribute.value).thenReturn("com.example.lib2")
        assertFalse(selector.appliesTo(xmlElement))
    }

    @Test
    fun selectorWithMultiplePackages_appliesTo() {
        val selector = Selector("com.example.lib1,com.example.lib2")
        whenever(xmlAttribute.value).thenReturn("com.example.lib2")
        assertTrue(selector.appliesTo(xmlElement))

        whenever(xmlAttribute.value).thenReturn("com.example.lib3")
        assertFalse(selector.appliesTo(xmlElement))
    }

    @Test
    fun selectorWithSinglePackage_isResolvable() {
        val selector = Selector("com.example.lib1")
        whenever(keyResolver.resolve("com.example.lib1")).thenReturn("somevalue")
        assertTrue(selector.isResolvable(keyResolver))
    }

    @Test
    fun selectorWithMultiplePackages_isResolvable() {
        val selector = Selector("com.example.lib1,com.example.lib2")
        whenever(keyResolver.resolve("com.example.lib1")).thenReturn("somevalue")
        whenever(keyResolver.resolve("com.example.lib2")).thenReturn("anothervalue")
        assertTrue(selector.isResolvable(keyResolver))

        whenever(keyResolver.resolve("com.example.lib2")).thenReturn(null)
        assertFalse(selector.isResolvable(keyResolver))
    }
}
