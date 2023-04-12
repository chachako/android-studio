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
package com.android.fakeadbserver

import com.android.annotations.concurrency.GuardedBy
import com.android.fakeadbserver.DeviceState.HostConnectionType
import com.android.fakeadbserver.devicecommandhandlers.AbbCommandHandler
import com.android.fakeadbserver.devicecommandhandlers.AbbExecCommandHandler
import com.android.fakeadbserver.devicecommandhandlers.DeviceCommandHandler
import com.android.fakeadbserver.devicecommandhandlers.FakeSyncCommandHandler
import com.android.fakeadbserver.devicecommandhandlers.JdwpCommandHandler
import com.android.fakeadbserver.devicecommandhandlers.ReverseForwardCommandHandler
import com.android.fakeadbserver.devicecommandhandlers.TrackAppCommandHandler
import com.android.fakeadbserver.devicecommandhandlers.TrackJdwpCommandHandler
import com.android.fakeadbserver.hostcommandhandlers.FeaturesCommandHandler
import com.android.fakeadbserver.hostcommandhandlers.ForwardCommandHandler
import com.android.fakeadbserver.hostcommandhandlers.GetDevPathCommandHandler
import com.android.fakeadbserver.hostcommandhandlers.GetSerialNoCommandHandler
import com.android.fakeadbserver.hostcommandhandlers.GetStateCommandHandler
import com.android.fakeadbserver.hostcommandhandlers.HostCommandHandler
import com.android.fakeadbserver.hostcommandhandlers.HostFeaturesCommandHandler
import com.android.fakeadbserver.hostcommandhandlers.KillCommandHandler
import com.android.fakeadbserver.hostcommandhandlers.KillForwardAllCommandHandler
import com.android.fakeadbserver.hostcommandhandlers.KillForwardCommandHandler
import com.android.fakeadbserver.hostcommandhandlers.ListDevicesCommandHandler
import com.android.fakeadbserver.hostcommandhandlers.ListForwardCommandHandler
import com.android.fakeadbserver.hostcommandhandlers.MdnsCommandHandler
import com.android.fakeadbserver.hostcommandhandlers.NetworkConnectCommandHandler
import com.android.fakeadbserver.hostcommandhandlers.NetworkDisconnectCommandHandler
import com.android.fakeadbserver.hostcommandhandlers.PairCommandHandler
import com.android.fakeadbserver.hostcommandhandlers.TrackDevicesCommandHandler
import com.android.fakeadbserver.hostcommandhandlers.VersionCommandHandler
import com.android.fakeadbserver.shellcommandhandlers.ActivityManagerCommandHandler
import com.android.fakeadbserver.shellcommandhandlers.CatCommandHandler
import com.android.fakeadbserver.shellcommandhandlers.CmdCommandHandler
import com.android.fakeadbserver.shellcommandhandlers.DumpsysCommandHandler
import com.android.fakeadbserver.shellcommandhandlers.EchoCommandHandler
import com.android.fakeadbserver.shellcommandhandlers.GetPropCommandHandler
import com.android.fakeadbserver.shellcommandhandlers.LogcatCommandHandler
import com.android.fakeadbserver.shellcommandhandlers.PackageManagerCommandHandler
import com.android.fakeadbserver.shellcommandhandlers.PingCommandHandler
import com.android.fakeadbserver.shellcommandhandlers.RmCommandHandler
import com.android.fakeadbserver.shellcommandhandlers.SetPropCommandHandler
import com.android.fakeadbserver.shellcommandhandlers.ShellProtocolEchoCommandHandler
import com.android.fakeadbserver.shellcommandhandlers.StatCommandHandler
import com.android.fakeadbserver.shellcommandhandlers.WindowManagerCommandHandler
import com.android.fakeadbserver.shellcommandhandlers.WriteNoStopCommandHandler
import com.android.fakeadbserver.statechangehubs.DeviceStateChangeHub
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ThreadFactoryBuilder
import java.io.IOException
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.StandardSocketOptions
import java.nio.channels.ServerSocketChannel
import java.util.Collections
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.function.Consumer
import java.util.function.Supplier

/** See `FakeAdbServerTest#testInteractiveServer()` for example usage.  */
class FakeAdbServer private constructor(var features: Set<String> = DEFAULT_FEATURES) :
    AutoCloseable {

    private val mServerSocket: ServerSocketChannel
    private var mServerSocketLocalAddress: InetSocketAddress? = null

    /**
     * The [CommandHandler]s have internal state. To allow for reentrancy, instead of using a
     * pre-allocated [CommandHandler] object, the constructor is passed in and a new object is
     * created as-needed.
     */
    private val mHostCommandHandlers: MutableMap<String, Supplier<HostCommandHandler>> = HashMap()
    val handlers: MutableList<DeviceCommandHandler> = ArrayList()
    private val mDevices: MutableMap<String, DeviceState> = HashMap()

    // Device ip address to DeviceState. Device may or may not currently be connected to adb.
    private val mNetworkDevices: MutableMap<String, DeviceState> = HashMap()
    private val mMdnsServices: MutableSet<MdnsService> = HashSet()

    /**
     * Grabs the DeviceStateChangeHub from the server. This should only be used for implementations
     * for handlers that inherit from [CommandHandler]. The purpose of the hub is to propagate
     * server events to existing connections with the server.
     *
     *
     * For example, if [.connectDevice] is called, an event will be sent
     * through the [DeviceStateChangeHub] to all open connections waiting on
     * host:track-devices messages.
     */
    val deviceChangeHub = DeviceStateChangeHub()
    private val mLastTransportId = AtomicInteger()

    // This is the executor for accepting incoming connections as well as handling the execution of
    // the commands over the connection. There is one task for accepting connections, and multiple
    // tasks to handle the execution of the commands.
    private val mThreadPoolExecutor = Executors.newCachedThreadPool(
        ThreadFactoryBuilder().setNameFormat("fake-adb-server-connection-pool-%d").build()
    )
    private var mConnectionHandlerTask: Future<*>? = null

    // All "external" server controls are synchronized through a central executor, much like the EDT
    // thread in Swing.
    private val mMainServerThreadExecutor = Executors.newSingleThreadExecutor()

    @Volatile
    private var mServerKeepAccepting = false

    @GuardedBy("this")
    @Volatile
    private var mStopRequestTask: Future<*>? = null

    init {
        mServerSocket = ServerSocketChannel.open()
    }

    @Throws(IOException::class)
    fun start() {
        assert(
            mConnectionHandlerTask == null // Do not reuse the server.
        )
        mServerSocket.bind(InetSocketAddress(InetAddress.getLoopbackAddress(), 0))
        mServerSocket.setOption(StandardSocketOptions.SO_REUSEADDR, true)
        mServerSocketLocalAddress = mServerSocket.localAddress as InetSocketAddress
        mServerKeepAccepting = true
        mConnectionHandlerTask = mThreadPoolExecutor.submit {
            while (mServerKeepAccepting) {
                try { // Socket can not be closed in finally block, because a separate
                    // thread will
                    // read from the socket. Closing the socket leads to a race
                    // condition.
                    val socket = mServerSocket.accept()
                    val handler = ConnectionHandler(this, socket)
                    mThreadPoolExecutor.execute(handler)
                } catch (ignored: IOException) { // close() is called in a separate thread, and will cause
                    // accept() to throw an
                    // exception if closed here.
                }
            }
        }
    }

    val inetAddress: InetAddress
        get() = mServerSocketLocalAddress!!.address
    val port: Int
        get() = mServerSocketLocalAddress!!.port

    /** This method allows for the caller thread to wait until the server shuts down.  */
    @JvmOverloads
    @Throws(InterruptedException::class)
    fun awaitServerTermination(
        time: Long = Int.MAX_VALUE.toLong(),
        unit: TimeUnit? = TimeUnit.DAYS
    ): Boolean {
        return (mMainServerThreadExecutor.awaitTermination(
            time,
            unit
        ) && mThreadPoolExecutor.awaitTermination(time, unit))
    }

    /**
     * Stops the server. This method records the first stop request and all subsequent ones will get
     * that value. This ensures no task submissions are attempted after [ ][.mMainServerThreadExecutor] is shut down.
     *
     * @return a [Future] if the caller needs to wait until the server is stopped.
     */
    @Synchronized
    fun stop(): Future<*>? {
        if (mStopRequestTask == null) {
            mStopRequestTask = mMainServerThreadExecutor.submit {
                if (!mServerKeepAccepting) {
                    return@submit
                }
                mServerKeepAccepting = false
                deviceChangeHub.stop()
                mDevices.forEach { (id: String?, device: DeviceState) -> device.stop() }
                mConnectionHandlerTask!!.cancel(true)
                try {
                    mServerSocket.close()
                } catch (ignored: IOException) {
                }

                // Note: Use "shutdownNow()" to ensure threads of long-running tasks
                // are interrupted, as opposed
                // to merely waiting for the tasks to finish. This is because
                // mThreadPoolExecutor is used to
                // run CommandHandler implementations, and some of them (e.g.
                // TrackJdwpCommandHandler) wait
                // indefinitely on queues and expect to be interrupted as a signal
                // to terminate.
                mThreadPoolExecutor.shutdownNow()
                mMainServerThreadExecutor.shutdown()
            }
        }
        return mStopRequestTask
    }

    @Throws(Exception::class)
    override fun close() {
        try {
            stop()!!.get()
        } catch (ignored: InterruptedException) { // Catch InterruptedException as specified by JavaDoc.
        } catch (ignored: RejectedExecutionException) { // The server has already been closed once
        }
    }

    /**
     * Connects a device to the ADB server. Must be called on the EDT/main thread.
     *
     * @param deviceId           is the unique device ID of the device
     * @param manufacturer       is the manufacturer name of the device
     * @param deviceModel        is the model name of the device
     * @param release            is the Android OS version of the device
     * @param sdk                is the SDK version of the device
     * @param cpuAbi             is the ABI of the device CPU
     * @param properties         is the device properties
     * @param hostConnectionType is the simulated connection type to the device @return the future
     * @return a future to allow synchronization of the side effects of the call
     */
    fun connectDevice(
        deviceId: String,
        manufacturer: String,
        deviceModel: String,
        release: String,
        sdk: String,
        cpuAbi: String,
        properties: Map<String, String>,
        hostConnectionType: HostConnectionType
    ): Future<DeviceState> {
        val device = DeviceState(
            this,
            deviceId,
            manufacturer,
            deviceModel,
            release,
            sdk,
            cpuAbi,
            properties,
            hostConnectionType,
            newTransportId()
        )
        return connectDevice(device)
    }

    private fun connectDevice(device: DeviceState): Future<DeviceState> {
        val deviceId = device.deviceId
        return if (mConnectionHandlerTask == null) {
            assert(!mDevices.containsKey(deviceId))
            mDevices[deviceId] = device
            Futures.immediateFuture(device)
        } else {
            mMainServerThreadExecutor.submit<DeviceState> {
                assert(!mDevices.containsKey(deviceId))
                mDevices[deviceId] = device
                deviceChangeHub.deviceListChanged(mDevices.values)
                device
            }
        }
    }

    fun connectDevice(
        deviceId: String,
        manufacturer: String,
        deviceModel: String,
        release: String,
        sdk: String,
        hostConnectionType: HostConnectionType
    ): Future<DeviceState> {
        return connectDevice(
            deviceId,
            manufacturer,
            deviceModel,
            release,
            sdk,
            "x86_64",
            emptyMap(),
            hostConnectionType
        )
    }

    fun addDevice(deviceConfig: DeviceStateConfig?) {
        val device = DeviceState(this, newTransportId(), deviceConfig!!)
        mDevices[device.deviceId] = device
    }

    fun registerNetworkDevice(
        address: String,
        deviceId: String,
        manufacturer: String,
        deviceModel: String,
        release: String,
        sdk: String,
        hostConnectionType: HostConnectionType
    ) {
        val device = DeviceState(
            this,
            deviceId,
            manufacturer,
            deviceModel,
            release,
            sdk,
            "x86_64",
            emptyMap(),
            hostConnectionType,
            newTransportId()
        )
        mNetworkDevices[address] = device
    }

    fun connectNetworkDevice(address: String): Future<DeviceState> {
        val device = mNetworkDevices[address]
        return device?.let { connectDevice(it) }
            ?: Futures.immediateFailedFuture(Exception("Device $address not found"))
    }

    fun disconnectNetworkDevice(address: String): Future<*> {
        val device = mNetworkDevices[address]
        return if (device != null) {
            disconnectDevice(device.deviceId)
        } else { // The real adb will disconnect ongoing sessions, but this is an edge case.
            Futures.immediateFuture<Any?>(null)
        }
    }

    fun addMdnsService(service: MdnsService): Future<*> {
        return if (mConnectionHandlerTask == null) {
            assert(!mMdnsServices.contains(service))
            mMdnsServices.add(service)
            Futures.immediateFuture<Any?>(null)
        } else {
            mMainServerThreadExecutor.submit<Any?> {
                assert(!mMdnsServices.contains(service))
                mMdnsServices.add(service)
                null
            }
        }
    }

    fun removeMdnsService(service: MdnsService): Future<*> {
        return if (mConnectionHandlerTask == null) {
            mMdnsServices.remove(service)
            Futures.immediateFuture<Any?>(null)
        } else {
            mMainServerThreadExecutor.submit<Any?> {
                mMdnsServices.remove(service)
                null
            }
        }
    }

    val mdnsServicesCopy: Future<List<MdnsService>>
        /**
         * Thread-safely gets a copy of the mDNS service list. This is useful for asynchronous handlers.
         */
        get() = mMainServerThreadExecutor.submit<List<MdnsService>> { ArrayList(mMdnsServices) }

    private fun newTransportId(): Int {
        return mLastTransportId.incrementAndGet()
    }

    /**
     * Removes a device from the ADB server. Must be called on the EDT/main thread.
     *
     * @param deviceId is the unique device ID of the device
     * @return a future to allow synchronization of the side effects of the call
     */
    fun disconnectDevice(deviceId: String): Future<*> {
        return mMainServerThreadExecutor.submit {
            assert(mDevices.containsKey(deviceId))
            val removedDevice = mDevices.remove(deviceId)
            removedDevice?.stop()
            deviceChangeHub.deviceListChanged(mDevices.values)
        }
    }

    val deviceListCopy: Future<List<DeviceState>>
        /**
         * Thread-safely gets a copy of the device list. This is useful for asynchronous handlers.
         */
        get() = mMainServerThreadExecutor.submit<List<DeviceState>> { ArrayList(mDevices.values) }

    fun getHostCommandHandler(command: String): HostCommandHandler? {
        val supplier = mHostCommandHandlers[command]
        return supplier?.get()
    }

    val currentConfig: FakeAdbServerConfig
        get() {
            val config = FakeAdbServerConfig()
            config.hostHandlers.putAll(mHostCommandHandlers)
            config.deviceHandlers.addAll(handlers)
            config.mdnsServices.addAll(mMdnsServices)
            mDevices.forEach { (serial: String?, device: DeviceState) ->
                val deviceConfig = device.config
                config.devices.add(deviceConfig)
            }
            return config
        }

    class Builder {

        private val mServer: FakeAdbServer
        private var mConfig: FakeAdbServerConfig? = null

        init {
            mServer = FakeAdbServer()
        }

        /** Used to restore a [FakeAdbServer] instance from a previously running instance  */
        fun setConfig(config: FakeAdbServerConfig): Builder {
            mConfig = config
            return this
        }

        /**
         * Sets the handler for a specific host ADB command. This only needs to be called if the
         * test author requires additional functionality that is not provided by the default [ ]s.
         *
         * @param command            The ADB protocol string of the command.
         * @param handlerConstructor The constructor for the handler.
         */
        fun setHostCommandHandler(
            command: String, handlerConstructor: Supplier<HostCommandHandler>
        ): Builder {
            mServer.mHostCommandHandlers[command] = handlerConstructor
            return this
        }

        /**
         * Adds the handler for a device command. Handlers added last take priority over existing
         * handlers.
         */
        fun addDeviceHandler(handler: DeviceCommandHandler): Builder {
            mServer.handlers.add(0, handler)
            return this
        }

        /**
         * Installs the default set of host command handlers. The user may override any command
         * handler.
         */
        fun installDefaultCommandHandlers(): Builder {
            setHostCommandHandler(KillCommandHandler.COMMAND) { KillCommandHandler() }
            setHostCommandHandler(
                ListDevicesCommandHandler.COMMAND
            ) { ListDevicesCommandHandler() }
            setHostCommandHandler(
                ListDevicesCommandHandler.LONG_COMMAND
            ) { ListDevicesCommandHandler(true) }
            setHostCommandHandler(
                TrackDevicesCommandHandler.COMMAND
            ) { TrackDevicesCommandHandler() }
            setHostCommandHandler(
                TrackDevicesCommandHandler.LONG_COMMAND
            ) { TrackDevicesCommandHandler(true) }
            setHostCommandHandler(ForwardCommandHandler.COMMAND) { ForwardCommandHandler() }
            setHostCommandHandler(KillForwardCommandHandler.COMMAND) { KillForwardCommandHandler() }
            setHostCommandHandler(
                KillForwardAllCommandHandler.COMMAND
            ) { KillForwardAllCommandHandler() }
            setHostCommandHandler(
                ListForwardCommandHandler.COMMAND
            ) { ListForwardCommandHandler() }
            setHostCommandHandler(FeaturesCommandHandler.COMMAND) { FeaturesCommandHandler() }
            setHostCommandHandler(
                HostFeaturesCommandHandler.COMMAND
            ) { HostFeaturesCommandHandler() }
            setHostCommandHandler(VersionCommandHandler.COMMAND) { VersionCommandHandler() }
            setHostCommandHandler(MdnsCommandHandler.COMMAND) { MdnsCommandHandler() }
            setHostCommandHandler(PairCommandHandler.COMMAND) { PairCommandHandler() }
            setHostCommandHandler(GetStateCommandHandler.COMMAND) { GetStateCommandHandler() }
            setHostCommandHandler(GetSerialNoCommandHandler.COMMAND) { GetSerialNoCommandHandler() }
            setHostCommandHandler(GetDevPathCommandHandler.COMMAND) { GetDevPathCommandHandler() }
            setHostCommandHandler(
                NetworkConnectCommandHandler.COMMAND
            ) { NetworkConnectCommandHandler() }
            setHostCommandHandler(
                NetworkDisconnectCommandHandler.COMMAND
            ) { NetworkDisconnectCommandHandler() }
            addDeviceHandler(TrackJdwpCommandHandler())
            addDeviceHandler(TrackAppCommandHandler())
            addDeviceHandler(FakeSyncCommandHandler())
            addDeviceHandler(ReverseForwardCommandHandler())
            addDeviceHandler(PingCommandHandler(ShellProtocolType.EXEC))
            addDeviceHandler(RmCommandHandler(ShellProtocolType.SHELL))
            addDeviceHandler(LogcatCommandHandler(ShellProtocolType.SHELL))
            addDeviceHandler(GetPropCommandHandler(ShellProtocolType.EXEC))
            addDeviceHandler(GetPropCommandHandler(ShellProtocolType.SHELL))
            addDeviceHandler(GetPropCommandHandler(ShellProtocolType.SHELL_V2))
            addDeviceHandler(SetPropCommandHandler(ShellProtocolType.SHELL))
            addDeviceHandler(WriteNoStopCommandHandler(ShellProtocolType.SHELL))
            addDeviceHandler(PackageManagerCommandHandler(ShellProtocolType.SHELL))
            addDeviceHandler(WindowManagerCommandHandler(ShellProtocolType.SHELL))
            addDeviceHandler(CmdCommandHandler(ShellProtocolType.EXEC))
            addDeviceHandler(CmdCommandHandler(ShellProtocolType.SHELL))
            addDeviceHandler(DumpsysCommandHandler(ShellProtocolType.SHELL))
            addDeviceHandler(CatCommandHandler(ShellProtocolType.EXEC))
            addDeviceHandler(CatCommandHandler(ShellProtocolType.SHELL))
            addDeviceHandler(CatCommandHandler(ShellProtocolType.SHELL_V2))
            addDeviceHandler(EchoCommandHandler(ShellProtocolType.SHELL))
            addDeviceHandler(ShellProtocolEchoCommandHandler())
            addDeviceHandler(AbbCommandHandler())
            addDeviceHandler(AbbExecCommandHandler())
            addDeviceHandler(ActivityManagerCommandHandler(ShellProtocolType.SHELL))
            addDeviceHandler(ActivityManagerCommandHandler(ShellProtocolType.SHELL_V2))
            addDeviceHandler(JdwpCommandHandler())
            addDeviceHandler(StatCommandHandler(ShellProtocolType.SHELL))
            return this
        }

        fun build(): FakeAdbServer {
            if (mConfig != null) {
                mConfig!!.hostHandlers.forEach { (command: String, handlerConstructor: Supplier<HostCommandHandler>) ->
                    setHostCommandHandler(
                        command, handlerConstructor
                    )
                }
                mConfig!!.deviceHandlers.forEach(Consumer { handler: DeviceCommandHandler ->
                    addDeviceHandler(
                        handler
                    )
                })
                mConfig!!.devices.forEach(Consumer { deviceConfig: DeviceStateConfig? ->
                    mServer.addDevice(
                        deviceConfig
                    )
                })
                mConfig!!.mdnsServices.forEach(Consumer { service: MdnsService ->
                    mServer.addMdnsService(
                        service
                    )
                })
            }
            return mServer
        }

        fun setFeatures(features: Set<String>) {
            mServer.features = features
        }
    }

    companion object {

        private val DEFAULT_FEATURES = Collections.unmodifiableSet(
            HashSet(
                mutableListOf(
                    "push_sync",
                    "fixed_push_mkdir",
                    "shell_v2",
                    "apex",
                    "stat_v2",
                    "cmd",
                    "abb",
                    "abb_exec"
                )
            )
        )
    }
}
