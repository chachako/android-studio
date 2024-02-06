/*
 * Copyright (C) 2020 The Android Open Source Project
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

package com.android.tools.checker;

public class TestUtils {

    private static final String JAVA_REFLECT_PACKAGE =
            Runtime.version().feature() >= 11 ? "jdk.internal.reflect" : "sun.reflect";
    public static final String REFLECT_INVOKE_METHOD =
            Runtime.version().feature() >= 21 ? "invoke" : "invoke0";
    public static final String REFLECT_METHOD_ACCESSOR_CLASS =
            Runtime.version().feature() >= 21
                    ? "DirectMethodHandleAccessor"
                    : "NativeMethodAccessorImpl";
    public static final String REFLECT_METHOD_ACCESSOR_CLASS_FQN =
            String.format("%s.%s", JAVA_REFLECT_PACKAGE, REFLECT_METHOD_ACCESSOR_CLASS);
    public static final String REFLECT_INVOKE_METHOD_FQN =
            String.format("%s.%s", REFLECT_METHOD_ACCESSOR_CLASS_FQN, REFLECT_INVOKE_METHOD);
}
