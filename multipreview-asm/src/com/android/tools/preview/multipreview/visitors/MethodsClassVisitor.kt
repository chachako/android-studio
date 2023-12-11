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

package com.android.tools.preview.multipreview.visitors

import com.android.tools.preview.multipreview.Graph
import com.android.tools.preview.multipreview.MultipreviewSettings
import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes

/**
 * [ClassVisitor] that looks for potential methods related to the multipreview. For every method, if
 * not skipped, it delegates the job to the [AnnotatedMethodVisitor]
 */
internal class MethodsClassVisitor(
    private val settings: MultipreviewSettings,
    private val className: String,
    private val graph: Graph,
    private val methodsFilter: MethodsFilter,
) : ClassVisitor(Opcodes.ASM9) {

    override fun visitMethod(
        access: Int,
        name: String?,
        descriptor: String?,
        signature: String?,
        exceptions: Array<out String>?,
    ): MethodVisitor? {
        return if (name == null || descriptor == null || !methodsFilter.allowMethod("$className.$name"))
            super.visitMethod(access, name, descriptor, signature, exceptions)
        else
            AnnotatedMethodVisitor(settings, graph, "$className.$name")
    }
}
