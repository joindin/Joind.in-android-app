package com.noxlogic.joindin;

/*
 * Preferences activity. We use the android own preference Activity for easy handling
 */

import android.accounts.*;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceManager;
import android.util.Log;
import com.noxlogic.joindin.authenticator.AuthenticatorActivity;

import java.io.IOException;

public class Preferences extends PreferenceActivity {

    protected final static int RESULT_OAUTH = 1;

    AccountManager am;
    Account thisAccount;
    Preference authButton;
    Context ctx;

    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ctx = this;
        addPreferencesFromResource(R.xml.preferences);

        // Get account details
        configureAccounts();
    }

    @Override
    protected void onResume() {
        super.onResume();    //To change body of overridden methods use File | Settings | File Templates.
        configureAccounts();
    }

    protected void configureAccounts()
    {
        am = AccountManager.get(this);
        Account[] accounts = am.getAccountsByType(getString(R.string.authenticatorAccountType));
        thisAccount = (accounts.length > 0 ? accounts[0] : null);

        authButton = findPreference("auth_button");

        if (thisAccount != null && !thisAccount.name.equals("")) {
            authButton.setTitle(getString(R.string.prefAuthLogoutTitle));
            authButton.setSummary(getString(R.string.prefAuthLogoutSummary));
        }

        authButton.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference arg0) {
                if (thisAccount == null || thisAccount.name.equals("")) {
                    // Fire account creation
                    am.addAccount(getString(R.string.authenticatorAccountType), getString(R.string.authTokenType), null, new Bundle(), (Activity) ctx, new OnAccountAddComplete(), null);
                } else {
                    // Fire logout
                    am.removeAccount(thisAccount, null, null);
                    authButton.setTitle(getString(R.string.prefAuthLoginTitle));
                    authButton.setSummary("");
                    thisAccount = null;
                }
                return true;
            }
        });
    }

    protected void onPause () {
        super.onPause();

        /* When we are paused (also called when we quit the preference activity), we check
         * to see if our credentials are valid. If so, we set an additional flag so we can
         * let the user send registered comments instead of anonymous ones */
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
        SharedPreferences.Editor editor = prefs.edit();
        editor.putBoolean("validated", false);//thisAccount != null);
        editor.commit();
    }

    private class OnAccountAddComplete implements AccountManagerCallback<Bundle> {

        @Override
        public void run(AccountManagerFuture<Bundle> result) {
            Bundle bundle;
            try {
                bundle = result.getResult();
            } catch (OperationCanceledException e) {
                e.printStackTrace();
                return;
            } catch (AuthenticatorException e) {
                e.printStackTrace();
                return;
            } catch (IOException e) {
                e.printStackTrace();
                return;
            }
            thisAccount = new Account(
                    bundle.getString(AccountManager.KEY_ACCOUNT_NAME),
                    bundle.getString(AccountManager.KEY_ACCOUNT_TYPE)
            );
            Log.d("JoindInApp", "Added account " + thisAccount.name);
        }
    }
}
