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
import com.android.ide.common.rendering.api.ResourceNamespace;
import com.android.ide.common.rendering.api.ResourceReference;
import com.android.ide.common.util.PathString;
import com.android.resources.Density;
import com.android.resources.ResourceType;
import com.android.resources.ResourceVisibility;
import com.android.utils.Base128InputStream;
import com.android.utils.Base128InputStream.StreamFormatException;
import com.android.utils.Base128OutputStream;
import com.android.utils.HashCodes;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import java.io.IOException;
import java.util.List;

/**
 * Resource item representing a file resource, e.g. a drawable or a layout.
 */
public class BasicFileResourceItem extends BasicResourceItemBase {
  @NonNull private final RepositoryConfiguration myConfiguration;
  @NonNull private final String myRelativePath;

  /**
   * Initializes the resource.
   *
   * @param type the type of the resource
   * @param name the name of the resource
   * @param configuration the configuration the resource belongs to
   * @param visibility the visibility of the resource
   * @param relativePath defines location of the resource. Exact semantics of the path may vary depending on the resource repository
   */
  public BasicFileResourceItem(@NonNull ResourceType type,
                               @NonNull String name,
                               @NonNull RepositoryConfiguration configuration,
                               @NonNull ResourceVisibility visibility,
                               @NonNull String relativePath) {
    super(type, name, visibility);
    myConfiguration = configuration;
    myRelativePath = relativePath;
  }

  @Override
  public final boolean isFileBased() {
    return true;
  }

  @Override
  @Nullable
  public final ResourceReference getReference() {
    return null;
  }

  @Override
  @NonNull
  public RepositoryConfiguration getRepositoryConfiguration() {
    return myConfiguration;
  }

  @Override
  @NonNull
  public final ResourceNamespace.Resolver getNamespaceResolver() {
    return ResourceNamespace.Resolver.EMPTY_RESOLVER;
  }

  @Override
  @NonNull
  public String getValue() {
    return getRepository().getResourceUrl(myRelativePath);
  }

  /**
   * {@inheritDoc}
   *
   * <p>The returned PathString points either to a file on disk, or to a ZIP entry inside a res.apk file.
   * In the latter case the filesystem URI part points to res.apk itself, e.g. {@code "zip:///foo/bar/res.apk"}.
   * The path part is the path of the ZIP entry containing the resource.
   */
  @Override
  @NonNull
  public final PathString getSource() {
    return getRepository().getSourceFile(myRelativePath, true);
  }

  @Override
  @Nullable
  public final PathString getOriginalSource() {
    return getRepository().getOriginalSourceFile(myRelativePath, true);
  }

  @Override
  public boolean equals(@Nullable Object obj) {
    if (this == obj) return true;
    if (!super.equals(obj)) return false;
    BasicFileResourceItem other = (BasicFileResourceItem) obj;
    return myConfiguration.equals(other.myConfiguration)
        && myRelativePath.equals(other.myRelativePath);
  }

  @Override
  public int hashCode() {
    return HashCodes.mix(super.hashCode(), myRelativePath.hashCode());
  }

  @Override
  public void serialize(@NonNull Base128OutputStream stream,
                        @NonNull Object2IntMap<String> configIndexes,
                        @NonNull Object2IntMap<ResourceSourceFile> sourceFileIndexes,
                        @NonNull Object2IntMap<ResourceNamespace.Resolver> namespaceResolverIndexes) throws IOException {
    super.serialize(stream, configIndexes, sourceFileIndexes, namespaceResolverIndexes);
    stream.writeString(myRelativePath);
    String qualifierString = getConfiguration().getQualifierString();
    int index = configIndexes.getInt(qualifierString);
    assert index >= 0;
    stream.writeInt(index);
    stream.writeInt(getEncodedDensityForSerialization());
  }

  /**
   * Creates a BasicFileResourceItem by reading its contents from the given stream.
   */
  @NonNull
  static BasicFileResourceItem deserialize(@NonNull Base128InputStream stream,
                                           @NonNull ResourceType resourceType,
                                           @NonNull String name,
                                           @NonNull ResourceVisibility visibility,
                                           @NonNull List<RepositoryConfiguration> configurations) throws IOException {
    String relativePath = stream.readString();
    if (relativePath == null) {
      throw StreamFormatException.invalidFormat();
    }
    RepositoryConfiguration configuration = configurations.get(stream.readInt());
    int encodedDensity = stream.readInt();
    if (encodedDensity == 0) {
      return new BasicFileResourceItem(resourceType, name, configuration, visibility, relativePath);
        } else {
            Density density = Density.create(encodedDensity);
            return new BasicDensityBasedFileResourceItem(
                    resourceType, name, configuration, visibility, relativePath, density);
    }
  }

  protected int getEncodedDensityForSerialization() {
    return 0;
  }
}
