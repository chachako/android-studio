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

package com.android.tools.instrumentation.threading.agent.callback;

import java.util.concurrent.Callable;
import java.util.function.Supplier;

public class ThreadingCheckerUtil {
    /** Executes code that doesn't return a value while disabling the threading violation checks. */
    public static void withChecksDisabledForRunnable(Runnable runnable) {
        ThreadingCheckerTrampoline.withChecksDisabledForSupplier(
                () -> {
                    runnable.run();
                    return null;
                });
    }

    /**
     * Executes code that returns a value while disabling the threading violation checks.
     *
     * <p>Please, use this method if you need to execute code that doesn't throw checked exceptions.
     */
    public static <T> T withChecksDisabledForSupplier(Supplier<T> supplier) {
        return ThreadingCheckerTrampoline.withChecksDisabledForSupplier(supplier);
    }

    /**
     * Executes code that returns a value while disabling the threading violation checks.
     *
     * <p>Please, use this method if you need to execute code that throws a checked exceptions.
     */
    public static <T> T withChecksDisabledForCallable(Callable<T> callable) throws Exception {
        return ThreadingCheckerTrampoline.withChecksDisabledForCallable(callable);
    }

    private ThreadingCheckerUtil() {}
}
