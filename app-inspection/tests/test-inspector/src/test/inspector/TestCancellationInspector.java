/*
 * Copyright (C) 2020 The Android Open Source Project
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

package test.inspector;

import androidx.annotation.NonNull;
import androidx.inspection.Connection;
import androidx.inspection.Inspector;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;

public class TestCancellationInspector extends Inspector {
    private int commandCounter = 0;

    // Keep references to callbacks, so they don't get GC'ed and trigger the "finalize" method in
    // "InspectorContext.CommandCallbackImpl" (b/303234036)
    @SuppressWarnings("MismatchedQueryAndUpdateOfCollection")
    private final List<CommandCallback> callbacks = new ArrayList<>();

    public TestCancellationInspector(@NonNull Connection connection) {
        super(connection);
    }

    @Override
    public void onReceiveCommand(
            @NonNull byte[] bytes, @NonNull final CommandCallback commandCallback) {
        callbacks.add(commandCallback);
        final int currentCommand = ++commandCounter;
        System.out.println("command #" + currentCommand + " arrived");
        commandCallback.addCancellationListener(
                new DecoratedDirectExecutor("first executor"),
                new Runnable() {
                    @Override
                    public void run() {
                        System.out.println("cancellation #1 for command #" + currentCommand);
                    }
                });
        // tests that second listener is executed.
        commandCallback.addCancellationListener(
                new DecoratedDirectExecutor("second executor"),
                new Runnable() {
                    @Override
                    public void run() {
                        System.out.println("cancellation #2 for command #" + currentCommand);
                        // tests that listener that is added after command was cancelled is executed
                        commandCallback.addCancellationListener(
                                new DecoratedDirectExecutor("post cancellation executor"),
                                new Runnable() {
                                    @Override
                                    public void run() {
                                        System.out.println(
                                                "cancellation #3 for command #" + currentCommand);
                                    }
                                });
                    }
                });
    }

    private static class DecoratedDirectExecutor implements Executor {
        private final String mName;

        private DecoratedDirectExecutor(String name) {
            this.mName = name;
        }

        @Override
        public void execute(Runnable command) {
            System.out.print("Executing on " + mName + ": ");
            command.run();
        }
    }
}
