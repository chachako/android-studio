/*
 * Copyright (C) 2019 The Android Open Source Project
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
package androidx.compose.runtime;

import java.util.ArrayList;
import java.util.List;

// TODO: Should probably translate this to Kotlin instead.
/** Mock the Jet Pack Compose Runtime */
public class HotReloader {
    public static String state = "";
    public static Companion Companion = new Companion();

    public static class RecomposerErrorInfo {
        private final Exception cause;

        public RecomposerErrorInfo(Exception cause) {
            this.cause = cause;
        }

        public Exception getCause() {
            return cause;
        }

        public boolean getRecoverable() {
            return true;
        }
    }

    public static List<RecomposerErrorInfo> exceptions = new ArrayList<>();

    public static class Companion {
        public Object saveStateAndDispose(Object c) {
            state += " saveStateAndDispose()";
            return " loadStateAndCompose()";
        }

        public void loadStateAndCompose(Object c) {
            System.out.println("loadStateAndCompose");
            state += c;
        }

        public boolean invalidateGroupsWithKey(int key) {
            System.out.println("invalidateGroupsWithKey(" + key + ")");

            try {
                // The Compose runtime will know exactly which composable function(s) needs
                // to be called given an invalidation group key. In this mock, we are just
                // going to hard code some key to Composable calls.
                switch (key) {
                    case 1111:
                        pkg.LiveEditRecomposeKt.LiveEditRecompose();
                        break;
                    case 1112:
                        pkg.LiveEditRecomposeCrashKt.LiveEditRecomposeCrash();
                        break;
                }
            } catch (Exception e) {
                exceptions.add(new RecomposerErrorInfo(e));
            }

            return true;
        }

        public List<RecomposerErrorInfo> getCurrentErrors() {
            return exceptions;
        }

        public void clearErrors() {
            exceptions.clear();
        }
    }
}
