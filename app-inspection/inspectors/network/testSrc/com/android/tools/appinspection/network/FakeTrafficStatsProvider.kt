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

package com.android.tools.appinspection.network

import java.util.concurrent.atomic.AtomicInteger

/** Fake implementation of [TrafficStatsProvider] */
class FakeTrafficStatsProvider : TrafficStatsProvider {

  private val rxIndex = AtomicInteger(0)
  private val txIndex = AtomicInteger(0)

  private val rxData = mutableListOf<Long>()
  private val txData = mutableListOf<Long>()

  fun setData(vararg data: Stat) {
    rxData.addAll(data.map { it.rxBytes })
    txData.addAll(data.map { it.txBytes })
  }

  override fun getUidRxBytes(uid: Int) =
    rxData.getOrElse(rxIndex.getAndIncrement()) { rxData.last() }

  override fun getUidTxBytes(uid: Int) =
    txData.getOrElse(txIndex.getAndIncrement()) { txData.last() }

  data class Stat(val rxBytes: Long, val txBytes: Long)
}
