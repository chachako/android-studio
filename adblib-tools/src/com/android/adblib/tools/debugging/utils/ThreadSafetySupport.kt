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
package com.android.adblib.tools.debugging.utils

/**
 * A class implements the [ThreadSafetySupport] to expose its thread-safety characteristics
 * at run-time.
 */
internal interface ThreadSafetySupport {

    /**
     * Returns whether this instance is thread-safe and immutable, meaning it can be
     * safely shared across threads and coroutines, without any observable change in
     * state and behavior.
     */
    val isThreadSafeAndImmutable: Boolean
}
