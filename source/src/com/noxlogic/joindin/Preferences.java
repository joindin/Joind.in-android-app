package com.noxlogic.joindin;

/*
 * Preferences activity. We use the android own preference Activity for easy handling
 */

import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceActivity;
import android.preference.PreferenceManager;

public class Preferences extends PreferenceActivity {
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        addPreferencesFromResource(R.xml.preferences);
    }

    protected void onPause () {
        super.onPause();

        /* When we are paused (also called when we quit the preference activity), we check
         * to see if our credentials are valid. If so, we set an additional flag so we can
         * let the user send registered comments instead of anonymous ones */
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getBaseContext());
        SharedPreferences.Editor editor = prefs.edit();
        editor.putBoolean("validated", JIActivity.hasValidCredentials (getBaseContext()));
        editor.commit();
    }
}





