/*
 * Copyright (C) 2010 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package in.joind.authenticator;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.app.ProgressDialog;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import in.joind.JIActivity;
import in.joind.JIRest;
import in.joind.OAuthHelper;
import in.joind.R;

/**
 * Activity which displays login screen to the user.
 */
public class AuthenticatorActivity extends JoindInAuthenticatorActivity {

    final static public String DUMMY_USERNAME = "joind.in";

    private AccountManager mAccountManager;

    private String oauthClientID;
    private String oauthClientSecret;

    /**
     * {@inheritDoc}
     */
    @Override
    public void onCreate(Bundle icicle) {
        super.onCreate(icicle);

        // Allow ActionBar 'up' navigation
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        getSupportActionBar().setTitle(R.string.authenticatorTitle);

        mAccountManager = AccountManager.get(this);
        setContentView(R.layout.authenticator);

        // Client ID first - if we can't get this, no use continuing
        oauthClientID = OAuthHelper.getClientID(this);
        oauthClientSecret = OAuthHelper.getClientSecret(this);
        if (oauthClientID == null || oauthClientSecret == null) {
            runOnUiThread(new Runnable() {
                public void run() {
                    Toast toast = Toast.makeText(getApplicationContext(), "OAuth properties file not found; cannot continue", Toast.LENGTH_LONG);
                    toast.show();

                    Intent resultIntent = new Intent();
                    setResult(RESULT_CANCELED, resultIntent);
                    finish();
                }
            });
        }

        setupSigninClick();
    }

    /**
     * Sets up the onClick handler for the sign-in button
     */
    protected void setupSigninClick() {
        // Button click handler - does the sign-in
        Button signInButton = (Button) findViewById(R.id.authSignInButton);
        signInButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // Clear any errors
                TextView errorView = (TextView) findViewById(R.id.authErrors);
                errorView.setText("");

                // Sign user in and retrieve token, user uri
                TextView usernameView = (TextView) findViewById(R.id.authUsername);
                TextView passwordView = (TextView) findViewById(R.id.authPassword);
                final String username = usernameView.getText().toString();
                final String password = passwordView.getText().toString();

                final ProgressDialog pleaseWait = ProgressDialog.show(AuthenticatorActivity.this, getString(R.string.generalPleaseWait), getString(R.string.authSignInProgressText), true);

                // Create new thread, otherwise the progress dialog does not show
                new Thread() {
                    public void run() {
                        JSONObject data = new JSONObject();
                        try {
                            data.put("username", username);
                            data.put("password", password);
                            data.put("grant_type", "password");
                            data.put("client_id", oauthClientID);
                            data.put("client_secret", oauthClientSecret);
                        } catch (JSONException e) {
                            pleaseWait.dismiss();

                            return;
                        }

                        String url = getString(R.string.apiURL) + "token";
                        JIRest rest = new JIRest(AuthenticatorActivity.this);
                        rest.requestToFullURI(url, data, JIRest.METHOD_POST);

                        // Dismiss the dialog
                        pleaseWait.dismiss();

                        // Handle the response (success or otherwise)
                        onAuthenticationResult(rest.getResult());
                    }
                }.start();
            }
        });
    }

    /**
     * Called when response is received from the server for authentication
     * request. See onAuthenticationResult(). Sets the
     * AccountAuthenticatorResult which is sent back to the caller. We store the
     * authToken that's returned from the server as the 'password' for this
     * account - so we're never storing the user's actual password locally.
     *
     * @param authToken the authentication token result.
     */
    private void finishLogin(final String authToken, final String userURI) {

        //
        new Thread() {
            public void run() {
                String username = DUMMY_USERNAME;
                JIRest rest = new JIRest(AuthenticatorActivity.this);
                int result = rest.getJSONFullURI(userURI);
                if (result != JIRest.OK) {
                    // A problem retrieving the user's details
                } else {
                    // We've got the user's profile data back
                    // Try and extract their username
                    try {
                        JSONObject jsonResult = rest.getJSONResult();
                        JSONArray allUsers = jsonResult.getJSONArray("users");
                        JSONObject thisUser = allUsers.getJSONObject(0);
                        username = thisUser.getString("username");
                    } catch (Exception e) {
                    }
                }

                // Add the account details
                final Account account = new Account(username, getString(R.string.authenticatorAccountType));
                mAccountManager.addAccountExplicitly(account, "", null);
                mAccountManager.setAuthToken(account, getString(R.string.authTokenType), authToken);

                final Intent intent = new Intent();
                intent.putExtra(AccountManager.KEY_ACCOUNT_NAME, username);
                intent.putExtra(AccountManager.KEY_ACCOUNT_TYPE, getString(R.string.authenticatorAccountType));
                intent.putExtra(AccountManager.KEY_AUTHTOKEN, authToken);
                setAccountAuthenticatorResult(intent.getExtras());
                setResult(RESULT_OK, intent);
                finish();
            }
        }.start();
    }

    /**
     * Called when the initial token request returns
     *
     * @param result the JSON result object returned from the request, or NULL if failed
     */
    public void onAuthenticationResult(String result) {

        if (result == null) {
            // Major fail!
            handleSigninError(getString(R.string.oauthGenericErrorMessage));
            Log.e(JIActivity.LOG_JOINDIN_APP, "onAuthenticationResult: failed to authenticate, no return data");

            return;
        }

        JSONObject jsonResult;
        try {
            jsonResult = new JSONObject(result);
        } catch (JSONException e) {
            // We couldn't get a JSON object from the result
            // What about an array? That's normally an error message as the single element in the array
            String errorMessage = getString(R.string.oauthGenericErrorMessage);
            try {
                JSONArray jsonArray = new JSONArray(result);
                if (jsonArray.length() == 1) {
                    errorMessage = jsonArray.optString(0);
                }
            } catch (Exception e1) {
            }

            handleSigninError(errorMessage);
            return;
        }

        String accessToken = jsonResult.optString("access_token");
        String userURI = jsonResult.optString("user_uri");
        boolean success = ((accessToken != null) && (accessToken.length() > 0));

        if (success) {
            finishLogin(accessToken, userURI);
        } else {
            Log.e(JIActivity.LOG_JOINDIN_APP, "onAuthenticationResult: failed to authenticate successfully");
            handleSigninError(getString(R.string.oauthGenericErrorMessage));
        }
    }

    /**
     * Shows any authentication errors to the user
     *
     * @param rawErrorMessage
     */
    protected void handleSigninError(final String rawErrorMessage) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                TextView errorView = (TextView) findViewById(R.id.authErrors);
                String errorMessage = rawErrorMessage.trim();
                errorMessage = (errorMessage.length() > 0 ? errorMessage : getString(R.string.oauthGenericErrorMessage));
                errorView.setText(errorMessage);
            }
        });
    }
}
