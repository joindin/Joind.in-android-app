package in.joind.fragment;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.support.v4.app.DialogFragment;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import in.joind.OAuthHelper;
import in.joind.R;

public class LogInDialogFragment extends DialogFragment {

    String oauthClientID;
    String oauthClientSecret;
    TextView errorView;

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
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

                // Continue to signin
            }
        });
    }

    /**
     * Loads in the OAuth configuration details, ready to authenticate
     *
     * @return
     */
    protected boolean getOAuthDetails() {

        oauthClientID = OAuthHelper.getClientID(getActivity());
        oauthClientSecret = OAuthHelper.getClientSecret(getActivity());
        if (oauthClientID == null || oauthClientSecret == null) {
            return false;
        }

        return true;
    }

    /**
     * Shows an error to the user
     *
     * @param errorMessage
     */
    protected void showError(String errorMessage) {
        errorView = (TextView) getDialog().findViewById(R.id.authErrors);
        errorView.setText(errorMessage);
        errorView.setVisibility(View.VISIBLE);
    }

    /**
     * Hide the error display
     */
    protected void hideErrors() {
        errorView = (TextView) getDialog().findViewById(R.id.authErrors);
        errorView.setVisibility(View.GONE);
    }
}
