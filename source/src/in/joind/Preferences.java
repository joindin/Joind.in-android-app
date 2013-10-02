package in.joind;

/*
 * Preferences activity. We use the android own preference Activity for easy handling
 */

import android.accounts.*;
import android.app.Activity;
import android.content.Context;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.util.Log;
import in.joind.R;

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
