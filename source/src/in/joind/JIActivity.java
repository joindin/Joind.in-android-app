package in.joind;

/*
 * This is the main activity class. All activities in our application will extend
 * JIActivity instead of activity. This class supplies us with some additional
 * tools and options which need to be set for all activity screens (for instance,
 * the menu)
 */

import android.accounts.Account;
import android.accounts.AccountManager;
import com.crashlytics.android.Crashlytics;
import in.joind.R;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.NavUtils;
import android.support.v4.app.TaskStackBuilder;
import android.support.v7.app.ActionBarActivity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Intent;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.Window;
import android.widget.Toast;

public class JIActivity extends ActionBarActivity {

    protected boolean isAuthenticated;
    static String _comment_history;
    final public static String LOG_JOINDIN_APP = "JoindInApp";
    final public static String SHARED_PREF_NAME = "in.joind";

	public static void setCommentHistory(String comment) {
		_comment_history = comment;
	}

    // Automatically called by all activities.
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Needed to show the circular progress animation in the top right corner.
        requestWindowFeature(Window.FEATURE_INDETERMINATE_PROGRESS);

        Crashlytics.start(this);
    }

    public void onResume()
    {
        super.onResume();

        // Get account details
        AccountManager am = AccountManager.get(this);
        Account[] accounts = am.getAccountsByType(this.getString(R.string.authenticatorAccountType));
        Account thisAccount = (accounts.length > 0 ? accounts[0] : null);
        isAuthenticated = (thisAccount != null);
    }

    public boolean isAuthenticated() {
        return isAuthenticated;
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
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            // Respond to the action bar's Up/Home button
            case android.R.id.home:
                Intent upIntent = NavUtils.getParentActivityIntent(this);
                if (NavUtils.shouldUpRecreateTask(this, upIntent)) {
                    // This activity is NOT part of this app's task, so create a new task
                    // when navigating up, with a synthesized back stack.
                    TaskStackBuilder.create(this)
                            // Add all of this activity's parents to the back stack
                            .addNextIntentWithParentStack(upIntent)
                            // Navigate up to the closest parent
                            .startActivities();
                } else {
                    // This activity is part of this app's task, so simply
                    // navigate up to the logical parent activity.
                    NavUtils.navigateUpTo(this, upIntent);
                }
                return true;

            case R.id.about_menu_item:
                // Display about box
                Dialog about = new AlertDialog.Builder(this)
                        .setTitle(R.string.generalAboutTitle)
                        .setPositiveButton(R.string.generalAboutButtonCaption, null)
                        .setMessage(R.string.generalAboutMessage)
                        .create();
                about.show();
                break;

            case R.id.clear_menu_item:
                // Removes all items from the database
                DataHelper dh = DataHelper.getInstance();
                dh.deleteAll();
                Toast toast = Toast.makeText(getApplicationContext(), R.string.generalCacheCleared, Toast.LENGTH_LONG);
                toast.show();
                break;

            case R.id.settings_menu_item:
                // Displays preferences
                Intent settingsActivity = new Intent(getApplicationContext(), Preferences.class);
                startActivity(settingsActivity);
                break;
        }
        return true;
    }

    protected void showToast(String message, int duration) {
        Toast toast = Toast.makeText(getApplicationContext(), getString(R.string.activityEventDetailFailedJSON), duration);
        toast.show();
    }
}
