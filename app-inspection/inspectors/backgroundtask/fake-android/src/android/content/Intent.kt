/*
 * Copyright (C) 2021 The Android Open Source Project
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
package android.content

import android.net.Uri
import android.os.Bundle

class Intent(
  val action: String? = "action",
  val data: Uri? = Uri(),
  val type: String? = "type",
  val identifier: String? = "identifier",
  val `package`: String? = "package",
  val component: ComponentName? = ComponentName("package", "classname"),
  val categories: Set<String>? = setOf("cat1", "cat2"),
  val flags: Int = 0x121,
  val extras: Bundle? = Bundle(),
) {

  fun filterEquals(other: Intent?): Boolean {
    return equals(other)
  }

  fun filterHashCode(): Int {
    return hashCode()
  }
}
