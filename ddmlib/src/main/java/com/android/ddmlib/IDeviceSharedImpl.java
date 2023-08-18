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
import static com.android.ddmlib.IDevice.PROP_DEVICE_MANUFACTURER;
import static com.android.ddmlib.IDevice.PROP_DEVICE_MODEL;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.sdklib.AndroidVersion;
import com.android.sdklib.AndroidVersionUtil;
import com.google.common.collect.Lists;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
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
    private String mName;

    /** Flag indicating whether the device has the screen recorder binary. */
    private Boolean mHasScreenRecorder;

    private static final long LS_TIMEOUT_SEC = 2;
    private static final char DEVICE_NAME_SEPARATOR = '-';

    /** Path to the screen recorder binary on the device. */
    private static final String SCREEN_RECORDER_DEVICE_PATH = "/system/bin/screenrecord";

    private static final String LOG_TAG = "Device";

    public static final long INSTALL_TIMEOUT_MINUTES;

    static {
        String installTimeout = System.getenv("ADB_INSTALL_TIMEOUT");
        long time = 4;
        if (installTimeout != null) {
            try {
                time = Long.parseLong(installTimeout);
            } catch (NumberFormatException e) {
                // use default value
            }
        }
        INSTALL_TIMEOUT_MINUTES = time;
    }

    public IDeviceSharedImpl(IDevice iDevice) {
        this.iDevice = iDevice;
    }

    @NonNull
    public String getName() {
        if (mName != null) {
            return mName;
        }

        if (iDevice.isOnline()) {
            // cache name only if device is online
            mName = constructName();
            return mName;
        } else {
            return constructName();
        }
    }

    @NonNull
    private String constructName() {
        if (iDevice.isEmulator()) {
            String avdName = iDevice.getAvdName();
            if (avdName != null) {
                return String.format("%s [%s]", avdName, iDevice.getSerialNumber());
            } else {
                return iDevice.getSerialNumber();
            }
        } else {
            String manufacturer = null;
            String model = null;

            try {
                manufacturer =
                        cleanupStringForDisplay(iDevice.getProperty(PROP_DEVICE_MANUFACTURER));
                model = cleanupStringForDisplay(iDevice.getProperty(PROP_DEVICE_MODEL));
            } catch (Exception e) {
                // If there are exceptions thrown while attempting to get these properties,
                // we can just use the serial number, so ignore these exceptions.
            }

            StringBuilder sb = new StringBuilder(20);

            if (manufacturer != null) {
                if (model == null
                        || !model.toUpperCase(Locale.US)
                                .startsWith(manufacturer.toUpperCase(Locale.US))) {
                    sb.append(manufacturer);
                    sb.append(DEVICE_NAME_SEPARATOR);
                }
            }

            if (model != null) {
                sb.append(model);
                sb.append(DEVICE_NAME_SEPARATOR);
            }

            sb.append(iDevice.getSerialNumber());
            return sb.toString();
        }
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

    public int getDensity() {
        String densityValue = iDevice.getProperty(IDevice.PROP_DEVICE_DENSITY);
        if (densityValue == null) {
            densityValue = iDevice.getProperty(IDevice.PROP_DEVICE_EMULATOR_DENSITY);
        }
        if (densityValue != null) {
            try {
                return Integer.parseInt(densityValue);
            } catch (NumberFormatException e) {
                return -1;
            }
        }

        return -1;
    }

    @NonNull
    public List<String> getAbis() {
        /* Try abiList (implemented in L onwards) otherwise fall back to abi and abi2. */
        String abiList = iDevice.getProperty(IDevice.PROP_DEVICE_CPU_ABI_LIST);
        if (abiList != null) {
            return Lists.newArrayList(abiList.split(","));
        } else {
            List<String> abis = Lists.newArrayListWithExpectedSize(2);
            String abi = iDevice.getProperty(IDevice.PROP_DEVICE_CPU_ABI);
            if (abi != null) {
                abis.add(abi);
            }

            abi = iDevice.getProperty(IDevice.PROP_DEVICE_CPU_ABI2);
            if (abi != null) {
                abis.add(abi);
            }

            return abis;
        }
    }

    @NonNull
    public Map<String, ServiceInfo> services() {

        CountDownLatch latch = new CountDownLatch(1);
        ServiceReceiver receiver = new ServiceReceiver();
        try {
            iDevice.executeShellCommand("service list", receiver);
        } catch (Exception e) {
            Log.e(LOG_TAG, new RuntimeException("Error obtaining services: ", e));
            return new HashMap<>();
        }

        try {
            latch.await(LS_TIMEOUT_SEC, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Log.e(
                    LOG_TAG,
                    new RuntimeException("Error obtaining services caused by interruption ", e));
            return new HashMap<>();
        }

        return receiver.getRunningServices();
    }

    public void forceStop(String applicationName) {
        try {
            // Force stop the app, even in case it's in the crashed state.
            iDevice.executeShellCommand(
                    "am force-stop " + applicationName, new NullOutputReceiver());
        } catch (IOException
                | TimeoutException
                | AdbCommandRejectedException
                | ShellCommandUnresponsiveException ignored) {
        }
    }

    public void kill(String applicationName) {
        try {
            // Kills the app, even in case it's in the crashed state.
            iDevice.executeShellCommand("am kill " + applicationName, new NullOutputReceiver());
        } catch (IOException
                | TimeoutException
                | AdbCommandRejectedException
                | ShellCommandUnresponsiveException ignored) {
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

    @Nullable
    private static String cleanupStringForDisplay(String s) {
        if (s == null) {
            return null;
        }

        StringBuilder sb = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);

            if (Character.isLetterOrDigit(c)) {
                sb.append(Character.toLowerCase(c));
            } else {
                sb.append('_');
            }
        }

        return sb.toString();
    }
}
