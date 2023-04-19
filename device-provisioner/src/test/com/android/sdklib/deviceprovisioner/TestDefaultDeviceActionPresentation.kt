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

import javax.swing.UIManager

object TestDefaultDeviceActionPresentation : DeviceAction.DefaultPresentation {
  // A rather arbitrary icon that is part of Swing:
  private val icon = UIManager.getIcon("Tree.collapsedIcon")
  override val createDeviceAction = DeviceAction.Presentation("Create", icon, true)
  override val createDeviceTemplateAction = DeviceAction.Presentation("Create", icon, true)
  override val activationAction = DeviceAction.Presentation("Start", icon, true)
  override val deactivationAction = DeviceAction.Presentation("Stop", icon, true)
  override val editAction = DeviceAction.Presentation("Edit", icon, true)
  override val deleteAction = DeviceAction.Presentation("Delete", icon, true)
  override val editTemplateAction = DeviceAction.Presentation("Edit", icon, true)
  override val reservationAction = DeviceAction.Presentation("Start", icon, true)
  override val templateActivationAction = DeviceAction.Presentation("Start", icon, true)
}
