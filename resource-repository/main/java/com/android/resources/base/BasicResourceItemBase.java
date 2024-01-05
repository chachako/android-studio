/*
 * Copyright (C) 2021 The Android Open Source Project
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
import com.android.ide.common.rendering.api.ResourceValue;
import com.android.ide.common.resources.configuration.FolderConfiguration;
import com.android.resources.ResourceType;
import com.android.resources.ResourceVisibility;
import com.android.utils.Base128InputStream;
import com.android.utils.Base128InputStream.StreamFormatException;
import com.android.utils.Base128OutputStream;
import com.android.utils.HashCodes;
import com.google.common.base.MoreObjects;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import java.io.IOException;
import java.util.List;

/** Base class for implementations of the {@link BasicResourceItem} interface. */
public abstract class BasicResourceItemBase implements BasicResourceItem {
  @NonNull private final String myName;
  // Store enums as their ordinals in byte form to minimize memory footprint.
  private final byte myTypeOrdinal;
  private final byte myVisibilityOrdinal;

  BasicResourceItemBase(@NonNull ResourceType type, @NonNull String name, @NonNull ResourceVisibility visibility) {
    myName = name;
    myTypeOrdinal = (byte)type.ordinal();
    myVisibilityOrdinal = (byte)visibility.ordinal();
  }

  @Override
  @NonNull
  public final ResourceType getType() {
    return getResourceType();
  }

  @Override
  @NonNull
  public ResourceNamespace getNamespace() {
    return getRepository().getNamespace();
  }

  @Override
  @NonNull
  public final String getName() {
    return myName;
  }

  @Override
  @Nullable
  public final String getLibraryName() {
    return getRepository().getLibraryName();
  }

  @Override
  @NonNull
  public final ResourceType getResourceType() {
    return ResourceType.values()[myTypeOrdinal];
  }

  @Override
  @NonNull
  public final ResourceVisibility getVisibility() {
    return ResourceVisibility.values()[myVisibilityOrdinal];
  }

  @Override
  @NonNull
  public final ResourceValue getResourceValue() {
    return this;
  }

  @Override
  public final boolean isUserDefined() {
    return getRepository().containsUserDefinedResources();
  }

  @Override
  @NonNull
  public final ResourceReference asReference() {
    return getReferenceToSelf();
  }

  /**
   * Returns the repository this resource belongs to.
   * <p>
   * Framework resource items may move between repositories with the same origin.
   * @see RepositoryConfiguration#transferOwnershipTo(LoadableResourceRepository)
   */
  @Override
  @NonNull
  public final LoadableResourceRepository getRepository() {
    return getRepositoryConfiguration().getRepository();
  }

  @Override
  @NonNull
  public final FolderConfiguration getConfiguration() {
    return getRepositoryConfiguration().getFolderConfiguration();
  }

  @NonNull
  public abstract RepositoryConfiguration getRepositoryConfiguration();

  @Override
  public boolean equals(@Nullable Object obj) {
    if (this == obj) {
      return true;
    }
    if (obj == null || getClass() != obj.getClass()) {
      return false;
    }
    BasicResourceItemBase other = (BasicResourceItemBase) obj;
    return myTypeOrdinal == other.myTypeOrdinal
        && myName.equals(other.myName)
        && myVisibilityOrdinal == other.myVisibilityOrdinal;
  }

  @Override
  public int hashCode() {
    // The myVisibilityOrdinal field is intentionally not included in hash code because having two resource items
    // differing only by visibility in the same hash table is extremely unlikely.
    return HashCodes.mix(myTypeOrdinal, myName.hashCode());
  }

  @Override
  @NonNull
  public String toString() {
    return MoreObjects.toStringHelper(this)
                      .add("namespace", getNamespace())
                      .add("type", getResourceType())
                      .add("name", getName())
                      .add("value", getValue())
                      .toString();
  }

  /**
   * Serializes the resource item to the given stream.
   */
  public void serialize(@NonNull Base128OutputStream stream,
                        @NonNull Object2IntMap<String> configIndexes,
                        @NonNull Object2IntMap<ResourceSourceFile> sourceFileIndexes,
                        @NonNull Object2IntMap<ResourceNamespace.Resolver> namespaceResolverIndexes) throws IOException {
    stream.writeInt((myTypeOrdinal << 1) + (isFileBased() ? 1 : 0));
    stream.writeString(myName);
    stream.writeInt(myVisibilityOrdinal);
  }

  /**
   * Creates a resource item by reading its contents from the given stream.
   */
  @NonNull
  public static BasicResourceItemBase deserialize(@NonNull Base128InputStream stream,
                                                  @NonNull List<RepositoryConfiguration> configurations,
                                                  @NonNull List<ResourceSourceFile> sourceFiles,
                                                  @NonNull List<ResourceNamespace.Resolver> namespaceResolvers) throws IOException {
    assert !configurations.isEmpty();
    int encodedType = stream.readInt();
    boolean isFileBased = (encodedType & 0x1) != 0;
    ResourceType resourceType = ResourceType.values()[encodedType >>> 1];
    String name = stream.readString();
    if (name == null) {
      throw StreamFormatException.invalidFormat();
    }
    ResourceVisibility visibility = ResourceVisibility.values()[stream.readInt()];

    if (isFileBased) {
      LoadableResourceRepository repository = configurations.get(0).getRepository();
      return repository.deserializeFileResourceItem(stream, resourceType, name, visibility, configurations);
    }

    return BasicValueResourceItemBase.deserialize(stream, resourceType, name, visibility, configurations, sourceFiles, namespaceResolvers);
  }
}
