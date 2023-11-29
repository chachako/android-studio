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

class TestApplicationManagedDeviceTest {

    @get:Rule
    val customAndroidSdkRule = CustomAndroidSdkRule()

    @get:Rule
    val project = GradleTestProjectBuilder().fromTestProject("utp").create()

    private val executor: GradleTaskExecutor
        get() = customAndroidSdkRule.run { project.executorWithCustomAndroidSdk() }

    @Before
    fun setUp() {
        project.getSubproject("testOnlyModule").addManagedDevice("device1")
    }

    @Test
    fun runBasicManagedDevice() {
        executor.run(":testOnlyModule:allDevicesCheck")

        val reportDir = FileUtils.join(
            project.getSubproject("testOnlyModule").buildDir,
            "reports",
            "androidTests",
            "managedDevice",
            "device1")
        assertThat(File(reportDir, "index.html")).exists()
        assertThat(File(reportDir, "com.example.android.kotlin.html")).exists()
        assertThat(
            File(reportDir, "com.example.android.kotlin.ExampleInstrumentedTest.html")).exists()
        assertThat(File(reportDir, "com.example.android.kotlin.ExampleInstrumentedTest.html"))
            .containsAllOf(
                """<div class="infoBox success" id="successRate">""",
                """<div class="percent">100%</div>"""
            )
    }
}
