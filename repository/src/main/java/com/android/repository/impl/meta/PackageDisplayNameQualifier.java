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
package com.android.repository.impl.meta;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;

public interface PackageDisplayNameQualifier {
    /**
     * Returns a non-empty string to be appended to the package name, or null to not append
     * anything.
     */
    @Nullable
    default String getPackageDisplayNameQualifier() {
        return null;
    }

    /** Returns the term used to identify package version in UI. */
    @NonNull
    default String getVersionTerm() {
        return "version";
    }
}
