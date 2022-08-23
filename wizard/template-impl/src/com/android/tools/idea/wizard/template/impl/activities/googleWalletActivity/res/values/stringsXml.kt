/*
 * Copyright (C) 2021 The Android Open Source Project
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

package com.android.tools.idea.wizard.template.impl.activities.googleWalletActivity.res.values

fun stringsXml(
  activityTitle: String,
  simpleName: String
) = """
<resources>

    <!-- Wallet activity -->
    <string name="title_${simpleName}">${activityTitle}</string>
    <string name="add_to_google_wallet_button_content_description">Add to Google Wallet</string>
    <string name="sample_pass" translatable="false">Sample Pass</string>
    <string name="google_wallet_status_unavailable" translatable="false">Unfortunately, Google Wallet is not available on this phone.</string>
    <string name="add_google_wallet_success" translatable="false">The pass was added successfully!</string>
    <string name="add_google_wallet_cancelled" translatable="false">The user cancelled adding the pass to Google Wallet.</string>
    <string name="add_google_wallet_unknown_error" translatable="false">An unknown error occurred when adding pass to Google Wallet.</string>
</resources>
"""
