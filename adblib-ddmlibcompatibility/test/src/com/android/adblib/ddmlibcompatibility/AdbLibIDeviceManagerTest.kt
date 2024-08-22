package com.android.adblib.ddmlibcompatibility

import com.android.adblib.testingutils.CoroutineTestUtils.runBlockingWithTimeout
import com.android.adblib.testingutils.CoroutineTestUtils.yieldUntil
import com.android.adblib.testingutils.FakeAdbServerProviderRule
import com.android.annotations.concurrency.WorkerThread
import com.android.ddmlib.AndroidDebugBridge
import com.android.ddmlib.IDevice
import com.android.ddmlib.idevicemanager.IDeviceManagerListener
import com.android.fakeadbserver.DeviceState
import com.android.fakeadbserver.devicecommandhandlers.SyncCommandHandler
import kotlinx.coroutines.delay
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import kotlin.math.max

class AdbLibIDeviceManagerTest {

    @JvmField
    @Rule
    val fakeAdbRule = FakeAdbServerProviderRule {
        installDefaultCommandHandlers()
        installDeviceHandler(SyncCommandHandler())
    }

    private val fakeAdb get() = fakeAdbRule.fakeAdb
    private val bridge = AndroidDebugBridge.createBridge() ?: error("Couldn't create a bridge")

    @Test
    fun hasInitialDeviceList() = runBlockingWithTimeout {
        // Prepare
        val deviceManager =
            AdbLibIDeviceManager(fakeAdbRule.adbSession, bridge, TestIDeviceManagerListener())

        // Act / Assert
        yieldUntil { deviceManager.hasInitialDeviceList() }
    }

    @Test
    fun getDevices() = runBlockingWithTimeout {
        // Prepare
        val deviceManager =
            AdbLibIDeviceManager(fakeAdbRule.adbSession, bridge, TestIDeviceManagerListener())
        val fakeDevice = fakeAdb.connectDevice(
            "dev1234",
            "test1",
            "test2",
            "model",
            "sdk",
            DeviceState.HostConnectionType.USB
        )
        fakeDevice.deviceStatus = DeviceState.DeviceStatus.ONLINE

        // Act / Assert
        yieldUntil { deviceManager.devices.size == 1 }
        assertEquals("dev1234", deviceManager.devices[0].serialNumber)
    }

    @Test
    fun tracksDeviceStateChanges() = runBlockingWithTimeout {
        // Prepare
        val iDeviceManagerListener = TestIDeviceManagerListener()
        val deviceManager =
            AdbLibIDeviceManager(fakeAdbRule.adbSession, bridge, iDeviceManagerListener)
        val fakeDevice = fakeAdb.connectDevice(
            "dev1234",
            "test1",
            "test2",
            "model",
            "sdk",
            DeviceState.HostConnectionType.USB
        )
        fakeDevice.deviceStatus = DeviceState.DeviceStatus.BOOTLOADER

        // Act / Assert
        yieldUntil { deviceManager.devices.size == 1 }
        // Wait a little for the `IDeviceManagerListener` events to get processed
        delay(100)
        assertFalse(deviceManager.devices[0].isOnline)

        fakeDevice.deviceStatus = DeviceState.DeviceStatus.ONLINE
        // Wait a little for the `IDeviceManagerListener` events to get processed
        delay(100)
        assertTrue(deviceManager.devices[0].isOnline)
        assertArrayEquals(
            arrayOf(
                TestIDeviceManagerListener.EventType.Added,
                TestIDeviceManagerListener.EventType.StateChanged
            ), iDeviceManagerListener.events.toTypedArray()
        )
        assertArrayEquals(
            arrayOf(IDevice.DeviceState.BOOTLOADER),
            iDeviceManagerListener.addedDevicesStateValues.toTypedArray()
        )
        assertArrayEquals(
            arrayOf(IDevice.DeviceState.ONLINE),
            iDeviceManagerListener.deviceStateChangedValues.toTypedArray()
        )
    }

    @Test
    fun removingOnlineDevice_doesNotTriggerDeviceChangeEventForDisconnectedValue() = runBlockingWithTimeout {
        // Prepare
        val deviceId = "dev1234"
        val iDeviceManagerListener = TestIDeviceManagerListener()
        val deviceManager =
            AdbLibIDeviceManager(fakeAdbRule.adbSession, bridge, iDeviceManagerListener)
        val fakeDevice = fakeAdb.connectDevice(
            deviceId,
            "test1",
            "test2",
            "model",
            "sdk",
            DeviceState.HostConnectionType.USB
        )
        fakeDevice.deviceStatus = DeviceState.DeviceStatus.ONLINE

        // Act / Assert
        yieldUntil { deviceManager.devices.size == 1 }
        // Wait a little for the `IDeviceManagerListener` events to get processed
        delay(100)
        val device = deviceManager.devices[0]
        assertTrue(device.isOnline)

        fakeAdb.disconnectDevice(deviceId)
        yieldUntil { deviceManager.devices.size == 0 }
        // Wait a little for the `IDeviceManagerListener` events to get processed
        delay(100)
        assertEquals(IDevice.DeviceState.DISCONNECTED, device.state)
        assertArrayEquals(
            arrayOf(
                TestIDeviceManagerListener.EventType.Added,
                TestIDeviceManagerListener.EventType.Removed
            ), iDeviceManagerListener.events.toTypedArray()
        )
        assertArrayEquals(
            arrayOf(IDevice.DeviceState.DISCONNECTED),
            iDeviceManagerListener.removedDevicesStateValues.toTypedArray()
        )
        // Assert that `DISCONNECTED` device state changed event didn't trigger
        assertTrue(iDeviceManagerListener.deviceStateChangedValues.isEmpty())
    }

    @Test
    fun testStateChangeUpdatesAreSerialized() = runBlockingWithTimeout {
        // Prepare
        val iDeviceManagerListener = object : IDeviceManagerListener {
            val lock = Any()

            @Volatile
            var totalCalls = 0

            @Volatile
            var concurrentCalls = 0

            @Volatile
            var maxConcurrentCalls = 0

            @WorkerThread
            override fun addedDevices(deviceList: MutableList<IDevice>) {}

            @WorkerThread
            override fun removedDevices(deviceList: MutableList<IDevice>) {}

            @WorkerThread
            override fun deviceStateChanged(device: IDevice) {
                Thread.sleep(1)
                synchronized(lock) {
                    totalCalls++
                    concurrentCalls++
                    maxConcurrentCalls = max(concurrentCalls, maxConcurrentCalls)
                }
                Thread.sleep(1)
                synchronized(lock) {
                    concurrentCalls--
                }
            }
        }
        val deviceManager =
            AdbLibIDeviceManager(fakeAdbRule.adbSession, bridge, iDeviceManagerListener)
        val fakeDevice1 = fakeAdb.connectDevice(
            "dev1234",
            "test1",
            "test2",
            "model",
            "sdk",
            DeviceState.HostConnectionType.USB
        )
        val fakeDevice2 = fakeAdb.connectDevice(
            "dev87878",
            "test1",
            "test2",
            "model",
            "sdk",
            DeviceState.HostConnectionType.USB
        )

        // Act
        yieldUntil { deviceManager.devices.size == 2 }
        while (iDeviceManagerListener.totalCalls < 100) {
            fakeDevice1.deviceStatus = DeviceState.DeviceStatus.ONLINE
            fakeDevice2.deviceStatus = DeviceState.DeviceStatus.ONLINE
            delay(5)
            fakeDevice1.deviceStatus = DeviceState.DeviceStatus.OFFLINE
            fakeDevice2.deviceStatus = DeviceState.DeviceStatus.OFFLINE
            delay(5)
        }

        // Assert
        assertEquals(
            "There were more than one concurrent call to the listener, meaning calls were not serialized as expected",
            1,
            iDeviceManagerListener.maxConcurrentCalls
        )
    }

    private class TestIDeviceManagerListener : IDeviceManagerListener {

        enum class EventType {
            Added,
            Removed,
            StateChanged
        }

        val events = mutableListOf<EventType>()

        val addedDevicesStateValues = mutableListOf<IDevice.DeviceState?>()
        val removedDevicesStateValues = mutableListOf<IDevice.DeviceState?>()
        val deviceStateChangedValues = mutableListOf<IDevice.DeviceState?>()

        @WorkerThread
        override fun addedDevices(deviceList: MutableList<IDevice>) {
            deviceList.forEach { addedDevicesStateValues.add(it.state) }
            events.add(EventType.Added)
        }

        @WorkerThread
        override fun removedDevices(deviceList: MutableList<IDevice>) {
            deviceList.forEach { removedDevicesStateValues.add(it.state) }
            events.add(EventType.Removed)
        }

        @WorkerThread
        override fun deviceStateChanged(device: IDevice) {
            deviceStateChangedValues.add(device.state)
            events.add(EventType.StateChanged)
        }
    }
}
