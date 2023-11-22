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

package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Detector

class SecretDetectorTest : AbstractCheckTest() {

  private val generativeModelStubKt =
    kotlin(
        """
          package com.google.ai.client.generativeai

          /*HIDE-FROM-DOCUMENTATION*/

          class GenerativeModel(val modelName: String, val apiKey: String)
        """
      )
      .indented()

  override fun getDetector(): Detector {
    return SecretDetector()
  }

  fun testDocumentationExample() {
    lint()
      .files(
        kotlin(
            """
              package com.pkg.keydemo

              import com.google.ai.client.generativeai.GenerativeModel

              val KEY = "AIzadGhpcyBpcyBhbm90aGVy_IHQ-akd=="

              fun foo(extra: String) {
                val model1 = GenerativeModel("name", KEY)
                val model2 = GenerativeModel("name", "AIzadGhpcyBpcyBhbm90aGVy_IHQ-akd==")
              }
          """
          )
          .indented(),
        generativeModelStubKt,
      )
      .run()
      .expect(
        """
          src/com/pkg/keydemo/test.kt:8: Warning: This argument looks like an API key that has come from source code; API keys should not be included in source code [SecretInSource]
            val model1 = GenerativeModel("name", KEY)
                                                 ~~~
          src/com/pkg/keydemo/test.kt:9: Warning: This argument looks like an API key that has come from source code; API keys should not be included in source code [SecretInSource]
            val model2 = GenerativeModel("name", "AIzadGhpcyBpcyBhbm90aGVy_IHQ-akd==")
                                                  ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
          0 errors, 2 warnings
          """
      )
  }

  fun testKeys() {
    lint()
      .files(
        // This makes the project look like a Gradle project; the check will not report the use of a
        // field from a different file unless the project looks like a Gradle project because the
        // check tries to see whether the field is in a generated file.
        kts(""),
        generativeModelStubKt,
        java(
            """
                package com.pkg.keydemo;

                public class JKeys {
                  public static final String KEY = "AIzaYWJjZGVmZ2hp-MTIzNDU2-YX_ZGQ";

                  @SuppressWarnings("SecretInSource")
                  public static final String OK_KEY = "AIzaYWJjZGVmZ2hp-MTIzNDU2-YX_ZGQ";

                }
                """
          )
          .indented(),
        java(
            """
                package com.pkg.keydemo;

                import com.google.ai.client.generativeai.GenerativeModel;

                public class KeyDemo {
                  public static final String KEY = "AIzaYWJjZGVmZ2hp-MTIzNDU2-YX_ZGQ";

                  public void hello() {
                    GenerativeModel model1 = new GenerativeModel("name", "AIzaYWJjZGVmZ2hp-MTIzNDU2-YX_ZGQ");
                    GenerativeModel model2 = new GenerativeModel("name", "NOT_A_REAL_KEY");
                    GenerativeModel model3 = new GenerativeModel("name", KEY);
                    GenerativeModel model4 = new GenerativeModel("name", JKeys.KEY);
                    GenerativeModel model5 = new GenerativeModel("name", JKeys.OK_KEY);
                    String s = "AIzaYWJjZGVmZ2hp-MTIzNDU2-YX_ZGQ";
                    GenerativeModel model6 = new GenerativeModel("name", s);
                  }
                }
                """
          )
          .indented(),
        kotlin(
            """
              package com.pkg.keydemo

              val KEY_TOP = "AIzadGhpcyBpcyBhbm90aGVy_IHQ-akd=="

              @Suppress("SecretInSource")
              val KEY_TOP_OK = "AIzadGhpcyBpcyBhbm90aGVy_IHQ-akd=="

              class Keys {
                companion object {
                  const val KEY = "AIzadGhpcyBpcyBhbm90aGVy_IHQ-akd=="

                  @Suppress("SecretInSource")
                  const val KEY_OK = "AIzadGhpcyBpcyBhbm90aGVy_IHQ-akd=="
                }
              }
          """
          )
          .indented(),
        kotlin(
            """
              package com.pkg.keydemo

              import com.google.ai.client.generativeai.GenerativeModel

              val KEY = "AIzadGhpcyBpcyBhbm90aGVy_IHQ-akd=="

              fun foo(extra: String) {
                val model1 = GenerativeModel("name", "AIzadGhpcyBpcyBhbm90aGVy_IHQ-akd==")
                val model2 = GenerativeModel("name", "NOT_A_REAL_KEY")
                val model3 = GenerativeModel("name", KEY)
                val model4 = GenerativeModel("name", KEY_TOP)
                val model5 = GenerativeModel("name", KEY_TOP_OK)
                val model6 = GenerativeModel("name", Keys.KEY)
                val model7 = GenerativeModel("name", Keys.KEY_OK)
                val s = "AIzadGhpcyBpcyBhbm90aGVy_IHQ-akd=="
                val model8 = GenerativeModel("name", s)
              }
          """
          )
          .indented(),
      )
      .run()
      .expect(
        """
          src/com/pkg/keydemo/KeyDemo.java:9: Warning: This argument looks like an API key that has come from source code; API keys should not be included in source code [SecretInSource]
              GenerativeModel model1 = new GenerativeModel("name", "AIzaYWJjZGVmZ2hp-MTIzNDU2-YX_ZGQ");
                                                                   ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
          src/com/pkg/keydemo/KeyDemo.java:11: Warning: This argument looks like an API key that has come from source code; API keys should not be included in source code [SecretInSource]
              GenerativeModel model3 = new GenerativeModel("name", KEY);
                                                                   ~~~
          src/com/pkg/keydemo/KeyDemo.java:12: Warning: This argument looks like an API key that has come from source code; API keys should not be included in source code [SecretInSource]
              GenerativeModel model4 = new GenerativeModel("name", JKeys.KEY);
                                                                   ~~~~~~~~~
          src/com/pkg/keydemo/KeyDemo.java:15: Warning: This argument looks like an API key that has come from source code; API keys should not be included in source code [SecretInSource]
              GenerativeModel model6 = new GenerativeModel("name", s);
                                                                   ~
          src/com/pkg/keydemo/test.kt:8: Warning: This argument looks like an API key that has come from source code; API keys should not be included in source code [SecretInSource]
            val model1 = GenerativeModel("name", "AIzadGhpcyBpcyBhbm90aGVy_IHQ-akd==")
                                                  ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
          src/com/pkg/keydemo/test.kt:10: Warning: This argument looks like an API key that has come from source code; API keys should not be included in source code [SecretInSource]
            val model3 = GenerativeModel("name", KEY)
                                                 ~~~
          src/com/pkg/keydemo/test.kt:11: Warning: This argument looks like an API key that has come from source code; API keys should not be included in source code [SecretInSource]
            val model4 = GenerativeModel("name", KEY_TOP)
                                                 ~~~~~~~
          src/com/pkg/keydemo/test.kt:13: Warning: This argument looks like an API key that has come from source code; API keys should not be included in source code [SecretInSource]
            val model6 = GenerativeModel("name", Keys.KEY)
                                                 ~~~~~~~~
          src/com/pkg/keydemo/test.kt:16: Warning: This argument looks like an API key that has come from source code; API keys should not be included in source code [SecretInSource]
            val model8 = GenerativeModel("name", s)
                                                 ~
          0 errors, 9 warnings
        """
      )
  }
}
