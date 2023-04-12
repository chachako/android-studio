/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.fakeadbserver.shellcommandhandlers;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.fakeadbserver.DeviceState;
import com.android.fakeadbserver.FakeAdbServer;
import com.android.fakeadbserver.ShellProtocolType;
import com.android.fakeadbserver.services.ShellCommandOutput;

/** shell:write-no-stop continuously write to the output stream without stopping. */
public class WriteNoStopCommandHandler extends SimpleShellHandler {

    public WriteNoStopCommandHandler(ShellProtocolType shellProtocolType) {
        super(shellProtocolType, "write-no-stop");
    }

    @Override
    public void execute(
            @NonNull FakeAdbServer fakeAdbServer,
            @NonNull StatusWriter statusWriter,
            @NonNull ShellCommandOutput shellCommandOutput,
            @NonNull DeviceState device,
            @NonNull String shellCommand,
            @Nullable String shellCommandArgs) {
        try {
            statusWriter.writeOk(); // Send ok first.
            while (true) {
                shellCommandOutput.writeStdout("write-no-stop test in progress\n");
                Thread.sleep(200);
            }
        } catch (InterruptedException ignored) {
        }
    }
}
