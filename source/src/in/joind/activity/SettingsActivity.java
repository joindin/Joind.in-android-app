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
import android.widget.TextView;

import in.joind.ImageLoader;
import in.joind.JIActivity;
import in.joind.R;
import in.joind.fragment.LogInDialogFragment;
import in.joind.fragment.PreferenceListFragment;

public class SettingsActivity extends JIActivity implements PreferenceListFragment.OnPreferenceAttachedListener {

    final public static String ACTION_USER_LOGGED_IN = "in.joind.UserLoggedIn_Action";

    TextView loginLogoutText;
    ImageView gravatarImage;
    Button logInOutButton;
    AccountManager accountManager;
    Account thisAccount;
    LogInReceiver logInReceiver;

    ImageLoader imageLoader;

    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_settings);

        setTitle(getString(R.string.titleSettings));
    }

    public void onResume() {
        super.onResume();

        imageLoader = new ImageLoader(getApplicationContext(), "gravatars");

        getViewObjects();
        configureAccounts();

        logInReceiver = new LogInReceiver();
        IntentFilter intentFilter = new IntentFilter(SettingsActivity.ACTION_USER_LOGGED_IN);
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
                    logInOutButton.setText(getString(R.string.prefAuthLogInButton));
                    gravatarImage.setVisibility(View.GONE);
                    thisAccount = null;

                    // Clear the image cache of this gravatar
                    if (!gravatarImage.getTag().equals("")) {
                        imageLoader.clearCacheEntry((String) gravatarImage.getTag());
                    }
                }
            }
        });
        loginLogoutText = (TextView) findViewById(R.id.logInOutText);

        // Configure gravatar image
        gravatarImage = (ImageView) findViewById(R.id.gravatarImage);
        gravatarImage.setTag("");
        gravatarImage.setImageDrawable(null);
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
    protected void configureAccounts() {
        accountManager = AccountManager.get(this);
        Account[] accounts = accountManager.getAccountsByType(getString(R.string.authenticatorAccountType));
        thisAccount = (accounts.length > 0 ? accounts[0] : null);

        if (thisAccount != null && !thisAccount.name.equals("")) {
            loginLogoutText.setText(String.format(getString(R.string.prefAuthLoggedInAs), thisAccount.name));
            logInOutButton.setText(getString(R.string.prefAuthLogOutButton));

            // Fetch gravatar
            String gravatarHash = accountManager.getUserData(thisAccount, getString(R.string.authGravatarHash));
            String filename = gravatarHash + "?d=mm"; // Default of the "mystery man"
            gravatarImage.setTag(filename);
            imageLoader.displayImage("http://www.gravatar.com/avatar/", filename, this, gravatarImage);
        }
    }

    /**
     * Handle login intents
     */
    private class LogInReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (action == null) {
                return;
            }
            if (action.equals(SettingsActivity.ACTION_USER_LOGGED_IN)) {
                configureAccounts();
            }
        }
    }
}
