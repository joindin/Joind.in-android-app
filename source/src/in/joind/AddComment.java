package in.joind;

/*
 * Comment activity. This activity will handle both Events comments and talk comments.
 */

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.RatingBar;
import android.widget.Toast;

import com.crashlytics.android.Crashlytics;

import org.json.JSONException;
import org.json.JSONObject;

public class AddComment extends JIActivity implements OnClickListener {
    ProgressDialog saveDialog;
    private String commentType;
    private JSONObject talkJSON;
    private JSONObject eventJSON;

    final public static int CODE_COMMENT = 1;

    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Allow ActionBar 'up' navigation
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        // Set layout
        setContentView(R.layout.addcomment);

        // Get comment type from the intent scratch board
        this.commentType = getIntent().getStringExtra("commentType");
        setTitle(String.format(getString(R.string.activityAddCommentTitle), this.commentType));

        EditText te = (EditText) findViewById(R.id.CommentText);
        te.setText("");
        te.addTextChangedListener(new TextWatcher() {
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                JIActivity.setCommentHistory(s.toString());
            }

            public void afterTextChanged(Editable s) {
            }

            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }
        });


        // Are we commenting on an event?
        CheckBox privateComment = (CheckBox) findViewById(R.id.CommentPrivate);
        privateComment.setChecked(false); // default
        if (this.commentType.compareTo("event") == 0) {
            // Get event information
            try {
                this.eventJSON = new JSONObject(getIntent().getStringExtra("eventJSON"));
            } catch (JSONException e) {
                Log.e(JIActivity.LOG_JOINDIN_APP, "No event passed to activity", e);
            }

            // Hide the private comment checkbox
            // (not used for events)
            privateComment.setVisibility(View.GONE);
        } else {
            privateComment.setVisibility(View.VISIBLE);
        }

        // Add handler to the buttons
        Button button;
        button = (Button) findViewById(R.id.ButtonAddCommentCancel);
        button.setOnClickListener(this);
        button = (Button) findViewById(R.id.ButtonAddCommentSend);
        button.setOnClickListener(this);
    }

    // Called when button is clicked
    public void onClick(View v) {
        // Did we click cancel button?
        if (v == findViewById(R.id.ButtonAddCommentCancel)) {
            // Cancel, do nothing. No comment will be posted
            this.finish();
        }

        // Did we click send button?
        if (v == findViewById(R.id.ButtonAddCommentSend)) {
            // Show progress dialog
            saveDialog = ProgressDialog.show(this, getString(R.string.generalPleaseWait), getString(R.string.activityAddCommentSendingComment), true);

            // Create new thread, otherwise the progress dialog does not show
            new Thread() {
                public void run() {
                    // Send comment and fetch result
                    final boolean result = sendComment();

                    // Dismiss the dialog
                    saveDialog.dismiss();

                    // Must be done if we want to display the toast on screen. This must be done by a thread that can update the UI
                    runOnUiThread(new Runnable() {
                        public void run() {
                            // Display result from sendcomment()
                            int stringID;
                            if (result) {
                                stringID = R.string.generalSuccessPostComment;
                            } else {
                                stringID = R.string.generalFailPostComment;
                            }
                            Toast toast = Toast.makeText(getApplicationContext(), getString(stringID), Toast.LENGTH_LONG);
                            toast.show();

                            // If successful, close this activity and return
                            if (result) {
                                Intent resultIntent = new Intent();
                                setResult(Activity.RESULT_OK, resultIntent);
                                finish();
                            }
                        }
                    });
                }
            }.start();
        }
    }

    // Sendcomment() will do 2 things. Sending comments for talks AND sending comments
    // for events.
    public boolean sendComment() {
        String url;

        // Display progress bar
        displayProgressBarCircular(true);

        String lastError;

        // Get information from the layout
        RatingBar ratingbar = (RatingBar) findViewById(R.id.CommentRatingBar);
        int rating = (int) ratingbar.getRating();
        EditText tmp1 = (EditText) findViewById(R.id.CommentText);
        String comment = tmp1.getText().toString();

        CheckBox tmp2 = (CheckBox) findViewById(R.id.CommentPrivate);
        int privateComment = tmp2.isChecked() ? 1 : 0;

        JSONObject data = new JSONObject();

        try {
            data.put("comment", comment);
            data.put("rating", rating);
        } catch (JSONException e) {
            Crashlytics.log("Couldn't add comment and rating to JSON object");
            return false;
        }

        // Are we sending a talk comment?
        if (this.commentType.compareTo("talk") == 0) {
            try {
                this.talkJSON = new JSONObject(getIntent().getStringExtra("talkJSON"));
            } catch (JSONException e) {
                Log.e(JIActivity.LOG_JOINDIN_APP, "No talk passed to activity", e);
            }

            // Talk comments have a private status
            try {
                data.put("private", privateComment);
            } catch (JSONException e) {
                // do nothing
            }

            url = this.talkJSON.optString("comments_uri");

        } else {
            // We are sending an event comment
            try {
                this.eventJSON = new JSONObject(getIntent().getStringExtra("eventJSON"));
            } catch (JSONException e) {
                Log.e(JIActivity.LOG_JOINDIN_APP, "No event passed to activity", e);
            }

            url = this.eventJSON.optString("comments_uri");
        }

        // Send data to joind.in API
        JIRest rest = new JIRest(AddComment.this);
        int result = rest.requestToFullURI(url, data, JIRest.METHOD_POST);
        if (result != JIRest.OK) {
            lastError = rest.getError();
            Crashlytics.log(lastError);
        }

        // Remove progress bar
        displayProgressBarCircular(false);

        return result == JIRest.OK;
    }

    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            // Respond to the action bar's Up/Home button
            case android.R.id.home:
                finish();
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }
}
