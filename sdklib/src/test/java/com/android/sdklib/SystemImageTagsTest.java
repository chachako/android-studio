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
package com.android.sdklib;

import static com.android.sdklib.SystemImageTags.ANDROID_TV_TAG;
import static com.android.sdklib.SystemImageTags.DEFAULT_TAG;
import static com.android.sdklib.SystemImageTags.GOOGLE_APIS_TAG;
import static com.android.sdklib.SystemImageTags.GOOGLE_APIS_X86_TAG;
import static com.android.sdklib.SystemImageTags.GOOGLE_TV_TAG;
import static com.android.sdklib.SystemImageTags.PLAY_STORE_TAG;
import static com.android.sdklib.SystemImageTags.TABLET_TAG;
import static com.android.sdklib.SystemImageTags.WEAR_TAG;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.google.common.collect.ImmutableList;
import org.junit.Test;

public class SystemImageTagsTest {
    @Test
    public void hasGoogleApi() {
        assertTrue(SystemImageTags.hasGoogleApi(ImmutableList.of(GOOGLE_APIS_TAG)));
        assertTrue(SystemImageTags.hasGoogleApi(ImmutableList.of(PLAY_STORE_TAG)));
        assertTrue(SystemImageTags.hasGoogleApi(ImmutableList.of(TABLET_TAG, GOOGLE_APIS_TAG)));
        assertTrue(
                SystemImageTags.hasGoogleApi(
                        ImmutableList.of(TABLET_TAG, GOOGLE_APIS_TAG, PLAY_STORE_TAG)));
        assertTrue(SystemImageTags.hasGoogleApi(ImmutableList.of(ANDROID_TV_TAG)));
        assertTrue(SystemImageTags.hasGoogleApi(ImmutableList.of(GOOGLE_TV_TAG)));
        assertTrue(SystemImageTags.hasGoogleApi(ImmutableList.of(WEAR_TAG)));
        assertTrue(SystemImageTags.hasGoogleApi(ImmutableList.of(GOOGLE_APIS_X86_TAG)));

        assertFalse(SystemImageTags.hasGoogleApi(ImmutableList.of(DEFAULT_TAG)));
        assertFalse(SystemImageTags.hasGoogleApi(ImmutableList.of(TABLET_TAG)));
        // Google APIs is implied by Play store
        assertTrue(SystemImageTags.hasGoogleApi(ImmutableList.of(TABLET_TAG, PLAY_STORE_TAG)));
        assertTrue(SystemImageTags.hasGoogleApi(ImmutableList.of(WEAR_TAG, PLAY_STORE_TAG)));
    }
}
