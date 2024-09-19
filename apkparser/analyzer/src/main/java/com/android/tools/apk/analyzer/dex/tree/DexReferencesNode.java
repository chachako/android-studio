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
package com.android.tools.apk.analyzer.dex.tree;

public class DexReferencesNode extends DexElementNode {

    public static final String NAME = "References";

    public DexReferencesNode() {
        super(NAME, true);
    }

    @Override
    public long getSize() {
        long size = 0;
        for (int i = 0, n = getChildCount(); i < n; i++) {
            DexElementNode node = getChildAt(i);
            size += node.getSize();
        }
        return size;
    }

    @Override
    public boolean isDefined() {
        return false;
    }

    @Override
    public int getMethodDefinitionsCount() {
        return 0;
    }

    @Override
    public void update() {
        super.update();
        int count = 0;
        for (int i = 0, n = getChildCount(); i < n; i++) {
            DexElementNode node = getChildAt(i);
            count += node.getMethodReferencesCount();
        }
        setMethodReferencesCount(count);
    }
}
