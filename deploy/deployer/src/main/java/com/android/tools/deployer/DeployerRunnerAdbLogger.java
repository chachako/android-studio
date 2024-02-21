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
import com.android.utils.StdLogger;
import java.io.PrintWriter;
import java.io.StringWriter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class DeployerRunnerAdbLogger extends AdbLogger {

    private final StdLogger logger;
    private final StdLogger.Level level;
    private final String category;

    public DeployerRunnerAdbLogger(StdLogger.Level level, String category) {
        this.logger = new StdLogger(level);
        this.level = level;
        this.category = category;
    }

    @NotNull
    @Override
    public Level getMinLevel() {
        switch (level) {
            case INFO:
                return Level.INFO;
            case WARNING:
                return Level.WARN;
            case ERROR:
                return Level.ERROR;
            default:
                return Level.VERBOSE;
        }
    }

    @Override
    public void log(@NotNull AdbLogger.Level level, @NotNull String message) {
        message = category + ":" + message;
        switch (level) {
            case INFO:
                logger.info(message);
                break;
            case WARN:
                logger.warning(message);
                break;
            case DEBUG:
            case VERBOSE:
                logger.verbose(message);
                break;
            case ERROR:
                logger.error(null, message);
        }
    }

    @Override
    public void log(
            @NotNull AdbLogger.Level level,
            @Nullable Throwable exception,
            @NotNull String message) {
        message = category + ":" + message;
        switch (level) {
            case INFO:
                logger.info(message);
                if (exception != null) {
                    logger.info(exceptionToString(exception));
                }
                break;
            case WARN:
                logger.warning(message);
                if (exception != null) {
                    logger.warning(exceptionToString(exception));
                }
                break;
            case DEBUG:
            case VERBOSE:
                logger.verbose(message);
                if (exception != null) {
                    logger.verbose(exceptionToString(exception));
                }
                break;
            case ERROR:
                logger.error(exception, message);
                break;
        }
    }

    private String exceptionToString(Throwable exception) {
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        exception.printStackTrace(pw);
        return sw.toString();
    }
}
