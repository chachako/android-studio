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
package com.android.ddmlib;

import static com.android.ddmlib.IDevice.PROP_BUILD_API_LEVEL;

import com.android.annotations.NonNull;
import com.android.sdklib.AndroidVersion;
import com.android.sdklib.AndroidVersionUtil;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * This is a helper class that should be used only by `DeviceImpl` and `AdblibIDeviceWrapper` for
 * the purposes of migrating from ddmlib to adblib.
 */
public class IDeviceSharedImpl {

    private final IDevice iDevice;
    private AndroidVersion mVersion;

    /** Flag indicating whether the device has the screen recorder binary. */
    private Boolean mHasScreenRecorder;

    private static final long LS_TIMEOUT_SEC = 2;

    /** Path to the screen recorder binary on the device. */
    private static final String SCREEN_RECORDER_DEVICE_PATH = "/system/bin/screenrecord";

    public IDeviceSharedImpl(IDevice iDevice) {
        this.iDevice = iDevice;
    }

    @NonNull
    public AndroidVersion getVersion() {
        if (mVersion == null) {
            // Try to fetch all properties with a reasonable timeout
            String buildApi = iDevice.getProperty(PROP_BUILD_API_LEVEL);
            if (buildApi == null) {
                // Properties are not available yet, return default value
                return AndroidVersion.DEFAULT;
            }
            Map<String, String> properties = iDevice.getProperties();
            mVersion = AndroidVersionUtil.androidVersionFromDeviceProperties(properties);
            if (mVersion == null) {
                mVersion = AndroidVersion.DEFAULT;
            }
        }
        return mVersion;
    }

    public boolean supportsFeature(@NonNull IDevice.HardwareFeature feature) {
        try {
            return iDevice.getHardwareCharacteristics().contains(feature.getCharacteristic());
        } catch (Exception e) {
            return false;
        }
    }

    public boolean supportsFeature(@NonNull IDevice.Feature feature, Set<String> adbFeatures) {
        switch (feature) {
            case SCREEN_RECORD:
                if (supportsFeature(IDevice.HardwareFeature.WATCH)
                        && !getVersion().isGreaterOrEqualThan(30)) {
                    // physical watches before API 30, do not support screen recording.
                    return false;
                }
                if (!getVersion().isGreaterOrEqualThan(19)) {
                    return false;
                }
                if (mHasScreenRecorder == null) {
                    mHasScreenRecorder = hasBinary(SCREEN_RECORDER_DEVICE_PATH);
                }
                return mHasScreenRecorder;
            case PROCSTATS:
                return getVersion().isGreaterOrEqualThan(19);
            case ABB_EXEC:
                return adbFeatures.contains("abb_exec");
            case REAL_PKG_NAME:
                return getVersion().compareTo(AndroidVersion.VersionCodes.Q, "R") >= 0;
            case SKIP_VERIFICATION:
                if (getVersion().compareTo(AndroidVersion.VersionCodes.R, null) >= 0) {
                    return true;
                } else if (getVersion().compareTo(AndroidVersion.VersionCodes.Q, "R") >= 0) {
                    String sdkVersionString = iDevice.getProperty("ro.build.version.preview_sdk");
                    if (sdkVersionString != null) {
                        try {
                            // Only supported on R DP2+.
                            return Integer.parseInt(sdkVersionString) > 1;
                        } catch (NumberFormatException e) {
                            // do nothing and fall through
                        }
                    }
                }
                return false;
            case SHELL_V2:
                return adbFeatures.contains("shell_v2");
            default:
                return false;
        }
    }

    private boolean hasBinary(String path) {
        CountDownLatch latch = new CountDownLatch(1);
        CollectingOutputReceiver receiver = new CollectingOutputReceiver(latch);
        try {
            iDevice.executeShellCommand("ls " + path, receiver, LS_TIMEOUT_SEC, TimeUnit.SECONDS);
        } catch (Exception e) {
            return false;
        }

        try {
            latch.await(LS_TIMEOUT_SEC, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            return false;
        }

        String value = receiver.getOutput().trim();
        return !value.endsWith("No such file or directory");
    }
}
