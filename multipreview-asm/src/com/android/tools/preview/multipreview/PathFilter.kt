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

package com.android.tools.preview.multipreview

/**
 * Interface for filtering annotated methods based on the path of the file containing the class
 * the methods belong to.
 */
fun interface PathFilter {
  fun allowMethodsForPath(path: String): Boolean

  companion object {
    val ALLOW_ALL = PathFilter { true }

    fun allowForSet(paths: Set<String>): PathFilter = AllowForSet(paths)
  }
}

/**
 * [PathFilter] implementation allowing only methods from the classes located in the paths from the
 * [paths] set.
 */
private class AllowForSet(private val paths: Set<String>) : PathFilter {
  override fun allowMethodsForPath(path: String): Boolean = paths.contains(path)
}
