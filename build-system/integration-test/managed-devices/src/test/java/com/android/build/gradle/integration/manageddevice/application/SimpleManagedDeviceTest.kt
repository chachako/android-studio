package com.android.build.gradle.integration.manageddevice.application

import com.android.build.gradle.integration.common.fixture.GradleTaskExecutor
import com.android.build.gradle.integration.common.fixture.GradleTestProjectBuilder
import com.android.build.gradle.integration.manageddevice.utils.CustomAndroidSdkRule
import com.android.build.gradle.integration.manageddevice.utils.addManagedDevice
import com.android.testutils.truth.PathSubject.assertThat
import com.android.utils.FileUtils
import java.io.File
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class SimpleManagedDeviceTest {

    @get:Rule
    val customAndroidSdkRule = CustomAndroidSdkRule()

    @get:Rule
    val project = GradleTestProjectBuilder().fromTestProject("utp").create()

    private val executor: GradleTaskExecutor
        get() = customAndroidSdkRule.run { project.executorWithCustomAndroidSdk() }

    @Before
    fun setUp() {
        project.getSubproject("app").addManagedDevice("device1")
    }

    @Test
    fun runBasicManagedDevice() {
        executor.run(":app:device1DebugAndroidTest")

        val reportDir = FileUtils.join(
            project.getSubproject("app").buildDir,
            "reports",
            "androidTests",
            "managedDevice",
            "debug",
            "device1")
        assertThat(File(reportDir, "index.html")).exists()
        assertThat(File(reportDir, "com.example.android.kotlin.html")).exists()
        assertThat(
            File(reportDir, "com.example.android.kotlin.ExampleInstrumentedTest.html")).exists()

        val mergedTestReportDir = FileUtils.join(
            project.getSubproject("app").buildDir,
            "reports",
            "androidTests",
            "managedDevice",
            "debug",
            "allDevices")
        assertThat(File(mergedTestReportDir, "index.html")).exists()
        assertThat(File(mergedTestReportDir, "com.example.android.kotlin.html")).exists()
        assertThat(
            File(mergedTestReportDir, "com.example.android.kotlin.ExampleInstrumentedTest.html")).exists()
    }
}
