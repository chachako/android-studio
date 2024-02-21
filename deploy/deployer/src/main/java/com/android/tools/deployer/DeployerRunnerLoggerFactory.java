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
package com.android.tools.deployer;

import com.android.adblib.AdbLogger;
import com.android.adblib.AdbLoggerFactory;
import com.android.utils.StdLogger;
import org.jetbrains.annotations.NotNull;

public class DeployerRunnerLoggerFactory implements AdbLoggerFactory {

    private final AdbLogger rootLogger;
    private final StdLogger.Level level;

    DeployerRunnerLoggerFactory(StdLogger.Level level) {
        this.rootLogger = new DeployerRunnerAdbLogger(level, "");
        this.level = level;
    }

    @NotNull
    @Override
    public AdbLogger getLogger() {
        return rootLogger;
    }

    @NotNull
    @Override
    public AdbLogger createLogger(@NotNull Class<?> cls) {
        return new DeployerRunnerAdbLogger(level, cls.getName());
    }

    @NotNull
    @Override
    public AdbLogger createLogger(@NotNull String category) {
        return new DeployerRunnerAdbLogger(level, category);
    }
}
