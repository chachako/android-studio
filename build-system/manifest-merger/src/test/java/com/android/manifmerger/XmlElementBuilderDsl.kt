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

package com.android.manifmerger

import org.mockito.Answers
import org.mockito.Mockito
import org.mockito.Mockito.anyInt
import org.mockito.Mockito.anyString
import org.mockito.Mockito.mock

open class ElementBuilder {
    var key: String? = null
    var featureFlag: String? = null
    var multipleDeclarationAllowed: Boolean = false
    val children = mutableListOf<ElementBuilder>()

    fun childElement(init: ElementBuilder.() -> Unit) {
        val child = ElementBuilder()
        child.init()
        children.add(child)
    }
    fun toElement(): XmlElement {
        val xmlElement = mock(XmlElement::class.java, Answers.RETURNS_DEEP_STUBS)
        Mockito.`when`(xmlElement.key).thenReturn(key)
        Mockito.`when`(xmlElement.featureFlag()).thenReturn(featureFlag?.let { FeatureFlag.from(it) })
        Mockito.`when`(xmlElement.hasFeatureFlag()).thenReturn(featureFlag != null)
        Mockito.`when`(xmlElement.type.areMultipleDeclarationAllowed()).thenReturn(multipleDeclarationAllowed)
        Mockito.`when`(xmlElement.setFeatureFlag(anyString())).then { invocation ->
            featureFlag = invocation.getArgument(0)
            xmlElement
        }
        val xmlElementChildren = children.map { it.toElement() }
        Mockito.`when`(xmlElement.mergeableElements[anyInt()]).thenAnswer { invocation ->
            xmlElementChildren[invocation.getArgument(0)]
        }
        val childrenByKey = xmlElementChildren.groupBy { it.key ?: "" }
        Mockito.`when`(xmlElement.childrenByTypeAndKey).thenReturn(childrenByKey)
        return xmlElement
    }
}

class RootElementBuilder : ElementBuilder()

fun rootElement(init: RootElementBuilder.() -> Unit): RootElementBuilder {
    val builder = RootElementBuilder()
    builder.init()
    return builder
}
