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
package com.android.tools.idea.wizard.template.impl.activities.googleWalletActivity.app_package

import com.android.tools.idea.wizard.template.escapeKotlinIdentifier

fun samplePassKt(
    packageName: String
): String {
  return """
package ${escapeKotlinIdentifier(packageName)}

import java.util.Date
import kotlin.random.Random

/**
 * A helper class that allows us to easily configure the JSON we will be sending to
 * [PayClient.savePasses].
 *
 * If you are using the [temporary issuer](https://wallet-lab-tools.web.app/issuers) tool, then you
 * can use this class with the parameters created on the linked website.
 */
data class SamplePass(
    private val issuerEmail: String,
    private val issuerId: String,
    private val passClass: String,
    private val passId: String
) {
    val toJson: String = ""${'"'}
    {
      "iss": "${'$'}issuerEmail",
      "aud": "google",
      "typ": "savetowallet",
      "iat": ${'$'}{Date().time / 1000L},
      "origins": [],
      "payload": {
        "genericObjects": [
          {
            "id": "${'$'}issuerId.${'$'}passId",
            "classId": "${'$'}passClass",
            "genericType": "GENERIC_TYPE_UNSPECIFIED",
            "hexBackgroundColor": "#4285f4",
            "logo": {
              "sourceUri": {
                "uri": "https://storage.googleapis.com/wallet-lab-tools-codelab-artifacts-public/pass_google_logo.jpg"
              }
            },
            "cardTitle": {
              "defaultValue": {
                "language": "en",
                "value": "Google I/O '22  [DEMO ONLY]"
              }
            },
            "subheader": {
              "defaultValue": {
                "language": "en",
                "value": "Attendee"
              }
            },
            "header": {
              "defaultValue": {
                "language": "en",
                "value": "Nicholas Corder"
              }
            },
            "barcode": {
              "type": "QR_CODE",
              "value": "${'$'}passId"
            },
            "heroImage": {
              "sourceUri": {
                "uri": "https://storage.googleapis.com/wallet-lab-tools-codelab-artifacts-public/google-io-hero-demo-only.jpg"
              }
            },
            "textModulesData": [
              {
                "header": "POINTS",
                "body": "${'$'}{Random.nextInt(0, 9999)}",
                "id": "points"
              },
              {
                "header": "CONTACTS",
                "body": "${'$'}{Random.nextInt(1, 99)}",
                "id": "contacts"
              }
            ]
          }
        ]
      }
    }
    ""${'"'}
}
"""
}
