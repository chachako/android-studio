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
package com.android.mockito.kotlin

import org.mockito.MockSettings
import org.mockito.MockedStatic
import org.mockito.MockedStatic.Verification
import org.mockito.Mockito
import org.mockito.Mockito.withSettings
import org.mockito.invocation.InvocationOnMock
import org.mockito.stubbing.OngoingStubbing

/**
 * Convenience wrapper around [Mockito.mockStatic] that allows the type to be inferred.
 *
 * Mocks static method invocations within **the current thread only**.
 */
inline fun <reified T> mockStatic(mockSettings: MockSettings = withSettings()): MockedStatic<T> =
    Mockito.mockStatic(T::class.java, mockSettings)

/**
 * Convenience wrapper around [InvocationOnMock.getArgument] that allows the type to be inferred.
 */
inline fun <reified T> InvocationOnMock.getTypedArgument(i: Int): T = getArgument(i, T::class.java)

/**
 * Wrapper around [MockedStatic.when] that isn't called "when", which is a reserved word in Kotlin.
 *
 * @See MockedStatic.when
 */
fun <T> MockedStatic<*>.whenever(verification: Verification): OngoingStubbing<T> =
    `when`(verification)

