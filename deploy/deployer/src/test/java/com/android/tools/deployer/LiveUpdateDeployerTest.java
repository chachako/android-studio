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
package com.android.tools.deployer;

import com.android.ddmlib.AdbInitOptions;
import com.android.ddmlib.AndroidDebugBridge;
import com.android.ddmlib.IDevice;
import com.android.tools.deployer.devices.FakeDevice;
import com.android.tools.deployer.rules.ApiLevel;
import com.android.tools.deployer.rules.FakeDeviceConnection;
import com.android.tools.deployer.tasks.LiveUpdateDeployer;
import com.android.utils.ILogger;
import com.google.common.collect.Lists;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(ApiLevel.class)
public class LiveUpdateDeployerTest {

    @Rule @ApiLevel.Init public FakeDeviceConnection connection;
    private FakeDevice device;
    private ILogger logger;

    @Before
    public void setUp() {
        device = connection.getDevice();
        logger = new TestLogger();
    }

    @Test
    @ApiLevel.InRange(min = 30)
    public void pushUpdateWithSupportClassChangeOnly() throws IOException, InterruptedException {
        Map<String, byte[]> classes = new HashMap<>();
        Map<String, byte[]> supportClasses = new HashMap<>();
        supportClasses.put("supporting.classes.Example", new byte[0]);

        LiveUpdateDeployer.UpdateLiveEditsParam param =
                new LiveUpdateDeployer.UpdateLiveEditsParam(
                        classes, supportClasses, Lists.newArrayList(), true, true);
        AndroidDebugBridge.init(AdbInitOptions.DEFAULT);
        AndroidDebugBridge bridge = AndroidDebugBridge.createBridge();
        while (!bridge.hasInitialDeviceList()) {
            Thread.sleep(100);
        }
        IDevice iDevice = bridge.getDevices()[0];

        AdbClient adb = new AdbClient(iDevice, logger);

        Path installersPath = DeployerTestUtils.prepareInstaller().toPath();
        ArrayList<DeployMetric> metrics = new ArrayList<>();
        Installer installer = new AdbInstaller(installersPath.toString(), adb, metrics, logger);
        LiveUpdateDeployer deployer = new LiveUpdateDeployer(logger);
        LiveUpdateDeployer.UpdateLiveEditResult result =
                deployer.updateLiveEdit(installer, adb, "ignored", param);

        Assert.assertTrue(result.errors.get(0).getMessage().contains("No target pids"));
    }
}
