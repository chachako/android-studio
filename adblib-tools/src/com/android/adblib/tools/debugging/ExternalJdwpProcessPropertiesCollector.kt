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
package com.android.adblib.tools.debugging

import com.android.adblib.AdbSession
import com.android.adblib.CoroutineScopeCache
import kotlinx.coroutines.flow.Flow
import java.util.concurrent.CopyOnWriteArrayList

/**
 * A component that produces a [Flow] of [JdwpProcessProperties] as the properties of
 * a given [process] change over time.
 */
interface ExternalJdwpProcessPropertiesCollector {

    /**
     * The [JdwpProcess] this collector is attached to
     */
    val process: JdwpProcess

    /**
     * The [Flow] of [JdwpProcessProperties] this collector produces when
     * the associated external source is notifying of process properties changes.
     */
    fun trackProperties(): Flow<JdwpProcessProperties>
}

/**
 * A factory of [ExternalJdwpProcessPropertiesCollector], typically injected into an
 * [AdbSession] with the [AdbSession.addExternalJdwpProcessPropertiesCollectorFactory]
 */
interface ExternalJdwpProcessPropertiesCollectorFactory {

    /**
     * Creates an [ExternalJdwpProcessPropertiesCollector] for the given [process] if appropriate,
     * or returns `null` if this factory does not want to provide one.
     */
    suspend fun create(process: JdwpProcess): ExternalJdwpProcessPropertiesCollector?
}

/**
 * The [CoroutineScopeCache.Key] for the list of [ExternalJdwpProcessPropertiesCollectorFactory]
 */
private val ExternalJdwpProcessPropertiesCollectorFactoryListKey =
    CoroutineScopeCache.Key<CopyOnWriteArrayList<ExternalJdwpProcessPropertiesCollectorFactory>>("ExternalJdwpProcessPropertiesCollectorFactoryListKey")

/**
 * The list of [ExternalJdwpProcessPropertiesCollectorFactory] associated to this [AdbSession]
 */
internal val AdbSession.externalJdwpProcessPropertiesCollectorFactoryList: CopyOnWriteArrayList<ExternalJdwpProcessPropertiesCollectorFactory>
    get() = this.cache.getOrPut(ExternalJdwpProcessPropertiesCollectorFactoryListKey) {
        CopyOnWriteArrayList<ExternalJdwpProcessPropertiesCollectorFactory>()
    }

/**
 * Adds a [ExternalJdwpProcessPropertiesCollectorFactory] to this [AdbSession]
 */
fun AdbSession.addExternalJdwpProcessPropertiesCollectorFactory(factory: ExternalJdwpProcessPropertiesCollectorFactory) {
    externalJdwpProcessPropertiesCollectorFactoryList.add(factory)
}

/**
 * Removes a [ExternalJdwpProcessPropertiesCollectorFactory] from this [AdbSession]
 */
fun AdbSession.removeExternalJdwpProcessPropertiesCollectorFactory(factory: ExternalJdwpProcessPropertiesCollectorFactory) {
    externalJdwpProcessPropertiesCollectorFactoryList.remove(factory)
}
