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
package com.android.tools.lint.checks

import com.android.tools.lint.checks.infrastructure.ProjectDescription
import com.android.tools.lint.checks.infrastructure.TestFiles

class CredentialManagerDigitalAssetLinkDetectorTest : AbstractCheckTest() {
  override fun getDetector() = CredentialManagerDigitalAssetLinkDetector()

  fun testDocumentationExample() {
    lint()
      .projects(
        STUB_CREDENTIAL_MANAGER_LIB_PROJECT,
        project()
          .type(ProjectDescription.Type.APP)
          .name("app")
          .dependsOn(STUB_CREDENTIAL_MANAGER_LIB_PROJECT)
          .files(
            // We need there to be an <application> element, as this is where we report the missing
            // meta-data element.
            manifest(
                """
                <manifest package="com.example.app" xmlns:android="http://schemas.android.com/apk/res/android">
                  <uses-sdk android:minSdkVersion="33" android:targetSdkVersion="34" />
                  <application>
                    <activity android:name=".MainActivity" android:exported="true">
                      <intent-filter>
                        <action android:name="android.intent.action.MAIN" />
                        <category android:name="android.intent.category.LAUNCHER" />
                      </intent-filter>
                    </activity>
                  </application>
                </manifest>
                """
              )
              .indented(),
            kotlin(
                """
                package com.example.app

                import androidx.credentials.CreatePasswordRequest

                fun foo() {
                  val createPasswordRequest = CreatePasswordRequest("user", "pass")
                }
                """
              )
              .indented(),
          ),
      )
      .issues(CredentialManagerDigitalAssetLinkDetector.ISSUE)
      .run()
      .expect(
        """
        AndroidManifest.xml:3: Error: Missing <meta-data> tag for asset statements for Credential Manager [CredManMissingDal]
          <application>
           ~~~~~~~~~~~
        1 errors, 0 warnings
        """
      )
  }

  fun testMissingMetaDataMultiModule() {
    // In this test, the use of CreatePasswordRequest is in a library module.
    lint()
      .projects(
        STUB_CREDENTIAL_MANAGER_LIB_PROJECT,
        project()
          .type(ProjectDescription.Type.LIBRARY)
          .dependsOn(STUB_CREDENTIAL_MANAGER_LIB_PROJECT)
          .name("lib")
          .files(
            kotlin(
                """
                package com.example.app

                import androidx.credentials.CreatePasswordRequest

                fun foo() {
                  val createPasswordRequest = CreatePasswordRequest("user", "pass")
                }
                """
              )
              .indented()
          ),
        project()
          .type(ProjectDescription.Type.APP)
          .name("app")
          .dependsOn("lib")
          .files(
            manifest(
                """
                <manifest package="com.example.app" xmlns:android="http://schemas.android.com/apk/res/android">
                  <uses-sdk android:minSdkVersion="33" android:targetSdkVersion="34" />
                  <application>
                    <activity android:name=".MainActivity" android:exported="true">
                      <intent-filter>
                        <action android:name="android.intent.action.MAIN" />
                        <category android:name="android.intent.category.LAUNCHER" />
                      </intent-filter>
                    </activity>
                  </application>
                </manifest>
                """
              )
              .indented()
          ),
      )
      .issues(CredentialManagerDigitalAssetLinkDetector.ISSUE)
      .run()
      .expect(
        """
        AndroidManifest.xml:3: Error: Missing <meta-data> tag for asset statements for Credential Manager [CredManMissingDal]
          <application>
           ~~~~~~~~~~~
        1 errors, 0 warnings
        """
      )
  }

  fun testMissingResourceAttribute() {
    lint()
      .projects(
        STUB_CREDENTIAL_MANAGER_LIB_PROJECT,
        project()
          .type(ProjectDescription.Type.APP)
          .name("app")
          .dependsOn(STUB_CREDENTIAL_MANAGER_LIB_PROJECT)
          .files(
            manifest(
              """
              <manifest package="com.example.app" xmlns:android="http://schemas.android.com/apk/res/android">
                <uses-sdk android:minSdkVersion="33" android:targetSdkVersion="34" />
                <application>
                  <meta-data android:name="asset_statements" />
                  <activity android:name=".MainActivity" android:exported="true">
                    <intent-filter>
                      <action android:name="android.intent.action.MAIN" />
                      <category android:name="android.intent.category.LAUNCHER" />
                    </intent-filter>
                  </activity>
                </application>
              </manifest>
              """
            ),
            kotlin(
                """
                package com.example.app

                import androidx.credentials.CreatePasswordRequest

                fun foo() {
                  val createPasswordRequest = CreatePasswordRequest("user", "pass")
                }
                """
              )
              .indented(),
          ),
      )
      .issues(CredentialManagerDigitalAssetLinkDetector.ISSUE)
      .run()
      .expect(
        """
        AndroidManifest.xml:5: Error: Missing android:resource attribute for asset statements string resource [CredManMissingDal]
                          <meta-data android:name="asset_statements" />
                          ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
        1 errors, 0 warnings
        """
      )
  }

  fun testMissingInclude() {
    lint()
      .projects(
        STUB_CREDENTIAL_MANAGER_LIB_PROJECT,
        project()
          .type(ProjectDescription.Type.APP)
          .name("app")
          .dependsOn(STUB_CREDENTIAL_MANAGER_LIB_PROJECT)
          .files(
            xml(
                "res/values/strings.xml",
                """
                <resources>
                  <string name="myAssetStatements">
                  [{
                  }]
                  </string>
                </resources>
                """,
              )
              .indented(),
            manifest(
              """
              <manifest package="com.example.app" xmlns:android="http://schemas.android.com/apk/res/android">
                <uses-sdk android:minSdkVersion="33" android:targetSdkVersion="34" />
                <application>
                  <meta-data android:name="asset_statements" android:resource="@string/myAssetStatements" />
                  <activity android:name=".MainActivity" android:exported="true">
                    <intent-filter>
                      <action android:name="android.intent.action.MAIN" />
                      <category android:name="android.intent.category.LAUNCHER" />
                    </intent-filter>
                  </activity>
                </application>
              </manifest>
              """
            ),
            kotlin(
                """
                package com.example.app

                import androidx.credentials.CreatePasswordRequest

                fun foo() {
                  val createPasswordRequest = CreatePasswordRequest("user", "pass")
                }
                """
              )
              .indented(),
          ),
      )
      .issues(CredentialManagerDigitalAssetLinkDetector.ISSUE)
      .run()
      .expect(
        """
        AndroidManifest.xml:5: Error: Could not find "include" in asset statements string resource [CredManMissingDal]
                          <meta-data android:name="asset_statements" android:resource="@string/myAssetStatements" />
                          ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
        1 errors, 0 warnings
        """
      )
  }

  fun testMissingUrl() {
    lint()
      .projects(
        STUB_CREDENTIAL_MANAGER_LIB_PROJECT,
        project()
          .type(ProjectDescription.Type.APP)
          .name("app")
          .dependsOn(STUB_CREDENTIAL_MANAGER_LIB_PROJECT)
          .files(
            xml(
                "res/values/strings.xml",
                """
                <resources>
                  <string name="myAssetStatements">
                  [{
                    \"include\": \"https://signin.example.com/assetlinks.json\"
                  }]
                  </string>
                </resources>
                """,
              )
              .indented(),
            manifest(
              """
              <manifest package="com.example.app" xmlns:android="http://schemas.android.com/apk/res/android">
                <uses-sdk android:minSdkVersion="33" android:targetSdkVersion="34" />
                <application>
                  <meta-data android:name="asset_statements" android:resource="@string/myAssetStatements" />
                  <activity android:name=".MainActivity" android:exported="true">
                    <intent-filter>
                      <action android:name="android.intent.action.MAIN" />
                      <category android:name="android.intent.category.LAUNCHER" />
                    </intent-filter>
                  </activity>
                </application>
              </manifest>
              """
            ),
            kotlin(
                """
                package com.example.app

                import androidx.credentials.CreatePasswordRequest

                fun foo() {
                  val createPasswordRequest = CreatePasswordRequest("user", "pass")
                }
                """
              )
              .indented(),
          ),
      )
      .issues(CredentialManagerDigitalAssetLinkDetector.ISSUE)
      .run()
      .expect(
        """
        AndroidManifest.xml:5: Error: Could not find .well-known/assetlinks.json in asset statements string resource [CredManMissingDal]
                          <meta-data android:name="asset_statements" android:resource="@string/myAssetStatements" />
                          ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
        1 errors, 0 warnings
        """
      )
  }

  fun testCorrectLink() {
    lint()
      .projects(
        STUB_CREDENTIAL_MANAGER_LIB_PROJECT,
        project()
          .type(ProjectDescription.Type.APP)
          .name("app")
          .dependsOn(STUB_CREDENTIAL_MANAGER_LIB_PROJECT)
          .files(
            xml(
                "res/values/strings.xml",
                """
                <resources>
                  <string name="myAssetStatements">
                  [{
                    \"include\": \"https://signin.example.com/.well-known/assetlinks.json\"
                  }]
                  </string>
                </resources>
                """,
              )
              .indented(),
            manifest(
              """
              <manifest package="com.example.app" xmlns:android="http://schemas.android.com/apk/res/android">
                <uses-sdk android:minSdkVersion="33" android:targetSdkVersion="34" />
                <application>
                  <meta-data android:name="asset_statements" android:resource="@string/myAssetStatements" />
                  <activity android:name=".MainActivity" android:exported="true">
                    <intent-filter>
                      <action android:name="android.intent.action.MAIN" />
                      <category android:name="android.intent.category.LAUNCHER" />
                    </intent-filter>
                  </activity>
                </application>
              </manifest>
              """
            ),
            kotlin(
                """
                package com.example.app

                import androidx.credentials.CreatePasswordRequest

                fun foo() {
                  val createPasswordRequest = CreatePasswordRequest("user", "pass")
                }
                """
              )
              .indented(),
          ),
      )
      .issues(CredentialManagerDigitalAssetLinkDetector.ISSUE)
      .run()
      .expectClean()
  }
}

private val STUB_CREDENTIAL_MANAGER_LIB_PROJECT =
  ProjectDescription()
    .files(
      TestFiles.kotlin(
          """
          package androidx.credentials

          /*HIDE-FROM-DOCUMENTATION*/

          class CreatePasswordRequest(id: String, password: String)
          """
        )
        .indented()
    )
    .type(ProjectDescription.Type.LIBRARY)
    .name("CredMan")
