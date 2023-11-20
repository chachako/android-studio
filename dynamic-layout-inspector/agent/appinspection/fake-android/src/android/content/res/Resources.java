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

package android.content.res;

import androidx.annotation.NonNull;
import androidx.annotation.VisibleForTesting;
import java.util.Map;

public final class Resources {

    /** The {@code null} resource ID. This denotes an invalid resource ID */
    public static final int ID_NULL = 0;

    /**
     * The package of a resource ID.
     *
     * <ul>
     *   <li>0x10 for the platform
     *   <li>0x7F for applications
     *   <li>other values are allocated at build time for dynamic and shared libraries
     * </ul>
     */
    public static final int ID_PACKAGE_MASK = 0xff000000;

    /** The type of a resource ID. */
    public static final int ID_TYPE_MASK = 0x00ff0000;

    public static final class NotFoundException extends RuntimeException {
        public NotFoundException(@NonNull String message) {
            super(message);
        }
    }

    private final Map<Integer, String> mResourceNames;
    private final Configuration mConfiguration;

    /** @param resourceNames name format: "namespace.type/entry", e.g. "android.id/next_button" */
    @VisibleForTesting
    public Resources(Map<Integer, String> resourceNames) {
        mResourceNames = resourceNames;
        mConfiguration = new Configuration();
    }

    public String getResourceName(int resourceId) throws NotFoundException {
        check(resourceId);
        if (!mResourceNames.containsKey(resourceId)) {
            throw new NotFoundException("Resource not found: " + resourceId);
        }
        return mResourceNames.get(resourceId);
    }

    public String getResourceTypeName(int resourceId) throws NotFoundException {
        String name = getResourceName(resourceId);
        String typeName = name.substring(name.indexOf('.'));
        typeName = typeName.substring(0, name.indexOf('/'));
        return typeName;
    }

    public String getResourcePackageName(int resourceId) throws NotFoundException {
        String name = getResourceName(resourceId);
        return name.substring(0, name.lastIndexOf('.'));
    }

    public String getResourceEntryName(int resourceId) throws NotFoundException {
        String name = getResourceName(resourceId);
        return name.substring(name.indexOf('/'));
    }

    public Configuration getConfiguration() {
        return mConfiguration;
    }

    private static void check(int resourceId) {
        // A test should fail if our production code is asking for resources with an invalid
        // resourceId. This check is similar to the function is_valid_resid in ResourceUtils.h
        // of the framework.
        if ((resourceId & 0x00ff0000) != 0
                && (resourceId & 0xff000000) != 0
                && (resourceId & 0xff000000) != 0xff000000) { // 0xff is an invalid package name
            return;
        }
        throw new RuntimeException("Invalid resource ID: " + Integer.toHexString(resourceId));
    }
}
