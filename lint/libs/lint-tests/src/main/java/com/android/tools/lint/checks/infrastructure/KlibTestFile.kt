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
package com.android.tools.lint.checks.infrastructure

import java.io.InputStream

// TODO(b/292298949)
class KlibTestFile(to : String, val encoded : String?) : TestFile() {
  private val files = mutableListOf<TestFile>()

  init {
    to(to)
    from("/", object : TestResourceProvider {
      override fun getTestResource(relativePath: String, expectExists: Boolean) = InputStream.nullInputStream()
    })
  }

  fun files(vararg files : TestFile) : KlibTestFile = this.also { this.files.addAll(files) }
}
