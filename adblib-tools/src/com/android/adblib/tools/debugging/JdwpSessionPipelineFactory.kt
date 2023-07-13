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
package com.android.adblib.tools.debugging

import com.android.adblib.AdbSession
import com.android.adblib.CoroutineScopeCache
import java.util.concurrent.CopyOnWriteArrayList

/**
 * A component that creates instances of [JdwpSessionPipeline] each time a new JDWP session
 * is established. [JdwpSessionPipelineFactory] instances are registered with
 * [AdbSession.addJdwpSessionPipelineFactory].
 *
 * @see AdbSession.addJdwpSessionPipelineFactory
 * @see AdbSession.removeJdwpSessionPipelineFactory
 */
interface JdwpSessionPipelineFactory {

    /**
     * The priority of this factory, higher priority factories will be called before lower
     * priority factories, meaning their [JdwpSessionPipeline] will be "closer" to the
     * [JdwpSessionPipeline] corresponding to the direct connection to the external
     * debugger (e.g. Android Studio or IntelliJ).
     */
    val priority: Int

    /**
     * Creates a [JdwpSessionPipeline] connected to the given [previousPipeline],
     * or return `null` if the factory is not active for some reason.
     *
     * @param session The [AdbSession] corresponding to the underlying JDWP session
     * @param previousPipeline The [JdwpSessionPipeline] the new end point should forward packets to
     * and from
     */
    fun create(session: AdbSession, previousPipeline: JdwpSessionPipeline): JdwpSessionPipeline?
}

/**
 * The [CoroutineScopeCache.Key] for the list of [JdwpSessionPipelineFactory]
 */
private val JdwpSessionPipelineFactoryKey =
    CoroutineScopeCache.Key<CopyOnWriteArrayList<JdwpSessionPipelineFactory>>("JdwpSessionPipelineFactoryListKey")

/**
 * The list of [JdwpSessionPipelineFactory] associated to this [AdbSession]
 */
internal val AdbSession.jdwpSessionPipelineFactoryList: CopyOnWriteArrayList<JdwpSessionPipelineFactory>
    get() = this.cache.getOrPut(JdwpSessionPipelineFactoryKey) {
        CopyOnWriteArrayList<JdwpSessionPipelineFactory>()
    }

/**
 * Adds a [JdwpSessionPipelineFactory] for [SharedJdwpSession] of this [AdbSession]
 */
fun AdbSession.addJdwpSessionPipelineFactory(factory: JdwpSessionPipelineFactory) {
    jdwpSessionPipelineFactoryList.add(factory)
}

/**
 * Removes a [JdwpSessionPipelineFactory] for [SharedJdwpSession] of this [AdbSession]
 */
fun AdbSession.removeJdwpSessionPipelineFactory(factory: JdwpSessionPipelineFactory) {
    jdwpSessionPipelineFactoryList.remove(factory)
}
