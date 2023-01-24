/*
 * Copyright (C) 2017 The Android Open Source Project
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

package com.android.fakeadbserver;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import java.net.Socket;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

public class ClientState {

    private final int mPid;

    private final int mUid;

    @NonNull private final String mProcessName;

    @NonNull
    private final String mPackageName;

    // Whether this client is waiting for a debugger connection or not
    private boolean mWaiting;

    private final ClientViewsState mViewsState = new ClientViewsState();

    /**
     * Set of DDMS features for this process.
     *
     * <p>See <a
     * href="https://cs.android.com/android/platform/superproject/+/android13-release:frameworks/base/core/java/android/ddm/DdmHandleHello.java;l=107">HandleFEAT
     * source code</a>
     */
    private final Set<String> mFeatures = new HashSet<>();

    /**
     * See <a
     * href="https://cs.android.com/android/platform/superproject/+/android13-release:art/runtime/native/dalvik_system_VMDebug.cc;l=56">List
     * of VM features</a>
     */
    private static final String[] mBuiltinVMFeatures = {
        "method-trace-profiling",
        "method-trace-profiling-streaming",
        "method-sample-profiling",
        "hprof-heap-dump",
        "hprof-heap-dump-streaming"
    };

    /**
     * See <a
     * href="https://cs.android.com/android/platform/superproject/+/android13-release:frameworks/base/core/java/android/ddm/DdmHandleHello.java;drc=4794e479f4b485be2680e83993e3cf93f0f42d03;l=44">Framework
     * features</a>
     */
    private static final String[] mBuiltinFrameworkFeatures = {
        "opengl-tracing", "view-hierarchy",
    };

    @Nullable private Socket jdwpSocket;

    private final AtomicInteger hgpcRequestsCount = new AtomicInteger();

    ClientState(
            int pid,
            int uid,
            @NonNull String processName,
            @NonNull String packageName,
            boolean isWaiting) {
        mPid = pid;
        mUid = uid;
        mProcessName = processName;
        mPackageName = packageName;
        mWaiting = isWaiting;
        mFeatures.addAll(Arrays.asList(mBuiltinVMFeatures));
        mFeatures.addAll(Arrays.asList(mBuiltinFrameworkFeatures));
    }

    public int getPid() {
        return mPid;
    }

    public int getUid() {
        return mUid;
    }

    @NonNull
    public String getProcessName() {
        return mProcessName;
    }

    @NonNull
    public String getPackageName() {
        return mPackageName;
    }

    public boolean getIsWaiting() {
        return mWaiting;
    }

    @NonNull
    public ClientViewsState getViewsState() {
        return mViewsState;
    }

    public synchronized boolean startJdwpSession(@NonNull Socket socket) {
        if (this.jdwpSocket != null) {
            return false;
        }
        this.jdwpSocket = socket;
        return true;
    }

    public synchronized void stopJdwpSession() {
        if (this.jdwpSocket != null) {
            try {
                this.jdwpSocket.shutdownOutput();
                Thread.sleep(10); // So that FIN is received by peer
                this.jdwpSocket.close();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
        this.jdwpSocket = null;
    }

    public synchronized void clearFeatures() {
        mFeatures.clear();
    }

    public synchronized void addFeature(@NonNull String value) {
        mFeatures.add(value);
    }

    public synchronized void removeFeature(@NonNull String value) {
        mFeatures.remove(value);
    }

    @NonNull
    public synchronized Set<String> getFeatures() {
        return new HashSet<>(mFeatures);
    }

    public void requestHgpc() {
        hgpcRequestsCount.incrementAndGet();
    }

    public int getHgpcRequestsCount() {
        return hgpcRequestsCount.get();
    }
}
