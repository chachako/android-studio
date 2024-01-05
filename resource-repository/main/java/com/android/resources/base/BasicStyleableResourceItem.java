/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.resources.base;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.ide.common.rendering.api.AttrResourceValue;
import com.android.ide.common.rendering.api.ResourceNamespace;
import com.android.ide.common.rendering.api.StyleableResourceValue;
import com.android.ide.common.resources.ResourceItem;
import com.android.ide.common.resources.ResourceRepository;
import com.android.resources.ResourceType;
import com.android.resources.ResourceVisibility;
import com.android.utils.Base128InputStream;
import com.android.utils.Base128InputStream.StreamFormatException;
import com.android.utils.Base128OutputStream;
import com.google.common.collect.ImmutableList;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Resource item representing a styleable resource.
 */
public final class BasicStyleableResourceItem extends BasicValueResourceItemBase implements StyleableResourceValue {
  @NonNull private final List<AttrResourceValue> myAttrs;

  /**
   * Initializes the resource.
   *
   * @param name the name of the resource
   * @param sourceFile the source file containing definition of the resource
   * @param visibility the visibility of the resource
   * @param attrs the attributes of the styleable
   */
  public BasicStyleableResourceItem(@NonNull String name,
                                    @NonNull ResourceSourceFile sourceFile,
                                    @NonNull ResourceVisibility visibility,
                                    @NonNull List<AttrResourceValue> attrs) {
    super(ResourceType.STYLEABLE, name, sourceFile, visibility);
    myAttrs = ImmutableList.copyOf(attrs);
  }

  @Override
  @NonNull
  public List<AttrResourceValue> getAllAttributes() {
    return myAttrs;
  }

  @Override
  public boolean equals(@Nullable Object obj) {
    if (this == obj) return true;
    if (!super.equals(obj)) return false;
    BasicStyleableResourceItem other = (BasicStyleableResourceItem) obj;
    return myAttrs.equals(other.myAttrs);
  }

  @Override
  public void serialize(@NonNull Base128OutputStream stream,
                        @NonNull Object2IntMap<String> configIndexes,
                        @NonNull Object2IntMap<ResourceSourceFile> sourceFileIndexes,
                        @NonNull Object2IntMap<ResourceNamespace.Resolver> namespaceResolverIndexes) throws IOException {
    super.serialize(stream, configIndexes, sourceFileIndexes, namespaceResolverIndexes);
    stream.writeInt(myAttrs.size());
    for (AttrResourceValue attr : myAttrs) {
      if (attr instanceof BasicAttrResourceItem && !attr.getFormats().isEmpty()) {
        // Don't write redundant format information to the stream.
        attr = ((BasicAttrResourceItem)attr).createReference();
      }
      ((BasicValueResourceItemBase)attr).serialize(stream, configIndexes, sourceFileIndexes, namespaceResolverIndexes);
    }
  }

  /**
   * Creates a BasicStyleableResourceItem by reading its contents from the given stream.
   */
  @NonNull
  static BasicStyleableResourceItem deserialize(@NonNull Base128InputStream stream,
                                                @NonNull String name,
                                                @NonNull ResourceVisibility visibility,
                                                @NonNull ResourceSourceFile sourceFile,
                                                @NonNull ResourceNamespace.Resolver resolver,
                                                @NonNull List<RepositoryConfiguration> configurations,
                                                @NonNull List<ResourceSourceFile> sourceFiles,
                                                @NonNull List<ResourceNamespace.Resolver> namespaceResolvers) throws IOException {
    ResourceRepository repository = sourceFile.getRepository();
    int n = stream.readInt();
    List<AttrResourceValue> attrs = n == 0 ? Collections.emptyList() : new ArrayList<>(n);
    for (int i = 0; i < n; i++) {
      BasicResourceItemBase attrItem = deserialize(stream, configurations, sourceFiles, namespaceResolvers);
      if (!(attrItem instanceof AttrResourceValue)) {
        throw StreamFormatException.invalidFormat();
      }
      AttrResourceValue attr = getCanonicalAttr((AttrResourceValue)attrItem, repository);
      attrs.add(attr);
    }
    BasicStyleableResourceItem item = new BasicStyleableResourceItem(name, sourceFile, visibility, attrs);
    item.setNamespaceResolver(resolver);
    return item;
  }

  /**
   * For an attr reference that doesn't contain formats tries to find an attr definition the reference is pointing to.
   * If such attr definition belongs to this resource repository and has the same description and group name as
   * the attr reference, returns the attr definition. Otherwise returns the attr reference passed as the parameter.
   */
  @NonNull
  public static AttrResourceValue getCanonicalAttr(@NonNull AttrResourceValue attr, @NonNull ResourceRepository repository) {
    if (attr.getFormats().isEmpty()) {
      List<ResourceItem> items = repository.getResources(attr.getNamespace(), ResourceType.ATTR, attr.getName());
      for (ResourceItem item : items) {
        if (item instanceof AttrResourceValue &&
            Objects.equals(((AttrResourceValue)item).getDescription(), attr.getDescription()) &&
            Objects.equals(((AttrResourceValue)item).getGroupName(), attr.getGroupName())) {
          return (AttrResourceValue)item;
        }
      }
    }
    return attr;
  }
}
