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
package com.android.jdwptracer;

public class DdmJDWPTiming {

    private final int id;
    private final int cmdSet;
    private final int cmd;
    private final long start_ns;
    private final long duration_ns;

    public DdmJDWPTiming(int id, int cmdset, int cmd, long start_ns, long duration_ns) {
        this.id = id;
        this.cmdSet = cmdset;
        this.cmd = cmd;
        this.start_ns = start_ns;
        this.duration_ns = duration_ns;
    }

    public int id() {
        return id;
    }

    public int cmd() {
        return cmd;
    }

    public int cmdSet() {
        return cmdSet;
    }

    public long start_ns() {
        return start_ns;
    }

    public long duration_ns() {
        return duration_ns;
    }
}
