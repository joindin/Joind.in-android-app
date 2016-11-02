package in.joind.activity;

/*
 * Displays detailed information about a talk (info, comments etc)
 */


import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.support.v7.app.ActionBar;
import android.text.TextUtils;
import android.text.util.Linkify;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;

import in.joind.api.DataHelper;
import in.joind.api.JIRest;
import in.joind.R;

public class TalkDetail extends JIActivity implements OnClickListener {

    MenuItem starTalkButton;

    private JSONObject talkJSON, eventJSON;
    private boolean talkIsStarred = false;

    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Allow ActionBar 'up' navigation
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) actionBar.setDisplayHomeAsUpEnabled(true);

        // Set talk detail layout
        setContentView(R.layout.talkdetails);

        // Get info from the intent scratch board
        try {
            this.talkJSON = new JSONObject(getIntent().getStringExtra("talkJSON"));
            this.eventJSON = new JSONObject(getIntent().getStringExtra("eventJSON"));
        } catch (JSONException e) {
            Log.e(JIActivity.LOG_JOINDIN_APP, "No talk and/or event passed to activity", e);
        }

        // Set correct text in layout
        getSupportActionBar().setTitle(eventJSON.optString("name"));
        getSupportActionBar().setSubtitle(R.string.TalkDetailSubtitle);

        TextView t;
        t = (TextView) this.findViewById(R.id.TalkDetailCaption);
        t.setText(this.talkJSON.optString("talk_title"));
        t = (TextView) this.findViewById(R.id.TalkDetailSpeaker);

        ArrayList<String> speakerNames = new ArrayList<>();
        try {
            JSONArray speakerEntries = this.talkJSON.getJSONArray("speakers");
            for (int i = 0; i < speakerEntries.length(); i++) {
                speakerNames.add(speakerEntries.getJSONObject(i).getString("speaker_name"));
            }
        } catch (JSONException e) {
            Log.d(JIActivity.LOG_JOINDIN_APP, "Couldn't get speaker names");
            e.printStackTrace();
        }
        if (speakerNames.size() == 1) {
            t.setText("Speaker: " + speakerNames.get(0));
        } else if (speakerNames.size() > 1) {
            String allSpeakers = TextUtils.join(", ", speakerNames);
            t.setText("Speakers: " + allSpeakers);
        } else {
            t.setText("");
        }
        t = (TextView) this.findViewById(R.id.TalkDetailDescription);
        String s = this.talkJSON.optString("talk_description");
        // Strip away newlines and additional spaces. Somehow these get added when
        // adding talks. It doesn't really look nice when viewing.
        s = s.replace("\n", "");
        s = s.replace("  ", "");
        t.setText(s);
        Linkify.addLinks(t, Linkify.ALL);

        // Update view X comments button
        buttonCommentCount(this.talkJSON.optInt("comment_count"));

        // Add handlers to button
        Button button = (Button) findViewById(R.id.ButtonNewComment);
        button.setOnClickListener(this);
        button = (Button) findViewById(R.id.ButtonViewComment);
        button.setOnClickListener(this);
    }

    public void onResume() {
        super.onResume();

        Button button = (Button) findViewById(R.id.ButtonNewComment);

        // Button is only present if we're authenticated
        if (!isAuthenticated()) {
            button.setVisibility(View.GONE);
        } else {
            button.setVisibility(View.VISIBLE);
        }
    }

    /**
     * Talk detail allows a user to star talks
     *
     * @param menu Menu
     * @return Display menu?
     */
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.talk_detail_menu, menu);
        starTalkButton = menu.findItem(R.id.starTalk);

        // We have to wait until this menu is created before we can set the starred status
        talkIsStarred = (isAuthenticated() && talkJSON.optBoolean("starred", false));
        updateStarredIcon(talkIsStarred);

        return true;
    }

    /**
     * Handle menu clicks
     * Essentially this is just the "star talk" option
     *
     * @param item Menu item
     */
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.starTalk:
                // Adjust talk status via API
                // Only for authenticated users though
                if (!isAuthenticated()) {
                    Toast toast = Toast.makeText(this, getString(R.string.activityTalkDetailAuthRequiredForStarring), Toast.LENGTH_LONG);
                    toast.show();

                    return true;
                }

                if (talkIsStarred) {
                    markTalkStarred(false);
                } else {
                    markTalkStarred(true);
                    starTalkButton.setIcon(getResources().getDrawable(R.drawable.ic_star_active));
                }
                talkIsStarred = !talkIsStarred;
                break;
        }

        return super.onOptionsItemSelected(item);
    }

    public void onClick(View v) {
        if (v == findViewById(R.id.ButtonNewComment)) {
            // Goto comment activity and add new comment to this talk
            Intent myIntent = new Intent();
            myIntent.setClass(getApplicationContext(), AddComment.class);

            myIntent.putExtra("commentType", "talk");
            myIntent.putExtra("talkJSON", this.talkJSON.toString());
            myIntent.putExtra("eventJSON", this.eventJSON.toString());
            startActivityForResult(myIntent, AddComment.CODE_COMMENT);
        }
        if (v == findViewById(R.id.ButtonViewComment)) {
            // Goto talk comments activity and display all comments about this talk
            Intent myIntent = new Intent();
            myIntent.setClass(getApplicationContext(), TalkComments.class);

            myIntent.putExtra("talkJSON", this.talkJSON.toString());
            myIntent.putExtra("eventJSON", this.eventJSON.toString());
            startActivity(myIntent);
        }
    }

    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        switch (requestCode) {
            case AddComment.CODE_COMMENT:
                if (resultCode == RESULT_OK) {
                    // reload the comments
                    try {
                        updateCommentCount(this.talkJSON.getInt("rowID"), this.talkJSON.getString("uri"));
                    } catch (JSONException e) {
                        // nothing
                    }
                }
        }
    }

    protected void updateCommentCount(int talkID, String commentsUri) throws JSONException {
        JIRest rest = new JIRest(TalkDetail.this);
        int result = rest.getJSONFullURI(commentsUri);

        if (result == JIRest.OK) {
            JSONObject fullResponse = rest.getJSONResult();
            JSONArray json = fullResponse.getJSONArray("talks");
            if (json.length() != 1) {
                // TODO: error, we were expecting a single talk
            }

            DataHelper dh = DataHelper.getInstance(this);
            JSONObject thisTalk = json.getJSONObject(0);
            dh.insertTalk(talkID, thisTalk);
            this.talkJSON = thisTalk;

            buttonCommentCount(this.talkJSON.optInt("comment_count"));
        }
    }

    protected void buttonCommentCount(int commentCount) {
        Button b = (Button) this.findViewById(R.id.ButtonViewComment);
        if (commentCount == 1) {
            b.setText(String.format(getString(R.string.generalViewCommentSingular), commentCount));
        } else {
            b.setText(String.format(getString(R.string.generalViewCommentPlural), commentCount));
        }
    }

    /**
     * Mark the talk as starred - update the icon and submit the request
     *
     * @param isStarred Is it starred?
     */
    protected void markTalkStarred(final boolean isStarred) {
        updateStarredIcon(isStarred);

        final Context context = this;
        new Thread() {
            public void run() {
                displayProgressBarCircular(true);

                String talkURI = TalkDetail.this.talkJSON.optString("uri");
                boolean result = doStarTalk(isStarred);
                if (result) {
                    DataHelper.getInstance().markTalkStarred(talkURI, isStarred);

                    runOnUiThread(new Runnable() {
                        public void run() {
                            String toastMessage = context.getString(R.string.generalSuccessStarred);
                            if (!isStarred) {
                                toastMessage = context.getString(R.string.generalSuccessUnstarred);
                            }
                            Toast toast = Toast.makeText(getApplicationContext(), toastMessage, Toast.LENGTH_LONG);
                            toast.show();
                        }
                    });
                } else {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            updateStarredIcon(!isStarred);
                            Toast.makeText(context, context.getString(R.string.generalCouldntUpdateStarredStatus), Toast.LENGTH_SHORT).show();
                        }
                    });
                }

                displayProgressBarCircular(false);
            }
        }.start();
    }

    /**
     * Update the starred icon to a set state
     *
     * @param isStarred Is it starred?
     */
    protected void updateStarredIcon(boolean isStarred) {
        int iconID = (isStarred) ? R.drawable.ic_star_active : R.drawable.ic_star;
        starTalkButton.setIcon(getResources().getDrawable(iconID));
    }

    /**
     * Post/delete the starred status from this talk
     *
     * @param initialState Initial state.
     * @return
     */
    private boolean doStarTalk(boolean initialState) {
        JIRest rest = new JIRest(this);
        int error = rest.requestToFullURI(this.talkJSON.optString("starred_uri"), null, initialState ? JIRest.METHOD_POST : JIRest.METHOD_DELETE);

        return error == JIRest.OK;
    }
}
