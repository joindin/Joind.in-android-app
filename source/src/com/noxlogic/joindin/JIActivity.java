package com.noxlogic.joindin;

/*
 * This is the main activity class. All activities in our application will extend
 * JIActivity instead of activity. This class supplies us with some additional
 * tools and options which need to be set for all activity screens (for instance,
 * the menu)
 */

import org.json.JSONObject;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.Window;
import android.widget.Toast;

public class JIActivity extends Activity {

	static String _comment_history;
	static public String getCommentHistory () {
		return _comment_history;
	}
	public static void setCommentHistory(String comment) {
		_comment_history = comment;
	}

    // Automatically called by all activities.
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Needed to show the circular progress animation in the top right corner.
        requestWindowFeature(Window.FEATURE_INDETERMINATE_PROGRESS);
    }

    // Displays (or hides) the circular progress animation in the top left corner
    public void displayProgressBar (final boolean state) {
        runOnUiThread(new Runnable() {
            public void run() {
                setProgressBarIndeterminateVisibility(state);
            }
        });
    }

    // Automatically called. Creates option menu. All activities share the same menu.
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.menu, menu);
        return true;
    }

    // Handler for options menu
    public boolean onOptionsItemSelected (MenuItem item) {
        switch (item.getItemId()) {
            case R.id.about_menu_item :
                        // Display about box
                        Dialog about = new AlertDialog.Builder(this)
                            .setTitle(R.string.generalAboutTitle)
                            .setPositiveButton(R.string.generalAboutButtonCaption, null)
                            .setMessage(R.string.generalAboutMessage)
                            .create();
                        about.show();
                        break;

            case R.id.clear_menu_item :
                        // Removes all items from the database
                        DataHelper dh = DataHelper.getInstance ();
                        dh.deleteAll ();
                        Toast toast = Toast.makeText (getApplicationContext(), R.string.generalCacheCleared, Toast.LENGTH_LONG);
                        toast.show ();
                        break;

            case R.id.settings_menu_item :
                        // Displays preferences
                        Intent settingsActivity = new Intent(getApplicationContext(), Preferences.class);
                        startActivity(settingsActivity);
                        break;
        }
        return true;
    }

}
