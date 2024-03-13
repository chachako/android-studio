/*
 * Copyright (C) 2021 The Android Open Source Project
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

@file:JvmName("ApiTestUtils")

package com.android.build.api

import com.google.common.base.Charsets
import com.google.common.base.Splitter
import com.google.common.io.Resources
import com.google.common.reflect.ClassPath
import java.net.URL

fun filterNonApiClasses(classInfo: ClassPath.ClassInfo): Boolean =
    !classInfo.simpleName.endsWith("Test") &&
            classInfo.simpleName != "StableApiUpdater" &&
            classInfo.simpleName != "DeprecatedApiUpdater" &&
            classInfo.simpleName != "ApiTestUtils"


internal fun transformFinalFileContent(currentSnapshotContent: List<String>, snapshotFileUrl: URL, currentKey: String, keyPrefix: String, keyOrdering: Comparator<String>):
        Collection<String> {
    val expectedSnapshotContent = Splitter.on("\n")
        .omitEmptyStrings()
        .splitToList(Resources.toString(snapshotFileUrl, Charsets.UTF_8))

    val expectedToKey = mutableMapOf<String, String>()
    var key: String? = null
    expectedSnapshotContent.subList(5, expectedSnapshotContent.size).forEach {
        if (it.startsWith(keyPrefix)) {
            key = it.removePrefix(keyPrefix)
        } else {
            expectedToKey[it.removePrefix("  * ")] = key!!
        }
    }

    val actualToKey = mutableMapOf<String, MutableList<String>>()
    currentSnapshotContent.subList(5, currentSnapshotContent.size).forEach { api ->
        actualToKey.getOrPut(
            expectedToKey[api] ?: currentKey
        ) { mutableListOf() }.add(api)
    }

    val newExpectedList = mutableListOf<String>()
    actualToKey.keys.sortedWith(keyOrdering).forEach { key ->
        newExpectedList.add("$keyPrefix$key")
        actualToKey[key]!!.sorted().forEach { apiSignature ->
            newExpectedList.add("  * $apiSignature")
        }
    }

    return expectedSnapshotContent.subList(0, 5) + newExpectedList
}


