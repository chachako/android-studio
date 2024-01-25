/*
 * Copyright (C) 2018 The Android Open Source Project
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

package com.android.tools.deployer;

import com.android.adblib.AdbDeviceServices;
import com.android.adblib.AdbSession;
import com.android.adblib.DeviceSelector;
import com.android.adblib.tools.InstallerKt;
import com.android.adblib.tools.JavaBridge;
import com.android.annotations.NonNull;
import com.android.ddmlib.AdbCommandRejectedException;
import com.android.ddmlib.Client;
import com.android.ddmlib.IDevice;
import com.android.ddmlib.IShellOutputReceiver;
import com.android.ddmlib.InstallException;
import com.android.ddmlib.InstallMetrics;
import com.android.ddmlib.ShellCommandUnresponsiveException;
import com.android.ddmlib.SimpleConnectedSocket;
import com.android.ddmlib.SyncException;
import com.android.ddmlib.TimeoutException;
import com.android.sdklib.AndroidVersion;
import com.android.tools.deploy.proto.Deploy;
import com.android.tools.deployer.model.Apk;
import com.android.tools.deployer.model.App;
import com.android.tools.tracer.Trace;
import com.android.utils.ILogger;
import com.google.common.collect.ImmutableMap;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public class AdbClient {
    private static final Map<String, Deploy.Arch> ABI_MAP =
            ImmutableMap.of(
                    "arm64-v8a", Deploy.Arch.ARCH_64_BIT,
                    "armeabi-v7a", Deploy.Arch.ARCH_32_BIT,
                    "x86_64", Deploy.Arch.ARCH_64_BIT,
                    "x86", Deploy.Arch.ARCH_32_BIT);

    private final IDevice device;
    private final ILogger logger;

    private final Optional<AdbSession> adbSession;

    public AdbClient(IDevice device, ILogger logger) {
        this(device, logger, null);
    }

    public AdbClient(IDevice device, ILogger logger, AdbSession adbSession) {
        this.device = device;
        this.logger = logger;
        this.adbSession = Optional.ofNullable(adbSession);
    }

    public static class InstallResult {
        public final InstallStatus status;
        public final String reason;
        public final InstallMetrics metrics;

        InstallResult(InstallStatus status, String reason) {
            this.status = status;
            this.reason = reason;
            metrics = null;
        }

        InstallResult(InstallStatus status, String reason, InstallMetrics metrics) {
            this.status = status;
            this.reason = reason;
            this.metrics = metrics;
        }
    }

    public SimpleConnectedSocket rawExec(String executable, String[] parameters)
            throws AdbCommandRejectedException, IOException, TimeoutException {
        return device.rawExec2(executable, parameters);
    }

    /** Executes the given command with no stdin and returns stdout as a byte[] */
    public byte[] shell(String[] parameters, long timeOutmS) throws IOException {
        return shell(parameters, null, timeOutmS);
    }

    public byte[] shell(String[] parameters, InputStream input, long timeOutmS) throws IOException {
        return shell(parameters, input, timeOutmS, TimeUnit.MILLISECONDS);
    }

    /**
     * Executes the given command and sends {@code input} to stdin and returns stdout as a byte[]
     */
    public byte[] shell(
            String[] parameters, InputStream input, long maxTimeOutMs, TimeUnit timeUnit)
            throws IOException {
        ByteArrayOutputReceiver receiver;
        try (Trace ignored = Trace.begin("adb shell" + Arrays.toString(parameters))) {
            receiver = new ByteArrayOutputReceiver();
            device.executeShellCommand(
                    String.join(" ", parameters), receiver, maxTimeOutMs, timeUnit, input);
            return receiver.toByteArray();
        } catch (AdbCommandRejectedException
                | ShellCommandUnresponsiveException
                | TimeoutException e) {
            throw new IOException(e);
        }
    }

    /**
     * Executes the given Binder command and sends {@code input} to stdin and returns stdout as a
     * byte[]
     */
    public byte[] binder(String[] parameters, InputStream input) throws IOException {
        logger.info("BINDER: " + String.join(" ", parameters));
        ByteArrayOutputReceiver receiver;
        try (Trace ignored = Trace.begin("binder" + Arrays.toString(parameters))) {
            receiver = new ByteArrayOutputReceiver();
            device.executeBinderCommand(parameters, receiver, 5, TimeUnit.MINUTES, input);
            return receiver.toByteArray();
        } catch (AdbCommandRejectedException
                | ShellCommandUnresponsiveException
                | TimeoutException e) {
            throw new IOException(e);
        }
    }

    public InstallResult install(@NonNull App app, List<String> options, boolean reinstall) {
        // If there are baseline profiles, let's add them to the list of files to install.
        List<Path> paths = new ArrayList<>();
        paths.addAll(
                app.getApks().stream()
                        .map(apk -> Paths.get(apk.path))
                        .collect(Collectors.toList()));

        List<Path> bps = app.getBaselineProfile(device.getVersion().getApiLevel());
        paths.addAll(bps);
        logger.info("Installing:");
        for (Path p : paths) {
            logger.info("    " + p.getFileName());
        }

        InstallResult ir;
        if (adbSession.isPresent()) {
            logger.info("Installing with adblib");
            ir = installWithAdbLib(paths, options, reinstall);
        } else {
            logger.info("Installing with ddmlib");
            ir = installWithDdmLib(paths, options, reinstall);
        }

        if (!bps.isEmpty() && baselineInstallationStatusSupported()) {
            // Expected output from this command:
            // path: /data/app/~~-[...]==/c.e.c-==/base.apk
            //    arm64: [status=speed-profile] [reason=install] [primary-abi]
            //      [location is /data/app/~~[...]==/c.e.c-==/oat/arm64/base.odex]
            String[] cmd = {"pm", "art", "dump", app.getAppId()};
            byte[] rawResult = new byte[0];
            try {
                rawResult = shell(cmd, Timeouts.SHELL_BASELINE_PROFILE_STATUS);
            } catch (IOException e) {
                logger.error(e, "Unable to retrieve baseline profile status");
            }
            String result = new String(rawResult);
            if (!result.contains("status=speed-profile")) {
                return new InstallResult(
                        InstallStatus.INSTALL_BASELINE_PROFILE_FAILED,
                        "Baseline profile did not install: " + result);
            }
            logger.info(result);
            return ir;
        }

        return ir;
    }

    private boolean baselineInstallationStatusSupported() {
        return device.getVersion().getApiLevel() > 33;
    }

    private InstallResult makeInstallResult(String code, String message, Throwable t) {
        if (code != null) {
            try {
                return ApkInstaller.toInstallerResult(code, message);
            } catch (IllegalArgumentException | NullPointerException ignored) {
                logger.warning("Unrecognized Installation Failure: %s\n%s\n", code, message);
            }
        } else {
            if (t instanceof ShellCommandUnresponsiveException) {
                return new InstallResult(InstallStatus.SHELL_UNRESPONSIVE, message);
            } else {
                logger.warning("Installation Failure: %s\n", message);
                return new InstallResult(InstallStatus.UNKNOWN_ERROR, message, null);
            }
        }
        return new InstallResult(InstallStatus.UNKNOWN_ERROR, "Unknown Error");
    }

    private static long toNanos(@NonNull Instant instant) {
        return TimeUnit.MILLISECONDS.toNanos(instant.toEpochMilli()) + instant.getNano();
    }

    private InstallResult installWithAdbLib(
            @NonNull List<Path> paths, List<String> options, boolean reinstall) {
        try {
            if (reinstall) {
                options.add("-r");
            }
            DeviceSelector deviceSelector =
                    DeviceSelector.fromSerialNumber(device.getSerialNumber());
            Duration timeout = Duration.of(Timeouts.CMD_OINSTALL_MS, ChronoUnit.MILLIS);
            AdbDeviceServices deviceServices = adbSession.get().getDeviceServices();
            long startNanos = System.nanoTime();
            com.android.adblib.tools.InstallMetrics metrics =
                    JavaBridge.runBlocking(
                            deviceServices.getSession(),
                            c ->
                                    InstallerKt.install(
                                            deviceServices,
                                            deviceSelector,
                                            paths,
                                            options,
                                            timeout,
                                            c));

            long installStartTimeNs = toNanos(metrics.getStartTime());
            long installEndTimeNs = installStartTimeNs + metrics.getDuration().toNanos();
            long pushStartTimeNs = toNanos(metrics.getUploadStartTime());
            long pushEndTimeNs = pushStartTimeNs + metrics.getUploadDuration().toNanos();

            // Change time origin from epoch to System.nanoTime
            long timeDiff = startNanos - installStartTimeNs;
            installStartTimeNs += timeDiff;
            installEndTimeNs += timeDiff;
            pushStartTimeNs += timeDiff;
            pushEndTimeNs += timeDiff;

            InstallMetrics ddmMetrics =
                    new InstallMetrics(
                            pushStartTimeNs, pushEndTimeNs, installStartTimeNs, installEndTimeNs);
            return new InstallResult(InstallStatus.OK, null, ddmMetrics);
        } catch (com.android.adblib.tools.InstallException e) {
            String code = e.getErrorCode();
            String message = e.getMessage();
            return makeInstallResult(code, message, e);
        }
    }

    private InstallResult installWithDdmLib(
            @NonNull List<Path> paths, List<String> options, boolean reinstall) {

        List<File> files = paths.stream().map(Path::toFile).collect(Collectors.toList());
        try {
            if (device.getVersion().isGreaterOrEqualThan(AndroidVersion.VersionCodes.LOLLIPOP)) {
                device.installPackages(files, reinstall, options, 5, TimeUnit.MINUTES);
                return new InstallResult(InstallStatus.OK, null, device.getLastInstallMetrics());
            } else {
                if (files.size() != 1) {
                    return new InstallResult(
                            InstallStatus.MULTI_APKS_NO_SUPPORTED_BELOW21,
                            "Splits are not supported below API 21");
                } else {
                    device.installPackage(
                            files.get(0).getAbsolutePath(),
                            reinstall,
                            options.toArray(new String[0]));
                    return new InstallResult(
                            InstallStatus.OK, null, device.getLastInstallMetrics());
                }
            }
        } catch (InstallException e) {
            String code = e.getErrorCode();
            String message = e.getMessage();
            return makeInstallResult(code, message, e);
        }
    }

    public boolean uninstall(String packageName) {
        try {
            device.uninstallPackage(packageName);
            return true;
        } catch (InstallException e) {
        }
        return false;
    }

    public List<String> getAbis() {
        return device.getAbis();
    }

    /**
     * Gets the PIDs of the given package name. R+ only.
     *
     * @return a {@link List} of PIDs, or null if this isn't supported on the device.
     */
    public List<Integer> getPids(String packageName) {
        if (!device.supportsFeature(IDevice.Feature.REAL_PKG_NAME)) {
            throw new IllegalStateException(
                    String.format(
                            "Device %s, do not support REAL_PKG_NAME", device.getSerialNumber()));
        }
        List<Integer> results = new ArrayList<>();
        for (Client client : device.getClients()) {
            if (packageName.equals(client.getClientData().getPackageName())) {
                results.add(client.getClientData().getPid());
            }
        }
        return results;
    }

    public Deploy.Arch getArch(List<Integer> pids) {
        Deploy.Arch result = Deploy.Arch.ARCH_UNKNOWN;
        for (int pid : pids) {
            Deploy.Arch curProc = getArch(pid);
            if (result == Deploy.Arch.ARCH_UNKNOWN) {
                result = curProc;
            } else if (curProc != Deploy.Arch.ARCH_UNKNOWN && result != curProc) {
                // We can't throw an exception here; this happens when you have a webview process.
                logger.warning("Mixed ABIs detected: %s and %s", result, curProc);
            }
        }
        return result;
    }

    /**
     * The application will run with the most-preferred device ABI that the application also
     * natively supports. An application with no native libraries automatically runs with the
     * most-preferred device ABI.
     */
    public String getAbiForApks(List<Apk> apks) throws DeployerException {
        HashSet<String> appSupported = new HashSet<>();
        for (Apk apk : apks) {
            appSupported.addAll(apk.libraryAbis);
        }

        List<String> deviceSupported = getAbis();
        if (deviceSupported.isEmpty()) {
            throw DeployerException.unsupportedArch();
        }

        // No native library means we use the first device-preferred ABI.
        if (appSupported.isEmpty()) {
            String abi = deviceSupported.get(0);
            return abi;
        }

        for (String abi : deviceSupported) {
            if (appSupported.contains(abi)) {
                return abi;
            }
        }

        throw DeployerException.unsupportedArch();
    }

    public static Deploy.Arch getArchForAbi(String abi) throws DeployerException {
        return ABI_MAP.get(abi);
    }

    private Deploy.Arch getArch(int pid) {
        for (Client client : device.getClients()) {
            if (client.getClientData().getPid() != pid) {
                continue;
            }

            String abi = client.getClientData().getAbi();
            if (abi == null) {
                return Deploy.Arch.ARCH_UNKNOWN;
            } else if (abi.startsWith("32-bit")) {
                return Deploy.Arch.ARCH_32_BIT;
            } else if (abi.startsWith("64-bit")) {
                return Deploy.Arch.ARCH_64_BIT;
            } else {
                return Deploy.Arch.ARCH_UNKNOWN;
            }
        }
        return Deploy.Arch.ARCH_UNKNOWN;
    }

    public void push(String from, String to) throws IOException {
        try (Trace ignored = Trace.begin("adb push")) {
            device.pushFile(from, to);
        } catch (SyncException | TimeoutException | AdbCommandRejectedException e) {
            throw new IOException(e);
        }
    }

    public AndroidVersion getVersion() {
        return device.getVersion();
    }

    public String getName() {
        return device.getName();
    }

    public String getSerial() {
        return device.getSerialNumber();
    }

    // TODO: Replace this to void copying the full byte[] incurred when calling stream.toByteArray()
    private class ByteArrayOutputReceiver implements IShellOutputReceiver {

        ByteArrayOutputStream stream = new ByteArrayOutputStream();

        @Override
        public void addOutput(byte[] data, int offset, int length) {
            stream.write(data, offset, length);
        }

        @Override
        public void flush() {}

        @Override
        public boolean isCancelled() {
            return false;
        }

        byte[] toByteArray() {
            return stream.toByteArray();
        }
    }

    // TODO: Returning a String is not enough since it delegates parsing that String to the caller.
    // This method should return an AbortSessionResponse object, built on top of a ShellResponse object
    // with a status code and the raw output string. Parsing the string output should be done in
    // AbortSessionResponse.
    public String abortSession(String sessionId) {
        String prefix =
                device.getVersion().isGreaterOrEqualThan(AndroidVersion.VersionCodes.N)
                        ? "cmd package"
                        : "pm";

        String[] command = {prefix, "install-abandon", sessionId};

        String response;
        try {
            byte[] bytes = shell(command, Timeouts.SHELL_ABORT_INSTALL_MS);
            response = new String(bytes, StandardCharsets.UTF_8);
        } catch (Exception e) {
            response = e.getMessage();
        }
        return response;
    }

    public String getSkipVerificationOption(String packageName) {
        return ApkVerifierTracker.getSkipVerificationInstallationFlag(device, packageName);
    }

    public IDevice getDevice() {
        return device;
    }
}
