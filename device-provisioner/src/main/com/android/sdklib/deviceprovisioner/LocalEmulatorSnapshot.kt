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

import com.android.adblib.AdbLogger
import com.android.emulator.snapshot.SnapshotOuterClass
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.isDirectory
import kotlin.io.path.name
import kotlin.io.path.useDirectoryEntries

/**
 * A Snapshot that a LocalEmulatorDeviceHandle can boot from.
 *
 * Each AVD snapshot is stored in its own subdirectory, within the "snapshots" subdirectory of the
 * AVD directory.
 */
data class LocalEmulatorSnapshot(override val name: String, val path: Path) : Snapshot {
  constructor(path: Path) : this(path.name, path)
}

/** Reads LocalEmulatorSnapshots from an AVD's snapshots directory. */
class LocalEmulatorSnapshotReader(private val logger: AdbLogger) {
  companion object {
    /**
     * The snapshot used for "quick boot", i.e. the default when no snapshot argument is passed to
     * the emulator. We do not represent these as LocalEmulatorSnapshots.
     */
    private const val QUICK_BOOT_SNAPSHOT_NAME = "default_boot"
  }

  /**
   * Reads snapshots from the given directory; this should normally be a directory named "snapshots"
   * within an AVD.
   */
  fun readSnapshots(snapshots: Path): List<LocalEmulatorSnapshot> {
    if (!Files.isDirectory(snapshots)) {
      logger.warn("Attempted to read snapshots from $snapshots")
      return emptyList()
    }

    try {
      return snapshots.useDirectoryEntries { entries ->
        entries
          .filter { it.isDirectory() && it.name != QUICK_BOOT_SNAPSHOT_NAME }
          .mapNotNull { readSnapshot(it) }
          .sortedBy { it.name }
          .toList()
      }
    } catch (exception: IOException) {
      logger.warn(exception, "Reading snapshots from $snapshots")
      return emptyList()
    }
  }

  fun readSnapshot(snapshotPath: Path): LocalEmulatorSnapshot? {
    val snapshotProtoPath = snapshotPath.resolve("snapshot.pb")
    if (!snapshotProtoPath.exists()) {
      return LocalEmulatorSnapshot(snapshotPath)
    }
    try {
      val snapshotProto =
        SnapshotOuterClass.Snapshot.parseFrom(Files.newInputStream(snapshotProtoPath))
      return when {
        snapshotProto.imagesCount == 0 -> null
        snapshotProto.logicalName.isEmpty() -> LocalEmulatorSnapshot(snapshotPath)
        else -> LocalEmulatorSnapshot(snapshotProto.logicalName, snapshotPath)
      }
    } catch (exception: IOException) {
      logger.warn(exception, "Reading snapshot proto $snapshotProtoPath")
      return null
    }
  }
}
