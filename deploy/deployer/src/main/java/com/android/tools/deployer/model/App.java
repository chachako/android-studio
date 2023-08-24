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

package com.android.tools.deployer.model;

import com.android.annotations.NonNull;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class App {
    private final List<Apk> apks;

    private final String appId;

    private App(@NonNull String appId, @NonNull List<Apk> apks) {
        this.appId = appId;
        this.apks = apks;
    }

    public static App fromApks(@NonNull String appId, @NonNull List<Apk> apks) {
        return new App(appId, apks);
    }

    public static App fromApk(@NonNull String appId, @NonNull Apk apk) {
        return new App(appId, Arrays.asList(apk));
    }

    public static App fomApks(@NonNull String appId, @NonNull List<Apk> apks) {
        return new App(appId, apks);
    }

    public static App fromString(@NonNull String appId, @NonNull String apkPath) {
        return new App(appId, Arrays.asList(ApkParser.parse(apkPath)));
    }

    public static App fromPaths(@NonNull String appId, @NonNull List<Path> paths) {
        return new App(appId, convert(paths));
    }

    public static App fromPath(@NonNull String appId, @NonNull Path path) {
        return fromPaths(appId, Arrays.asList(path));
    }

    @NonNull
    private static List<Apk> convert(@NonNull List<Path> paths) {
        List<Apk> apks = new ArrayList<>();
        for (Path path : paths) {
            apks.add(ApkParser.parse(path.toAbsolutePath().toString()));
        }
        return apks;
    }

    public String getAppId() {
        return appId;
    }

    public List<Apk> getApks() {
        return apks;
    }
}
