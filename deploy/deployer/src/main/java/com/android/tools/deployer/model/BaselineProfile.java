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

package com.android.tools.deployer.model;

import java.nio.file.Path;
import java.util.List;

public class BaselineProfile {

    private final int minApi;

    private final int maxApi;

    private final List<Path> paths;

    public BaselineProfile(int minApi, int maxApi, List<Path> paths) {
        this.minApi = minApi;
        this.maxApi = maxApi;
        this.paths = paths;
    }

    public int getMaxApi() {
        return maxApi;
    }

    public int getMinApi() {
        return minApi;
    }

    public List<Path> getPaths() {
        return paths;
    }
}
