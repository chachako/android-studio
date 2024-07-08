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
package com.android.tools.leakcanarylib.data

enum class GcRootType(val description: String) {
    JNI_GLOBAL("Global variable in native code"),
    JNI_LOCAL("Local variable in native code"),
    JAVA_FRAME("Java local variable"),
    NATIVE_STACK("Input or output parameters in native code"),
    STICKY_CLASS("System class"),
    THREAD_BLOCK("Thread block"),
    MONITOR_USED(
        "Monitor (anything that called the wait() or notify() methods, or that is synchronized.)"
    ),
    THREAD_OBJECT("Thread object"),
    JNI_MONITOR("Root JNI monitor");

    companion object {
        fun fromDescription(description: String): GcRootType {
            return entries.find { it.description == description }
                ?: throw IllegalArgumentException("Invalid GC root type description: $description")
        }
    }
}
