/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android;

import static junit.framework.TestCase.assertTrue;
import static org.junit.Assert.assertFalse;

import com.android.testutils.TestUtils;
import com.google.common.truth.Truth;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Properties;
import java.util.regex.Pattern;
import org.junit.Test;

public class SdkConstantsTest {

    @Test
    public void testRegexClassesDex() {
        Pattern pattern = Pattern.compile(SdkConstants.REGEX_APK_CLASSES_DEX);

        assertTrue(pattern.matcher("classes.dex").matches());
        assertTrue(pattern.matcher("classes0.dex").matches());
        assertTrue(pattern.matcher("classes101.dex").matches());
        assertFalse(pattern.matcher("classesA.dex").matches());
    }

    // Ensure the AGP versions are the same version set in the supported-version.properties
    // file for consistency.
    @Test
    public void testAGPVersionAlignmentWithSupportVersionProperties() throws Exception {
        Path dir = TestUtils.resolveWorkspacePath("tools/base/build-system");
        // Loading the property file to compare with AGP versions
        Properties property = new Properties();
        try (FileInputStream inputStream =
                new FileInputStream(dir.resolve("supported-versions.properties").toFile())) {
            property.load(inputStream);
        } catch (IOException e) {
            throw new Exception("Error loading properties file: " + e.getMessage());
        }
        String gradleMinimum = property.getProperty("gradle_minimum");
        String buildToolsMinimum = property.getProperty("build_tools_minimum");
        String ndkDefault = property.getProperty("ndk_default");

        Path propertyFilePath = dir.resolve("supported-versions.properties");

        assertVersionConsistency(
                gradleMinimum,
                SdkConstants.GRADLE_LATEST_VERSION,
                property.get("gradle_name"),
                propertyFilePath);
        assertVersionConsistency(
                buildToolsMinimum,
                SdkConstants.CURRENT_BUILD_TOOLS_VERSION,
                property.get("build_tools_name"),
                propertyFilePath);
        assertVersionConsistency(
                ndkDefault,
                SdkConstants.NDK_DEFAULT_VERSION,
                property.get("ndk_name"),
                propertyFilePath);
    }

    private void assertVersionConsistency(
            String actual, String expected, Object propertyName, Path propertiesFile) {
        String updatePropertiesMsg =
                String.format(
                        " The version of the %s property in '%s' (a version source for developers.android.com) is not consistent. The version in the property file is %s, has been changed to %s elsewhere. Consider updating the %s to %s, if this change is intended.",
                        propertyName, propertiesFile, expected, actual, propertyName, actual);
        Truth.assertWithMessage(updatePropertiesMsg).that(actual).isEqualTo(expected);
    }
}
