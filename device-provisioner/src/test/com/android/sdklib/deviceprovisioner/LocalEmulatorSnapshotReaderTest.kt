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
package com.android.sdklib.deviceprovisioner

import com.android.adblib.utils.JdkLoggerFactory
import com.android.emulator.snapshot.SnapshotOuterClass
import com.android.testutils.file.createInMemoryFileSystem
import com.android.testutils.file.someRoot
import com.google.common.truth.Truth.assertThat
import java.nio.file.Files
import java.nio.file.Path
import org.junit.Test

class LocalEmulatorSnapshotReaderTest {
  private val fileSystem = createInMemoryFileSystem()
  private val snapshotReader =
    LocalEmulatorSnapshotReader(JdkLoggerFactory.JdkLogger("LocalEmulatorSnapshotReaderTest"))

  private val avdPath = fileSystem.someRoot.resolve("avds").resolve("Pixel_4.avd")
  private val snapshotsPath = avdPath.resolve("snapshots")

  @Test
  fun readNormalSnapshot() {
    val snapshotPath = snapshotsPath.resolve("snap1")
    createNormalSnapshot(snapshotPath)

    assertThat(snapshotReader.readSnapshot(snapshotPath))
      .isEqualTo(LocalEmulatorSnapshot("Bug-567", snapshotPath))
  }

  @Test
  fun readAnonymousSnapshot() {
    val snapshotPath = snapshotsPath.resolve("snap1")
    createAnonymousSnapshot(snapshotPath)

    assertThat(snapshotReader.readSnapshot(snapshotPath))
      .isEqualTo(LocalEmulatorSnapshot(snapshotPath))
  }

  @Test
  fun readSnapshotWithoutImages() {
    val snapshotPath = snapshotsPath.resolve("snap1")
    createSnapshotWithoutImages(snapshotPath)

    assertThat(snapshotReader.readSnapshot(snapshotPath)).isNull()
  }

  @Test
  fun readSnapshots() {
    val defaultBootPath = snapshotsPath.resolve("default_boot")
    Files.createDirectories(defaultBootPath)

    val snapshot1Path = snapshotsPath.resolve("snap_1")
    val snapshot2Path = snapshotsPath.resolve("snap_2")
    val snapshot3Path = snapshotsPath.resolve("snap_3")
    createNormalSnapshot(snapshot1Path)
    createAnonymousSnapshot(snapshot2Path)
    createSnapshotWithoutImages(snapshot3Path)

    assertThat(snapshotReader.readSnapshots(snapshotsPath))
      .containsExactly(
        LocalEmulatorSnapshot("Bug-567", snapshot1Path),
        LocalEmulatorSnapshot(snapshot2Path),
      )
      .inOrder()
  }
}

fun createNormalSnapshot(path: Path) {
  Files.createDirectories(path)
  Files.newOutputStream(path.resolve("snapshot.pb")).use {
    SnapshotOuterClass.Snapshot.newBuilder()
      .setLogicalName("Bug-567")
      .addImages(SnapshotOuterClass.Image.getDefaultInstance())
      .build()
      .writeTo(it)
  }
}

fun createAnonymousSnapshot(path: Path) {
  Files.createDirectories(path)
  Files.newOutputStream(path.resolve("snapshot.pb")).use {
    SnapshotOuterClass.Snapshot.newBuilder()
      .addImages(SnapshotOuterClass.Image.getDefaultInstance())
      .build()
      .writeTo(it)
  }
}

fun createSnapshotWithoutImages(path: Path) {
  Files.createDirectories(path)
  Files.newOutputStream(path.resolve("snapshot.pb")).use {
    SnapshotOuterClass.Snapshot.newBuilder().setLogicalName("Snap-3").build().writeTo(it)
  }
}
