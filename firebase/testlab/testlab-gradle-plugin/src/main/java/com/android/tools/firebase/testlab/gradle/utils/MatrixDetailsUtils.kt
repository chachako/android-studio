/*
 * Copyright (C) 2022 The Android Open Source Project
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

package com.android.tools.firebase.testlab.gradle.services

internal fun getInvalidMatrixDetailsErrorMessage(invalidMatrixDetailsEnumValue: String) =
    when(invalidMatrixDetailsEnumValue) {
        "MALFORMED_APK" -> "The input app APK could not be parsed."
        "MALFORMED_TEST_APK" -> "The input test APK could not be parsed."
        "NO_MANIFEST" -> "The AndroidManifest.xml could not be found."
        "NO_PACKAGE_NAME" -> "The APK manifest does not declare a package name."
        "INVALID_PACKAGE_NAME" -> "The APK application ID (aka package name) is invalid. See also https://developer.android.com/studio/build/application-id"
        "TEST_SAME_AS_APP" -> "The test package and app package are the same."
        "NO_INSTRUMENTATION" -> "The test apk does not declare an instrumentation."
        "NO_SIGNATURE" -> "The input app apk does not have a signature."
        "INSTRUMENTATION_ORCHESTRATOR_INCOMPATIBLE" -> "The test runner class specified by user or in the test APK's manifest file is not compatible with Android Test Orchestrator. Orchestrator is only compatible with AndroidJUnitRunner version 1.1 or higher. Orchestrator can be disabled by using DO_NOT_USE_ORCHESTRATOR OrchestratorOption."
        "NO_TEST_RUNNER_CLASS" -> "The test APK does not contain the test runner class specified by user or in the manifest file. This can be caused by either of the following reasons: - the user provided a runner class name that's incorrect, or - the test runner isn't built into the test APK (might be in the app APK instead)."
        "NO_LAUNCHER_ACTIVITY" -> "A main launcher activity could not be found."
        "FORBIDDEN_PERMISSIONS" -> "The app declares one or more permissions that are not allowed."
        "INVALID_ROBO_DIRECTIVES" -> "There is a conflict in the provided roboDirectives."
        "INVALID_RESOURCE_NAME" -> "There is at least one invalid resource name in the provided robo directives"
        "INVALID_DIRECTIVE_ACTION" -> "Invalid definition of action in the robo directives (e.g. a click or ignore action includes an input text field)"
        "TEST_LOOP_INTENT_FILTER_NOT_FOUND" -> "There is no test loop intent filter, or the one that is given is not formatted correctly."
        "SCENARIO_LABEL_NOT_DECLARED" -> "The request contains a scenario label that was not declared in the manifest."
        "SCENARIO_LABEL_MALFORMED" -> "There was an error when parsing a label's value."
        "SCENARIO_NOT_DECLARED" -> "The request contains a scenario number that was not declared in the manifest."
        "DEVICE_ADMIN_RECEIVER" -> "Device administrator applications are not allowed."
        "MALFORMED_XC_TEST_ZIP" -> "The zipped XCTest was malformed. The zip did not contain a single .xctestrun file and the contents of the DerivedData/Build/Products directory."
        "BUILT_FOR_IOS_SIMULATOR" -> "The zipped XCTest was built for the iOS simulator rather than for a physical device."
        "NO_TESTS_IN_XC_TEST_ZIP" -> "The .xctestrun file did not specify any test targets."
        "USE_DESTINATION_ARTIFACTS" -> "One or more of the test targets defined in the .xctestrun file specifies \"UseDestinationArtifacts\", which is disallowed."
        "TEST_NOT_APP_HOSTED" -> "XC tests which run on physical devices must have \"IsAppHostedTestBundle\" == \"true\" in the xctestrun file."
        "PLIST_CANNOT_BE_PARSED" -> "An Info.plist file in the XCTest zip could not be parsed."
        "MALFORMED_IPA" -> "The input IPA could not be parsed."
        "MISSING_URL_SCHEME" -> "The application doesn't register the game loop URL scheme."
        "MALFORMED_APP_BUNDLE" -> "The iOS application bundle (.app) couldn't be processed."
        "NO_CODE_APK" -> "APK contains no code. See also https://developer.android.com/guide/topics/manifest/application-element.html#code"
        "INVALID_INPUT_APK" -> "Either the provided input APK path was malformed, the APK file does not exist, or the user does not have permission to access the APK file."
        "INVALID_APK_PREVIEW_SDK" -> "APK is built for a preview SDK which is unsupported"
        else -> "The matrix is INVALID, but there are no further details available."
    }
