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
package com.android.declarative.internal.toml

import org.tomlj.TomlArray
import org.tomlj.TomlParseResult
import org.tomlj.TomlTable

/**
 * [TomlTable and [TomlArray] extension functions to make parsing of
 * build files easier.
 */
fun TomlTable.forEachKey(action: (String) -> Unit) {
    keySet().forEach { action(safeGetString(it)) }
}

fun TomlArray.forEachTable(action: (TomlTable) -> Unit) {
    for (i in 0 until size()) {
        action(getTable(i))
    }
}

fun <T> TomlArray.mapTable(action: (TomlTable) -> T?): List<T> {
    val results = mutableListOf<T>()
    forEachTable { source ->
        action.invoke(source)?.let { result ->
            results.add(result)
        }
    }
    return results.toList()
}

fun TomlArray.forEachString(action: (String) -> Unit) {
    for (i in 0 until size()) {
        action(getString(i))
    }
}

fun TomlParseResult.forEach(dottedKey: String, action: (String) -> Unit) {
    if (contains(dottedKey)) {
        when(val value = get(dottedKey)) {
            is TomlArray -> value.forEachString(action)
            is TomlTable -> value.forEachKey(action)
            else -> throw RuntimeException("Cannot handle ${value?.javaClass} type")
        }
    }
}

fun TomlTable.safeGetString(dottedKey: String) =
    getString(dottedKey)
        ?: throw RuntimeException("Required element $dottedKey not provided")

fun TomlTable.checkElementsPresence(context: String, vararg elements: String) {
    elements.forEach { element ->
        get(element)
            ?: throw IncorrectTomlSpecificationException(
                context = context,
                missingElement = element,
                providedElements = keySet(),
                requiredElements = elements.toList(),
            )
    }
}
