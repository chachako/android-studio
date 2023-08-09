package com.android.adblib.ddmlibcompatibility.debugging

import com.android.adblib.AdbSession
import com.android.adblib.ConnectedDevice
import com.android.adblib.connectedDevicesTracker
import com.android.adblib.deviceInfo
import com.android.adblib.serialNumber
import com.android.adblib.testingutils.CoroutineTestUtils.runBlockingWithTimeout
import com.android.adblib.testingutils.CoroutineTestUtils.yieldUntil
import com.android.adblib.testingutils.FakeAdbServerProviderRule
import com.android.ddmlib.AndroidDebugBridge
import com.android.ddmlib.IDevice
import com.android.ddmlib.IDevice.PROP_DEVICE_DENSITY
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
    private val bridge = AndroidDebugBridge.createBridge() ?: error("Couldn't create a bridge")

    @Test
    fun getSerialNumber() = runBlockingWithTimeout {
        // Prepare
        val serialNumber = "ABC123DEF"
        val (connectedDevice, _) = createConnectedDevice(
            serialNumber, DeviceState.DeviceStatus.ONLINE
        )
        val adblibIDeviceWrapper = AdblibIDeviceWrapper(connectedDevice, bridge)

        // Act / Assert
        assertEquals(serialNumber, adblibIDeviceWrapper.serialNumber)
    }

    @Test
    fun getState() = runBlockingWithTimeout {
        // Prepare
        val (connectedDevice, _) = createConnectedDevice(
            "device1", DeviceState.DeviceStatus.FASTBOOTD
        )
        val adblibIDeviceWrapper = AdblibIDeviceWrapper(connectedDevice, bridge)

        // Act / Assert
        assertEquals(IDevice.DeviceState.FASTBOOTD, adblibIDeviceWrapper.state)
    }

    @Test
    fun toStringReturnsSerialNumber() = runBlockingWithTimeout {
        // Prepare
        val serialNumber = "kjdlkjsi837892"
        val (connectedDevice, _) = createConnectedDevice(
            serialNumber, DeviceState.DeviceStatus.ONLINE
        )
        val adblibIDeviceWrapper = AdblibIDeviceWrapper(connectedDevice, bridge)

        // Act / Assert
        assertEquals(serialNumber, adblibIDeviceWrapper.toString())
    }

    @Test
    fun isOnline() = runBlockingWithTimeout {
        // Prepare
        val (connectedDevice, _) = createConnectedDevice("device1", DeviceState.DeviceStatus.ONLINE)
        val adblibIDeviceWrapper = AdblibIDeviceWrapper(connectedDevice, bridge)

        // Act / Assert
        assertTrue(adblibIDeviceWrapper.isOnline)
        assertFalse(adblibIDeviceWrapper.isOffline)
    }

    @Test
    fun isOffline() = runBlockingWithTimeout {
        // Prepare
        val (connectedDevice, _) = createConnectedDevice(
            "device1", DeviceState.DeviceStatus.OFFLINE
        )
        val adblibIDeviceWrapper = AdblibIDeviceWrapper(connectedDevice, bridge)

        // Act / Assert
        assertTrue(adblibIDeviceWrapper.isOffline)
        assertFalse(adblibIDeviceWrapper.isOnline)
    }

    @Test
    fun isBootLoader() = runBlockingWithTimeout {
        // Prepare
        val (connectedDevice, _) = createConnectedDevice(
            "device1", DeviceState.DeviceStatus.BOOTLOADER
        )
        val adblibIDeviceWrapper = AdblibIDeviceWrapper(connectedDevice, bridge)

        // Act / Assert
        assertTrue(adblibIDeviceWrapper.isBootLoader)
    }

    @Test
    fun executeShellCommand() = runBlockingWithTimeout {
        // Prepare
        val (connectedDevice, _) = createConnectedDevice(
            "device1", DeviceState.DeviceStatus.BOOTLOADER
        )
        val adblibIDeviceWrapper = AdblibIDeviceWrapper(connectedDevice, bridge)
        val listReceiver = ListReceiver()

        // Act
        adblibIDeviceWrapper.executeShellCommand("echo a\\nb", listReceiver)

        // Assert
        // Echo command outputs an additional newline
        assertEquals(2, listReceiver.lines.size)
        assertEquals("a\\nb", listReceiver.lines[0])
    }

    @Test
    fun getProperty() = runBlockingWithTimeout {
        // Prepare
        val (connectedDevice, _) = createConnectedDevice("device1", DeviceState.DeviceStatus.ONLINE)
        val adblibIDeviceWrapper = AdblibIDeviceWrapper(connectedDevice, bridge)

        // Act
        val propertyValue = adblibIDeviceWrapper.getProperty("ro.serialno")

        // Assert
        assertEquals("device1", propertyValue)
    }

    @Test
    fun getSystemProperty() = runBlockingWithTimeout {
        // Prepare
        val (connectedDevice, _) = createConnectedDevice("device1", DeviceState.DeviceStatus.ONLINE)
        val adblibIDeviceWrapper = AdblibIDeviceWrapper(connectedDevice, bridge)

        // Act
        val propertyValue = adblibIDeviceWrapper.getSystemProperty("ro.serialno").get()

        // Assert
        assertEquals("device1", propertyValue)
    }

    @Test
    fun getPropertyCount() = runBlockingWithTimeout {
        // Prepare
        val (connectedDevice, _) = createConnectedDevice("device1", DeviceState.DeviceStatus.ONLINE)
        val adblibIDeviceWrapper =
            AdblibIDeviceWrapper(
                connectedDevice,
                bridge
            )
        // Query property to populate the property cache
        adblibIDeviceWrapper.getProperty("some-random-property")

        // Act
        val propertyCount = adblibIDeviceWrapper.propertyCount

        // Assert
        assertEquals(6, propertyCount)
    }

    @Test
    fun getProperties() = runBlockingWithTimeout {
        // Prepare
        val (connectedDevice, _) = createConnectedDevice("device1", DeviceState.DeviceStatus.ONLINE)
        val adblibIDeviceWrapper =
            AdblibIDeviceWrapper(
                connectedDevice,
                bridge
            )
        // Query property to populate the property cache
        adblibIDeviceWrapper.getProperty("some-random-property")

        // Act
        val properties = adblibIDeviceWrapper.properties

        // Assert
        assertEquals(6, properties.size)
        assertEquals("device1", properties["ro.serialno"])
    }

    @Test
    fun arePropertiesSet() = runBlockingWithTimeout {
        // Prepare
        val (connectedDevice, _) = createConnectedDevice("device1", DeviceState.DeviceStatus.ONLINE)
        val adblibIDeviceWrapper = AdblibIDeviceWrapper(connectedDevice, bridge)

        // Assert
        assertFalse(adblibIDeviceWrapper.arePropertiesSet())

        // Act
        // Query property to populate the property cache
        adblibIDeviceWrapper.getProperty("some-random-property")

        // Assert
        assertTrue(adblibIDeviceWrapper.arePropertiesSet())
    }

    @Test
    fun getVersion() = runBlockingWithTimeout {
        // Prepare
        val (connectedDevice, _) = createConnectedDevice("device1", DeviceState.DeviceStatus.ONLINE)
        val adblibIDeviceWrapper = AdblibIDeviceWrapper(connectedDevice, bridge)

        // Act
        val version = adblibIDeviceWrapper.version

        // Assert
        assertEquals(30, version.apiLevel)
    }

    @Test
    fun getAbis() = runBlockingWithTimeout {
        // Prepare
        val (connectedDevice, _) = createConnectedDevice("device1", DeviceState.DeviceStatus.ONLINE)
        val adblibIDeviceWrapper = AdblibIDeviceWrapper(connectedDevice, bridge)

        // Act
        val abis = adblibIDeviceWrapper.abis

        // Assert
        assertEquals(listOf("x86_64"), abis)
    }

    @Test
    fun getDensity() = runBlockingWithTimeout {
        // Prepare
        val fakeDevice =
            fakeAdb.fakeAdbServer.connectDevice(
                "device1",
                "test1",
                "test2",
                "model",
                "30",
                "x86_64",
                mapOf(Pair(PROP_DEVICE_DENSITY, "120")),
                DeviceState.HostConnectionType.USB
            ).get()
        fakeDevice.deviceStatus = DeviceState.DeviceStatus.ONLINE
        val connectedDevice = waitForConnectedDevice(
            hostServices.session,
            "device1",
            DeviceState.DeviceStatus.ONLINE
        )

        val adblibIDeviceWrapper = AdblibIDeviceWrapper(connectedDevice, bridge)

        // Act
        val density = adblibIDeviceWrapper.density

        // Assert
        assertEquals(120, density)
    }

    @Test
    fun supportsFeature() = runBlockingWithTimeout {
        // Prepare
        val (connectedDevice, _) = createConnectedDevice("device1", DeviceState.DeviceStatus.ONLINE)
        val adblibIDeviceWrapper = AdblibIDeviceWrapper(connectedDevice, bridge)

        // Act
        val supportsShellV2 = adblibIDeviceWrapper.supportsFeature(IDevice.Feature.SHELL_V2)

        // Assert
        assertTrue(supportsShellV2)
    }

    @Test
    fun supportsHardwareFeature() = runBlockingWithTimeout {
        // Prepare
        val (connectedDevice, _) = createConnectedDevice("device1", DeviceState.DeviceStatus.ONLINE)
        val adblibIDeviceWrapper = AdblibIDeviceWrapper(connectedDevice, bridge)

        // Act
        val supportsWatch = adblibIDeviceWrapper.supportsFeature(IDevice.HardwareFeature.WATCH)

        // Assert
        assertFalse(supportsWatch)
    }

    @Test
    fun getName() = runBlockingWithTimeout {
        // Prepare
        val (connectedDevice, _) = createConnectedDevice("device1", DeviceState.DeviceStatus.ONLINE)
        val adblibIDeviceWrapper = AdblibIDeviceWrapper(connectedDevice, bridge)

        // Act/Assert
        assertEquals("test1-test2-device1", adblibIDeviceWrapper.name)
    }

    fun getClients() = runBlockingWithTimeout {
        // Prepare
        val (connectedDevice, deviceState) = createConnectedDevice(
            "device1", DeviceState.DeviceStatus.ONLINE
        )
        val adblibIDeviceWrapper = AdblibIDeviceWrapper(connectedDevice, bridge)
        deviceState.startClient(10, 0, "a.b.c", false)

        // Act / Assert
        yieldUntil {adblibIDeviceWrapper.clients.size == 1}
        assertEquals(10, adblibIDeviceWrapper.clients[0].clientData.pid)
    }

    @Test
    fun getClient() = runBlockingWithTimeout {
        // Prepare
        val (connectedDevice, deviceState) = createConnectedDevice(
            "device1", DeviceState.DeviceStatus.ONLINE
        )
        val adblibIDeviceWrapper = AdblibIDeviceWrapper(connectedDevice, bridge)
        deviceState.startClient(10, 0, "a.b.c", false)

        // Act / Assert
        yieldUntil {adblibIDeviceWrapper.clients.size == 1}
        yieldUntil {adblibIDeviceWrapper.getClient("a.b.c") != null}
        assertEquals(10, adblibIDeviceWrapper.getClient("a.b.c")?.clientData?.pid)
    }

    private suspend fun createConnectedDevice(
        serialNumber: String, deviceStatus: DeviceState.DeviceStatus
    ): Pair<ConnectedDevice, DeviceState> {
        val fakeDevice = fakeAdb.connectDevice(
            serialNumber, "test1", "test2", "model", "30", DeviceState.HostConnectionType.USB
        )
        fakeDevice.deviceStatus = deviceStatus
        val connectedDevice = waitForConnectedDevice(
            hostServices.session, serialNumber, deviceStatus
        )
        return Pair(
            connectedDevice, fakeDevice
        )
    }

    private suspend fun waitForConnectedDevice(
        session: AdbSession, serialNumber: String, deviceStatus: DeviceState.DeviceStatus
    ): ConnectedDevice {
        return session.connectedDevicesTracker.connectedDevices.mapNotNull { connectedDevices ->
                connectedDevices.firstOrNull { device ->
                    device.deviceInfo.deviceState == com.android.adblib.DeviceState.parseState(
                        deviceStatus.state
                    ) && device.serialNumber == serialNumber
                }
            }.first()
    }
}
