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
package com.android.ide.common.resources.configuration

import com.android.resources.GrammaticalGenderState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.test.assertNull

class GrammaticalGenderQualifierTest {
    private val grammaticalGenderQualifier = GrammaticalGenderQualifier()
    private val config = FolderConfiguration()

    @Test
    fun testUnset() {
        assertNull(grammaticalGenderQualifier.value)
    }

    @Test
    fun testNeuter() {
        assertTrue(grammaticalGenderQualifier.checkAndSet("neuter", config))
        assertEquals(GrammaticalGenderState.NEUTER, config.grammaticalGenderQualifier!!.value)
        assertEquals("neuter", config.grammaticalGenderQualifier!!.toString())
    }

    @Test
    fun testFeminine() {
        assertTrue(grammaticalGenderQualifier.checkAndSet("feminine", config))
        assertEquals(GrammaticalGenderState.FEMININE, config.grammaticalGenderQualifier!!.value)
        assertEquals("feminine", config.grammaticalGenderQualifier!!.toString())
    }

    @Test
    fun testMasculine() {
        assertTrue(grammaticalGenderQualifier.checkAndSet("masculine", config))
        assertEquals(GrammaticalGenderState.MASCULINE, config.grammaticalGenderQualifier!!.value)
        assertEquals("masculine", config.grammaticalGenderQualifier!!.toString())
    }

    @Test
    fun testFailures() {
        assertFalse(grammaticalGenderQualifier.checkAndSet("", config))
        assertFalse(grammaticalGenderQualifier.checkAndSet("invalid", config))
    }
}
