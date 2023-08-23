package com.android.adblib.ddmlibcompatibility

import com.android.adblib.testingutils.CoroutineTestUtils.runBlockingWithTimeout
import com.android.adblib.testingutils.CoroutineTestUtils.yieldUntil
import com.android.adblib.testingutils.FakeAdbServerProviderRule
import com.android.ddmlib.AndroidDebugBridge
import com.android.ddmlib.IDevice
import com.android.ddmlib.idevicemanager.IDeviceManagerListener
import com.android.fakeadbserver.DeviceState
import com.android.fakeadbserver.devicecommandhandlers.SyncCommandHandler
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

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

    private class TestIDeviceManagerListener : IDeviceManagerListener {

        override fun addedDevices(deviceList: MutableList<IDevice>) {}

        override fun removedDevices(deviceList: MutableList<IDevice>) {}
    }
}
