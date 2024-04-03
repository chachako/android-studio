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
package com.android.ddmlib;

import junit.framework.TestCase;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Unit tests for {@link SplitApkInstallerBase}. */
@RunWith(JUnit4.class)
public class InstallerSanitizerTest extends TestCase {
    @Test
    public void testSanitization() {
        String problematicName = "file wi+h space And ( and ]0123456789.apk";
        String expected = "file_wi_h_space_And___and__0123456789.apk";
        String actual = SplitApkInstallerBase.sanitizeApkFilename(problematicName);
        Assert.assertEquals("Unexpected install-write sanitization", expected, actual);
    }
}
