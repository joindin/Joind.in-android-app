package in.joind.user;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

import in.joind.JIRest;
import in.joind.R;

public class UserManager {

    Context context;
    JIRest rest;
    AccountManager accountManager;

    public UserManager(Context context)
    {
        this.context = context;
        this.rest = new JIRest(context);
        this.accountManager = AccountManager.get(context);
    }

    public void updateSavedUserDetails(String userURI)
    {
        Account account = getCurrentAccount();
        if (account == null) {
            return;
        }

        String username = null;
        String gravatarHash = ""; // dummy?
        String talksURI = "";
        String attendedEventsURI = "";
        String hostedEventsURI = "";
        String talkCommentsURI = "";

        String verboseUserURI = userURI + "?verbose=yes"; // force verbose URI
        int result = rest.getJSONFullURI(verboseUserURI);
        if (result == JIRest.OK) {
            // We've got the user's profile data back
            // Try and extract their username
            try {
                JSONObject jsonResult = rest.getJSONResult();
                JSONArray allUsers = jsonResult.getJSONArray("users");
                JSONObject thisUser = allUsers.getJSONObject(0);

                // Extract useful bits from the user's profile details
                username = thisUser.getString("username");
                gravatarHash = thisUser.getString(context.getString(R.string.authGravatarHash));
                talksURI = thisUser.getString("talks_uri");
                attendedEventsURI = thisUser.getString("attended_events_uri");
                hostedEventsURI = thisUser.getString("hosted_events_uri");
                talkCommentsURI = thisUser.getString("talk_comments_uri");
            } catch (Exception e) {
                // do nothing
            }
        }

        // Add the account details
        // If the username changes, we need to remove and recreate the account
        if (username != null && !username.equals(account.name)) {

            // Copy the auth token here, prior to account removal
            String authToken = accountManager.peekAuthToken(account, context.getString(R.string.authTokenType));

            // Remove and add again
            accountManager.removeAccountExplicitly(account);
            account = new Account(username, context.getString(R.string.authenticatorAccountType));
            accountManager.addAccountExplicitly(account, "", null);
            accountManager.setAuthToken(account, context.getString(R.string.authTokenType), authToken);
        }

        accountManager.setUserData(account, context.getString(R.string.authGravatarHash), gravatarHash);
        accountManager.setUserData(account, context.getString(R.string.authUserURITalks), talksURI);
        accountManager.setUserData(account, context.getString(R.string.authUserURIAttendedEvents), attendedEventsURI);
        accountManager.setUserData(account, context.getString(R.string.authUserURIHostedEvents), hostedEventsURI);
        accountManager.setUserData(account, context.getString(R.string.authUserURITalkComments), talkCommentsURI);
    }

    private Account getCurrentAccount()
    {
        AccountManager am = AccountManager.get(context);
        Account[] accounts = am.getAccountsByType(context.getString(R.string.authenticatorAccountType));

        return (accounts.length > 0 ? accounts[0] : null);
    }
}
