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

class CmdSetReferenceType extends CmdSet {

    protected CmdSetReferenceType() {
        super(2, "REF_TYPE");

        add(1, "Signature");
        add(2, "Classloader");
        add(3, "Modifiers");
        add(4, "Fields");
        add(5, "Methods");
        add(6, "GetValues");
        add(7, "SourceFile");
        add(8, "NestedTypes");
        add(9, "Status");
        add(10, "Interfaces");
        add(11, "ClassObject");
        add(12, "SourceDebugExtension");
        add(13, "SignatureWithGenerics");
        add(14, "FieldWithGenerics");
        add(15, "MethodWithGenerics");
        add(16, "Instances");
        add(17, "ClassFileVersion");
        add(18, "ConstantPool");
    }
}
