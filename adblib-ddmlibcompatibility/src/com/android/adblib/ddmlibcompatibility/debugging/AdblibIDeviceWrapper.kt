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

import com.android.adblib.AdbDeviceSyncServices
import com.android.adblib.AdbFailResponseException
import com.android.adblib.AdbProtocolErrorException
import com.android.adblib.ConnectedDevice
import com.android.adblib.DeviceSelector
import com.android.adblib.INFINITE_DURATION
import com.android.adblib.RemoteFileMode
import com.android.adblib.SocketSpec
import com.android.adblib.adbLogger
import com.android.adblib.availableFeatures
import com.android.adblib.deviceInfo
import com.android.adblib.isOffline
import com.android.adblib.isOnline
import com.android.adblib.serialNumber
import com.android.adblib.syncSend
import com.android.adblib.tools.EmulatorCommandException
import com.android.adblib.tools.defaultAuthTokenPath
import com.android.adblib.tools.localConsoleAddress
import com.android.adblib.tools.openEmulatorConsole
import com.android.adblib.withErrorTimeout
import com.android.ddmlib.AdbHelper
import com.android.ddmlib.AndroidDebugBridge
import com.android.ddmlib.AvdData
import com.android.ddmlib.Client
import com.android.ddmlib.DdmPreferences
import com.android.ddmlib.FileListingService
import com.android.ddmlib.IDevice
import com.android.ddmlib.IDevice.DeviceState
import com.android.ddmlib.IDevice.RE_EMULATOR_SN
import com.android.ddmlib.IDeviceSharedImpl
import com.android.ddmlib.IDeviceSharedImpl.INSTALL_TIMEOUT_MINUTES
import com.android.ddmlib.IShellOutputReceiver
import com.android.ddmlib.InstallException
import com.android.ddmlib.InstallMetrics
import com.android.ddmlib.InstallReceiver
import com.android.ddmlib.Log
import com.android.ddmlib.ProfileableClient
import com.android.ddmlib.PropertyFetcher
import com.android.ddmlib.RawImage
import com.android.ddmlib.ScreenRecorderOptions
import com.android.ddmlib.ServiceInfo
import com.android.ddmlib.SimpleConnectedSocket
import com.android.ddmlib.SplitApkInstaller
import com.android.ddmlib.SyncException
import com.android.ddmlib.SyncService
import com.android.ddmlib.log.LogReceiver
import com.android.sdklib.AndroidVersion
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.guava.asListenableFuture
import kotlinx.coroutines.runBlocking
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.nio.channels.SocketChannel
import java.nio.file.Files
import java.time.Duration
import java.util.Collections
import java.util.concurrent.ExecutionException
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import kotlin.coroutines.cancellation.CancellationException

/**
 * Implementation of [IDevice] that entirely relies on adblib services, i.e. does not depend on
 * implementation details of ddmlib.
 */
internal class AdblibIDeviceWrapper(
    private val connectedDevice: ConnectedDevice,
    bridge: AndroidDebugBridge,
) : IDevice {

    private val logger = adbLogger(connectedDevice.session)

    // TODO(b/294559068): Create our own implementation of PropertyFetcher before we can get rid of ddmlib
    private val propertyFetcher = PropertyFetcher(this)

    private val iDeviceSharedImpl = IDeviceSharedImpl(this)
    private val deviceClientManager =
        AdbLibClientManagerFactory.createClientManager(connectedDevice.session)
            .createDeviceClientManager(bridge, this)

    /** Name and path of the AVD  */
    private val mAvdData = connectedDevice.session.scope.async { createAvdData() }

    /** Information about the most recent installation via this device  */
    private var lastInstallMetrics: InstallMetrics? = null

    override fun getName(): String {
        return iDeviceSharedImpl.name
    }

    @Deprecated("")
    override fun executeShellCommand(
        command: String,
        receiver: IShellOutputReceiver,
        maxTimeToOutputResponse: Int
    ) {
        // This matches the behavior of `DeviceImpl`
        executeRemoteCommand(
            command,
            receiver,
            maxTimeToOutputResponse.toLong(),
            TimeUnit.MILLISECONDS
        )
    }

    override fun executeShellCommand(command: String, receiver: IShellOutputReceiver) {
        // This matches the behavior of `DeviceImpl`
        executeRemoteCommand(
            command,
            receiver,
            DdmPreferences.getTimeOut().toLong(),
            TimeUnit.MILLISECONDS
        )
    }

    override fun executeShellCommand(
        command: String,
        receiver: IShellOutputReceiver,
        maxTimeToOutputResponse: Long,
        maxTimeUnits: TimeUnit,
        `is`: InputStream?
    ) {
        // Note that `AdbHelper.AdbService.EXEC` is passed down to match the behavior of `DeviceImpl`
        executeRemoteCommand(
            AdbHelper.AdbService.EXEC,
            command,
            receiver,
            0L,
            maxTimeToOutputResponse,
            maxTimeUnits,
            `is`
        )
    }

    override fun executeShellCommand(
        command: String,
        receiver: IShellOutputReceiver,
        maxTimeToOutputResponse: Long,
        maxTimeUnits: TimeUnit
    ) {
        // This matches the behavior of `DeviceImpl`
        executeRemoteCommand(
            command,
            receiver,
            0L,
            maxTimeToOutputResponse,
            maxTimeUnits
        )
    }

    override fun executeShellCommand(
        command: String,
        receiver: IShellOutputReceiver,
        maxTimeout: Long,
        maxTimeToOutputResponse: Long,
        maxTimeUnits: TimeUnit
    ) {
        // This matches the behavior of `DeviceImpl`
        executeRemoteCommand(
            command,
            receiver,
            maxTimeout,
            maxTimeToOutputResponse,
            maxTimeUnits
        )
    }

    override fun getSystemProperty(name: String): ListenableFuture<String> {
        return propertyFetcher.getProperty(name)
    }

    override fun getSerialNumber(): String {
        return connectedDevice.serialNumber
    }

    @Deprecated("")
    override fun getAvdName(): String? {
        return if (mAvdData.isCompleted) mAvdData.getCompleted()?.name else null
    }

    @Deprecated("")
    override fun getAvdPath(): String? {
        return if (mAvdData.isCompleted) mAvdData.getCompleted()?.path else null
    }

    override fun getAvdData(): ListenableFuture<AvdData?> {
        return mAvdData.asListenableFuture()
    }

    private suspend fun createAvdData(): AvdData? {
        if (!isEmulator) {
            return null
        }
        val emulatorMatchResult = RE_EMULATOR_SN.toRegex().matchEntire(serialNumber) ?: return null
        val port = emulatorMatchResult.groupValues[1].toIntOrNull() ?: return null

        return try {
            connectedDevice.session.openEmulatorConsole(
                localConsoleAddress(port),
                defaultAuthTokenPath()
            ).use {
                val avdName = kotlin.runCatching { it.avdName() }.getOrNull()
                val path = kotlin.runCatching { it.avdPath() }.getOrNull()

                AvdData(avdName, path)
            }
        } catch (e: EmulatorCommandException) {
            logger.warn(e, "Couldn't open emulator console")
            null
        }
    }

    override fun getState(): DeviceState? {
        return DeviceState.getState(connectedDevice.deviceInfo.deviceState.state)
    }

    @Deprecated("")
    override fun getProperties(): MutableMap<String, String> {
        return Collections.unmodifiableMap(propertyFetcher.properties)
    }

    @Deprecated("")
    override fun getPropertyCount(): Int {
        return propertyFetcher.properties.size
    }

    override fun getProperty(name: String): String? {
        val timeout =
            if (propertyFetcher.properties.isEmpty()) INITIAL_GET_PROP_TIMEOUT_MS else GET_PROP_TIMEOUT_MS

        val future = propertyFetcher.getProperty(name)
        try {
            return future.get(timeout, TimeUnit.MILLISECONDS)
        } catch (e: InterruptedException) {
            // ignore
        } catch (e: ExecutionException) {
            // ignore
        } catch (e: TimeoutException) {
            // ignore
        }
        return null
    }

    override fun arePropertiesSet(): Boolean {
        return propertyFetcher.arePropertiesSet()
    }

    @Deprecated("")
    override fun getPropertySync(name: String?): String {
        throw UnsupportedOperationException("This method is not used in Android Studio")
    }

    @Deprecated("")
    override fun getPropertyCacheOrSync(name: String?): String {
        throw UnsupportedOperationException("This method is not used in Android Studio")
    }

    override fun supportsFeature(feature: IDevice.Feature): Boolean {
        val availableFeatures: List<String> =
            runBlockingLegacy {
                connectedDevice.session.hostServices.availableFeatures(
                    DeviceSelector.fromSerialNumber(
                        serialNumber
                    )
                )
            }

        return iDeviceSharedImpl.supportsFeature(feature, availableFeatures.toSet())
    }

    override fun supportsFeature(feature: IDevice.HardwareFeature): Boolean {
        return iDeviceSharedImpl.supportsFeature(feature)
    }

    override fun services(): MutableMap<String, ServiceInfo> {
        return iDeviceSharedImpl.services()
    }

    override fun getMountPoint(name: String): String? {
        throw UnsupportedOperationException("This method is not used in Android Studio")
    }

    override fun toString(): String {
        return serialNumber
    }

    override fun isOnline(): Boolean {
        return connectedDevice.isOnline
    }

    override fun isEmulator(): Boolean {
        return serialNumber.matches(RE_EMULATOR_SN.toRegex())
    }

    override fun isOffline(): Boolean {
        return connectedDevice.isOffline
    }

    override fun isBootLoader(): Boolean {
        return connectedDevice.deviceInfo.deviceState == com.android.adblib.DeviceState.BOOTLOADER
    }

    override fun hasClients(): Boolean {
        throw UnsupportedOperationException("This method is not used in Android Studio")
    }

    override fun getClients(): Array<Client> {
        return deviceClientManager.clients.toTypedArray()
    }

    override fun getClient(applicationName: String?): Client? {
        return clients.firstOrNull { applicationName == it.clientData.clientDescription }
    }

    override fun getProfileableClients(): Array<ProfileableClient> {
        return deviceClientManager.profileableClients.toTypedArray()
    }

    override fun getSyncService(): SyncService? {
        TODO("Not yet implemented")
    }

    override fun getFileListingService(): FileListingService {
        TODO("Not yet implemented")
    }

    override fun getScreenshot(): RawImage {
        TODO("Not yet implemented")
    }

    override fun getScreenshot(timeout: Long, unit: TimeUnit?): RawImage {
        throw UnsupportedOperationException("This method is not used in Android Studio")
    }

    override fun startScreenRecorder(
        remoteFilePath: String,
        options: ScreenRecorderOptions,
        receiver: IShellOutputReceiver
    ) {
        throw UnsupportedOperationException("This method is not used in Android Studio")
    }

    override fun runEventLogService(receiver: LogReceiver?) {
        throw UnsupportedOperationException("This method is not used in Android Studio")
    }

    override fun runLogService(logname: String?, receiver: LogReceiver?) {
        throw UnsupportedOperationException("This method is not used in Android Studio")
    }

    override fun createForward(localPort: Int, remotePort: Int) {
        runBlockingLegacy {
            val deviceSelector = DeviceSelector.fromSerialNumber(connectedDevice.serialNumber)
            connectedDevice.session.hostServices.forward(
                deviceSelector,
                SocketSpec.Tcp(localPort),
                SocketSpec.Tcp(remotePort),
                rebind = true
            )
        }
    }

    override fun createForward(
        localPort: Int,
        remoteSocketName: String,
        namespace: IDevice.DeviceUnixSocketNamespace
    ) {
        runBlockingLegacy {
            val deviceSelector = DeviceSelector.fromSerialNumber(connectedDevice.serialNumber)
            val remoteSocketSpec = when (namespace) {
                IDevice.DeviceUnixSocketNamespace.ABSTRACT -> SocketSpec.LocalAbstract(
                    remoteSocketName
                )

                IDevice.DeviceUnixSocketNamespace.RESERVED -> SocketSpec.LocalReserved(
                    remoteSocketName
                )

                IDevice.DeviceUnixSocketNamespace.FILESYSTEM -> SocketSpec.LocalFileSystem(
                    remoteSocketName
                )
            }
            connectedDevice.session.hostServices.forward(
                deviceSelector,
                SocketSpec.Tcp(localPort),
                remoteSocketSpec,
                rebind = true
            )
        }
    }

    override fun removeForward(localPort: Int) {
        runBlockingLegacy {
            val deviceSelector = DeviceSelector.fromSerialNumber(connectedDevice.serialNumber)
            connectedDevice.session.hostServices.killForward(
                deviceSelector,
                SocketSpec.Tcp(localPort)
            )
        }
    }

    override fun getClientName(pid: Int): String {
        TODO("Not yet implemented")
    }

    override fun pushFile(local: String, remote: String) {
        runBlockingLegacy {
            val deviceSelector = DeviceSelector.fromSerialNumber(connectedDevice.serialNumber)

            val localFile = File(local).toPath()
            val localFileDate = Files.getLastModifiedTime(localFile)
            Log.d(LOG_TAG, "Uploading $localFile onto device '$serialNumber'")

            mapToSyncException {
                connectedDevice.session.deviceServices.syncSend(
                    deviceSelector,
                    localFile,
                    remote,
                    RemoteFileMode.fromPath(localFile) ?: RemoteFileMode.DEFAULT,
                    localFileDate
                )
            }
        }
    }

    override fun pullFile(remote: String?, local: String?) {
        TODO("Not yet implemented")
    }

    override fun installPackage(
        packageFilePath: String?,
        reinstall: Boolean,
        vararg extraArgs: String?
    ) {
        TODO("Not yet implemented")
    }

    override fun installPackage(
        packageFilePath: String?,
        reinstall: Boolean,
        receiver: InstallReceiver?,
        vararg extraArgs: String?
    ) {
        TODO("Not yet implemented")
    }

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

    override fun installPackages(
        apks: MutableList<File>,
        reinstall: Boolean,
        installOptions: MutableList<String>,
        timeout: Long,
        timeoutUnit: TimeUnit
    ) {
        lastInstallMetrics = try {
            SplitApkInstaller.create(this, apks, reinstall, installOptions)
                .install(timeout, timeoutUnit)
        } catch (e: InstallException) {
            throw e
        } catch (e: Exception) {
            throw InstallException(e)
        }
    }

    override fun installPackages(
        apks: MutableList<File>, reinstall: Boolean, installOptions: MutableList<String>
    ) {
        // Use the default single apk installer timeout.
        installPackages(apks, reinstall, installOptions, INSTALL_TIMEOUT_MINUTES, TimeUnit.MINUTES)
    }

    override fun getLastInstallMetrics(): InstallMetrics? {
        return lastInstallMetrics
    }

    override fun syncPackageToDevice(localFilePath: String?): String {
        TODO("Not yet implemented")
    }

    override fun installRemotePackage(
        remoteFilePath: String?,
        reinstall: Boolean,
        vararg extraArgs: String?
    ) {
        throw UnsupportedOperationException("This method is not used in Android Studio")
    }

    override fun installRemotePackage(
        remoteFilePath: String?,
        reinstall: Boolean,
        receiver: InstallReceiver?,
        vararg extraArgs: String?
    ) {
        throw UnsupportedOperationException("This method is not used in Android Studio")
    }

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

    override fun removeRemotePackage(remoteFilePath: String?) {
        TODO("Not yet implemented")
    }

    override fun uninstallPackage(packageName: String?): String {
        TODO("Not yet implemented")
    }

    override fun uninstallApp(applicationID: String?, vararg extraArgs: String?): String {
        TODO("Not yet implemented")
    }

    override fun reboot(into: String?) {
        TODO("Not yet implemented")
    }

    override fun root(): Boolean {
        TODO("Not yet implemented")
    }

    override fun forceStop(applicationName: String?) {
        iDeviceSharedImpl.forceStop(applicationName)
    }

    override fun kill(applicationName: String?) {
        iDeviceSharedImpl.kill(applicationName)
    }

    override fun isRoot(): Boolean {
        TODO("Not yet implemented")
    }

    @Deprecated("")
    override fun getBatteryLevel(): Int {
        TODO("Not yet implemented")
    }

    @Deprecated("")
    override fun getBatteryLevel(freshnessMs: Long): Int {
        TODO("Not yet implemented")
    }

    override fun getBattery(): Future<Int> {
        TODO("Not yet implemented")
    }

    override fun getBattery(freshnessTime: Long, timeUnit: TimeUnit): Future<Int> {
        TODO("Not yet implemented")
    }

    override fun getAbis(): MutableList<String> {
        return iDeviceSharedImpl.abis
    }

    override fun getDensity(): Int {
        return iDeviceSharedImpl.density
    }

    override fun getLanguage(): String? {
        throw UnsupportedOperationException("This method is not used in Android Studio")
    }

    override fun getRegion(): String? {
        throw UnsupportedOperationException("This method is not used in Android Studio")
    }

    override fun getVersion(): AndroidVersion {
        return iDeviceSharedImpl.version
    }

    override fun executeRemoteCommand(
        command: String,
        rcvr: IShellOutputReceiver,
        maxTimeout: Long,
        maxTimeToOutputResponse: Long,
        maxTimeUnits: TimeUnit
    ) {
        // Note that `AdbHelper.AdbService.SHELL` is passed down to match the behavior of `DeviceImpl`
        executeRemoteCommand(
            AdbHelper.AdbService.SHELL,
            command,
            rcvr,
            maxTimeout,
            maxTimeToOutputResponse,
            maxTimeUnits,
            null /* inputStream */
        )
    }

    override fun executeRemoteCommand(
        command: String,
        rcvr: IShellOutputReceiver,
        maxTimeToOutputResponse: Long,
        maxTimeUnits: TimeUnit
    ) {
        // Note that `AdbHelper.AdbService.SHELL` is passed down to match the behavior of `DeviceImpl`
        executeRemoteCommand(
            AdbHelper.AdbService.SHELL,
            command,
            rcvr,
            maxTimeToOutputResponse,
            maxTimeUnits,
            null /* inputStream */
        )
    }

    override fun executeRemoteCommand(
        adbService: AdbHelper.AdbService,
        command: String,
        rcvr: IShellOutputReceiver,
        maxTimeToOutputResponse: Long,
        maxTimeUnits: TimeUnit,
        `is`: InputStream?
    ) {
        executeRemoteCommand(
            adbService,
            command,
            rcvr,
            0L,
            maxTimeToOutputResponse,
            maxTimeUnits,
            `is`
        )
    }

    override fun executeRemoteCommand(
        adbService: AdbHelper.AdbService,
        command: String,
        receiver: IShellOutputReceiver,
        maxTimeout: Long,
        maxTimeToOutputResponse: Long,
        maxTimeUnits: TimeUnit,
        inputStream: InputStream?
    ) {
        if (adbService != AdbHelper.AdbService.ABB_EXEC) {
            executeShellCommand(
                adbService,
                connectedDevice,
                command,
                receiver,
                maxTimeout,
                maxTimeToOutputResponse,
                maxTimeUnits,
                inputStream
            )
        } else {
            val maxTimeoutDuration =
                if (maxTimeout > 0) Duration.ofMillis(maxTimeUnits.toMillis(maxTimeout)) else INFINITE_DURATION
            runBlockingLegacy(timeout = maxTimeoutDuration) {
                val adbInputChannel = inputStream?.let {connectedDevice.session.channelFactory.wrapInputStream(it)}
                val deviceSelector = DeviceSelector.fromSerialNumber(connectedDevice.serialNumber)
                // TODO: Use `maxTimeToOutputResponse`
                // TODO(b/299483329): wrap abb_exec and abb with a AbbCommand (similar to ShellCommand)
                val abbExecFlow = connectedDevice.session.deviceServices.abb_exec(
                    deviceSelector,
                    command.split(" "),
                    ShellCollectorToIShellOutputReceiver(
                        receiver
                    ),
                    adbInputChannel,
                    // TODO(b/298475728): Revisit this when we are closer to having a working implementation of `IDevice`
                    // If `shutdownOutput` is true then we get a "java.lang.SecurityException: Files still open" exception
                    // when executing a "package install-commit" command after the "package install-write" command
                    // since the package manager doesn't handle shutdown correctly.
                    shutdownOutput = false
                )
                abbExecFlow.first()
            }
        }
    }

    override fun rawExec(executable: String, parameters: Array<out String>): SocketChannel {
        throw UnsupportedOperationException("This method is not used in Android Studio outside ddmlib")
    }

    override fun rawExec2(
        executable: String,
        parameters: Array<out String>
    ): SimpleConnectedSocket = runBlockingLegacy {
        val command = StringBuilder(executable)
        for (parameter in parameters) {
            command.append(" ")
            command.append(parameter)
        }
        val channel = connectedDevice.session.deviceServices.rawExec(
            DeviceSelector.fromSerialNumber(
                serialNumber
            ), command.toString()
        )
        AdblibChannelWrapper(channel)
    }

    /**
     * Similar to [runBlocking] but with a custom [timeout]
     *
     * @throws TimeoutException if [block] take more than [timeout] to execute
     */
    internal fun <R> runBlockingLegacy(
        timeout: Duration = Duration.ofMillis(DdmPreferences.getTimeOut().toLong()),
        block: suspend CoroutineScope.() -> R
    ): R {
        return runBlocking {
            connectedDevice.session.withErrorTimeout(timeout) {
                block()
            }
        }
    }

    /**
     * Maps exceptions throws from the [AdbDeviceSyncServices] methods of `adblib` to the
     * (approximately) equivalent [SyncException] of `ddmlib`.
     *
     * TODO: Map [IOException] and [AdbFailResponseException] to the corresponding [SyncException].
     *       This is not trivial as we don't have enough info in the exceptions thrown
     *       to correctly map to SyncException
     */
    private inline fun <R> mapToSyncException(block: () -> R): R {
        return try {
            block()
        } catch (e: CancellationException) {
            throw SyncException(SyncException.SyncError.CANCELED, e)
        } catch (e: AdbProtocolErrorException) {
            throw SyncException(SyncException.SyncError.TRANSFER_PROTOCOL_ERROR, e)
        } catch (e: AdbFailResponseException) {
            throw SyncException(SyncException.SyncError.TRANSFER_PROTOCOL_ERROR, e)
        }
    }

    companion object {

        private const val LOG_TAG = "AdblibIDeviceWrapper"
        private const val GET_PROP_TIMEOUT_MS = 1000L
        private const val INITIAL_GET_PROP_TIMEOUT_MS = 5000L
    }
}
