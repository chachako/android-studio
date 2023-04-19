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
package com.android.fakeadbserver

import com.android.fakeadbserver.services.Service
import com.android.fakeadbserver.services.ServiceManager
import com.android.fakeadbserver.statechangehubs.ClientStateChangeHandlerFactory
import com.android.fakeadbserver.statechangehubs.ClientStateChangeHub
import com.android.fakeadbserver.statechangehubs.StateChangeQueue
import com.google.common.collect.ImmutableMap
import java.util.Collections
import java.util.TreeMap
import java.util.Vector
import java.util.function.Consumer
import java.util.stream.Collectors

class DeviceState internal constructor(
    private val mServer: FakeAdbServer,
    val deviceId: String,
    val manufacturer: String,
    val model: String,
    val buildVersionRelease: String,
    val buildVersionSdk: String,
    val cpuAbi: String,
    properties: Map<String, String>,
    val hostConnectionType: HostConnectionType,
    val transportId: Int
) {

    val clientChangeHub = ClientStateChangeHub()
    private val mFiles: MutableMap<String, DeviceFileState> = HashMap()
    private val mLogcatMessages: MutableList<String> = ArrayList()

    /** PID -> [ProcessState]  */
    private val mProcessStates: MutableMap<Int, ProcessState> = HashMap()
    private val mPortForwarders: MutableMap<Int, PortForwarder?> = HashMap()
    private val mReversePortForwarders: MutableMap<Int, PortForwarder?> = HashMap()
    val features: Set<String>
    val properties: Map<String, String>
    private var mDeviceStatus: DeviceStatus
    val serviceManager: ServiceManager

    // Keep track of all PM commands invocation
    private val mPmLogs = Vector<String>()

    // Keep track of all cmd commands invocation
    private val mCmdLogs = Vector<String>()

    // Keep track of all ABB/ABB_EXEC commands invocation
    private val mAbbLogs = Vector<String>()

    init {
        features = initFeatures(buildVersionSdk)
        this.properties =
            combinedProperties(
                deviceId,
                manufacturer,
                model,
                buildVersionRelease,
                buildVersionSdk,
                cpuAbi,
                properties
            )
        mDeviceStatus = DeviceStatus.OFFLINE
        serviceManager = ServiceManager()
    }

    internal constructor(server: FakeAdbServer, transportId: Int, config: DeviceStateConfig) : this(
        server,
        config.serialNumber,
        config.manufacturer,
        config.model,
        config.buildVersionRelease,
        config.buildVersionSdk,
        config.cpuAbi,
        config.properties,
        config.hostConnectionType,
        transportId
    ) {
        config.files.forEach(Consumer { fileState: DeviceFileState ->
            mFiles[fileState.path] =
                fileState
        })
        mLogcatMessages.addAll(config.logcatMessages)
        mDeviceStatus = config.deviceStatus
        config.processes
            .forEach(Consumer { clientState: ProcessState ->
                mProcessStates[clientState.pid] =
                    clientState
            })
    }

    fun stop() {
        clientChangeHub.stop()
    }

    val apiLevel: Int
        get() = try {
            buildVersionSdk.toInt()
        } catch (e: NumberFormatException) {
            1
        }
    var deviceStatus: DeviceStatus
        get() = mDeviceStatus
        set(status) {
            mDeviceStatus = status
            mServer.deviceChangeHub.deviceStatusChanged(this, status)
        }

    fun addLogcatMessage(message: String) {
        synchronized(mLogcatMessages) {
            mLogcatMessages.add(message)
            clientChangeHub.logcatMessageAdded(message)
        }
    }

    fun subscribeLogcatChangeHandler(
        handlerFactory: ClientStateChangeHandlerFactory
    ): LogcatChangeHandlerSubscriptionResult? {
        synchronized(mLogcatMessages) {
            val queue = clientChangeHub.subscribe(handlerFactory) ?: return null
            return LogcatChangeHandlerSubscriptionResult(
                queue, ArrayList(mLogcatMessages)
            )
        }
    }

    fun createFile(file: DeviceFileState) {
        synchronized(mFiles) { mFiles.put(file.path, file) }
    }

    fun getFile(filepath: String): DeviceFileState? {
        synchronized(mFiles) { return mFiles[filepath] }
    }

    fun deleteFile(filepath: String) {
        synchronized(mFiles) { mFiles.remove(filepath) }
    }

    fun startClient(
        pid: Int, uid: Int, packageName: String, isWaiting: Boolean
    ): ClientState {
        return startClient(pid, uid, packageName, packageName, isWaiting)
    }

    fun startClient(
        pid: Int,
        uid: Int,
        processName: String,
        packageName: String,
        isWaiting: Boolean
    ): ClientState {
        synchronized(mProcessStates) {
            val clientState = ClientState(pid, uid, processName, packageName, isWaiting)
            if (apiLevel >= 34) {
                clientState.setStage(AppStage.BOOT)
            }
            mProcessStates[pid] = clientState
            clientChangeHub.clientListChanged()
            clientChangeHub.appProcessListChanged()
            return clientState
        }
    }

    fun stopClient(pid: Int) {
        synchronized(mProcessStates) {
            val processState = mProcessStates.remove(pid)
            if (processState is ClientState) {
                clientChangeHub.clientListChanged()
                clientChangeHub.appProcessListChanged()
                processState.stopJdwpSession()
            }
        }
    }

    fun getClient(pid: Int): ClientState? {
        synchronized(mProcessStates) {
            val processState = mProcessStates[pid]
            return if (processState is ClientState) {
                processState
            } else {
                null
            }
        }
    }

    fun startProfileableProcess(
        pid: Int, architecture: String, commandLine: String
    ): ProfileableProcessState {
        synchronized(mProcessStates) {
            val process = ProfileableProcessState(pid, architecture, commandLine)
            mProcessStates[pid] = process
            clientChangeHub.appProcessListChanged()
            return process
        }
    }

    fun stopProfileableProcess(pid: Int) {
        synchronized(mProcessStates) {
            val process = mProcessStates.remove(pid)
            if (process is ProfileableProcessState) {
                clientChangeHub.appProcessListChanged()
            }
        }
    }

    fun getProfileableProcess(pid: Int): ProfileableProcessState? {
        synchronized(mProcessStates) {
            val process = mProcessStates[pid]
            return if (process is ProfileableProcessState) {
                process
            } else {
                null
            }
        }
    }

    val allPortForwarders: ImmutableMap<Int, PortForwarder?>
        get() {
            synchronized(mPortForwarders) { return ImmutableMap.copyOf(mPortForwarders) }
        }

    val allReversePortForwarders: ImmutableMap<Int, PortForwarder?>
        get() {
            synchronized(mReversePortForwarders) { return ImmutableMap.copyOf(mReversePortForwarders) }
        }

    fun addPortForwarder(forwarder: PortForwarder, noRebind: Boolean): Boolean {
        synchronized(mPortForwarders) {
            return if (noRebind) {
                (mPortForwarders.computeIfAbsent(
                    forwarder.source.port
                ) { port: Int? -> forwarder }
                        == forwarder)
            } else {
                // Just overwrite the previous forwarder.
                mPortForwarders[forwarder.source.port] = forwarder
                true
            }
        }
    }

    fun addReversePortForwarder(forwarder: PortForwarder, noRebind: Boolean): Boolean {
        synchronized(mReversePortForwarders) {
            return if (noRebind) {
                (mReversePortForwarders.computeIfAbsent(
                    forwarder.source.port
                ) { port: Int? -> forwarder }
                        == forwarder)
            } else {
                // Just overwrite the previous forwarder.
                mReversePortForwarders[forwarder.source.port] = forwarder
                true
            }
        }
    }

    fun removePortForwarder(hostPort: Int): Boolean {
        synchronized(mPortForwarders) { return mPortForwarders.remove(hostPort) != null }
    }

    fun removeReversePortForwarder(hostPort: Int): Boolean {
        synchronized(mReversePortForwarders) { return mReversePortForwarders.remove(hostPort) != null }
    }

    fun removeAllPortForwarders() {
        synchronized(mPortForwarders) { mPortForwarders.clear() }
    }

    fun removeAllReversePortForwarders() {
        synchronized(mReversePortForwarders) { mReversePortForwarders.clear() }
    }

    val clientListString: String
        get() {
            synchronized(mProcessStates) {
                return mProcessStates.values.stream()
                    .filter { process: ProcessState? -> process is ClientState }
                    .map { clientState: ProcessState -> Integer.toString(clientState.pid) }
                    .collect(Collectors.joining("\n"))
            }
        }

    fun copyOfProcessStates(): List<ProcessState> {
        synchronized(mProcessStates) { return ArrayList(mProcessStates.values) }
    }

    val config: DeviceStateConfig
        get() = DeviceStateConfig(
            deviceId,
            ArrayList(mFiles.values),
            ArrayList(mLogcatMessages),
            ArrayList(mProcessStates.values),
            hostConnectionType,
            manufacturer,
            model,
            buildVersionRelease,
            buildVersionSdk,
            cpuAbi,
            properties,
            mDeviceStatus
        )

    fun setActivityManager(newActivityManager: Service?) {
        serviceManager.setActivityManager(newActivityManager!!)
    }

    fun addPmLog(cmd: String) {
        mPmLogs.add(cmd)
    }

    val pmLogs: List<String>
        get() = mPmLogs.clone() as List<String>

    fun addCmdLog(cmd: String) {
        mCmdLogs.add(cmd)
    }

    val cmdLogs: List<String>
        get() = mCmdLogs.clone() as List<String>

    fun addAbbLog(cmd: String) {
        mAbbLogs.add(cmd)
    }

    val abbLogs: List<String>
        get() = mAbbLogs.clone() as List<String>

    /**
     * The state of a device.
     */
    enum class DeviceStatus( //$NON-NLS-1$
        val state: String
    ) {

        BOOTLOADER("bootloader"),  //$NON-NLS-1$

        /** bootloader mode with is-userspace = true though `adb reboot fastboot`  */
        FASTBOOTD("fastbootd"),  //$NON-NLS-1$
        OFFLINE("offline"),  //$NON-NLS-1$
        ONLINE("device"),  //$NON-NLS-1$
        RECOVERY("recovery"),  //$NON-NLS-1$

        /**
         * Device is in "sideload" state either through `adb sideload` or recovery menu
         */
        SIDELOAD("sideload"),  //$NON-NLS-1$
        UNAUTHORIZED("unauthorized"),  //$NON-NLS-1$
        DISCONNECTED("disconnected");

        companion object {

            /**
             * Returns a [DeviceStatus] from the string returned by `adb devices`.
             *
             * @param state the device state.
             * @return a {DeviceStatus} object or `null` if the state is unknown.
             */
            fun getState(state: String): DeviceStatus? {
                for (deviceStatus in values()) {
                    if (deviceStatus.state == state) {
                        return deviceStatus
                    }
                }
                return null
            }
        }
    }

    enum class HostConnectionType {
        USB, LOCAL, NETWORK
    }

    /**
     * This class represents the result of calling [subscribeLogcatChangeHandler]. This is needed to
     * synchronize between adding the listener and getting the correct lines from the logcat buffer.
     */
    class LogcatChangeHandlerSubscriptionResult(
        val mQueue: StateChangeQueue,
        val mLogcatContents: List<String>
    )

    companion object {

        private fun initFeatures(sdk: String): Set<String> {
            val features: MutableSet<String> =
                HashSet(mutableListOf("push_sync", "fixed_push_mkdir", "apex"))
            try {
                val api = sdk.toInt()
                if (api >= 24) {
                    features.add("cmd")
                    features.add("shell_v2")
                    features.add("stat_v2")
                }
                if (api >= 30) {
                    features.add("abb")
                    features.add("abb_exec")
                }
                if (api >= 34) {
                    features.add("support_boot_stages")
                }
            } catch (e: NumberFormatException) {
                // Cannot add more features based on API level since it is not the expected integer
                // This is expected in many of our test that don't pass a correct value but instead
                // pass "sdk". In such case, we return the default set of features.
                // TODO: Fix adblist test to not send "sdk" and delete this catch.
            }
            return Collections.unmodifiableSet(features)
        }

        private fun combinedProperties(
            serialNumber: String,
            manufacturer: String,
            model: String,
            release: String,
            sdk: String,
            cpuAbi: String,
            properties: Map<String, String>
        ): Map<String, String> {
            val combined: MutableMap<String, String> = TreeMap(properties)
            combined["ro.serialno"] = serialNumber
            combined["ro.product.manufacturer"] = manufacturer
            combined["ro.product.model"] = model
            combined["ro.build.version.release"] = release
            combined["ro.build.version.sdk"] = sdk
            combined["ro.product.cpu.abi"] = cpuAbi
            return combined
        }
    }
}
