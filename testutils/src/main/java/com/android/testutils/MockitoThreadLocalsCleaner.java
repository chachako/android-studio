// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.android.testutils;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Set;
import org.mockito.internal.progress.ThreadSafeMockingProgress;

public class MockitoThreadLocalsCleaner {

    public void setup() {
        // Not needed currently.
    }

    public void cleanupAndTearDown() throws Exception {
        resetWellKnownThreadLocals();
    }

    protected void resetWellKnownThreadLocals()
            throws NoSuchFieldException, IllegalAccessException, NoSuchMethodException, InvocationTargetException {
        Field provider = ThreadSafeMockingProgress.class.getDeclaredField("MOCKING_PROGRESS_PROVIDER");
        provider.setAccessible(true);
        ThreadLocal<?> key = (ThreadLocal<?>)provider.get(ThreadSafeMockingProgress.class);
        Field threadLocalsField = Thread.class.getDeclaredField("threadLocals");
        threadLocalsField.setAccessible(true);
        Method remove = threadLocalsField.getType().getDeclaredMethod("remove", ThreadLocal.class);
        remove.setAccessible(true);
        Set<Thread> threads = Thread.getAllStackTraces().keySet();
        for (Thread thread : threads) {
            Object o = threadLocalsField.get(thread);
            if (o != null) {
                remove.invoke(o, key);
            }
        }
    }
}
