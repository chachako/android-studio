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

package kexter.tools

import com.android.zipflinger.ZipRepo
import kexter.Dex
import kexter.DexDumper
import kexter.Logger

class ApkDumper {
  companion object {

    @JvmStatic
    fun main(arg: Array<String>) {
      if (arg.isEmpty()) {
        println("No apk given as parameter. Aborting.")
        return
      }
      dump(arg[0])
    }

    private fun dump(path: String, logger: Logger = Logger()) {
      ZipRepo(path).use { repo ->
        repo.entries.values.forEach { entry ->
          if (!entry.name.endsWith(".dex")) {
            return@forEach
          }
          logger.info("Dex file: ${entry.name}")
          val dexFile = repo.getContent(entry.name).array()
          val dex = Dex.fromBytes(dexFile)
          DexDumper.dump(dex, logger)
        }
      }
    }
  }
}
