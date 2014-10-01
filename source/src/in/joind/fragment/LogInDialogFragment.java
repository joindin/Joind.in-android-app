package in.joind.fragment;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.support.v4.app.DialogFragment;
import android.view.LayoutInflater;

import in.joind.R;

public class LogInDialogFragment extends DialogFragment {
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
        LayoutInflater inflater = getActivity().getLayoutInflater();

        builder.setView(inflater.inflate(R.layout.dialog_login, null))
            .setPositiveButton(R.string.authSignInButton, new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int id) {
                    // Do the signin
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
}
