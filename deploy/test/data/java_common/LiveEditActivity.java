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
package app;

import android.app.Activity;

public class LiveEditActivity extends Activity {

    public LiveEditActivity() {
        super("Live Edit Test Activity");
    }

    public void invokeStubbedMethods() {
        StubTarget target = new StubTarget();
        target.nonStaticVoidMethod();
        target.nonStaticObjectMethod();
        target.nonStaticIntMethod();
        StubTarget.staticVoidMethod();
        StubTarget.staticObjectMethod();
        StubTarget.staticIntMethod();
    }

    public void downgradeComposeRuntime() {
        androidx.compose.runtime.ComposeVersion.version = 1;
    }

    public void invokeLiveEditSimple() {
        pkg.LiveEditSimpleKt.LiveEditSimple();
    }

    public void invokeLiveEditUseAccessor() {
        System.out.println("UseAccessor: " + (new pkg.UseAccessor()).accessX().invoke());
    }

    public void invokeLiveEditAddAccessor() {
        System.out.println("AddAccessor: " + (new pkg.AddAccessor()).accessX().invoke());
    }
}
