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
package com.android.tools.deployer.devices.shell;

import com.android.tools.deployer.devices.FakeDevice;
import com.android.tools.deployer.devices.shell.interpreter.ShellContext;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.util.Arrays;

public class Su extends ShellCommand {
    @Override
    public int execute(ShellContext context, String[] args, InputStream stdin, PrintStream stdout)
            throws IOException {
        FakeDevice device = context.getDevice();
        FakeDevice.User user;
        if (args[0].equals("shell")) {
            user = device.getShellUser();
        } else if (args[0].equals("root")) {
            user = device.getRootUser();
        } else {
            stdout.println("invalid uid/gid '" + args[0] + "'");
            return 1;
        }
        String cmd = String.join(" ", Arrays.stream(args).skip(1).toArray(String[]::new));
        int output = device.getShell().execute(cmd, user, stdout, stdin, device);
        // Hack: remove this last command from the history so that we don't get duplicates.
        device.getShell().getHistory().remove(device.getShell().getHistory().size() - 1);
        return output;
    }

    @Override
    public String getExecutable() {
        return "su";
    }
}
