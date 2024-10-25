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
package com.android.sdklib.internal.avd

import com.android.resources.ScreenOrientation
import com.android.sdklib.devices.Storage
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class MapBindingTest {
  @Test
  fun read() {
    val data = TestData()
    TestData.binder.read(
      data,
      mapOf(
        "string" to "a",
        "integer" to "2",
        "boolean" to "yes",
        "storage" to "30K",
        "camera" to "webcam0",
        "orientation" to "square",
      ),
    )

    assertThat(data.string).isEqualTo("a")
    assertThat(data.integer).isEqualTo(2)
    assertThat(data.storage).isEqualTo(Storage(30, Storage.Unit.KiB))
    assertThat(data.camera).isEqualTo(AvdCamera.WEBCAM)
    assertThat(data.orientation).isEqualTo(ScreenOrientation.SQUARE)
  }

  @Test
  fun readPartial() {
    val data = TestData()
    TestData.binder.read(
      data,
      mapOf(
        "string" to "a",
        "camera" to "webcam0",
      ),
    )

    assertThat(data.string).isEqualTo("a")
    assertThat(data.integer).isEqualTo(0)
    assertThat(data.storage).isEqualTo(Storage(0))
    assertThat(data.camera).isEqualTo(AvdCamera.WEBCAM)
    assertThat(data.orientation).isEqualTo(ScreenOrientation.PORTRAIT)
  }

  @Test
  fun write() {
    val output = mutableMapOf<String, String>()
    val data =
      TestData().apply {
        string = "a"
        integer = 2
        boolean = true
        storage = Storage(30, Storage.Unit.KiB)
        camera = AvdCamera.WEBCAM
        orientation = ScreenOrientation.SQUARE
      }

    TestData.binder.write(data, output)

    assertThat(output)
      .containsExactlyEntriesIn(
        mapOf(
          "string" to "a",
          "integer" to "2",
          "boolean" to "yes",
          "storage" to "30K",
          "camera" to "webcam0",
          "orientation" to "square",
        )
      )
  }

  @Test
  fun storageConverter() {
    StorageConverter(defaultUnit = Storage.Unit.MiB).let {
      assertThat(it.toString(Storage(30, Storage.Unit.B))).isEqualTo("30B")
      assertThat(it.toString(Storage(30, Storage.Unit.KiB))).isEqualTo("30K")
      assertThat(it.toString(Storage(30, Storage.Unit.MiB))).isEqualTo("30")
      assertThat(it.toString(Storage(30, Storage.Unit.GiB))).isEqualTo("30G")
    }
    StorageConverter(defaultUnit = Storage.Unit.MiB, allowUnitSuffix = false).let {
      assertThat(it.toString(Storage(30, Storage.Unit.B))).isEqualTo("0")
      assertThat(it.toString(Storage(1048576, Storage.Unit.B))).isEqualTo("1")
      assertThat(it.toString(Storage(3072, Storage.Unit.KiB))).isEqualTo("3")
      assertThat(it.toString(Storage(30, Storage.Unit.MiB))).isEqualTo("30")
      assertThat(it.toString(Storage(3, Storage.Unit.GiB))).isEqualTo("3072")
    }
    StorageConverter(defaultUnit = Storage.Unit.B).let {
      assertThat(it.toString(Storage(30, Storage.Unit.B))).isEqualTo("30")
      assertThat(it.toString(Storage(30, Storage.Unit.KiB))).isEqualTo("30K")
      assertThat(it.toString(Storage(30, Storage.Unit.MiB))).isEqualTo("30M")
      assertThat(it.toString(Storage(30, Storage.Unit.GiB))).isEqualTo("30G")
      assertThat(it.toString(Storage(1025 * 1024, Storage.Unit.B))).isEqualTo("1025K")
    }
  }
}

private class TestData {
  var string: String = ""
  var integer: Int = 0
  var boolean: Boolean = false
  var storage: Storage = Storage(0)
  var camera: AvdCamera = AvdCamera.NONE
  var orientation: ScreenOrientation = ScreenOrientation.PORTRAIT

  companion object {
    val binder =
      CompositeBinding(
        TestData::string bindToKey "string",
        TestData::integer bindToKey "integer",
        TestData::boolean bindToKey "boolean",
        TestData::storage bindVia StorageConverter() toKey "storage",
        TestData::camera bindToKey "camera",
        TestData::orientation bindToKey "orientation",
      )
  }
}
