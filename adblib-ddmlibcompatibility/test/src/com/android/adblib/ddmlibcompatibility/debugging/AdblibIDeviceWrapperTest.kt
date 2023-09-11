package com.android.adblib.ddmlibcompatibility.debugging

import com.android.adblib.AdbSession
import com.android.adblib.ConnectedDevice
import com.android.adblib.DeviceSelector
import com.android.adblib.RemoteFileMode
import com.android.adblib.SocketSpec
import com.android.adblib.connectedDevicesTracker
import com.android.adblib.deviceInfo
import com.android.adblib.serialNumber
import com.android.adblib.testingutils.CoroutineTestUtils.runBlockingWithTimeout
import com.android.adblib.testingutils.CoroutineTestUtils.yieldUntil
import com.android.adblib.testingutils.FakeAdbServerProviderRule
import com.android.ddmlib.AdbHelper
import com.android.ddmlib.AndroidDebugBridge
import com.android.ddmlib.IDevice
import com.android.ddmlib.IDevice.PROP_DEVICE_DENSITY
import com.android.ddmlib.SyncException
import com.android.fakeadbserver.DeviceFileState
import com.android.fakeadbserver.DeviceState
import com.android.fakeadbserver.devicecommandhandlers.SyncCommandHandler
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.mapNotNull
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExpectedException
import org.junit.rules.TemporaryFolder
import java.nio.file.Files
import java.nio.file.attribute.FileTime
import java.nio.file.attribute.PosixFilePermission.OWNER_READ
import java.util.concurrent.TimeUnit
import kotlin.io.path.readBytes

class AdblibIDeviceWrapperTest {

    @JvmField
    @Rule
    val fakeAdbRule = FakeAdbServerProviderRule {
        installDefaultCommandHandlers()
        installDeviceHandler(SyncCommandHandler())
    }

    @JvmField
    @Rule
    val exceptionRule: ExpectedException = ExpectedException.none()

    @JvmField
    @Rule
    val temporaryFolder = TemporaryFolder()

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
    fun executeRemoteCommandCanHandleAbbExec() = runBlockingWithTimeout {
        // Prepare
        val (connectedDevice, _) = createConnectedDevice(
            "device1", DeviceState.DeviceStatus.BOOTLOADER
        )
        val adblibIDeviceWrapper = AdblibIDeviceWrapper(connectedDevice, bridge)
        val listReceiver = ListReceiver()
        val appId = "com.foo.bar.app"

        // Act
        adblibIDeviceWrapper.executeRemoteCommand(
            AdbHelper.AdbService.ABB_EXEC,
            "package path $appId",
            listReceiver,
            0,
            TimeUnit.MILLISECONDS,
            null
        )

        // Assert
        // Echo command outputs an additional newline
        assertEquals(1, listReceiver.lines.size)
        assertEquals("/data/app/$appId/base.apk", listReceiver.lines[0])
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

    @Test
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
    fun getClients_whenDeviceStatusTransitionsToOnlineAfterAdblibIDeviceWrapperIsCreated() =
        runBlockingWithTimeout {
            // Prepare
            val (connectedDevice, deviceState) = createConnectedDevice(
                "device1", DeviceState.DeviceStatus.OFFLINE
            )
            val adblibIDeviceWrapper = AdblibIDeviceWrapper(connectedDevice, bridge)

            // Act
            deviceState.startClient(10, 0, "a.b.c", false)
            assertEquals(0, adblibIDeviceWrapper.clients.size)
            deviceState.deviceStatus = DeviceState.DeviceStatus.ONLINE

            // Assert
            yieldUntil { adblibIDeviceWrapper.clients.size == 1 }
            assertEquals(10, adblibIDeviceWrapper.clients[0].clientData.pid)
        }

    @Test
    fun getClient() = runBlockingWithTimeout {
        // Prepare
        val (connectedDevice, deviceState) = createConnectedDevice(
            "device1", DeviceState.DeviceStatus.ONLINE
        )
        val adblibIDeviceWrapper = AdblibIDeviceWrapper(connectedDevice, bridge)
        deviceState.startClient(10, 0, "processName1", "packageName1", false)

        // Act / Assert
        yieldUntil {adblibIDeviceWrapper.clients.size == 1}
        yieldUntil {adblibIDeviceWrapper.getClient("packageName1") != null}
        assertEquals(10, adblibIDeviceWrapper.getClient("packageName1")?.clientData?.pid)
    }

    @Test
    fun getProfileableClients() = runBlockingWithTimeout {
        // Prepare
        val (connectedDevice, deviceState) = createConnectedDevice(
            "device1", sdk = "31" // required for "track-app"
        )
        val adblibIDeviceWrapper = AdblibIDeviceWrapper(connectedDevice, bridge)
        deviceState.startProfileableProcess(25, "x86", "a.b.c")

        // Act / Assert
        yieldUntil {adblibIDeviceWrapper.profileableClients.size == 1}
        assertEquals(25, adblibIDeviceWrapper.profileableClients[0].profileableClientData.pid)
    }

    @Test
    fun getClientName() = runBlockingWithTimeout {
        // Prepare
        val (connectedDevice, deviceState) = createConnectedDevice(
            "device1", DeviceState.DeviceStatus.ONLINE
        )
        val adblibIDeviceWrapper = AdblibIDeviceWrapper(connectedDevice, bridge)
        deviceState.startClient(10, 0, "processName1", "packageName1", false)

        // Act / Assert
        yieldUntil {adblibIDeviceWrapper.clients.size == 1}
        yieldUntil {adblibIDeviceWrapper.getClient("packageName1") != null}
        assertEquals("packageName1", adblibIDeviceWrapper.getClientName(10))
    }

    @Test
    fun pushFile() = runBlockingWithTimeout {
        // Prepare
        val (connectedDevice, deviceState) = createConnectedDevice(
            "device1", DeviceState.DeviceStatus.ONLINE
        )
        val adblibIDeviceWrapper = AdblibIDeviceWrapper(connectedDevice, bridge)
        val lastModifiedTimeSec = 878392983L
        val localFile = temporaryFolder.newFile("sample.txt").toPath()
        val fileBytes = "some content".toByteArray()
        Files.write(localFile, fileBytes)
        Files.setLastModifiedTime(localFile, FileTime.from(lastModifiedTimeSec, TimeUnit.SECONDS))
        val remoteFilePath = "/sdcard/foo/bar.bin"

        // Act
        adblibIDeviceWrapper.pushFile(localFile.toAbsolutePath().toString(), remoteFilePath)

        // Assert
        assertNotNull(deviceState.getFile(remoteFilePath))
        val remoteFile = deviceState.getFile(remoteFilePath)!!
        assertEquals(lastModifiedTimeSec, remoteFile.modifiedDate.toLong())
        assertEquals(RemoteFileMode.fromPath(localFile), RemoteFileMode.fromModeBits(remoteFile.permission))
        assertEquals(fileBytes.toString(Charsets.UTF_8), remoteFile.bytes.toString(Charsets.UTF_8))
    }

    @Test
    fun installLegacy() = runBlockingWithTimeout {
        // Prepare
        val (connectedDevice, deviceState) = createConnectedDevice(
            "device1", DeviceState.DeviceStatus.ONLINE
        )
        val apk = temporaryFolder.newFile("adblib-tools_test.apk").toPath()
        val adblibIDeviceWrapper = AdblibIDeviceWrapper(connectedDevice, bridge)

        // Act
        adblibIDeviceWrapper.installPackage(apk.toAbsolutePath().toString(), false)

        // Assert
        assertEquals(1, deviceState.pmLogs.size)
        assertEquals("install  \"/data/local/tmp/${apk.fileName}\"", deviceState.pmLogs[0])
    }

    @Test
    fun install() = runBlockingWithTimeout {
        // Prepare
        val (connectedDevice, deviceState) = createConnectedDevice(
            "device1", DeviceState.DeviceStatus.ONLINE
        )
        val apk = temporaryFolder.newFile("adblib-tools_test.apk")
        val adblibIDeviceWrapper = AdblibIDeviceWrapper(connectedDevice, bridge)

        // Act
        adblibIDeviceWrapper.installPackages(
            mutableListOf(apk),
            false,
            mutableListOf(),
            0,
            TimeUnit.SECONDS
        )

        // Assert
        assertEquals(3, deviceState.abbLogs.size)
        assertTrue(deviceState.abbLogs[0].matches(Regex("^package\u0000install-create.*")))
        assertTrue(deviceState.abbLogs[1].matches(Regex("^package\u0000install-write\u0000.+adblib-tools_test.apk.*")))
        assertTrue(deviceState.abbLogs[2].matches(Regex("^package\u0000install-commit\u0000.*")))
    }

    @Test
    fun pullFile() = runBlockingWithTimeout {
        // Prepare
        val (connectedDevice, deviceState) = createConnectedDevice(
            "device1", DeviceState.DeviceStatus.ONLINE
        )
        val adblibIDeviceWrapper = AdblibIDeviceWrapper(connectedDevice, bridge)
        val localFile = Files.createTempFile("sample", ".txt")
        val remoteFilePath = "/sdcard/foo/bar.bin"
        val fileMode = RemoteFileMode.fromPosixPermissions(OWNER_READ)
        val bytes = "abcd12345".toByteArray()
        deviceState.createFile(DeviceFileState("/sdcard/foo/bar.bin", fileMode.modeBits, 0, bytes))

        // Act
        adblibIDeviceWrapper.pullFile(remoteFilePath, localFile.toAbsolutePath().toString())

        // Assert
        assertArrayEquals(bytes, localFile.readBytes())
    }

    @Test
    fun pushFile_throwsSyncExceptionOnError() = runBlockingWithTimeout {
        // Prepare
        val (connectedDevice, _) = createConnectedDevice(
            "device1", DeviceState.DeviceStatus.ONLINE
        )
        val adblibIDeviceWrapper = AdblibIDeviceWrapper(connectedDevice, bridge)
        val localFile = Files.createTempFile("sample", ".txt")
        val fileBytes = "some content".toByteArray()
        Files.write(localFile, fileBytes)
        exceptionRule.expect(SyncException::class.java)
        exceptionRule.expectMessage("Adb Transfer Protocol Error")

        // Act
        // Specifying an empty remote file name should fail the transfer
        adblibIDeviceWrapper.pushFile(localFile.toAbsolutePath().toString(), remote = "")

        // Assert
        fail("Should not reach")
    }

    @Test
    fun testForward() = runBlockingWithTimeout {
        // Prepare
        val (connectedDevice, deviceState) = createConnectedDevice(
            "device1", DeviceState.DeviceStatus.ONLINE
        )
        assertEquals(0, deviceState.allPortForwarders.size)
        val adblibIDeviceWrapper = AdblibIDeviceWrapper(connectedDevice, bridge)

        // Act
        adblibIDeviceWrapper.createForward(0, 4000)

        // Assert
        assertEquals(1, deviceState.allPortForwarders.size)
        val portForwarder = deviceState.allPortForwarders.values.asList()[0]
        assertEquals(4000, portForwarder?.destination?.port)
    }

    @Test
    fun testKillForward() = runBlockingWithTimeout {
        // Prepare
        val (connectedDevice, deviceState) = createConnectedDevice(
            "device1", DeviceState.DeviceStatus.ONLINE
        )
        val port =
            hostServices.forward(
                DeviceSelector.any(),
                SocketSpec.Tcp(),
                SocketSpec.Tcp(4000)
            ) ?: throw Exception("`forward` command should have returned a port")
        assertEquals(1, deviceState.allPortForwarders.size)
        val adblibIDeviceWrapper = AdblibIDeviceWrapper(connectedDevice, bridge)

        // Act
        adblibIDeviceWrapper.removeForward(Integer.valueOf(port))

        // Assert
        assertEquals(0, deviceState.allPortForwarders.size)
    }

    private suspend fun createConnectedDevice(
        serialNumber: String,
        deviceStatus: DeviceState.DeviceStatus = DeviceState.DeviceStatus.ONLINE,
        sdk: String = "30"
    ): Pair<ConnectedDevice, DeviceState> {
        val fakeDevice = fakeAdb.connectDevice(
            serialNumber, "test1", "test2", "model", sdk, DeviceState.HostConnectionType.USB
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
