package com.android.adblib.ddmlibcompatibility.debugging

import com.android.adblib.AdbSession
import com.android.adblib.ConnectedDevice
import com.android.adblib.connectedDevicesTracker
import com.android.adblib.deviceInfo
import com.android.adblib.serialNumber
import com.android.adblib.testingutils.CoroutineTestUtils.runBlockingWithTimeout
import com.android.adblib.testingutils.FakeAdbServerProviderRule
import com.android.ddmlib.IDevice
import com.android.fakeadbserver.DeviceState
import com.android.fakeadbserver.devicecommandhandlers.SyncCommandHandler
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.mapNotNull
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class AdblibIDeviceWrapperTest {

    @JvmField
    @Rule
    val fakeAdbRule = FakeAdbServerProviderRule {
        installDefaultCommandHandlers()
        installDeviceHandler(SyncCommandHandler())
    }

    private val fakeAdb get() = fakeAdbRule.fakeAdb
    private val hostServices get() = fakeAdbRule.adbSession.hostServices

    @Test
    fun getSerialNumber() = runBlockingWithTimeout {
        // Prepare
        val serialNumber = "ABC123DEF"
        val connectedDevice = createConnectedDevice(serialNumber, DeviceState.DeviceStatus.ONLINE)
        val adblibIDeviceWrapper = AdblibIDeviceWrapper(connectedDevice)

        // Act / Assert
        assertEquals(serialNumber, adblibIDeviceWrapper.serialNumber)
    }

    @Test
    fun getState() = runBlockingWithTimeout {
        // Prepare
        val connectedDevice = createConnectedDevice("device1", DeviceState.DeviceStatus.FASTBOOTD)
        val adblibIDeviceWrapper = AdblibIDeviceWrapper(connectedDevice)

        // Act / Assert
        assertEquals(IDevice.DeviceState.FASTBOOTD, adblibIDeviceWrapper.state)
    }

    @Test
    fun toStringReturnsSerialNumber() = runBlockingWithTimeout {
        // Prepare
        val serialNumber = "kjdlkjsi837892"
        val connectedDevice = createConnectedDevice(serialNumber, DeviceState.DeviceStatus.ONLINE)
        val adblibIDeviceWrapper = AdblibIDeviceWrapper(connectedDevice)

        // Act / Assert
        assertEquals(serialNumber, adblibIDeviceWrapper.toString())
    }

    @Test
    fun isOnline() = runBlockingWithTimeout {
        // Prepare
        val connectedDevice = createConnectedDevice("device1", DeviceState.DeviceStatus.ONLINE)
        val adblibIDeviceWrapper = AdblibIDeviceWrapper(connectedDevice)

        // Act / Assert
        assertTrue(adblibIDeviceWrapper.isOnline)
        assertFalse(adblibIDeviceWrapper.isOffline)
    }

    @Test
    fun isOffline() = runBlockingWithTimeout {
        // Prepare
        val connectedDevice = createConnectedDevice("device1", DeviceState.DeviceStatus.OFFLINE)
        val adblibIDeviceWrapper = AdblibIDeviceWrapper(connectedDevice)

        // Act / Assert
        assertTrue(adblibIDeviceWrapper.isOffline)
        assertFalse(adblibIDeviceWrapper.isOnline)
    }

    @Test
    fun isBootLoader() = runBlockingWithTimeout {
        // Prepare
        val connectedDevice = createConnectedDevice("device1", DeviceState.DeviceStatus.BOOTLOADER)
        val adblibIDeviceWrapper = AdblibIDeviceWrapper(connectedDevice)

        // Act / Assert
        assertTrue(adblibIDeviceWrapper.isBootLoader)
    }

    private suspend fun createConnectedDevice(
        serialNumber: String,
        deviceStatus: DeviceState.DeviceStatus
    ): ConnectedDevice {
        val fakeDevice =
            fakeAdb.connectDevice(
                serialNumber,
                "test1",
                "test2",
                "model",
                "30",
                DeviceState.HostConnectionType.USB
            )
        fakeDevice.deviceStatus = deviceStatus
        return waitForConnectedDevice(
            hostServices.session,
            serialNumber,
            deviceStatus
        )
    }

    private suspend fun waitForConnectedDevice(
        session: AdbSession,
        serialNumber: String,
        deviceStatus: DeviceState.DeviceStatus
    ): ConnectedDevice {
        return session.connectedDevicesTracker.connectedDevices
            .mapNotNull { connectedDevices ->
                connectedDevices.firstOrNull { device ->
                    device.deviceInfo.deviceState == com.android.adblib.DeviceState.parseState(
                        deviceStatus.state
                    ) && device.serialNumber == serialNumber
                }
            }.first()
    }
}
