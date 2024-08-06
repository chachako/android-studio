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

package com.android.backup

enum class ErrorCode {
  CANNOT_ENABLE_BMGR,
  TRANSPORT_NOT_SELECTED,
  TRANSPORT_INIT_FAILED,
  GMSCORE_NOT_FOUND,
  @Suppress("unused") // Will use when testing for GmsCore version is implemented
  GMSCORE_IS_TOO_OLD,
  BACKUP_FAILED,
  RESTORE_FAILED,
  INVALID_BACKUP_FILE,
  PLAY_STORE_NOT_INSTALLED,
  UNEXPECTED_ERROR,
}
