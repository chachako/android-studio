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
package com.android.tools.lint.client.api

import com.android.tools.lint.detector.api.Project
import com.android.tools.lint.model.PathVariables
import java.io.File

class PrettyPaths {
  companion object {
    private fun String.isParentDirectoryPath() = startsWith("..")

    /**
     * Given a file, return a representation of its path according to the given preferences.
     *
     * @param useUnixPaths should all paths be converted to Unix paths (file separator / instead of
     *   \ on Windows) ?
     * @param tryPathVariables should we try to match path strings with path variables?
     * @param preferRelativePathOverPathVariables for paths outside the project, should we prefer
     *   the project path over the absolute path with path variables?
     * @param allowParentRelativePaths should we use paths starting with ../ ?
     * @param preferRelativeOverAbsolute should relative paths be used instead of absolute paths,
     *   when path variables aren't used?
     */
    fun getPath(
      file: File,
      project: Project?,
      client: LintClient,
      useUnixPaths: Boolean,
      tryPathVariables: Boolean,
      pathVariables: PathVariables = client.pathVariables,
      preferRelativePathOverPathVariables: Boolean,
      allowParentRelativePaths: Boolean,
      preferRelativeOverAbsolute: Boolean,
    ): String {
      var path: String? = null

      if (tryPathVariables && pathVariables.any()) {
        if (preferRelativePathOverPathVariables && project != null) {
          val relativePath = client.getDisplayPath(project, file, fullPath = false)
          if (!relativePath.isParentDirectoryPath() || allowParentRelativePaths) {
            path = relativePath
          }
        }

        if (path == null) {
          path = pathVariables.toPathStringIfMatched(file, unix = useUnixPaths)

          if (path != null && PathVariables.startsWithVariable(path, "HOME")) {
            // Don't match $HOME if the location is inside the current project -- that just means
            // the project is under $HOME, which is pretty likely
            // (We do want to include HOME such that we pick up a relative location to files
            // outside of the project, such as (say ~/.android)
            val relativePath = client.getDisplayPath(project, file, fullPath = false)
            if (!relativePath.isParentDirectoryPath()) {
              path = relativePath
            }
          }
        }
      }

      if (path == null) {
        path = client.getDisplayPath(project, file, fullPath = !preferRelativeOverAbsolute)
      }

      return if (useUnixPaths) path.replace('\\', '/') else path
    }
  }
}
