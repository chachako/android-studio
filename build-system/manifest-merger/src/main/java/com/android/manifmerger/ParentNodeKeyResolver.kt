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

import com.google.common.collect.ImmutableList
import org.w3c.dom.Element

/**
 * A [NodeKeyResolver] that uses the parent's key.
 *
 * This is useful for elements that are not unique by themselves, but are unique when combined with
 * their parent. For example, `<action>`, `<category>`, and `<data>` elements are not necessarily
 * unique by themselves, but are unique when combined with their parent `<intent-filter>` element.
 */
internal class ParentNodeKeyResolver: NodeKeyResolver {

    override val keyAttributesNames: ImmutableList<String> = ImmutableList.of()

    override fun getKey(element: Element): String? {
        val parent = element.parentNode as? Element ?: return null
        if (parent.localName == null) return null
        return XmlNode.NodeKey.fromXml(parent, ManifestModel()).toString();
    }
}
