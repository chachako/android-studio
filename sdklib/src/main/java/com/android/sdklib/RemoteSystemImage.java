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
package com.android.sdklib;

import com.android.annotations.NonNull;
import com.android.repository.Revision;
import com.android.repository.api.RemotePackage;
import com.android.repository.api.RepoPackage;
import com.android.sdklib.repository.IdDisplay;
import com.android.sdklib.repository.meta.DetailsTypes;
import com.android.sdklib.repository.meta.DetailsTypes.ApiDetailsType;

import com.google.common.collect.ImmutableList;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

public final class RemoteSystemImage implements ISystemImage {
    private final RemotePackage myRemotePackage;
    private final ImmutableList<IdDisplay> myTags;
    private final IdDisplay myVendor;
    private final List<String> myAbis;
    private final List<String> myTranslatedAbis;
    private final AndroidVersion myAndroidVersion;

    public RemoteSystemImage(RemotePackage p) {
        IdDisplay vendor = null;
        ApiDetailsType details = (ApiDetailsType) p.getTypeDetails();

        if (details instanceof DetailsTypes.AddonDetailsType) {
            vendor = ((DetailsTypes.AddonDetailsType) details).getVendor();
        }
        if (details instanceof DetailsTypes.SysImgDetailsType) {
            vendor = ((DetailsTypes.SysImgDetailsType) details).getVendor();
        }

        myRemotePackage = p;
        myTags = SystemImageTags.getTags(p);
        myVendor = vendor;
        myAbis = details.getAbis();
        myTranslatedAbis = details.getTranslatedAbis();
        myAndroidVersion = details.getAndroidVersion();
    }

    @NonNull
    @Override
    public Path getLocation() {
        assert false : "Can't get location for remote image";
        return Paths.get("");
    }

    @NonNull
    @Override
    public List<IdDisplay> getTags() {
        return myTags;
    }

    @com.android.annotations.Nullable
    @Override
    public IdDisplay getAddonVendor() {
        return myVendor;
    }

    @NonNull
    @Override
    public String getPrimaryAbiType() {
        return myAbis.get(0);
    }

    @NonNull
    @Override
    public List<String> getAbiTypes() {
        return myAbis;
    }

    @NonNull
    @Override
    public List<String> getTranslatedAbiTypes() {
        return myTranslatedAbis;
    }

    @NonNull
    @Override
    public List<Path> getSkins() {
        return ImmutableList.of();
    }

    @NonNull
    @Override
    public Revision getRevision() {
        return myRemotePackage.getVersion();
    }

    @NonNull
    @Override
    public AndroidVersion getAndroidVersion() {
        return myAndroidVersion;
    }

    @NonNull
    @Override
    public RepoPackage getPackage() {
        return myRemotePackage;
    }

    @Override
    public boolean obsolete() {
        return myRemotePackage.obsolete();
    }

    @Override
    public int compareTo(@NonNull ISystemImage o) {
        if (o instanceof RemoteSystemImage) {
            return myRemotePackage.compareTo(((RemoteSystemImage) o).myRemotePackage);
        }
        return 1;
    }

    @Override
    public int hashCode() {
        return myRemotePackage.hashCode();
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof RemoteSystemImage)) {
            return false;
        }
        RemoteSystemImage other = (RemoteSystemImage) o;
        return myRemotePackage.equals(other.myRemotePackage);
    }
}
