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
package com.android.tools.instrumentation.threading.agent;

import static com.android.tools.instrumentation.threading.agent.callback.ThreadingCheckerUtil.withChecksDisabledForCallable;
import static com.android.tools.instrumentation.threading.agent.callback.ThreadingCheckerUtil.withChecksDisabledForRunnable;
import static com.android.tools.instrumentation.threading.agent.callback.ThreadingCheckerUtil.withChecksDisabledForSupplier;

import com.android.annotations.concurrency.AnyThread;
import com.android.annotations.concurrency.Slow;
import com.android.annotations.concurrency.UiThread;
import com.android.annotations.concurrency.WorkerThread;
import java.io.IOException;

@SuppressWarnings("unused") // This class is loaded dynamically
public class SampleClasses {

    /** Sample annotated class with a few method-level annotations. */
    public static class ClassWithAnnotatedMethods {

        @AnyThread
        public void anyThreadMethod1() {
            // Do nothing
        }

        @Slow
        public void slowThreadMethod1() {
            // Do nothing
        }

        @UiThread
        public void uiMethod1() {
            // Do nothing
        }

        @WorkerThread
        public void workerMethod1() {
            // Do nothing
        }

        @UiThread
        private void privateUiMethod1() {
            // Do nothing
        }

        // Note that in practice we would never have both of these annotations present at the same
        // time.
        @UiThread
        @WorkerThread
        public void workerAndUiMethod1() {
            // Do nothing
        }

        public void nonAnnotatedMethod1() {
            // Do nothing
        }
    }

    public static class ClassWithAnnotatedConstructor {

        @UiThread
        public ClassWithAnnotatedConstructor() {
            // Do nothing
        }
    }

    @UiThread
    public static class ClassWithUiThreadAnnotation {

        public void nonAnnotatedMethod1() {
            // Do nothing
        }

        @UiThread
        public void uiMethod1() {
            // Do nothing
        }

        @WorkerThread
        public void workerMethod1() {
            // Do nothing
        }

        @AnyThread
        public void anyThreadMethod1() {
            // Do nothing
        }

        @Slow
        public void slowThreadMethod1() {
            // Do nothing
        }

        @Slow
        public void executeWithLambda() throws Exception {
            Runnable doSomething =
                    () -> {
                        /* Do nothing */
                    };
            Thread t = new Thread(doSomething);
            t.start();
            t.join();
        }
    }

    @UiThread
    public static class AnnotatedClassWithInnerClass {
        private int a = 5;

        public class InnerClass {
            public void method1() {
                ++a;
                a++;
                --a;
                a--;
            }

            public void method2() {}
        }
    }

    @UiThread
    public static class AnnotatedClassWithStaticBlock {
        public static int a;

        static {
            a = 5;
        }
    }

    public static class ClassWithDisabledViolationsCodeBlocks {

        public int methodWithDisabledThreadingChecks() {
            int result =
                    withChecksDisabledForSupplier(
                            () -> {
                                uiMethod1();
                                workerMethod1();
                                return 505;
                            });
            // Checks will happen for the following method
            uiMethod1();
            return result;
        }

        public void methodWithNestedDisabledThreadingChecks() {
            withChecksDisabledForSupplier(this::methodWithDisabledThreadingChecks);
            // Checks will happen for the following method
            workerMethod1();
        }

        public void methodWithDisabledThreadingCheckSpawningAnotherThread() {
            withChecksDisabledForRunnable(
                    () -> {
                        // uiMethod1 is called on a different thread and so it should not be
                        // excluded from the threading violation checks.
                        Thread thread = new Thread(this::uiMethod1);
                        thread.start();
                        try {
                            thread.join();
                        } catch (InterruptedException e) {
                            throw new RuntimeException(e);
                        }
                        workerMethod1();
                    });
        }

        public int methodWithDisabledThreadingChecksThatThrowsCheckedExceptions()
                throws IOException {
            try {
                int result =
                        withChecksDisabledForCallable(
                                () -> {
                                    uiMethodThrowingCheckedException();
                                    workerMethod1();
                                    return 1001;
                                });
                // Checks will happen for the following method
                workerMethod1();
                return result;
            } catch (Exception e) {
                if (e instanceof IOException) {
                    throw (IOException) e;
                } else {
                    throw new RuntimeException("Unexpected checked exception");
                }
            }
        }

        @UiThread
        public void uiMethod1() {
            // Do nothing
        }

        @UiThread
        public void uiMethodThrowingCheckedException() throws IOException {
            // Do nothing
        }

        @WorkerThread
        public void workerMethod1() {
            // Do nothing
        }
    }
}
