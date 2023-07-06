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
package com.android.tools.deploy.liveedit;

public class InvokeMath {
    static float divf(float f1, float f2) {
        return f1 / f2;
    }

    static double divd(double d1, double d2) {
        return d1 / d2;
    }

    static float modf(float f1, float f2) {
        return f1 % f2;
    }

    static double modd(double f1, double f2) {
        return f1 % f2;
    }
}
