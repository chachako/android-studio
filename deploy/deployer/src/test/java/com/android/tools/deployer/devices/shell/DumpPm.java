/*
 * Copyright (C) 2024 The Android Open Source Project
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

/** A Pm command that only understand how to do "pm art dump" for verifying baseline profiles. */
public class DumpPm extends ShellCommand {
    @Override
    public int execute(ShellContext context, String[] args, InputStream stdin, PrintStream stdout)
            throws IOException {
        FakeDevice device = context.getDevice();
        Arguments arguments = new Arguments(args);
        String art = arguments.nextArgument();
        String dump = arguments.nextArgument();
        String packageId = arguments.nextArgument();
        if (art.equals("art") && dump.equals("dump")) {
            if (device.getApplication(packageId).hasBaselineProfile) {
                stdout.print("status=speed-profile");
            }
        }
        return 0;
    }

    @Override
    public String getExecutable() {
        return "pm";
    }
}
