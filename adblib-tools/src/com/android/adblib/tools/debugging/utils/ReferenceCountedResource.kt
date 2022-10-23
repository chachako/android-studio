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
package com.android.adblib.tools.debugging.utils

import com.android.adblib.AdbSession
import com.android.adblib.AutoShutdown
import com.android.adblib.thisLogger
import com.android.adblib.useShutdown
import com.android.adblib.utils.createChildScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext

/**
 * Provides access to an [AutoShutdown] resource using a "reference counting" mechanism.
 *
 * * The [retain] method increments the reference count, after creating an instance of
 * the resource using [factory] if current reference count value was 0.
 * * The [release] method decrements the reference count, invoking [AutoShutdown.shutdown]
 * if the reference count reaches 0.
 * * The [close] method cancels all async operations and calls [AutoShutdown.close] on
 * the resource if it is active.
 *
 * This class is useful when there is a need to coordinate multiple consumers of a single shared
 * resource that is expensive to acquire. For example, consumers may need to access a resource
 * concurrently at times and durations that are not known ahead of time.
 *
 * Instances of this class are thread-safe.
 */
internal class ReferenceCountedResource<T : AutoShutdown>(
    session: AdbSession,
    /**
     * The [CoroutineContext] used to run [factory] when a new resource instance is needed
     */
    factoryContext: CoroutineContext = EmptyCoroutineContext,
    /**
     * The [factory] coroutine, i.e. the coroutine that is called when a new instance of the
     * underlying resource is needed. It is guaranteed [factory] will never be executed
     * concurrently.
     *
     * Note: If [factory] succeeds, [ReferenceCountedResource] is responsible for
     * [closing][AutoCloseable.close] the returned instance of [T]. However, [factory] may be
     * cancelled (in case of early termination of [ReferenceCountedResource]) and is
     * responsible for cleaning up in that case.
     */
    private val factory: suspend () -> T,
) {

    private val logger = thisLogger(session)

    /**
     * The [CoroutineScope] to use to call [factory]
     */
    private val factoryScope = session.scope.createChildScope(context = factoryContext)

    /**
     * Lock for [referenceCount], [currentFactoryJob] and [closed]
     */
    private val lock = Any()

    private var referenceCount = 0

    private var currentFactoryJob: Deferred<T>? = null

    private var closed: Boolean = false

    /**
     * Increment the reference count, after creating an new instance of the resource if
     * this was the first reference.
     */
    suspend fun retain(): T {
        return synchronized(lock) {
            if (closed) {
                throw IllegalStateException("ReferenceCountedResource has been closed")
            }
            retainAsync()
        }.await()
    }

    /**
     * Decrement the reference count and [AutoShutdown.shutdown] the resource if this was
     * the last reference.
     */
    suspend fun release() {
        val newRefCount = synchronized(lock) {
            (--referenceCount).also {
                logger.verbose { "release: ref. count after=$referenceCount" }
            }
        }
        if (newRefCount == 0) {
            logger.debug { "Shutting down resource" }
            shutdownResource()
        }
    }

    fun close() {
        val closing = synchronized(lock) {
            if (!closed) {
                closed = true
                true
            } else {
                false
            }
        }

        if (closing) {
            logger.debug { "Closing resource" }
            factoryScope.cancel("ReferenceCountedResource closed")
            closeResource()
        }
    }

    private fun retainAsync(): Deferred<T> {
        logger.verbose { "retain: ref. count before=$referenceCount" }
        return if (referenceCount++ == 0) {
            logger.debug { "Creating resource from factory" }
            assert(currentFactoryJob == null)
            factoryScope.async {
                val value = factory()
                logger.debug { "Created resource from factory: $value" }
                value
            }.also {
                currentFactoryJob = it
            }
        } else {
            // We have a positive reference count, and we were not the first one, so we
            // are guaranteed to have a pending job
            assert(currentFactoryJob != null)
            currentFactoryJob
                ?: throw IllegalStateException("ReferenceCountedResource job should be initialized")
        }
    }

    private fun cancelFactoryJob(): T? {
        var resourceValue: T? = null
        synchronized(lock) {
            logger.debug { "Cancelling current job: $currentFactoryJob" }
            currentFactoryJob?.let { deferred ->
                deferred.invokeOnCompletion { throwable ->
                    if (throwable == null) {
                        @OptIn(ExperimentalCoroutinesApi::class)
                        resourceValue = deferred.getCompleted()
                    }
                }
                deferred.cancel()
            }
            currentFactoryJob = null
        }
        return resourceValue
    }

    private suspend fun shutdownResource() {
        // We need to close the value if we completed
        cancelFactoryJob()?.let { resource ->
            logger.debug { "Calling shutdown() and close() on resource: $resource" }
            resource.useShutdown { }
        }
    }

    private fun closeResource() {
        // We need to close the value if we completed
        cancelFactoryJob()?.let { resource ->
            logger.debug { "Calling close() on resource: $resource" }
            resource.close()
        }
    }
}

/**
 * Invoke [block] after [retaining][ReferenceCountedResource.retain] the value of the given
 * [ReferenceCountedResource].
 */
internal suspend inline fun <T : AutoShutdown, R> ReferenceCountedResource<T>.withResource(
    block: (T) -> R
): R {
    val resourceValue = retain()
    return try {
        block(resourceValue)
    } finally {
        release()
    }
}
