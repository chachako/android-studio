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
package com.android.tools.lint.checks.studio

import com.android.tools.lint.checks.infrastructure.TestFiles.java
import com.android.tools.lint.checks.infrastructure.TestFiles.xml
import org.junit.Test

class PluginXmlDetectorTest {

  @Test
  fun testUnresolvedExtensionClasses() {
    studioLint()
      .files(
        java("package test.pkg;\nclass SomeClassA {}").indented(),
        java("package test.pkg;\nclass SomeClassB {}").indented(),
        @Suppress("PluginXmlValidity", "PluginXmlCapitalization")
        xml(
            "res/META-INF/android-plugin.xml",
            """
            <idea-plugin>
              <actions>
                <action id="Android.SomeId" class="test.pkg.SomeClassA"/>
                <action id="Android.SomeId" class="test.pkg.MissingClassA"/>
              </actions>
              <extensions defaultExtensionNs="com.intellij">
                <projectService serviceImplementation="test.pkg.SomeClassB"/>
                <projectService serviceImplementation="test.pkg.MissingClassB"/>
              </extensions>
            </idea-plugin>
            """
          )
          .indented()
      )
      .issues(PluginXmlDetector.ISSUE)
      .run()
      .expect(
        """
        javalib: Error: Class MissingClassA not found in the current module or its dependencies [PluginXmlUnresolvedClass]
        javalib: Error: Class MissingClassB not found in the current module or its dependencies [PluginXmlUnresolvedClass]
        2 errors, 0 warnings
        """
      )
  }
}
