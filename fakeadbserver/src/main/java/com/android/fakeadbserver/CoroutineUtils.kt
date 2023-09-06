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
package com.android.fakeadbserver

import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import com.google.common.util.concurrent.Uninterruptibles
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.job
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.concurrent.ExecutionException
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

@Suppress("TestFunctionName")
fun SupervisorScope(context: CoroutineContext = EmptyCoroutineContext): CoroutineScope {
    return CoroutineScope(context + SupervisorJob())
}

/**
 * Note: This is a (partial) copy of
 * [await](https://github.com/Kotlin/kotlinx.coroutines/blob/7e2d23740c71b8ec5a7e2440f02b36e0519496a6/integration/kotlinx-coroutines-guava/src/ListenableFuture.kt)
 * so that this library does not need to take a dependency on the [kotlinx-coroutines-guava](https://github.com/Kotlin/kotlinx.coroutines/tree/7e2d23740c71b8ec5a7e2440f02b36e0519496a6/integration/kotlinx-coroutines-guava)
 * library.
 */
object Guava {

    /**
     * Awaits completion of `this` [ListenableFuture] without blocking a thread.
     *
     * This suspend function is cancellable.
     *
     * If the [Job] of the current coroutine is cancelled or completed while this suspending function is waiting, this function
     * stops waiting for the future and immediately resumes with [CancellationException][kotlinx.coroutines.CancellationException].
     *
     * This method is intended to be used with one-shot Futures, so on coroutine cancellation, the Future is cancelled as well.
     * If cancelling the given future is undesired, use [Futures.nonCancellationPropagating] or
     * [kotlinx.coroutines.NonCancellable].
     */
    suspend fun <T> ListenableFuture<T>.await(): T {
        try {
            if (isDone) return Uninterruptibles.getUninterruptibly(this)
        } catch (e: ExecutionException) {
            // ExecutionException is the only kind of exception that can be thrown from a gotten
            // Future, other than CancellationException. Cancellation is propagated upward so that
            // the coroutine running this suspend function may process it.
            // Any other Exception showing up here indicates a very fundamental bug in a
            // Future implementation.
            throw e.nonNullCause()
        }

        return suspendCancellableCoroutine { cont: CancellableContinuation<T> ->
            addListener(
                ToContinuation(this, cont),
                MoreExecutors.directExecutor()
            )
            cont.invokeOnCancellation {
                cancel(false)
            }
        }
    }

    /**
     * Propagates the outcome of [futureToObserve] to [continuation] on completion.
     *
     * Cancellation is propagated as cancelling the continuation. If [futureToObserve] completes
     * and fails, the cause of the Future will be propagated without a wrapping
     * [ExecutionException] when thrown.
     */
    private class ToContinuation<T>(
        val futureToObserve: ListenableFuture<T>,
        val continuation: CancellableContinuation<T>
    ) : Runnable {

        override fun run() {
            if (futureToObserve.isCancelled) {
                continuation.cancel()
            } else {
                try {
                    continuation.resume(Uninterruptibles.getUninterruptibly(futureToObserve))
                } catch (e: ExecutionException) {
                    // ExecutionException is the only kind of exception that can be thrown from a gotten
                    // Future. Anything else showing up here indicates a very fundamental bug in a
                    // Future implementation.
                    continuation.resumeWithException(e.nonNullCause())
                }
            }
        }
    }

    /**
     * Returns the cause from an [ExecutionException] thrown by a [Future.get] or similar.
     *
     * [ExecutionException] _always_ wraps a non-null cause when Future.get() throws. A Future cannot
     * fail without a non-null `cause`, because the only way a Future _can_ fail is an uncaught
     * [Exception].
     *
     * If this !! throws [NullPointerException], a Future is breaking its interface contract and losing
     * state - a serious fundamental bug.
     */
    private fun ExecutionException.nonNullCause(): Throwable {
        return this.cause!!
    }
}
