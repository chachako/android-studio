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

import com.android.tools.idea.wizard.template.Language
import com.android.tools.idea.wizard.template.impl.activities.common.importViewBindingClass
import com.android.tools.idea.wizard.template.impl.activities.common.layoutToViewBindingClass
import com.android.tools.idea.wizard.template.renderIf

fun walletActivityJava(
  activityClass: String,
  layoutName: String,
  packageName: String,
  applicationPackage: String?,
  isViewBindingSupported: Boolean
): String {

  val contentViewBlock = if (isViewBindingSupported)
    """layout = ${layoutToViewBindingClass(layoutName)}.inflate(getLayoutInflater());
     setContentView(layout.getRoot());
  """ else "setContentView(R.layout.$layoutName);"

  val addToWalletButtonBlock = if (isViewBindingSupported)
    "addToWalletButton = layout.addToGoogleWalletButton.getRoot();"
  else "addToWalletButton = findViewById(R.id.addToGoogleWalletButton);"

  return """
package $packageName;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Toast;

import com.google.android.gms.pay.Pay;
import com.google.android.gms.pay.PayApiAvailabilityStatus;
import com.google.android.gms.pay.PayClient;

import java.util.UUID;

${importViewBindingClass(isViewBindingSupported, packageName, applicationPackage, layoutName, Language.Java)}

/**
 * An Activity that allows the user to add a pass to the user's Google Wallet using the
 * Google Wallet Android SDK.
 *
 * If you are testing out the new Wallet functionality, you will need to create a
 * <a href="https://wallet-lab-tools.web.app/issuers">Temporary Issuer Account</a> prior to running
 * this example. If you are using the temporary issuer account, you must enter the TODO fields
 * in the {@link WalletActivity#onAddToWalletClicked()} function.
 *
 * If you are using this template to create a custom pass, you will need to sign into the Google Pay
 * Business Console, Create a New Pass, and Alter the JSON in the validator to ensure the pass looks
 * and behaves as preferred. For further instruction, please follow
 * <a href="https://developers.google.com/wallet/generic/android/prerequisites">the provided documentation</a>
 *
 * @see Pay#getClient(Activity)
 * @see PayClient#savePasses(String, Activity, int)
 * @see SamplePass
 */
public class $activityClass extends AppCompatActivity {

    /**
     * The request code we pass along to the Google Wallet API when attempting to add a pass.
     */
    private static final int ADD_TO_WALLET_REQUEST_CODE = 1000;

${renderIf(isViewBindingSupported) { """
    /**
     * The view binding associated with this Activity's layout.
     */
    private ${layoutToViewBindingClass(layoutName)} layout;
"""}}

    /**
     * The {@link PayClient} which we use to interact with the Google Wallet API.
     *
     * @see Pay#getClient(Activity)
     */
    private PayClient walletClient;

    /**
     * The button the user clicks on to add to wallet.
     */
    private View addToWalletButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        walletClient = Pay.getClient(this);

        $contentViewBlock

        $addToWalletButtonBlock
        addToWalletButton.setOnClickListener(view -> onAddToWalletClicked());

        fetchCanUseGoogleWalletApi();
    }
    /**
     * Adds the {@link SamplePass} to the user's Google Wallet.
     *
     * You can also alter this to allow for use of saving passes using a JWT by switching the method
     * to {@link PayClient#savePassesJwt(String, Activity, int)}. In this case, you will no longer
     * require the use of {@link SamplePass}.
     *
     * @see PayClient#savePasses(String, Activity, int)
     */
    private void onAddToWalletClicked() {
        final SamplePass samplePass = new SamplePass(
                "",  // TODO(you) – Enter issuer email address
                "",    // TODO(you) – Enter issuer id
                "",  // TODO(you) – Enter pass class
                UUID.randomUUID().toString()
        );

        walletClient.savePasses(
                samplePass.toJson(),
                this,
                ADD_TO_WALLET_REQUEST_CODE
        );
    }

    /**
     * A helper method that allows us to check if the Google Wallet API is available on the current
     * device.
     *
     * Please note, some countries do not have Google Wallet available to them yet.
     */
    private void fetchCanUseGoogleWalletApi() {
        walletClient
                .getPayApiAvailabilityStatus(PayClient.RequestType.SAVE_PASSES)
                .addOnSuccessListener(result -> {
                    // Display the "Add to Wallet" button if the wallet API is available on this device.
                    if (result == PayApiAvailabilityStatus.AVAILABLE) {
                        addToWalletButton.setVisibility(View.VISIBLE);
                    } else {
                        addToWalletButton.setVisibility(View.GONE);
                    }
                })
                .addOnFailureListener(exception -> {
                    // Hide the button and optionally show an error message
                    addToWalletButton.setVisibility(View.GONE);

                    Toast.makeText(
                            this,
                            R.string.google_wallet_status_unavailable,
                            Toast.LENGTH_SHORT
                    ).show();
                });
    }

    /**
     * Handle the result from {@link PayClient#savePasses(String, Activity, int)}, where we check to
     * see if our attempt to add the pass to the user's Google Wallet was successful, or not.
     *
     * It is up to the implementer to appropriately handle error cases.
     */
    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == ADD_TO_WALLET_REQUEST_CODE) {
            if (resultCode == RESULT_OK) {
                Toast.makeText(
                        this,
                        R.string.add_google_wallet_success,
                        Toast.LENGTH_SHORT
                ).show();
            } else if (resultCode == RESULT_CANCELED) {
                Toast.makeText(
                        this,
                        R.string.add_google_wallet_cancelled,
                        Toast.LENGTH_SHORT
                ).show();
            } else if (resultCode == PayClient.SavePassesResult.SAVE_ERROR) {
                // Handle the error message and optionally display it to the user.
                final String errorMessage = data.getStringExtra(PayClient.EXTRA_API_ERROR_MESSAGE);
                Toast.makeText(
                        this,
                        errorMessage != null
                                ? errorMessage
                                : getString(R.string.add_google_wallet_unknown_error),
                        Toast.LENGTH_SHORT
                ).show();
            } else {
                Toast.makeText(
                        this,
                        R.string.add_google_wallet_unknown_error,
                        Toast.LENGTH_SHORT
                ).show();
            }
        }
    }
}
"""
}
