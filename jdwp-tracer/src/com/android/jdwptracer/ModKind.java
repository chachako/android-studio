/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.jdwptracer;

import com.android.annotations.NonNull;
import java.util.HashMap;
import java.util.Map;

enum ModKind {
    COUNT(1),
    CONDITIONAL(2),
    THREAD_ONLY(3),
    CLASS_ONLY(4),
    CLASS_MATCH(5),
    CLASS_EXCLUDE(6),
    LOCATION_ONLY(7),
    EXCEPTION_ONLY(8),
    FIELD_ONLY(9),
    STEP(10),
    INSTANCE_ONLY(11),
    SOURCE_NAME_MATCH(12);

    private int id;

    ModKind(int id) {
        this.id = id;
    }

    static Map<Integer, ModKind> idToEnum = new HashMap<>();

    static {
        for (ModKind moKind : ModKind.values()) {
            idToEnum.put(moKind.id, moKind);
        }
    }

    @NonNull
    static ModKind fromID(int id) {
        if (!idToEnum.containsKey(id)) {
            throw new IllegalStateException("No ModKind with id=" + id);
        }
        return idToEnum.get(id);
    }
}
