package com.noxlogic.joindin;

/*
 * Comment activity. This activity will handle both Events comments and talk comments.
 */

import org.json.JSONException;
import org.json.JSONObject;

import android.app.ProgressDialog;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.RatingBar;
import android.widget.Toast;

public class AddComment extends JIActivity implements OnClickListener {
    ProgressDialog saveDialog;
    private String commentType;
    private JSONObject talkJSON;
    private JSONObject eventJSON;

    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Set layout
        setContentView(R.layout.addcomment);

        // Get comment type from the intent scratch board
        this.commentType = getIntent().getStringExtra("commentType");
        setTitle (String.format(getString(R.string.activityAddCommentTitle), this.commentType));

        // Are we commenting on an event?
        if (this.commentType.compareTo("event") == 0) {
            // Get event information
            try {
                this.eventJSON = new JSONObject(getIntent().getStringExtra("eventJSON"));
            } catch (JSONException e) { 
                android.util.Log.e("JoindInApp", "No event passed to activity", e); 
            }

            // Set spinner and checkbox to invisible since they are not used while
            // commenting on events
            View v;
            v = (View) findViewById(R.id.RatingBar01);
            v.setVisibility(View.GONE);
            v = (View) findViewById(R.id.TextViewRating);
            v.setVisibility(View.GONE);
            v = findViewById(R.id.CheckBox01);
            v.setVisibility(View.GONE);
        }

        // Add handler to the buttons
        Button button;
        button = (Button)findViewById(R.id.ButtonAddCommentCancel);
        button.setOnClickListener(this);
        button = (Button)findViewById(R.id.ButtonAddCommentSend);
        button.setOnClickListener(this);
    }


    // This must be done in onStart instead of onCreate. For instance, we could have started writing a comment,
    // the user sees he's sending anonymous comments, starts the preferences activity from the menu and returns
    // back to this activity. In that case, the onCreate is not called, but onStart is..
    public void onStart () {
        super.onStart ();

        // Check if we have entered correct credentials in the preferences. If not, we display the
        // text that the user is commenting anonymously..

        View v = (View) findViewById(R.id.AnonymousPost);
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
        if (prefs.getBoolean("validated", false)) {
            v.setVisibility (View.GONE);
        } else {
            v.setVisibility (View.VISIBLE);
        }
    }

    // Called when button is clicked
    public void onClick(View v) {
        // Did we click cancel button?
        if (v == findViewById(R.id.ButtonAddCommentCancel)) {
            // Cancel, do nothing. No comment will be posted
            this.finish ();
        }

        // Did we click send button?
        if (v == findViewById(R.id.ButtonAddCommentSend)) {
            // Show progress dialog
            saveDialog = ProgressDialog.show(this, getString(R.string.generalPleaseWait), getString(R.string.activityAddCommentSendingComment), true);

            // Create new thread, otherwise the progress dialog does not show
            new Thread() {
                public void run() {
                    // Send comment and fetch result
                    final String result = sendComment ();

                    // Dismiss the dialog
                    saveDialog.dismiss();

                    // Must be done if we want to display the toast on screen. This must be done by a thread that can update the UI
                    runOnUiThread(new Runnable() {
                        public void run() {
                            // Display result from sendcomment()
                            Toast toast = Toast.makeText(getApplicationContext(), result, Toast.LENGTH_LONG);
                            toast.show ();

                            // After displaying, close this activity and return
                            AddComment.this.finish();
                        }
                    });
                 }
            }.start();
        }
    }


    // Sendcomment() will do 2 things. Sending comments for talks AND sending comments
    // for events.
    public String sendComment () {
        String url, xml, result;

        // Display progress bar
        displayProgressBar (true);

        // Get information from the layout
        RatingBar ratingbar = (RatingBar)findViewById(R.id.RatingBar01);
        int rating = (int) ratingbar.getRating();
        EditText tmp1 = (EditText) findViewById(R.id.EditText01);
        String comment = tmp1.getText().toString();
        CheckBox tmp2 = (CheckBox) findViewById(R.id.CheckBox01);
        int priv = tmp2.isChecked() ? 1 : 0;

        // Are we sending a talk comment?
        if (this.commentType.compareTo("talk") == 0) {
            try {
                this.talkJSON = new JSONObject(getIntent().getStringExtra("talkJSON"));
            } catch (JSONException e) {
                android.util.Log.e("JoindInApp", "No talk passed to activity", e);
            }

            int talk_id = this.talkJSON.optInt("ID");
            url = "talk";
            xml = "<request>"+JIRest.getAuthXML(this)+"<action type=\"addcomment\" output=\"json\"><talk_id>"+talk_id+"</talk_id><rating>"+rating+"</rating><comment>"+comment+"</comment><private>"+priv+"</private></action></request>";
        } else {
            // We are sending an event comment
            try {
                this.eventJSON = new JSONObject(getIntent().getStringExtra("eventJSON"));
            } catch (JSONException e) {
                android.util.Log.e("JoindInApp", "No event passed to activity", e);
            }

            int event_id = this.eventJSON.optInt("ID");
            url = "event";
            xml = "<request>"+JIRest.getAuthXML(this)+"<action type=\"addcomment\" output=\"json\"><event_id>"+event_id+"</event_id><comment>"+comment+"</comment></action></request>";
        }

        // Send data to joind.in API
        JIRest rest = new JIRest (AddComment.this);
        int error = rest.postXML (url, xml);
        if (error == JIRest.OK) {
            try {
                // When the API returns something, check if it's JSON. If so
                // we parse the MSG key from it since it will be our value.
                JSONObject json = new JSONObject(rest.getResult());
                result = json.optString("msg");
            } catch (Exception e) {
                // Incorrect JSON, just return plain result from http
                result = rest.getResult();
            }
        } else {
            // Incorrect result, return error
            result = rest.getError();
        }

        // Remove progress bar
        displayProgressBar (false);

        // Return
        return result;
    }

}