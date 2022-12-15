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
package com.android.testutils

import org.junit.rules.ExternalResource
import kotlin.reflect.KMutableProperty

/**
 * Sets the property returned by the [propertyProvider] to the [newValue] and restores it after
 * the test.
 */
open class PropertySetterRule<T: Any>(
    private val newValue: T,
    private val propertyProvider: () -> KMutableProperty<T>
) : ExternalResource() {

    var oldValue: T? = null

    /**
     * Sets the [property] to the [newValue] and restores it after the test.
     */
    constructor(newValue: T, property: KMutableProperty<T>) : this(newValue, { property })

    override fun before() {
        val property = propertyProvider()
        oldValue = property.getter.call()
        property.setter.call(newValue)
    }

    override fun after() {
        propertyProvider().setter.call(oldValue)
        oldValue = null
    }
}
