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

package com.android.ddmlib.internal;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.android.ddmlib.IDevice;
import com.android.ddmlib.IShellOutputReceiver;
import com.android.ddmlib.RemoteSplitApkInstaller;
import com.android.ddmlib.ScreenRecorderOptions;
import com.android.ddmlib.SplitApkInstaller;
import com.android.sdklib.AndroidVersion;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import junit.framework.TestCase;

public class DeviceTest extends TestCase {

    public void testScreenRecorderOptions() {
        ScreenRecorderOptions options =
                new ScreenRecorderOptions.Builder().setBitRate(6).setSize(600, 400).build();
        assertEquals(
                "screenrecord --size 600x400 --bit-rate 6000000 /sdcard/1.mp4",
                DeviceImpl.getScreenRecorderCommand("/sdcard/1.mp4", options));

        options = new ScreenRecorderOptions.Builder().setTimeLimit(100, TimeUnit.SECONDS).build();
        assertEquals(
                "screenrecord --time-limit 100 /sdcard/1.mp4",
                DeviceImpl.getScreenRecorderCommand("/sdcard/1.mp4", options));

        options = new ScreenRecorderOptions.Builder().setTimeLimit(4, TimeUnit.MINUTES).build();
        assertEquals(
                "screenrecord --time-limit 180 /sdcard/1.mp4",
                DeviceImpl.getScreenRecorderCommand("/sdcard/1.mp4", options));
    }

    public void testInstallPackages() throws Exception {
        IDevice mMockDevice = createMockDevice2();
        List<File> apks = new ArrayList<File>();
        for (int i = 0; i < 3; i++) {
            File apkFile = File.createTempFile("test", ".apk");
            apks.add(apkFile);
        }
        List<String> installOptions = new ArrayList<String>();
        installOptions.add("-d");
        mMockDevice.installPackages(apks, true, installOptions);
        mMockDevice.installPackages(apks, true, installOptions, 1000L, TimeUnit.MINUTES);
        when(mMockDevice.getVersion())
                .thenReturn(
                        new AndroidVersion(
                                AndroidVersion.ALLOW_SPLIT_APK_INSTALLATION.getApiLevel()));
        when(mMockDevice.supportsFeature(IDevice.Feature.ABB_EXEC)).thenReturn(true);
        SplitApkInstaller.create(mMockDevice, apks, true, installOptions);
        for (File apkFile : apks) {
            apkFile.delete();
        }
        verify(mMockDevice).getVersion();
        verify(mMockDevice).supportsFeature(IDevice.Feature.ABB_EXEC);
    }

    public void testInstallRemotePackages() throws Exception {
        IDevice mMockDevice = createMockDevice2();
        List<String> remoteApkPaths = new ArrayList<String>();
        remoteApkPaths.add("/data/local/tmp/foo.apk");
        remoteApkPaths.add("/data/local/tmp/foo.dm");
        List<String> installOptions = new ArrayList<String>();
        installOptions.add("-d");
        mMockDevice.installRemotePackages(remoteApkPaths, true, installOptions);
        mMockDevice.installRemotePackages(
                remoteApkPaths, true, installOptions, 1000L, TimeUnit.MINUTES);
        when(mMockDevice.getVersion())
                .thenReturn(
                        new AndroidVersion(
                                AndroidVersion.ALLOW_SPLIT_APK_INSTALLATION.getApiLevel()));
        when(mMockDevice.supportsFeature(IDevice.Feature.ABB_EXEC)).thenReturn(true);
        RemoteSplitApkInstaller.create(mMockDevice, remoteApkPaths, true, installOptions);
        verify(mMockDevice).getVersion();
        verify(mMockDevice).supportsFeature(IDevice.Feature.ABB_EXEC);
    }

    public static void injectShellResponse2(
            IDevice mockDevice, int delayMillis, final Object... response) throws Exception {
        AtomicInteger count = new AtomicInteger(0);
        doAnswer(
                        (invocation) -> {
                            // insert small delay to simulate latency
                            Thread.sleep(delayMillis);
                            IShellOutputReceiver receiver = invocation.getArgument(1);
                            Object inputData =
                                    response[
                                            count.getAndUpdate(
                                                    (current) ->
                                                            Math.min(
                                                                    current + 1,
                                                                    response.length - 1))];
                            if (inputData instanceof String) {
                                byte[] bytes = ((String) inputData).getBytes();
                                receiver.addOutput(bytes, 0, bytes.length);
                                receiver.flush();
                            } else if (inputData instanceof Exception) {
                                throw (Exception) inputData;
                            }
                            return null;
                        })
                .when(mockDevice)
                .executeShellCommand(any(), any(), anyLong(), any());
    }

    /** Helper method that creates a mock device. */
    public static IDevice createMockDevice2() {
        IDevice mockDevice = mock(IDevice.class);
        when(mockDevice.getSerialNumber()).thenReturn("serial");
        when(mockDevice.isOnline()).thenReturn(Boolean.TRUE);
        return mockDevice;
    }
}
