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
package com.android.sdklib.deviceprovisioner

import com.android.adblib.testing.FakeAdbLoggerFactory
import com.android.adblib.utils.createChildScope
import com.android.sdklib.internal.avd.AvdInfo
import com.android.testutils.file.createInMemoryFileSystemAndFolder
import com.google.common.truth.Truth.assertThat
import kotlin.time.Duration.Companion.minutes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.takeWhile
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import org.junit.Test

class LocalEmulatorDeviceHandleTest {
  @OptIn(ExperimentalCoroutinesApi::class)
  @Test
  fun activationTimeout() = runTest {
    val avdManager =
      object : StubAvdManager() {
        override suspend fun startAvd(avdInfo: AvdInfo) {
          delay(Long.MAX_VALUE)
        }
      }
    val context = testContext(this, avdManager)
    val handle =
      LocalEmulatorDeviceHandle(
        context,
        {},
        this.createChildScope(),
        emptyList(),
        makeAvdInfo(createInMemoryFileSystemAndFolder("avds"), 1),
      )

    val activateJob = async(SupervisorJob()) { handle.activationAction.activate() }

    handle.stateFlow.takeWhile { !it.isTransitioning }.collect()

    advanceTimeBy(10.minutes)

    handle.stateFlow.takeWhile { it.isTransitioning }.collect()

    assertThat(activateJob.getCompletionExceptionOrNull())
      .isInstanceOf(DeviceActionException::class.java)

    handle.scope.cancel()
  }
}

fun unsupportedOperation(): Nothing = throw UnsupportedOperationException()

open class StubAvdManager : LocalEmulatorProvisionerPlugin.AvdManager {

  override suspend fun rescanAvds(): List<AvdInfo> = unsupportedOperation()

  override suspend fun createAvd(): AvdInfo? = unsupportedOperation()

  override suspend fun editAvd(avdInfo: AvdInfo): Boolean = unsupportedOperation()

  override suspend fun startAvd(avdInfo: AvdInfo): Unit = unsupportedOperation()

  override suspend fun coldBootAvd(avdInfo: AvdInfo): Unit = unsupportedOperation()

  override suspend fun bootAvdFromSnapshot(
    avdInfo: AvdInfo,
    snapshot: LocalEmulatorSnapshot,
  ): Unit = unsupportedOperation()

  override suspend fun stopAvd(avdInfo: AvdInfo): Unit = unsupportedOperation()

  override suspend fun showOnDisk(avdInfo: AvdInfo): Unit = unsupportedOperation()

  override suspend fun duplicateAvd(avdInfo: AvdInfo): Unit = unsupportedOperation()

  override suspend fun wipeData(avdInfo: AvdInfo): Unit = unsupportedOperation()

  override suspend fun deleteAvd(avdInfo: AvdInfo): Unit = unsupportedOperation()

  override suspend fun downloadAvdSystemImage(avdInfo: AvdInfo): Unit = unsupportedOperation()
}

private fun testContext(
  testScope: TestScope,
  avdManager: LocalEmulatorProvisionerPlugin.AvdManager,
) =
  LocalEmulatorContext(
    FakeAdbLoggerFactory().createClassLogger(LocalEmulatorProvisionerPlugin::class.java),
    DeviceIcons(EmptyIcon.DEFAULT, EmptyIcon.DEFAULT, EmptyIcon.DEFAULT, EmptyIcon.DEFAULT),
    defaultPresentation = TestDefaultDeviceActionPresentation,
    avdManager = avdManager,
    diskIoDispatcher = Dispatchers.IO,
    clock =
      object : Clock {
        override fun now() = Instant.fromEpochMilliseconds(testScope.currentTime)
      },
  )
