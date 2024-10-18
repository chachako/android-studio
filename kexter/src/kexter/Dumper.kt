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

package kexter

import java.nio.file.Path
import java.nio.file.Paths

class DexDumper {
  companion object {
    fun dumpDexFromString(path: String, logger: Logger = Logger()) {
      dumpDexFromPath(Paths.get(path), logger)
    }

    fun dumpDexFromPath(path: Path, logger: Logger = Logger()) {
      val dex = Dex.fromPath(path, logger)
      dump(dex, logger)
    }

    fun dump(dex: Dex, logger: Logger = Logger(), prefix: String = "") {
      dex.classes.values.forEach { clazz ->
        logger.debug("${prefix}${clazz.name}")
        clazz.methods.values.forEach { m ->
          logger.debug("$prefix   ${m.name}(${m.shorty}) returnType=${m.returnType}")

          val bc = m.byteCode
          logger.debug("$prefix        Instructions:(${bc.instructions.size})")
          bc.instructions.forEach { i ->
            logger.debug(
              "$prefix        ${i.opcode.name} ${i.opcode.toHex()} ${i.payload.joinToString( ",", "[", "]") { i -> "0x%02x".format(i) }}"
            )
          }
          logger.debug("")

          logger.debug("$prefix        LineTable (${bc.debugInfo.lineTable.size}):")
          bc.debugInfo.lineTable.forEach { lt ->
            logger.debug("$prefix        [idx=${lt.index}, ln${lt.lineNumber}]")
          }
          logger.debug("")
        }
      }
    }
  }
}
