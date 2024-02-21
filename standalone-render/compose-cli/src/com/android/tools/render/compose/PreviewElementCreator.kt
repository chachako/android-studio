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

package com.android.tools.render.compose

import com.android.tools.preview.AnnotatedMethod
import com.android.tools.preview.AnnotationAttributesProvider
import com.android.tools.preview.ComposePreviewElement
import com.android.tools.preview.ParametrizedComposePreviewElementTemplate
import com.android.tools.preview.PreviewParameter
import com.android.tools.preview.SingleComposePreviewElementInstance
import com.android.tools.preview.previewAnnotationToPreviewElement
import com.android.tools.render.StandaloneRenderContext
import com.intellij.psi.PsiElement
import com.intellij.psi.SmartPsiElementPointer

/** Creates [ComposePreviewElement] from [ComposeScreenshot] data. */
internal fun ComposeScreenshot.toPreviewElement(): ComposePreviewElement? {
    val attrProvider = DeserializedAnnotationAttributesProvider(this.previewParams)
    return previewAnnotationToPreviewElement(
        attrProvider,
        DeserializedAnnotatedMethod(this.methodFQN, this.methodParams),
        null,
        ::parameterizedElementConstructor
    )
}

private fun parameterizedElementConstructor(
    basePreviewElement: SingleComposePreviewElementInstance,
    parameters: Collection<PreviewParameter>
): ComposePreviewElement {
    return ParametrizedComposePreviewElementTemplate(basePreviewElement, parameters) {
        StandaloneRenderContext()
    }
}

/**
 * [AnnotatedMethod] for the method represented by its FQN and parameters each of which is
 * represented by the mapping between its @ParameterProvider annotation parameters names and values.
 */
private class DeserializedAnnotatedMethod(
    methodFqn: String,
    methodParams: List<Map<String, String>>,
) : AnnotatedMethod {
    override val name: String = methodFqn.substringAfterLast(".")
    override val qualifiedName: String = methodFqn
    override val methodBody: SmartPsiElementPointer<PsiElement>? = null
    override val parameterAnnotations: List<Pair<String, AnnotationAttributesProvider>> =
        methodParams.mapIndexed { i, param -> ("param$i" to DeserializedAnnotationAttributesProvider(param)) }
}

/**
 * [AnnotationAttributesProvider] for the annotation represented by the mapping between its
 * parameters names and values.
 */
private class DeserializedAnnotationAttributesProvider(private val params: Map<String, String>) : AnnotationAttributesProvider {

    override fun <T> getAttributeValue(attributeName: String): T? = params[attributeName] as? T

    override fun getIntAttribute(attributeName: String): Int? = getAttributeValue<String>(attributeName)?.toInt()

    override fun getStringAttribute(attributeName: String): String? = getAttributeValue<String>(attributeName)

    override fun getFloatAttribute(attributeName: String): Float? = getAttributeValue<String>(attributeName)?.toFloat()

    override fun getBooleanAttribute(attributeName: String): Boolean? = getAttributeValue<String>(attributeName)?.toBoolean()

    override fun <T> getDeclaredAttributeValue(attributeName: String): T? = getAttributeValue(attributeName)

    override fun findClassNameValue(name: String): String? = getAttributeValue(name)
}
