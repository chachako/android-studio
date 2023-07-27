package com.android.adblib.ddmlibcompatibility.debugging

import com.android.adblib.AdbSession
import com.android.adblib.ConnectedDevice
import com.android.adblib.connectedDevicesTracker
import com.android.adblib.isOnline
import com.android.adblib.serialNumber
import com.android.adblib.testingutils.CoroutineTestUtils.runBlockingWithTimeout
import com.android.adblib.testingutils.FakeAdbServerProviderRule
import com.android.fakeadbserver.DeviceState
import com.android.fakeadbserver.devicecommandhandlers.SyncCommandHandler
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.mapNotNull
import org.junit.Assert.assertEquals
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
        val fakeDevice =
            fakeAdb.connectDevice(
                serialNumber,
                "test1",
                "test2",
                "model",
                "30",
                DeviceState.HostConnectionType.USB
            )
        fakeDevice.deviceStatus = DeviceState.DeviceStatus.ONLINE
        val connectedDevice =
            waitForOnlineConnectedDevice(
                hostServices.session,
                fakeDevice.deviceId
            )
        val adblibIDeviceWrapper = AdblibIDeviceWrapper(connectedDevice)

        // Act / Assert
        assertEquals(serialNumber, adblibIDeviceWrapper.serialNumber)
    }

    private suspend fun waitForOnlineConnectedDevice(
        session: AdbSession,
        serialNumber: String
    ): ConnectedDevice {
        return session.connectedDevicesTracker.connectedDevices
            .mapNotNull { connectedDevices ->
                connectedDevices.firstOrNull { device ->
                    device.isOnline && device.serialNumber == serialNumber
                }
            }.first()
    }
}
