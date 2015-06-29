package in.joind.fragment;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.app.DialogFragment;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import in.joind.C;
import in.joind.JIActivity;
import in.joind.JIRest;
import in.joind.OAuthHelper;
import in.joind.R;

public class LogInDialogFragment extends DialogFragment {

    final static public String DUMMY_USERNAME = "joind.in";

    AccountManager accountManager;
    String oauthClientID;
    String oauthClientSecret;
    TextView errorView;


    @NonNull
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        accountManager = AccountManager.get(getActivity());

        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
        LayoutInflater inflater = getActivity().getLayoutInflater();

        builder.setView(inflater.inflate(R.layout.dialog_login, null))
                .setPositiveButton(R.string.authSignInButton, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                        // Do nothing here
                        // We want the ability to prevent dismiss if required,
                        // so the main logic is handled by the onClick listener that
                        // we add in the onStart() method below
                    }
                })
                .setNegativeButton(R.string.authCancelButton, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                        LogInDialogFragment.this.getDialog().cancel();
                    }
                })
        ;

        return builder.create();
    }

    /**
     * This method allows us to override the click handler for the positive button
     * eg "sign in"
     * Then we can choose whether or not to dismiss the dialog
     */
    public void onStart() {
        super.onStart();

        AlertDialog thisDialog = (AlertDialog) getDialog();

        Button btnOK = thisDialog.getButton(AlertDialog.BUTTON_POSITIVE);

        btnOK.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (!getOAuthDetails()) {
                    showError(getActivity().getString(R.string.authOAuthPropertiesNotFound));

                    return;
                }
                hideErrors();

                TextView usernameView = (TextView) getDialog().findViewById(R.id.authUsername);
                TextView passwordView = (TextView) getDialog().findViewById(R.id.authPassword);
                final String username = usernameView.getText().toString();
                final String password = passwordView.getText().toString();

                // TODO Dismiss this dialog first?
                // TODO Or show a different "please wait" layout instead?
                final ProgressDialog pleaseWait = ProgressDialog.show(getActivity(), getString(R.string.generalPleaseWait), getString(R.string.authSignInProgressText), true);

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
                        JIRest rest = new JIRest(getActivity());
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
     * Loads in the OAuth configuration details, ready to authenticate
     *
     * @return OAuth details.
     */
    protected boolean getOAuthDetails() {

        oauthClientID = OAuthHelper.getClientID(getActivity());
        oauthClientSecret = OAuthHelper.getClientSecret(getActivity());
        return !(oauthClientID == null || oauthClientSecret == null);

    }

    /**
     * Shows an error to the user
     *
     * @param rawErrorMessage Error message.
     */
    protected void showError(final String rawErrorMessage) {
        getActivity().runOnUiThread(new Runnable() {
            @Override
            public void run() {
                String errorMessage = rawErrorMessage.trim();
                errorMessage = (errorMessage.length() > 0 ? errorMessage : getString(R.string.oauthGenericErrorMessage));
                errorView = (TextView) getDialog().findViewById(R.id.authErrors);
                errorView.setText(errorMessage);
                errorView.setVisibility(View.VISIBLE);
            }
        });
    }

    /**
     * Hide the error display
     */
    protected void hideErrors() {
        getActivity().runOnUiThread(new Runnable() {
            @Override
            public void run() {
                errorView = (TextView) getDialog().findViewById(R.id.authErrors);
                errorView.setVisibility(View.GONE);
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
    protected void finishLogin(final String authToken, final String userURI) {

        new Thread() {
            public void run() {
                String username = DUMMY_USERNAME;
                String gravatarHash = ""; // dummy?
                JIRest rest = new JIRest(getActivity());
                String verboseUserURI = userURI + "?verbose=yes"; // force verbose URI
                int result = rest.getJSONFullURI(verboseUserURI);
                if (result == JIRest.OK) { // No problem retrieving the user's details
                    // We've got the user's profile data back
                    // Try and extract their username
                    try {
                        JSONObject jsonResult = rest.getJSONResult();
                        JSONArray allUsers = jsonResult.getJSONArray("users");
                        JSONObject thisUser = allUsers.getJSONObject(0);

                        // Extract useful bits from the user's profile details
                        username = thisUser.getString("username");
                        gravatarHash = thisUser.getString(getActivity().getString(R.string.authGravatarHash));
                    } catch (Exception e) {
                        // do nothing
                    }
                }

                // Add the account details
                final Account account = new Account(username, getActivity().getString(R.string.authenticatorAccountType));
                accountManager.addAccountExplicitly(account, "", null);
                accountManager.setAuthToken(account, getActivity().getString(R.string.authTokenType), authToken);
                accountManager.setUserData(account, getActivity().getString(R.string.authGravatarHash), gravatarHash);

                getActivity().runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        Toast toast = Toast.makeText(getActivity(), getActivity().getString(R.string.authSignedInOK), Toast.LENGTH_LONG);
                        toast.show();
                    }
                });

                // Announce the user has signed in
                Intent intent = new Intent(C.USER_LOGGED_IN);
                getActivity().sendBroadcast(intent);

                // and close the dialog
                getDialog().dismiss();
            }
        }.start();
    }

    /**
     * Called when the initial token request returns
     *
     * @param result the JSON result object returned from the request, or NULL if failed
     */
    protected void onAuthenticationResult(String result) {

        if (result == null) {
            // Major fail!
            showError(getActivity().getString(R.string.oauthGenericErrorMessage));
            Log.e(JIActivity.LOG_JOINDIN_APP, "onAuthenticationResult: failed to authenticate, no return data");

            return;
        }

        JSONObject jsonResult;
        try {
            jsonResult = new JSONObject(result);
        } catch (JSONException e) {
            e.printStackTrace();
            // We couldn't get a JSON object from the result
            // What about an array? That's normally an error message as the single element in the array
            String errorMessage = getActivity().getString(R.string.oauthGenericErrorMessage);
            try {
                JSONArray jsonArray = new JSONArray(result);
                if (jsonArray.length() == 1) {
                    errorMessage = jsonArray.optString(0);
                }
            } catch (Exception e1) {
                e1.printStackTrace();
            }

            showError(errorMessage);
            return;
        }

        String accessToken = jsonResult.optString("access_token");
        String userURI = jsonResult.optString("user_uri");
        boolean success = ((accessToken != null) && (accessToken.length() > 0));

        if (success) {
            finishLogin(accessToken, userURI);
        } else {
            Log.e(JIActivity.LOG_JOINDIN_APP, "onAuthenticationResult: failed to authenticate successfully");
            showError(getActivity().getString(R.string.oauthGenericErrorMessage));
        }
    }
}
