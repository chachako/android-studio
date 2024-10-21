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

package com.android.builder.packaging

import com.android.builder.packaging.DexFileComparator.compare
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.io.File

class DexPackagingUtilsTest {

    @Test
    fun testDexFileComparator() {
        // Case 1. Both dex files are classes.dex
        assertThat(compare(File("a/classes.dex"), File("a/classes.dex"))).isEqualTo(0)
        assertThat(compare(File("a/classes.dex"), File("b/classes.dex"))).isEqualTo(-1)
        assertThat(compare(File("b/classes.dex"), File("a/classes.dex"))).isEqualTo(1)

        // Case 2. Only one dex file is classes.dex
        assertThat(compare(File("a/classes.dex"), File("a/classes2.dex"))).isEqualTo(-1)
        assertThat(compare(File("a/classes.dex"), File("b/classes2.dex"))).isEqualTo(-1)
        assertThat(compare(File("b/classes.dex"), File("a/classes2.dex"))).isEqualTo(-1)

        assertThat(compare(File("a/classes2.dex"), File("a/classes.dex"))).isEqualTo(1)
        assertThat(compare(File("a/classes2.dex"), File("b/classes.dex"))).isEqualTo(1)
        assertThat(compare(File("b/classes2.dex"), File("a/classes.dex"))).isEqualTo(1)

        // Case 3. Neither dex files are classes.dex
        assertThat(compare(File("a/classes2.dex"), File("a/classes2.dex"))).isEqualTo(0)
        assertThat(compare(File("a/classes2.dex"), File("b/classes2.dex"))).isEqualTo(-1)
        assertThat(compare(File("b/classes2.dex"), File("a/classes2.dex"))).isEqualTo(1)

        assertThat(compare(File("a/classes2.dex"), File("a/classes3.dex"))).isEqualTo(-1)
        assertThat(compare(File("a/classes2.dex"), File("b/classes3.dex"))).isEqualTo(-1)
        assertThat(compare(File("b/classes2.dex"), File("a/classes3.dex"))).isEqualTo(1)
    }
}
