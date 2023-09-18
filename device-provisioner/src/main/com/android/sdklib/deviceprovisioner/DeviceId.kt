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

/**
 * A serializable identifier for a DeviceHandle or DeviceTemplate.
 *
 * @property pluginId the DeviceProvisionerPlugin that created this DeviceId
 * @property isTemplate if true, this refers to a DeviceTemplate rather than a DeviceHandle.
 * @property identifier an arbitrary string that uniquely identifies the device (among others with
 *   the same plugin ID). This is public only for the purpose of serialization; it should not be
 *   interpreted.
 */
data class DeviceId(
  val pluginId: String,
  val isTemplate: Boolean,
  val identifier: String,
)
