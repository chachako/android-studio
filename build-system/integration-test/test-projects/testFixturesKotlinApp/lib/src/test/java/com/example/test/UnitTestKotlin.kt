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
package com.example.test

import com.example.lib.LibInterface
import com.example.lib.testFixtures.LibInterfaceTester
import com.example.lib.testFixtures.LibResourcesTester
import org.junit.Test

class UnitTestKotlin {
    private inner class LibInterfaceImpl : LibInterface {
        override val name = "test"
    }

    @Test
    fun testLibInterfaceTester() {
        val tester: LibInterfaceTester = LibInterfaceTester("test")
        tester.test(LibInterfaceImpl())
    }

    @Test
    fun testLibResourcesTester() {
        val tester: LibResourcesTester =
            LibResourcesTester(
                com.example.lib.R.string.libResourceString,
                com.example.lib.testFixtures.R.string.testFixturesResourceString
            )
        tester.test()
    }
}
