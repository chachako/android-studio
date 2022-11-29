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

class NamedEvent extends Event {

    @NonNull private final String name;

    private final long time_ns;

    NamedEvent(@NonNull String name, long time_ns, int line) {
        super(line);
        this.name = name;
        this.time_ns = time_ns;
    }

    @NonNull
    String name() {
        return name;
    }

    long time_ns() {
        return time_ns;
    }
}
