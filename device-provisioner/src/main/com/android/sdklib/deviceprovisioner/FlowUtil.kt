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
package com.android.sdklib.deviceprovisioner

import com.intellij.util.containers.orNull
import java.util.Optional
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

sealed interface SetChange<T> {
  class Add<T>(val value: T) : SetChange<T> {
    override fun toString() = "Add($value)"
  }

  class Remove<T>(val value: T) : SetChange<T> {
    override fun toString() = "Remove($value)"
  }
}

/**
 * Given a Flow<Set<T>>, returns a Flow of Change<T>, identifying elements which have been added or
 * removed from the set.
 */
fun <T> Flow<Set<T>>.trackSetChanges(): Flow<SetChange<T>> = flow {
  var current = emptySet<T>()
  collect { new ->
    (current - new).forEach { emit(SetChange.Remove(it)) }
    (new - current).forEach { emit(SetChange.Add(it)) }
    current = new
  }
}

/**
 * Given a Flow<Iterable<T>>, where each T contains a nested Flow<S>, return a flow of
 * List<Pair<T,S>> that updates every time the source flow updates or the nested flow of any T
 * updates.
 *
 * Note that the returned flow will not emit any elements unless it has a value from every inner
 * flow; [innerState] should generally return a [StateFlow] or otherwise ensure that an element is
 * produced promptly, unless blocking the outer flow is desired.
 */
fun <T, S> Flow<Iterable<T>>.pairWithNestedState(
  innerState: (T) -> Flow<S>
): Flow<List<Pair<T, S>>> = mapNestedStateNotNull(innerState, ::Pair)

/**
 * Transforms the [devices] flow, applying a transform to each handle and its current state, and
 * omitting null results.
 */
fun <R> DeviceProvisioner.mapStateNotNull(
  transform: (DeviceHandle, DeviceState) -> R?
): Flow<List<R>> =
  devices.mapNestedStateNotNull(innerState = { it.stateFlow }, transform = transform)

/**
 * Given a Flow<Iterable<T>>, where each T contains a nested Flow<S>, and a function from (T, S) to
 * R?, return a flow of List<R> that updates every time the source flow updates or the nested flow
 * of any T updates, excluding elements that are mapped to null.
 *
 * Note that the returned flow will not emit any elements unless it has a value from every inner
 * flow; [innerState] should generally return a [StateFlow] or otherwise ensure that an element is
 * produced promptly, unless blocking the outer flow is desired.
 */
internal fun <T, S, R : Any> Flow<Iterable<T>>.mapNestedStateNotNull(
  innerState: (T) -> Flow<S>,
  transform: (T, S) -> R?,
): Flow<List<R>> = flatMapLatest { ts ->
  // Optional is used here for technical reasons: combine() below requires the flow type to be
  // reified, and if we made R reified, then this method and mapStateNotNull would need to be
  // inline, which would force this method to be public.
  val innerFlows = ts.map { t -> innerState(t).map { Optional.ofNullable(transform(t, it)) } }
  when {
    innerFlows.isEmpty() -> flowOf(emptyList())
    else -> combine(innerFlows) { rs: Array<Optional<R>> -> rs.mapNotNull { it.orNull() } }
  }
}
