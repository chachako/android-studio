/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.adblib.tools.tests

import com.android.adblib.DeviceSelector
import com.android.adblib.tools.INSTALL_APK_STAGING
import com.android.adblib.tools.InstallException
import com.android.adblib.tools.PMAbb
import com.android.adblib.tools.PMDriver
import com.android.adblib.tools.install
import com.android.fakeadbserver.services.PackageManager
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert
import org.junit.Ignore
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

@Ignore("b/321070782")
class TestInstall : TestInstallBase() {


    @Test
    fun testInstallSuccess() {
        val fakeDevice = addFakeDevice(fakeAdb, 30)
        val deviceSelector = DeviceSelector.fromSerialNumber(fakeDevice.deviceId)

        runBlocking {
            deviceServices.install(deviceSelector, listOf(), emptyList())
        }
    }

    @Test
    fun testInstallCommFailure() {
        val fakeDevice = addFakeDevice(fakeAdb, 29)
        val deviceSelector = DeviceSelector.fromSerialNumber(fakeDevice.deviceId)

        runBlocking {
            try {
                val client = PMAbb(deviceServices)
                val flow = client.commit(deviceSelector, "12345")
                PMDriver.parseInstallResult(flow.first())
                Assert.fail("Installation did not fail")
            } catch (e : Exception) {
                e.printStackTrace()
                // Expected
            }
        }
    }

    @Test
    fun testInstallBadParameterFailureCommit() {
        val fakeDevice = addFakeDevice(fakeAdb, 30)
        val deviceSelector = DeviceSelector.fromSerialNumber(fakeDevice.deviceId)

        runBlocking {
            try {
                val client = PMAbb(deviceServices)
                val flow = client.commit(deviceSelector, PackageManager.FAIL_ME_SESSION_TEST_ONLY)
                PMDriver.parseInstallResult(flow.first())
                Assert.fail("Installation did not fail")
            } catch (e : InstallException) {
                Assert.assertEquals("", PackageManager.SESSION_TEST_ONLY_CODE, e.errorCode)
            }
        }
    }

    // Use API = 20, when streaming and multi-apk was not supported.
    @Test
    fun testLegacyStrategyMultipleApksFail() {
        val fakeDevice = addFakeDevice(fakeAdb, 20)
        val deviceSelector = DeviceSelector.fromSerialNumber(fakeDevice.deviceId)

        val apk1 = Files.createTempFile("adblib-tools_test.apk", null)
        val apk2 = Files.createTempFile("adblib-tools_test.apk", null)
        val apks = listOf(apk1, apk2)
        try {
            runBlocking {
                deviceServices.install(deviceSelector, apks, emptyList())
                Assert.fail("Installing multiple apks on API 20 should have failed")
            }
        } catch (e :IllegalStateException) {
            // Expected
        }
    }

    // Use API = 20, when streaming and multi-apk was not supported. this should be a remote install.
    @Test
    fun testLegacyStrategy() {
        val fakeDevice = addFakeDevice(fakeAdb, 20)
        val deviceSelector = DeviceSelector.fromSerialNumber(fakeDevice.deviceId)

        val apk = Files.createTempFile("adblib-tools_test.apk", null)
        val apks = listOf<Path>(apk)
        runBlocking {
          deviceServices.install(deviceSelector, apks, emptyList())
        }
        Assert.assertEquals(1, fakeDevice.pmLogs.size)
        Assert.assertEquals("install $INSTALL_APK_STAGING", fakeDevice.pmLogs[0])
    }

    // Use API = 23, just before CMD was introduced. This should use PM binary.
    @Test
    fun testPmStrategy() {
        val fakeDevice = addFakeDevice(fakeAdb, 23)
        val deviceSelector = DeviceSelector.fromSerialNumber(fakeDevice.deviceId)

        runBlocking {
            deviceServices.install(deviceSelector, listOf(), emptyList())
        }

        Assert.assertEquals(2, fakeDevice.pmLogs.size)
        Assert.assertEquals("install-create -S 0", fakeDevice.pmLogs[0])
        Assert.assertEquals("install-commit 1234", fakeDevice.pmLogs[1])
    }

    // Use API = 24, just when CMD was introduced.
    @Test
    fun testCmdStrategy() {
        val fakeDevice = addFakeDevice(fakeAdb, 24)
        val deviceSelector = DeviceSelector.fromSerialNumber(fakeDevice.deviceId)

        runBlocking {
            deviceServices.install(deviceSelector, listOf(), emptyList())
        }

        Assert.assertEquals(2, fakeDevice.cmdLogs.size)
        Assert.assertEquals("package install-create -S 0", fakeDevice.cmdLogs[0])
        Assert.assertEquals("package install-commit 1234", fakeDevice.cmdLogs[1])
    }

    // Use API = 29, just before ABB was introduced. This should be using CMD.
    @Test
    fun testCmdBeforeAbbStrategy() {
        val fakeDevice = addFakeDevice(fakeAdb, 29)
        val deviceSelector = DeviceSelector.fromSerialNumber(fakeDevice.deviceId)

        runBlocking {
            deviceServices.install(deviceSelector, listOf(), emptyList())
        }

        Assert.assertEquals(2, fakeDevice.cmdLogs.size)
        Assert.assertEquals("package install-create -S 0", fakeDevice.cmdLogs[0])
        Assert.assertEquals("package install-commit 1234", fakeDevice.cmdLogs[1])
    }

    // Use API = 30 which should have ABB and ABB_EXEC
    @Test
    fun testAbbStrategy() {
        val fakeDevice = addFakeDevice(fakeAdb, 30)
        val deviceSelector = DeviceSelector.fromSerialNumber(fakeDevice.deviceId)

        runBlocking {
            deviceServices.install(deviceSelector, listOf(), emptyList())
        }

        Assert.assertEquals(2, fakeDevice.abbLogs.size)
        Assert.assertEquals("package\u0000install-create\u0000-S\u00000", fakeDevice.abbLogs[0])
        Assert.assertEquals("package\u0000install-commit\u00001234", fakeDevice.abbLogs[1])
    }

    // Upload base and splits. Check that all names used for install-write were distinct.
    @Test
    fun testSplits() {
        val fakeDevice = addFakeDevice(fakeAdb, 30)
        val deviceSelector = DeviceSelector.fromSerialNumber(fakeDevice.deviceId)

        val apk1 = Files.createTempFile("base.apk", null)
        val apk2 = Files.createTempFile("split1.apk", null)
        val apk3 = Files.createTempFile("split2.apk", null)
        val apks = listOf(apk1, apk2, apk3)
        runBlocking {
                deviceServices.install(deviceSelector, apks, emptyList())
        }
        Assert.assertEquals(5, fakeDevice.abbLogs.size)
        Assert.assertEquals("package\u0000install-create\u0000-S\u00000", fakeDevice.abbLogs[0])
        Assert.assertTrue("", fakeDevice.abbLogs[1].startsWith("package\u0000install-write"))
        Assert.assertTrue("", fakeDevice.abbLogs[2].startsWith("package\u0000install-write"))
        Assert.assertTrue("", fakeDevice.abbLogs[3].startsWith("package\u0000install-write"))
        Assert.assertEquals("package\u0000install-commit\u00001234", fakeDevice.abbLogs[4])
    }

    // Upload base and splits. Check that duplicate names are failing.
    @Test
    fun testDuplicateSplits() {
        val fakeDevice = addFakeDevice(fakeAdb, 30)
        val deviceSelector = DeviceSelector.fromSerialNumber(fakeDevice.deviceId)

        val apk1 = Files.createTempFile("base.apk", null)
        val apk2 = Files.createTempFile("split1.apk", null)
        val apks = listOf(apk1, apk2, apk2)

        try {
            runBlocking {
                deviceServices.install(deviceSelector, apks, emptyList())
                Assert.fail("PM did not detect duplicate names")
            }
        } catch (e: InstallException) {
        }
    }

    @Test
    fun testInstallNonExistentFile() {
        val fakeDevice = addFakeDevice(fakeAdb, 30)
        val deviceSelector = DeviceSelector.fromSerialNumber(fakeDevice.deviceId)

        val apks = listOf(Paths.get("/bad/non-existent/file.apk"))

        try {
            runBlocking {
                deviceServices.install(deviceSelector, apks, emptyList())
                Assert.fail("PM did not detect missing file")
            }
        } catch (_: InstallException) {
        }
    }

    @Test
    fun testInstallDirectory() {
        val fakeDevice = addFakeDevice(fakeAdb, 30)
        val deviceSelector = DeviceSelector.fromSerialNumber(fakeDevice.deviceId)

        val apks = listOf(Files.createTempDirectory("foo").toAbsolutePath())

        try {
            runBlocking {
                deviceServices.install(deviceSelector, apks, emptyList())
                Assert.fail("PM did not detect missing file")
            }
        } catch (_: InstallException) {
        }
    }
}
