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

import static java.util.Collections.emptyList;

import com.android.annotations.NonNull;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class App {
    private final List<Apk> apks;

    private final String appId;

    private final List<BaselineProfile> baselineProfiles;

    private App(
            @NonNull String appId,
            @NonNull List<Apk> apks,
            @NonNull List<BaselineProfile> baselineProfiles) {
        this.appId = appId;
        this.apks = apks;
        this.baselineProfiles = baselineProfiles;
    }

    public static App fromApks(@NonNull String appId, @NonNull List<Apk> apks) {
        return new App(appId, apks, emptyList());
    }

    public static App fromApk(@NonNull String appId, @NonNull Apk apk) {
        return new App(appId, Arrays.asList(apk), emptyList());
    }

    public static App fomApks(@NonNull String appId, @NonNull List<Apk> apks) {
        return new App(appId, apks, emptyList());
    }

    public static App fromString(@NonNull String appId, @NonNull String apkPath) {
        return new App(appId, Arrays.asList(ApkParser.parse(apkPath)), emptyList());
    }

    public static App fromPaths(
            @NonNull String appId,
            @NonNull List<Path> paths,
            @NonNull List<BaselineProfile> baselineProfiles) {
        return new App(appId, convert(paths), baselineProfiles);
    }

    public static App fromPaths(@NonNull String appId, @NonNull List<Path> paths) {
        List<Path> apks = new ArrayList<>();
        List<BaselineProfile> baselineProfiles = new ArrayList<>();
        for (Path path : paths) {
            if (path.toString().endsWith(".apk")) {
                apks.add(path);
                continue;
            }
            if (path.toString().endsWith(".dm")) {
                if (baselineProfiles.isEmpty()) {
                    baselineProfiles.add(
                            new BaselineProfile(
                                    Integer.MIN_VALUE, Integer.MAX_VALUE, new ArrayList<>()));
                }
                baselineProfiles.get(0).getPaths().add(path);
                continue;
            }
            throw new IllegalStateException("Unknown path type (neither apk nor dm):" + path);
        }
        return fromPaths(appId, apks, baselineProfiles);
    }

    public static App fromPath(@NonNull String appId, @NonNull Path path) {
        return fromPaths(appId, Arrays.asList(path), emptyList());
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

    public List<Path> getBaselineProfile(int api) {
        for (BaselineProfile bp : baselineProfiles) {
            if (bp.getMinApi() <= api && api <= bp.getMaxApi()) {
                return bp.getPaths();
            }
        }
        return emptyList();
    }
}
