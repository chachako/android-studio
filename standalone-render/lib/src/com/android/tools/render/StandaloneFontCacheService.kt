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

package com.android.tools.render

import com.android.tools.fonts.DownloadableFontCacheServiceImpl
import com.android.tools.fonts.FontDownloader
import java.io.File

/** [DownloadableFontCacheService] that can't download the fonts but can fetch them from the sdk. */
internal class StandaloneFontCacheService(sdkPath: String) :
    DownloadableFontCacheServiceImpl(FontDownloader.NOOP_FONT_DOWNLOADER, { File(sdkPath) })
