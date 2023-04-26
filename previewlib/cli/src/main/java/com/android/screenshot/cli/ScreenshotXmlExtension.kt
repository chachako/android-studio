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
package com.android.screenshot.cli

import com.android.SdkConstants
import com.android.resources.ResourceFolderType
import com.android.tools.idea.res.isInResourceSubdirectoryInAnyVariant
import com.android.tools.lint.client.api.LintXmlConfiguration
import com.intellij.lang.ASTNode
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.util.Computable
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiQualifiedNamedElement
import com.intellij.psi.impl.source.xml.SchemaPrefix
import com.intellij.psi.impl.source.xml.TagNameReference
import com.intellij.psi.xml.XmlFile
import com.intellij.psi.xml.XmlTag
import com.intellij.util.IncorrectOperationException
import com.intellij.xml.DefaultXmlExtension
import com.intellij.xml.XmlNSDescriptor
import org.jetbrains.android.dom.AndroidResourceDomFileDescription.Companion.isFileInResourceFolderType
import org.jetbrains.android.dom.drawable.DrawableResourceNSDescriptor
import org.jetbrains.android.dom.layout.AndroidLayoutNSDescriptor
import org.jetbrains.android.dom.manifest.ManifestDomFileDescription
import org.jetbrains.android.dom.xml.XmlResourceNSDescriptor
import org.jetbrains.android.facet.TagFromClassDescriptor

class ScreenshotXmlExtension : DefaultXmlExtension() {

    override fun getNSDescriptor(
        element: XmlTag,
        namespace: String,
        strict: Boolean
    ): XmlNSDescriptor? {
        val file = element.containingFile as XmlFile
        val isRoot = file.rootTag === element
        if (isRoot && isFileInResourceFolderType(file, ResourceFolderType.LAYOUT)) {
            return AndroidLayoutNSDescriptor
        }
        if (isRoot && isFileInResourceFolderType(file, ResourceFolderType.XML)) {
            return XmlResourceNSDescriptor
        }
        return if (isRoot && isFileInResourceFolderType(file, ResourceFolderType.DRAWABLE)) {
            DrawableResourceNSDescriptor
        } else super.getNSDescriptor(element, namespace, strict)
    }

    override fun createTagNameReference(
        nameElement: ASTNode,
        startTagFlag: Boolean
    ): TagNameReference? {
        return object : TagNameReference(nameElement, startTagFlag) {
            override fun isSoft(): Boolean {
                // To avoid default errors for unresolved tags.
                return true
            }

            @Throws(IncorrectOperationException::class)
            override fun bindToElement(element: PsiElement): PsiElement? {
                return if (element is PsiQualifiedNamedElement) {
                    // This case is handled by AndroidXmlReferenceProvider.
                    null
                } else super.bindToElement(element)
            }

            @Throws(IncorrectOperationException::class)
            override fun handleElementRename(newElementName: String): PsiElement? {
                val element = tagElement
                return if (element != null && element.descriptor is TagFromClassDescriptor) {
                    // This case is handled by AndroidXmlReferenceProvider.
                    null
                } else super.handleElementRename(newElementName)
            }
        }
    }

    override fun isAvailable(file: PsiFile): Boolean {
        return if (file is XmlFile) {
            ApplicationManager.getApplication().runReadAction(
                Computable {
                    if (file.getName() == SdkConstants.FN_ANDROID_MANIFEST_XML && ManifestDomFileDescription.isManifestFile(
                            file
                        )
                    ) {
                        return@Computable true
                    }
                    if (LintXmlConfiguration.CONFIG_FILE_NAME == file.getName()) {
                        return@Computable true
                    }
                    val tag = file.rootTag
                    if (tag != null) {
                        val tagName = tag.name
                        if (LintXmlConfiguration.TAG_LINT == tagName || SdkConstants.TAG_ISSUES == tagName) {
                            return@Computable true
                        }
                    }
                    false
                })
        } else false
    }

    override fun getPrefixDeclaration(context: XmlTag, namespacePrefix: String): SchemaPrefix? {
        val prefix = super.getPrefixDeclaration(context, namespacePrefix)
        if (prefix != null) {
            return prefix
        }
        return if (namespacePrefix.isEmpty()) {
            // In for example XHTML documents, the root element looks like this:
            //  <html xmlns="http://www.w3.org/1999/xhtml">
            // This means that the IDE can find the namespace for "".
            //
            // However, in Android XML files it's implicit, so just return a dummy SchemaPrefix so
            // // that we don't end up with a
            //      Namespace ''{0}'' is not bound
            // error from {@link XmlUnboundNsPrefixInspection#checkUnboundNamespacePrefix}
            EMPTY_SCHEMA
        } else null
    }

    override fun isRequiredAttributeImplicitlyPresent(tag: XmlTag, attrName: String): Boolean {
        return isAaptAttributeDefined(tag, attrName)
    }

    companion object {

        private val EMPTY_SCHEMA = SchemaPrefix(null, TextRange(0, 0), SdkConstants.ANDROID_NS_NAME)

        /**
         * Checks if an aapt:attr with the given name is defined for the XML tag.
         *
         * @param tag the XML tag to check
         * @param attrName the name of the attribute to look for
         * @return true if the attribute is defined, false otherwise
         */
        fun isAaptAttributeDefined(tag: XmlTag, attrName: String): Boolean {
            val subTags = tag.subTags
            for (child in subTags) {
                if (SdkConstants.TAG_ATTR == child.localName && SdkConstants.AAPT_URI == child.namespace) {
                    val attr = child.getAttribute(SdkConstants.ATTR_NAME)
                    if (attr != null && attrName == attr.value) {
                        return true
                    }
                }
            }
            return false
        }
    }
}
