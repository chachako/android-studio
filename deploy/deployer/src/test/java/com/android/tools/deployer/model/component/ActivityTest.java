/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.tools.deployer.model.component;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;

import com.android.ddmlib.AdbCommandRejectedException;
import com.android.ddmlib.IDevice;
import com.android.ddmlib.IShellOutputReceiver;
import com.android.ddmlib.NullOutputReceiver;
import com.android.ddmlib.ShellCommandUnresponsiveException;
import com.android.ddmlib.TimeoutException;
import com.android.testutils.TestUtils;
import com.android.tools.deployer.DeployerException;
import com.android.tools.deployer.TestLogger;
import com.android.tools.manifest.parser.ManifestInfo;
import com.android.tools.manifest.parser.XmlNode;
import com.android.tools.manifest.parser.components.ManifestActivityInfo;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.concurrent.TimeUnit;
import org.junit.Assert;
import org.junit.Test;
import org.mockito.Mockito;

public class ActivityTest {

    @Test
    public void testFlags()
            throws DeployerException, ShellCommandUnresponsiveException,
                    AdbCommandRejectedException, IOException, TimeoutException {
        IDevice device = Mockito.mock(IDevice.class);
        ManifestActivityInfo info =
                new ManifestActivityInfo(new XmlNode(), "com.example.myApp") {
                    @Override
                    public String getQualifiedName() {
                        return "com.example.myApp.MainActivity";
                    }
                };
        Activity activity = new Activity(info, "com.example.myApp", new TestLogger());
        activity.activate(" --user 123", AppComponent.Mode.DEBUG, new NullOutputReceiver(), device);

        String expectedCommand =
                "am start -n com.example.myApp/com.example.myApp.MainActivity -a android.intent.action.MAIN -c android.intent.category.LAUNCHER -D --user 123";

        Mockito.verify(device, Mockito.times(1))
                .executeShellCommand(
                        eq(expectedCommand),
                        any(IShellOutputReceiver.class),
                        eq(15L),
                        eq(TimeUnit.SECONDS));
    }

    @Test
    public void useCategoryFromManifest() throws Exception {
        IDevice device = Mockito.mock(IDevice.class);
        URL url =
                TestUtils.resolveWorkspacePath(
                                "tools/base/deploy/deployer/src/test/resource/manifestWithCategory/AndroidManifest.bxml")
                        .toUri()
                        .toURL();
        Assert.assertNotNull(url);
        try (InputStream input = url.openStream()) {
            ManifestInfo manifestInfo = ManifestInfo.parseBinaryFromStream(input);
            Activity activity =
                    new Activity(
                            manifestInfo.activities().get(0),
                            "com.example.myApp",
                            new TestLogger());
            activity.activate("", AppComponent.Mode.RUN, new NullOutputReceiver(), device);

            String expectedCommand =
                    "am start -n com.example.myApp/com.example.tv_app.MainActivity -a android.intent.action.MAIN -c android.intent.category.LEANBACK_LAUNCHER";

            Mockito.verify(device, Mockito.times(1))
                    .executeShellCommand(
                            eq(expectedCommand),
                            any(IShellOutputReceiver.class),
                            eq(15L),
                            eq(TimeUnit.SECONDS));
        }
    }
}
