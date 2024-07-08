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
package com.android.tools.leakcanarylib.data

import com.android.tools.leakcanarylib.data.Node.Companion.ZERO_WIDTH_SPACE
import com.android.tools.leakcanarylib.parser.LeakTraceParser

data class LeakTrace(
    var gcRootType: GcRootType,
    var nodes: List<Node>
) {

    companion object {
        fun fromString(lines: String): LeakTrace {
            return LeakTraceParser.parseLeakTrace(
                lines.lines(), "├─", "╰→"
            )
        }
    }

    override fun toString(): String = leakTraceAsString()

    private fun leakTraceAsString(): String {
        var result = """
        ┬───
        │ GC Root: ${gcRootType.description}
        │
        """.trimIndent()

        nodes.forEachIndexed { index, node ->
            val firstLinePrefix = if (node.referencingField == null) "╰→ " else "├─ "
            val additionalLinesPrefix =
                if (node.referencingField == null) "$ZERO_WIDTH_SPACE     " else "│    "
            result += "\n"
            result += node.toString(
                firstLinePrefix = firstLinePrefix,
                additionalLinesPrefix = additionalLinesPrefix,
                showLeakingStatus = true
            )
        }
        return result
    }
}
