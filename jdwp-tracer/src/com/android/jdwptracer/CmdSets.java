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

import com.android.annotations.NonNull;
import java.util.HashMap;

class CmdSets {

    private static HashMap<Integer, CmdSet> sets = new HashMap<>();

    static {
        add(new CmdSetVM()); // 1
        add(new CmdSetReferenceType()); // 2
        add(new CmdSetClassType()); // 3
        add(new CmdSetArrayType()); // 4
        add(new CmdSetInterfaceType()); // 5
        add(new CmdSetMethod()); // 6
        // 7 Unused
        add(new CmdSetField()); // 8
        add(new CmdSetObjectReference()); // 9
        add(new CmdSetStringReference()); // 10
        add(new CmdSetThreadReference()); // 11
        add(new CmdSetThreadGroupReference()); // 12
        add(new CmdSetArrayReference()); // 13
        add(new CmdSetClassLoaderReference()); // 14
        add(new CmdSetEventRequest()); // 15
        add(new CmdSetStackFrame()); // 16
        add(new CmdSetClassObjectReference()); // 17
        add(new CmdSetEvent()); // 64

        add(new CmdSetDdm()); // 199
    }

    private static void add(@NonNull CmdSet cmdSet) {
        if (sets.containsKey(cmdSet.id)) {
            throw new IllegalStateException("CmdSet " + cmdSet.id + " is already registered");
        }
        sets.put(cmdSet.id(), cmdSet);
    }

    @NonNull
    public static CmdSet get(int cmdSetID) {
        if (!sets.containsKey(cmdSetID)) {
            return new CmdSet(cmdSetID, "UNKNOWN(" + cmdSetID + ")");
        }
        return sets.get(cmdSetID);
    }
}
