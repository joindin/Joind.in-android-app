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

package com.noxlogic.joindin.authenticator;

import android.accounts.Account;
import android.accounts.AccountAuthenticatorActivity;
import android.accounts.AccountManager;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.provider.ContactsContract;
import android.util.Log;
import android.view.View;
import android.view.Window;
import android.webkit.CookieManager;
import android.webkit.CookieSyncManager;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import com.noxlogic.joindin.OAuthHelper;
import com.noxlogic.joindin.R;

import java.io.IOException;
import java.net.URLConnection;

/**
 * Activity which displays login screen to the user.
 */
public class AuthenticatorActivity extends AccountAuthenticatorActivity {

    final static public String USERNAME = "joind.in";

    private AccountManager mAccountManager;

    private String oauthAPIKey;
    private String oauthCallback;

    /**
     * {@inheritDoc}
     */
    @Override
    public void onCreate(Bundle icicle) {
        super.onCreate(icicle);
        mAccountManager = AccountManager.get(this);
        requestWindowFeature(Window.FEATURE_LEFT_ICON);
        setContentView(R.layout.authenticator);

        WebView webView = (WebView) findViewById(R.id.webview);
        CookieSyncManager.createInstance(this);
        CookieManager cookieManager = CookieManager.getInstance();
        cookieManager.removeAllCookie();

        // Don't remember passwords or form submission data
        webView.getSettings().setSaveFormData(false);
        webView.getSettings().setSavePassword(false);

        webView.setWebViewClient(new WebViewClient() {
            public boolean shouldOverrideUrlLoading(WebView view, String url) {

                // Get the URL without any query strings
                Uri thisUri = Uri.parse(url);
                String thisURL = thisUri.toString().replace("?" + thisUri.getQuery(), "");

                if (thisURL.equals(oauthCallback)) {
                    // Successful? We should have an access token (null if not found)
                    String accessToken = thisUri.getQueryParameter("access_token");
                    onAuthenticationResult(accessToken);
                    view.setVisibility(View.GONE);

                    return true;
                }
                return false;
            }
        });

        // API key first - if we can't get this, no use continuing
        oauthAPIKey = OAuthHelper.getApiKey(this);
        if (oauthAPIKey == null) {
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
        oauthCallback = OAuthHelper.getCallback(this);

        Uri.Builder builder = Uri.parse(getString(R.string.oauthURL)).buildUpon();
        builder.appendQueryParameter("api_key", oauthAPIKey);
        builder.appendQueryParameter("callback", oauthCallback);
        webView.loadUrl(builder.toString());
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
    private void finishLogin(String authToken) {
        // Use a dummy username here
        // as we can't currently retrieve the actual username
        // from the API

        final Account account = new Account(USERNAME, getString(R.string.authenticatorAccountType));
        mAccountManager.addAccountExplicitly(account, "", null);
        mAccountManager.setAuthToken(account, getString(R.string.authTokenType), authToken);

        final Intent intent = new Intent();
        intent.putExtra(AccountManager.KEY_ACCOUNT_NAME, USERNAME);
        intent.putExtra(AccountManager.KEY_ACCOUNT_TYPE, getString(R.string.authenticatorAccountType));
        intent.putExtra(AccountManager.KEY_AUTHTOKEN, authToken);
        setAccountAuthenticatorResult(intent.getExtras());
        setResult(RESULT_OK, intent);
        finish();
    }

    /**
     * Called when the authentication process completes (see attemptLogin()).
     *
     * @param authToken the authentication token returned by the server, or NULL if authentication failed.
     */
    public void onAuthenticationResult(String authToken) {

        boolean success = ((authToken != null) && (authToken.length() > 0));

        if (success) {
            finishLogin(authToken);
        } else {
            Log.e("JoindInApp", "onAuthenticationResult: failed to authenticate");

            Toast toast = Toast.makeText(this, getString(R.string.oauthDeniedMessage), Toast.LENGTH_LONG);
            toast.show();
            finish();
        }
    }
}
