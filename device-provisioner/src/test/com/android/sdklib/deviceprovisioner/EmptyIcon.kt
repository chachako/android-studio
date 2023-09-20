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

import java.awt.Component
import java.awt.Graphics
import javax.swing.Icon

/** A blank default icon for testing that doesn't require loading any UI code. */
data class EmptyIcon(private val iconWidth: Int, private val iconHeight: Int) : Icon {
  override fun paintIcon(c: Component?, g: Graphics?, x: Int, y: Int) {}

  override fun getIconWidth() = iconWidth

  override fun getIconHeight() = iconHeight

  companion object {
    val DEFAULT = EmptyIcon(16, 16)
  }
}
