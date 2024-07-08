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

class Logger {
  fun debug(message: String) {
    println("Debug $message")
  }

  fun info(message: String) {
    println("Info $message")
  }

  fun warn(message: String) {
    println("Warn $message")
  }

  fun error(message: String) {
    println("Error $message")
    throw IllegalStateException(message)
  }

  fun error(message: String, throwable: Throwable) {
    println("Error $message")
    throw throwable
  }
}
