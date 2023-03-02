/*
 * Copyright (C) 2023 The Android Open Source Project
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

package com.android.build.gradle.integration.manageddevice.application

import com.android.build.gradle.integration.common.fixture.GradleTaskExecutor
import com.android.build.gradle.integration.common.fixture.GradleTestProjectBuilder
import com.android.testutils.truth.PathSubject.assertThat
import com.android.utils.FileUtils
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.io.File

class ManagedDeviceExtensionTest {
    @get:Rule
    val project = GradleTestProjectBuilder()
            .fromTestProject("utp")
            .enableProfileOutput()
            .create()

    private val executor: GradleTaskExecutor
        get() = project.executor()

    @Before
    fun setUp() {
        project.gradlePropertiesFile.appendText("""
            android.experimental.testOptions.managedDevices.customDevice=true
        """.trimIndent())
        project.getSubproject("app").buildFile.appendText("""
import com.android.build.api.instrumentation.manageddevice.DeviceSetupInput
import com.android.build.api.instrumentation.manageddevice.DeviceSetupConfigureAction
import com.android.build.api.instrumentation.manageddevice.DeviceSetupTaskAction
import com.android.build.api.instrumentation.manageddevice.DeviceTestRunConfigureAction
import com.android.build.api.instrumentation.manageddevice.DeviceTestRunInput
import com.android.build.api.instrumentation.manageddevice.DeviceTestRunParameters
import com.android.build.api.instrumentation.manageddevice.DeviceTestRunTaskAction

interface MyCustomDevice extends com.android.build.api.dsl.Device {}

class MyCustomDeviceImpl implements MyCustomDevice {

    private String name

    @Inject
    public MyCustomDeviceImpl(String name) {
        this.name = name
    }

    @Override
    public String getName() {
        return name
    }
}

abstract class SetupInput implements DeviceSetupInput {
    @Input
    abstract Property<String> getDeviceName()
}

abstract class SetupConfigAction implements DeviceSetupConfigureAction<MyCustomDevice, SetupInput> {
    @Inject
    abstract public ObjectFactory getObjectFactory()

    @Override
    public SetupInput configureTaskInput(MyCustomDevice device) {
        def input = getObjectFactory().newInstance(SetupInput.class)
        input.getDeviceName().set(device.getName())
        input.getDeviceName().disallowChanges()
        return input
    }
}

class SetupTaskAction implements DeviceSetupTaskAction<SetupInput> {
    @Override
    void setup(SetupInput setupInput, Directory outputDir) {
        outputDir.file('deviceName.txt').getAsFile().withWriter('utf-8') { writer ->
            writer.writeLine(setupInput.getDeviceName().get())
        }
    }
}

abstract class TestRunInput implements DeviceTestRunInput {}

abstract class TestRunConfigAction implements DeviceTestRunConfigureAction<MyCustomDevice, TestRunInput> {
    @Inject
    abstract public ObjectFactory getObjectFactory()

    @Override
    public TestRunInput configureTaskInput(MyCustomDevice device) {
        return getObjectFactory().newInstance(TestRunInput.class)
    }
}

class TestRunTaskAction implements DeviceTestRunTaskAction<TestRunInput> {
    @Override
    public boolean runTests(DeviceTestRunParameters<TestRunInput> params) {
        params.getTestRunData().getOutputDirectory().file('TEST-' + params.getTestRunData().getDeviceName() + '.xml').getAsFile().withWriter('utf-8') { writer ->
            writer.writeLine ""${'"'}\
            <?xml version='1.0' encoding='UTF-8' ?>
            <testsuite name="com.example.android.kotlin.ExampleInstrumentedTest" tests="2" failures="0" errors="0" skipped="0" time="0.969" timestamp="2022-12-12T22:38:18" hostname="localhost">
            <properties>
                <property name="device" value="localDevice" />
                <property name="flavor" value="" />
                <property name="project" value=":app" />
            </properties>
            <testcase name="useAppContext2" classname="com.example.android.kotlin.ExampleInstrumentedTest" time="0.004" />
            <testcase name="useAppContext" classname="com.example.android.kotlin.ExampleInstrumentedTest" time="0.0" />
            </testsuite>
            ""${'"'}.stripIndent()
        }
        return true
    }
}

androidComponents.managedDeviceRegistry.registerDeviceType(MyCustomDevice.class) {
    it.dslImplementationClass = MyCustomDeviceImpl.class
    it.setSetupActions(
        SetupConfigAction.class,
        SetupTaskAction.class
    )
    it.setTestRunActions(
        TestRunConfigAction.class,
        TestRunTaskAction.class
    )
}

android.testOptions.managedDevices.devices {
    myCustomDevice(MyCustomDevice) {}
}
        """)
    }

    @Test
    fun runCustomManagedDevice() {
        executor.run(":app:myCustomDeviceCheck")

        val setupDir = FileUtils.join(
            project.getSubproject("app").buildDir,
            "managedDeviceSetupResults",
            "myCustomDevice")
        assertThat(File(setupDir, "deviceName.txt")).contains("myCustomDevice")

        val reportDir = FileUtils.join(
            project.getSubproject("app").buildDir,
            "reports",
            "androidTests",
            "managedDevice",
            "debug",
            "myCustomDevice")
        assertThat(File(reportDir, "index.html")).exists()
        assertThat(File(reportDir, "com.example.android.kotlin.html")).exists()
        assertThat(File(reportDir, "com.example.android.kotlin.ExampleInstrumentedTest.html")).exists()

        val mergedTestReportDir = FileUtils.join(
            project.getSubproject("app").buildDir,
            "reports",
            "androidTests",
            "managedDevice",
            "debug",
            "allDevices")
        assertThat(File(mergedTestReportDir, "index.html")).exists()
        assertThat(File(mergedTestReportDir, "com.example.android.kotlin.html")).exists()
        assertThat(File(mergedTestReportDir, "com.example.android.kotlin.ExampleInstrumentedTest.html")).exists()
    }
}
