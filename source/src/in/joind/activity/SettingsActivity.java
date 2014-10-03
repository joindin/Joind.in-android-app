package in.joind.activity;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.preference.PreferenceScreen;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import in.joind.C;
import in.joind.JIActivity;
import in.joind.R;
import in.joind.fragment.LogInDialogFragment;
import in.joind.fragment.PreferenceListFragment;

public class SettingsActivity extends JIActivity implements PreferenceListFragment.OnPreferenceAttachedListener {

    TextView loginLogoutText;
    ImageView gravatarImage;
    Button logInOutButton;
    AccountManager accountManager;
    Account thisAccount;
    LogInReceiver logInReceiver;

    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_settings);
    }

    public void onResume() {
        super.onResume();

        getViewObjects();
        configureAccounts();

        logInReceiver = new LogInReceiver();
        IntentFilter intentFilter = new IntentFilter(C.USER_LOGGED_IN);
        registerReceiver(logInReceiver, intentFilter);
    }

    public void onPause() {
        super.onPause();

        unregisterReceiver(logInReceiver);
    }

    protected void getViewObjects() {
        logInOutButton = (Button) findViewById(R.id.logInOutButton);
        logInOutButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (thisAccount == null || thisAccount.name.equals("")) {
                    requestLoginDetails();
                } else {
                    // Fire logout
                    accountManager.removeAccount(thisAccount, null, null);
                    loginLogoutText.setText(getString(R.string.prefAuthLoginTitle));
                    thisAccount = null;
                }
            }
        });
        loginLogoutText = (TextView) findViewById(R.id.logInOutText);
        gravatarImage = (ImageView) findViewById(R.id.gravatarImage);
    }

    @Override
    public void onPreferenceAttached(PreferenceScreen root, int xmlId) {

    }

    /**
     * Opens the login dialog
     * The dialog handles sending the intent around post-login
     */
    protected void requestLoginDetails() {
        LogInDialogFragment dlg = new LogInDialogFragment();
        dlg.show(getSupportFragmentManager(), "login");
    }

    /**
     * Look up accounts
     */
    protected void configureAccounts()
    {
        accountManager = AccountManager.get(this);
        Account[] accounts = accountManager.getAccountsByType(getString(R.string.authenticatorAccountType));
        thisAccount = (accounts.length > 0 ? accounts[0] : null);

        if (thisAccount != null && !thisAccount.name.equals("")) {
            loginLogoutText.setText(String.format(getString(R.string.prefAuthLoggedInAs), thisAccount.name));
            logInOutButton.setText(getString(R.string.prefAuthLogInButton));

            // TODO fetch gravatar
            gravatarImage.setVisibility(View.VISIBLE);
        }
    }

    /**
     * Handle login intents
     */
    private class LogInReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (action.equals(null)) {
                return;
            }
            if (action.equals(C.USER_LOGGED_IN)) {
                configureAccounts();
            }
        }

    }
}
