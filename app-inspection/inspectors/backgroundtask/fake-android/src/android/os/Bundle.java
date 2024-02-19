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

package android.os;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class Bundle {
    private final Map<String, Object> map = new HashMap<>();

    private final String mStr;

    public Bundle() {
        this("");
    }

    public Bundle(String str) {
        mStr = str;
    }

    @Override
    public String toString() {
        return mStr;
    }

    public Set<String> keySet() {
        return map.keySet();
    }

    /**
     * @deprecated This method is deprecated in Android. There is public alternative for getting a
     *     value without knowing its type.
     */
    @SuppressWarnings("DeprecatedIsStillUsed")
    @Deprecated
    public Object get(String key) {
        return map.get(key);
    }

    /** This is not an Android Bundle method. It's just for convenience in tests. */
    public Bundle put(String key, Object value) {
        map.put(key, value);
        return this;
    }
}
