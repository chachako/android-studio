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
package com.android.adblib.ddmlibcompatibility.debugging

import com.android.adblib.ConnectedDevice
import com.android.adblib.serialNumber
import com.android.ddmlib.Client
import com.android.ddmlib.FileListingService
import com.android.ddmlib.IDevice
import com.android.ddmlib.IShellOutputReceiver
import com.android.ddmlib.InstallReceiver
import com.android.ddmlib.RawImage
import com.android.ddmlib.ScreenRecorderOptions
import com.android.ddmlib.ServiceInfo
import com.android.ddmlib.SyncService
import com.android.ddmlib.log.LogReceiver
import com.android.sdklib.AndroidVersion
import com.google.common.util.concurrent.ListenableFuture
import java.io.File
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit

/**
 * Implementation of [IDevice] that entirely relies on adblib services, i.e. does not depend on
 * implementation details of ddmlib.
 */
internal class AdblibIDeviceWrapper(
    private val connectedDevice: ConnectedDevice
) : IDevice {

    /**
     * Returns a (humanized) name for this device. Typically this is the AVD name for AVD's, and
     * a combination of the manufacturer name, model name &amp; serial number for devices.
     */
    override fun getName(): String {
        TODO("Not yet implemented")
    }

    @Deprecated("")
    override fun executeShellCommand(
        command: String?,
        receiver: IShellOutputReceiver?,
        maxTimeToOutputResponse: Int
    ) {
        TODO("Not yet implemented")
    }

    /**
     * Executes a shell command on the device, and sends the result to a <var>receiver</var>
     *
     *
     * This is similar to calling `
     * executeShellCommand(command, receiver, DdmPreferences.getTimeOut())`.
     *
     * @param command the shell command to execute
     * @param receiver the [IShellOutputReceiver] that will receives the output of the shell
     * command
     * @throws TimeoutException in case of timeout on the connection.
     * @throws AdbCommandRejectedException if adb rejects the command
     * @throws ShellCommandUnresponsiveException in case the shell command doesn't send output for a
     * given time.
     * @throws IOException in case of I/O error on the connection.
     * @see .executeShellCommand
     * @see DdmPreferences.getTimeOut
     */
    override fun executeShellCommand(command: String?, receiver: IShellOutputReceiver?) {
        TODO("Not yet implemented")
    }

    /**
     * Executes a shell command on the device, and sends the result to a <var>receiver</var>.
     *
     * <var>maxTimeToOutputResponse</var> is used as a maximum waiting time when expecting the
     * command output from the device.<br></br>
     * At any time, if the shell command does not output anything for a period longer than
     * <var>maxTimeToOutputResponse</var>, then the method will throw
     * [ShellCommandUnresponsiveException].
     *
     * For commands like log output, a <var>maxTimeToOutputResponse</var> value of 0, meaning
     * that the method will never throw and will block until the receiver's
     * [IShellOutputReceiver.isCancelled] returns `true`, should be
     * used.
     *
     * @param command the shell command to execute
     * @param receiver the [IShellOutputReceiver] that will receives the output of the shell
     * command
     * @param maxTimeToOutputResponse the maximum amount of time during which the command is allowed
     * to not output any response. A value of 0 means the method will wait forever
     * (until the <var>receiver</var> cancels the execution) for command output and
     * never throw.
     * @param maxTimeUnits Units for non-zero `maxTimeToOutputResponse` values.
     * @throws TimeoutException in case of timeout on the connection when sending the command.
     * @throws AdbCommandRejectedException if adb rejects the command.
     * @throws ShellCommandUnresponsiveException in case the shell command doesn't send any output
     * for a period longer than <var>maxTimeToOutputResponse</var>.
     * @throws IOException in case of I/O error on the connection.
     *
     * @see DdmPreferences.getTimeOut
     */
    override fun executeShellCommand(
        command: String?,
        receiver: IShellOutputReceiver?,
        maxTimeToOutputResponse: Long,
        maxTimeUnits: TimeUnit?
    ) {
        TODO("Not yet implemented")
    }

    /**
     * Executes a shell command on the device, and sends the result to a <var>receiver</var>.
     *
     *
     * <var>maxTimeToOutputResponse</var> is used as a maximum waiting time when expecting the
     * command output from the device.<br></br>
     * At any time, if the shell command does not output anything for a period longer than
     * <var>maxTimeToOutputResponse</var>, then the method will throw [ ].
     *
     *
     * For commands like log output, a <var>maxTimeToOutputResponse</var> value of 0, meaning
     * that the method will never throw and will block until the receiver's [ ][IShellOutputReceiver.isCancelled] returns `true`, should be used.
     *
     * @param command the shell command to execute
     * @param receiver the [IShellOutputReceiver] that will receives the output of the shell
     * command
     * @param maxTimeout the maximum timeout for the command to return. A value of 0 means no max
     * timeout will be applied.
     * @param maxTimeToOutputResponse the maximum amount of time during which the command is allowed
     * to not output any response. A value of 0 means the method will wait forever (until the
     * <var>receiver</var> cancels the execution) for command output and never throw.
     * @param maxTimeUnits Units for non-zero `maxTimeout` and `maxTimeToOutputResponse`
     * values.
     * @throws TimeoutException in case of timeout on the connection when sending the command.
     * @throws AdbCommandRejectedException if adb rejects the command.
     * @throws ShellCommandUnresponsiveException in case the shell command doesn't send any output
     * for a period longer than <var>maxTimeToOutputResponse</var>.
     * @throws IOException in case of I/O error on the connection.
     * @see DdmPreferences.getTimeOut
     */
    override fun executeShellCommand(
        command: String?,
        receiver: IShellOutputReceiver?,
        maxTimeout: Long,
        maxTimeToOutputResponse: Long,
        maxTimeUnits: TimeUnit?
    ) {
        TODO("Not yet implemented")
    }

    /**
     * Do a potential asynchronous query for a system property.
     *
     * @param name the name of the value to return.
     * @return a [ListenableFuture.][ListenableFuture] [get][Future.get] may return
     * null.
     */
    override fun getSystemProperty(name: String): ListenableFuture<String> {
        TODO("Not yet implemented")
    }

    /** Returns the serial number of the device.  */
    override fun getSerialNumber(): String {
        return connectedDevice.serialNumber
    }

    /**
     * Returns the name of the AVD the emulator is running.
     *
     *
     * This is only valid if [.isEmulator] returns true.
     *
     *
     * If the emulator is not running any AVD (for instance it's running from an Android source
     * tree build), this method will return "`<build>`".
     *
     *
     * *Note: Prefer using [.getAvdData] if you want control over the timeout.*
     *
     * @return the name of the AVD or `null` if there isn't any.
     */
    @Deprecated("")
    override fun getAvdName(): String? {
        TODO("Not yet implemented")
    }

    /**
     * Returns the absolute path to the virtual device in the file system. The path is operating
     * system dependent; it will have / name separators on Linux and \ separators on Windows.
     *
     *
     * *Note: Prefer using [.getAvdData] if you want control over the timeout.*
     *
     * @return the AVD path or null if this is a physical device, the emulator console subcommand
     * failed, or the emulator's version is older than 30.0.18
     */
    @Deprecated("")
    override fun getAvdPath(): String? {
        TODO("Not yet implemented")
    }

    /** Returns the state of the device.  */
    override fun getState(): IDevice.DeviceState {
        TODO("Not yet implemented")
    }

    /**
     * Returns the cached device properties. It contains the whole output of 'getprop'
     *
     */
    @Deprecated("")
    override fun getProperties(): MutableMap<String, String> {
        TODO("Not yet implemented")
    }

    /**
     * Returns the number of property for this device.
     *
     */
    @Deprecated("")
    override fun getPropertyCount(): Int {
        TODO("Not yet implemented")
    }

    /**
     * Convenience method that attempts to retrieve a property via [ ][.getSystemProperty] with a very short wait time, and swallows exceptions.
     *
     *
     * *Note: Prefer using [.getSystemProperty] if you want control over the
     * timeout.*
     *
     * @param name the name of the value to return.
     * @return the value or `null` if the property value was not immediately available
     */
    override fun getProperty(name: String): String? {
        TODO("Not yet implemented")
    }

    /** Returns `true` if properties have been cached  */
    override fun arePropertiesSet(): Boolean {
        TODO("Not yet implemented")
    }

    /**
     * A variant of [.getProperty] that will attempt to retrieve the given property
     * from device directly, without using cache. This method should (only) be used for any volatile
     * properties.
     *
     * @param name the name of the value to return.
     * @return the value or `null` if the property does not exist
     * @throws TimeoutException in case of timeout on the connection.
     * @throws AdbCommandRejectedException if adb rejects the command
     * @throws ShellCommandUnresponsiveException in case the shell command doesn't send output for a
     * given time.
     * @throws IOException in case of I/O error on the connection.
     */
    @Deprecated("")
    override fun getPropertySync(name: String?): String {
        TODO("Not yet implemented")
    }

    /**
     * A combination of [.getProperty] and [.getPropertySync] that will
     * attempt to retrieve the property from cache. If not found, will synchronously attempt to
     * query device directly and repopulate the cache if successful.
     *
     * @param name the name of the value to return.
     * @return the value or `null` if the property does not exist
     * @throws TimeoutException in case of timeout on the connection.
     * @throws AdbCommandRejectedException if adb rejects the command
     * @throws ShellCommandUnresponsiveException in case the shell command doesn't send output for a
     * given time.
     * @throws IOException in case of I/O error on the connection.
     */
    @Deprecated("")
    override fun getPropertyCacheOrSync(name: String?): String {
        TODO("Not yet implemented")
    }

    /** Returns whether this device supports the given software feature.  */
    override fun supportsFeature(feature: IDevice.Feature): Boolean {
        TODO("Not yet implemented")
    }

    /** Returns whether this device supports the given hardware feature.  */
    override fun supportsFeature(feature: IDevice.HardwareFeature): Boolean {
        TODO("Not yet implemented")
    }

    /**
     * Returns a map of running services (key is service name) to [ServiceInfo] (value).
     */
    override fun services(): MutableMap<String, ServiceInfo> {
        TODO("Not yet implemented")
    }

    /**
     * Returns a mount point.
     *
     * @param name the name of the mount point to return
     * @see .MNT_EXTERNAL_STORAGE
     *
     * @see .MNT_ROOT
     *
     * @see .MNT_DATA
     */
    override fun getMountPoint(name: String): String? {
        TODO("Not yet implemented")
    }

    /**
     * Returns if the device is ready.
     *
     * @return `true` if [.getState] returns [DeviceState.ONLINE].
     */
    override fun isOnline(): Boolean {
        TODO("Not yet implemented")
    }

    /** Returns `true` if the device is an emulator.  */
    override fun isEmulator(): Boolean {
        TODO("Not yet implemented")
    }

    /**
     * Returns if the device is offline.
     *
     * @return `true` if [.getState] returns [DeviceState.OFFLINE].
     */
    override fun isOffline(): Boolean {
        TODO("Not yet implemented")
    }

    /**
     * Returns if the device is in bootloader mode.
     *
     * @return `true` if [.getState] returns [DeviceState.BOOTLOADER].
     */
    override fun isBootLoader(): Boolean {
        TODO("Not yet implemented")
    }

    /** Returns whether the [IDevice] has [Client]s.  */
    override fun hasClients(): Boolean {
        TODO("Not yet implemented")
    }

    /** Returns the array of clients.  */
    override fun getClients(): Array<Client> {
        TODO("Not yet implemented")
    }

    /**
     * Returns a [Client] by its application name.
     *
     * @param applicationName the name of the application
     * @return the `Client` object or `null` if no match was found.
     */
    override fun getClient(applicationName: String?): Client {
        TODO("Not yet implemented")
    }

    /**
     * Returns a [SyncService] object to push / pull files to and from the device.
     *
     * @return `null` if the SyncService couldn't be created. This can happen if adb
     * refuse to open the connection because the [IDevice] is invalid (or got
     * disconnected).
     * @throws TimeoutException in case of timeout on the connection.
     * @throws AdbCommandRejectedException if adb rejects the command
     * @throws IOException if the connection with adb failed.
     */
    override fun getSyncService(): SyncService? {
        TODO("Not yet implemented")
    }

    /** Returns a [FileListingService] for this device.  */
    override fun getFileListingService(): FileListingService {
        TODO("Not yet implemented")
    }

    /**
     * Takes a screen shot of the device and returns it as a [RawImage].
     *
     * @return the screenshot as a `RawImage` or `null` if something went
     * wrong.
     * @throws TimeoutException in case of timeout on the connection.
     * @throws AdbCommandRejectedException if adb rejects the command
     * @throws IOException in case of I/O error on the connection.
     */
    override fun getScreenshot(): RawImage {
        TODO("Not yet implemented")
    }

    override fun getScreenshot(timeout: Long, unit: TimeUnit?): RawImage {
        TODO("Not yet implemented")
    }

    /**
     * Initiates screen recording on the device if the device supports [ ][Feature.SCREEN_RECORD].
     */
    override fun startScreenRecorder(
        remoteFilePath: String,
        options: ScreenRecorderOptions,
        receiver: IShellOutputReceiver
    ) {
        TODO("Not yet implemented")
    }

    /**
     * Runs the event log service and outputs the event log to the [LogReceiver].
     *
     *
     * This call is blocking until [LogReceiver.isCancelled] returns true.
     *
     * @param receiver the receiver to receive the event log entries.
     * @throws TimeoutException in case of timeout on the connection. This can only be thrown if the
     * timeout happens during setup. Once logs start being received, no timeout will occur as
     * it's not possible to detect a difference between no log and timeout.
     * @throws AdbCommandRejectedException if adb rejects the command
     * @throws IOException in case of I/O error on the connection.
     */
    override fun runEventLogService(receiver: LogReceiver?) {
        TODO("Not yet implemented")
    }

    /**
     * Runs the log service for the given log and outputs the log to the [LogReceiver].
     *
     *
     * This call is blocking until [LogReceiver.isCancelled] returns true.
     *
     * @param logname the logname of the log to read from.
     * @param receiver the receiver to receive the event log entries.
     * @throws TimeoutException in case of timeout on the connection. This can only be thrown if the
     * timeout happens during setup. Once logs start being received, no timeout will occur as
     * it's not possible to detect a difference between no log and timeout.
     * @throws AdbCommandRejectedException if adb rejects the command
     * @throws IOException in case of I/O error on the connection.
     */
    override fun runLogService(logname: String?, receiver: LogReceiver?) {
        TODO("Not yet implemented")
    }

    /**
     * Creates a port forwarding between a local and a remote port.
     *
     * @param localPort the local port to forward
     * @param remotePort the remote port.
     * @throws TimeoutException in case of timeout on the connection.
     * @throws AdbCommandRejectedException if adb rejects the command
     * @throws IOException in case of I/O error on the connection.
     */
    override fun createForward(localPort: Int, remotePort: Int) {
        TODO("Not yet implemented")
    }

    /**
     * Creates a port forwarding between a local TCP port and a remote Unix Domain Socket.
     *
     * @param localPort the local port to forward
     * @param remoteSocketName name of the unix domain socket created on the device
     * @param namespace namespace in which the unix domain socket was created
     * @throws TimeoutException in case of timeout on the connection.
     * @throws AdbCommandRejectedException if adb rejects the command
     * @throws IOException in case of I/O error on the connection.
     */
    override fun createForward(
        localPort: Int,
        remoteSocketName: String?,
        namespace: IDevice.DeviceUnixSocketNamespace?
    ) {
        TODO("Not yet implemented")
    }

    /**
     * Returns the name of the client by pid or `null` if pid is unknown
     *
     * @param pid the pid of the client.
     */
    override fun getClientName(pid: Int): String {
        TODO("Not yet implemented")
    }

    /**
     * Pushes a single file.
     *
     * @param local the local filepath.
     * @param remote the remote filepath
     * @throws IOException in case of I/O error on the connection
     * @throws AdbCommandRejectedException if adb rejects the command
     * @throws TimeoutException in case of a timeout reading responses from the device
     * @throws SyncException if the file could not be pushed
     */
    override fun pushFile(local: String, remote: String) {
        TODO("Not yet implemented")
    }

    /**
     * Pulls a single file.
     *
     * @param remote the full path to the remote file
     * @param local The local destination.
     * @throws IOException in case of an IO exception.
     * @throws AdbCommandRejectedException if adb rejects the command
     * @throws TimeoutException in case of a timeout reading responses from the device.
     * @throws SyncException in case of a sync exception.
     */
    override fun pullFile(remote: String?, local: String?) {
        TODO("Not yet implemented")
    }

    /**
     * Installs an Android application on device. This is a helper method that combines the
     * syncPackageToDevice, installRemotePackage, and removePackage steps
     *
     * @param packageFilePath the absolute file system path to file on local host to install
     * @param reinstall set to `true` if re-install of app should be performed
     * @param extraArgs optional extra arguments to pass. See 'adb shell pm install --help' for
     * available options.
     * @throws InstallException if the installation fails.
     */
    override fun installPackage(
        packageFilePath: String?,
        reinstall: Boolean,
        vararg extraArgs: String?
    ) {
        TODO("Not yet implemented")
    }

    /**
     * Installs an Android application on device. This is a helper method that combines the
     * syncPackageToDevice, installRemotePackage, and removePackage steps
     *
     * @param packageFilePath the absolute file system path to file on local host to install
     * @param reinstall set to `true` if re-install of app should be performed
     * @param receiver The [InstallReceiver] to be used to monitor the install and get final
     * status.
     * @param extraArgs optional extra arguments to pass. See 'adb shell pm install --help' for
     * available options.
     * @throws InstallException if the installation fails.
     */
    override fun installPackage(
        packageFilePath: String?,
        reinstall: Boolean,
        receiver: InstallReceiver?,
        vararg extraArgs: String?
    ) {
        TODO("Not yet implemented")
    }

    /**
     * Installs an Android application on device. This is a helper method that combines the
     * syncPackageToDevice, installRemotePackage, and removePackage steps
     *
     * @param packageFilePath the absolute file system path to file on local host to install
     * @param reinstall set to `true` if re-install of app should be performed
     * @param receiver The [InstallReceiver] to be used to monitor the install and get final
     * status.
     * @param maxTimeout the maximum timeout for the command to return. A value of 0 means no max
     * timeout will be applied.
     * @param maxTimeToOutputResponse the maximum amount of time during which the command is allowed
     * to not output any response. A value of 0 means the method will wait forever (until the
     * <var>receiver</var> cancels the execution) for command output and never throw.
     * @param maxTimeUnits Units for non-zero `maxTimeout` and `maxTimeToOutputResponse`
     * values.
     * @param extraArgs optional extra arguments to pass. See 'adb shell pm install --help' for
     * available options.
     * @throws InstallException if the installation fails.
     */
    override fun installPackage(
        packageFilePath: String?,
        reinstall: Boolean,
        receiver: InstallReceiver?,
        maxTimeout: Long,
        maxTimeToOutputResponse: Long,
        maxTimeUnits: TimeUnit?,
        vararg extraArgs: String?
    ) {
        TODO("Not yet implemented")
    }

    /**
     * Installs an Android application made of several APK files (one main and 0..n split packages)
     *
     * @param apks list of apks to install (1 main APK + 0..n split apks)
     * @param reinstall set to `true` if re-install of app should be performed
     * @param installOptions optional extra arguments to pass. See 'adb shell pm install --help' for
     * available options.
     * @param timeout installation timeout
     * @param timeoutUnit [TimeUnit] corresponding to the timeout parameter
     * @throws InstallException if the installation fails.
     */
    override fun installPackages(
        apks: MutableList<File>,
        reinstall: Boolean,
        installOptions: MutableList<String>,
        timeout: Long,
        timeoutUnit: TimeUnit
    ) {
        TODO("Not yet implemented")
    }

    /**
     * Pushes a file to device
     *
     * @param localFilePath the absolute path to file on local host
     * @return [String] destination path on device for file
     * @throws TimeoutException in case of timeout on the connection.
     * @throws AdbCommandRejectedException if adb rejects the command
     * @throws IOException in case of I/O error on the connection.
     * @throws SyncException if an error happens during the push of the package on the device.
     */
    override fun syncPackageToDevice(localFilePath: String?): String {
        TODO("Not yet implemented")
    }

    /**
     * Installs the application package that was pushed to a temporary location on the device.
     *
     * @param remoteFilePath absolute file path to package file on device
     * @param reinstall set to `true` if re-install of app should be performed
     * @param extraArgs optional extra arguments to pass. See 'adb shell pm install --help' for
     * available options.
     * @throws InstallException if the installation fails.
     * @see .installRemotePackage
     */
    override fun installRemotePackage(
        remoteFilePath: String?,
        reinstall: Boolean,
        vararg extraArgs: String?
    ) {
        TODO("Not yet implemented")
    }

    /**
     * Installs the application package that was pushed to a temporary location on the device.
     *
     * @param remoteFilePath absolute file path to package file on device
     * @param reinstall set to `true` if re-install of app should be performed
     * @param receiver The [InstallReceiver] to be used to monitor the install and get final
     * status.
     * @param extraArgs optional extra arguments to pass. See 'adb shell pm install --help' for
     * available options.
     * @throws InstallException if the installation fails.
     * @see .installRemotePackage
     */
    override fun installRemotePackage(
        remoteFilePath: String?,
        reinstall: Boolean,
        receiver: InstallReceiver?,
        vararg extraArgs: String?
    ) {
        TODO("Not yet implemented")
    }

    /**
     * Installs the application package that was pushed to a temporary location on the device.
     *
     * @param remoteFilePath absolute file path to package file on device
     * @param reinstall set to `true` if re-install of app should be performed
     * @param receiver The [InstallReceiver] to be used to monitor the install and get final
     * status.
     * @param maxTimeout the maximum timeout for the command to return. A value of 0 means no max
     * timeout will be applied.
     * @param maxTimeToOutputResponse the maximum amount of time during which the command is allowed
     * to not output any response. A value of 0 means the method will wait forever (until the
     * <var>receiver</var> cancels the execution) for command output and never throw.
     * @param maxTimeUnits Units for non-zero `maxTimeout` and `maxTimeToOutputResponse`
     * values.
     * @param extraArgs optional extra arguments to pass. See 'adb shell pm install --help' for
     * available options.
     * @throws InstallException if the installation fails.
     */
    override fun installRemotePackage(
        remoteFilePath: String?,
        reinstall: Boolean,
        receiver: InstallReceiver?,
        maxTimeout: Long,
        maxTimeToOutputResponse: Long,
        maxTimeUnits: TimeUnit?,
        vararg extraArgs: String?
    ) {
        TODO("Not yet implemented")
    }

    /**
     * Removes a file from device.
     *
     * @param remoteFilePath path on device of file to remove
     * @throws InstallException if the installation fails.
     */
    override fun removeRemotePackage(remoteFilePath: String?) {
        TODO("Not yet implemented")
    }

    /**
     * Uninstalls a package from the device.
     *
     * @param packageName the Android application ID to uninstall
     * @return a [String] with an error code, or `null` if success.
     * @throws InstallException if the uninstallation fails.
     */
    override fun uninstallPackage(packageName: String?): String {
        TODO("Not yet implemented")
    }

    /**
     * Uninstalls an app from the device.
     *
     * @param applicationID the Android application ID to uninstall
     * @param extraArgs optional extra arguments to pass. See 'adb shell pm install --help' for
     * available options.
     * @return a [String] with an error code, or `null` if success.
     * @throws InstallException if the uninstallation fails.
     */
    override fun uninstallApp(applicationID: String?, vararg extraArgs: String?): String {
        TODO("Not yet implemented")
    }

    /**
     * Reboot the device.
     *
     * @param into the bootloader name to reboot into, or null to just reboot the device.
     * @throws TimeoutException in case of timeout on the connection.
     * @throws AdbCommandRejectedException if adb rejects the command
     * @throws IOException
     */
    override fun reboot(into: String?) {
        TODO("Not yet implemented")
    }

    /**
     * Ask the adb daemon to become root on the device. This may silently fail, and can only succeed
     * on developer builds. See "adb root" for more information.
     *
     * @return true if the adb daemon is running as root, otherwise false.
     * @throws TimeoutException in case of timeout on the connection.
     * @throws AdbCommandRejectedException if adb rejects the command.
     * @throws ShellCommandUnresponsiveException if the root status cannot be queried.
     * @throws IOException
     */
    override fun root(): Boolean {
        TODO("Not yet implemented")
    }

    /**
     * Queries the current root-status of the device. See "adb root" for more information.
     *
     * @return true if the adb daemon is running as root, otherwise false.
     * @throws TimeoutException in case of timeout on the connection.
     * @throws AdbCommandRejectedException if adb rejects the command.
     */
    override fun isRoot(): Boolean {
        TODO("Not yet implemented")
    }

    /**
     * Return the device's battery level, from 0 to 100 percent.
     *
     *
     * The battery level may be cached. Only queries the device for its battery level if 5
     * minutes have expired since the last successful query.
     *
     * @return the battery level or `null` if it could not be retrieved
     */
    @Deprecated("")
    override fun getBatteryLevel(): Int {
        TODO("Not yet implemented")
    }

    /**
     * Return the device's battery level, from 0 to 100 percent.
     *
     *
     * The battery level may be cached. Only queries the device for its battery level if `
     * freshnessMs` ms have expired since the last successful query.
     *
     * @param freshnessMs
     * @return the battery level or `null` if it could not be retrieved
     * @throws ShellCommandUnresponsiveException
     */
    @Deprecated("")
    override fun getBatteryLevel(freshnessMs: Long): Int {
        TODO("Not yet implemented")
    }

    /**
     * Return the device's battery level, from 0 to 100 percent.
     *
     *
     * The battery level may be cached. Only queries the device for its battery level if 5
     * minutes have expired since the last successful query.
     *
     * @return a [Future] that can be used to query the battery level. The Future will return
     * a [ExecutionException] if battery level could not be retrieved.
     */
    override fun getBattery(): Future<Int> {
        TODO("Not yet implemented")
    }

    /**
     * Return the device's battery level, from 0 to 100 percent.
     *
     *
     * The battery level may be cached. Only queries the device for its battery level if `
     * freshnessTime` has expired since the last successful query.
     *
     * @param freshnessTime the desired recency of battery level
     * @param timeUnit the [TimeUnit] of freshnessTime
     * @return a [Future] that can be used to query the battery level. The Future will return
     * a [ExecutionException] if battery level could not be retrieved.
     */
    override fun getBattery(freshnessTime: Long, timeUnit: TimeUnit): Future<Int> {
        TODO("Not yet implemented")
    }

    /**
     * Returns the ABIs supported by this device. The ABIs are sorted in preferred order, with the
     * first ABI being the most preferred.
     *
     * @return the list of ABIs.
     */
    override fun getAbis(): MutableList<String> {
        TODO("Not yet implemented")
    }

    /**
     * Returns the density bucket of the device screen by reading the value for system property
     * [.PROP_DEVICE_DENSITY].
     *
     * @return the density, or -1 if it cannot be determined.
     */
    override fun getDensity(): Int {
        TODO("Not yet implemented")
    }

    /**
     * Returns the user's language.
     *
     * @return the user's language, or null if it's unknown
     */
    override fun getLanguage(): String? {
        TODO("Not yet implemented")
    }

    /**
     * Returns the user's region.
     *
     * @return the user's region, or null if it's unknown
     */
    override fun getRegion(): String? {
        TODO("Not yet implemented")
    }

    /**
     * Returns the API level of the device.
     *
     * @return the API level if it can be determined, [AndroidVersion.DEFAULT] otherwise.
     */
    override fun getVersion(): AndroidVersion {
        TODO("Not yet implemented")
    }
}
