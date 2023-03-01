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
package com.android.tools.deploy.liveedit;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Support class to invoke Compose API functions. */
public class ComposeSupport {
    public static final String KEY_META_CLASS_NAME =
            "androidx.compose.runtime.internal.FunctionKeyMetaClass";
    public static final String KEY_META_CONTAINER_NAME =
            "androidx.compose.runtime.internal.FunctionKeyMeta$Container";
    public static final String KEY_META_NAME = "androidx.compose.runtime.internal.FunctionKeyMeta";

    private static final Map<RiskyChange, Set<String>> riskyChanges = new HashMap<>();

    public static void addRiskyChange(String className, RiskyChange type) {
        riskyChanges.computeIfAbsent(type, r -> new HashSet<>()).add(className);
    }

    // Return empty string if success. Otherwise, an error message is returned.
    public static String recomposeFunction(Object reloader, int[] groupIds) {
        Method invalidateGroupsWithKey = null;
        try {
            invalidateGroupsWithKey =
                    reloader.getClass().getMethod("invalidateGroupsWithKey", int.class);
        } catch (NoSuchMethodException ignored) {
            // TODO: Figure out why some builds has this runtime_release suffix.
            try {
                invalidateGroupsWithKey =
                        reloader.getClass()
                                .getMethod("invalidateGroupsWithKey$runtime_release", int.class);
            } catch (NoSuchMethodException e) {
                e.printStackTrace();
                return e.getMessage(); // Very unlikely.
            }
        }

        try {
            for (int groupId : groupIds) {
                invalidateGroupsWithKey.invoke(reloader, groupId);
            }
        } catch (IllegalAccessException | InvocationTargetException e) {
            e.printStackTrace();
            return e.getMessage(); // Very unlikely.
        }

        return "";
    }

    public static class LiveEditRecomposeError {
        public final String name;
        public final boolean recoverable;
        public final String cause;

        private LiveEditRecomposeError(boolean recoverable, Exception cause) {
            this.recoverable = recoverable;
            this.cause = cause.toString();
            this.name = cause.getClass().getName();
        }

        private LiveEditRecomposeError(boolean recoverable, Exception cause, String message) {
            this.recoverable = recoverable;
            this.cause = message;
            this.name = cause.getClass().getName();
        }
    }

    public static LiveEditRecomposeError[] fetchPendingErrors(Object reloader) {
        Method getCurrentErrors = null;
        try {
            getCurrentErrors = reloader.getClass().getMethod("getCurrentErrors");
        } catch (NoSuchMethodException ignored) {
            // The most recent builds of compose started to mangle '$runtime_release' into
            // certain API calls.
            try {
                getCurrentErrors =
                        reloader.getClass().getMethod("getCurrentErrors$runtime_release");
            } catch (NoSuchMethodException e) {
                // This is most likely due to the fact that the current runtime does not
                // support error retrieval. All public release of Compose runtime
                // will show a white screen should unhandled exceptions occurred during
                // recomposition.
                return null;
            }
        }

        List errors = null;
        try {
            errors = (List) getCurrentErrors.invoke(reloader);
        } catch (IllegalAccessException | InvocationTargetException e) {
            e.printStackTrace();
            return null;
        }

        LiveEditRecomposeError[] result = new LiveEditRecomposeError[errors.size()];
        int index = 0;
        try {
            for (Object errorObject : errors) {
                Method getRecoverable = errorObject.getClass().getDeclaredMethod("getRecoverable");
                boolean recoverable = ((Boolean) getRecoverable.invoke(errorObject)).booleanValue();

                Method getCause = errorObject.getClass().getDeclaredMethod("getCause");
                Exception cause = (Exception) getCause.invoke(errorObject);
                cause.printStackTrace();

                if (cause instanceof ProxyMissingFieldException) {
                    ProxyMissingFieldException pmfe = (ProxyMissingFieldException) cause;
                    Set<String> riskyClasses =
                            riskyChanges.getOrDefault(
                                    RiskyChange.FIELD_CHANGE, Collections.emptySet());
                    if (riskyClasses.contains(pmfe.getClassName())) {
                        result[index] =
                                new LiveEditRecomposeError(
                                        recoverable,
                                        cause,
                                        "Recomposition error, possibly due to lambda capture change");
                    }
                } else if (cause instanceof ProxyMissingMethodException) {
                    ProxyMissingMethodException pmme = (ProxyMissingMethodException) cause;
                    Set<String> riskyClasses =
                            riskyChanges.getOrDefault(
                                    RiskyChange.INTERFACE_CHANGE, Collections.emptySet());
                    if (riskyClasses.contains(pmme.getClassName())) {
                        result[index] =
                                new LiveEditRecomposeError(
                                        recoverable,
                                        cause,
                                        "Recomposition error, possibly due to lambda interface change");
                    }
                } else {
                    result[index] = new LiveEditRecomposeError(recoverable, cause);
                }
            }
        } catch (NoSuchMethodException | InvocationTargetException | IllegalAccessException e) {
            e.printStackTrace();
            return null;
        }

        Method clearErrors = null;
        try {
            clearErrors = reloader.getClass().getMethod("clearErrors");
        } catch (NoSuchMethodException ignored) {
            // The most recent builds of compose started to mangle '$runtime_release' into
            // certain API calls.
            try {
                clearErrors = reloader.getClass().getMethod("clearErrors$runtime_release");
            } catch (NoSuchMethodException e) {
                e.printStackTrace();
                return null;
            }
        }

        try {
            clearErrors.invoke(reloader);
        } catch (IllegalAccessException | InvocationTargetException e) {
            e.printStackTrace();
        }

        return result;
    }

    /**
     * Given a Compose Runtime Version number, check if the current runtime is equal or newer.
     *
     * @return Empty String if version check passes, otherwise error message.
     */
    public static String versionCheck(Object reloader, int expected) {
        ClassLoader cl = reloader.getClass().getClassLoader();
        Class composeVersionClass = null;
        int current = -1;

        try {
            composeVersionClass =
                    Class.forName("androidx.compose.runtime.ComposeVersion", true, cl);
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
            return "ClassNotFoundException: androidx.compose.runtime.ComposeVersion";
        }

        try {
            current = composeVersionClass.getField("version").getInt(null);
        } catch (NoSuchFieldException e) {
            e.printStackTrace();
            return "NoSuchFieldException: androidx.compose.runtime.ComposeVersion.version";
        } catch (IllegalAccessException e) {
            e.printStackTrace();
            return "IllegalAccessException: androidx.compose.runtime.ComposeVersion.version";
        }

        if (current < expected) {
            return "Current version number: " + current;
        }

        return "";
    }
}
