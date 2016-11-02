package in.joind.activity;

/*
 * Displays events comments
 */

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.support.v7.app.ActionBar;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;

import com.markupartist.android.widget.PullToRefreshListView;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;

import in.joind.api.DataHelper;
import in.joind.api.JIRest;
import in.joind.R;
import in.joind.adapter.EventCommentAdapter;

public class EventComments extends JIActivity implements OnClickListener {
    private EventCommentAdapter m_eventCommentAdapter;    // adapter for listview
    private JSONObject eventJSON;
    private int event_id;

    final public static int CODE_COMMENT = 1;

    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Allow ActionBar 'up' navigation
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) actionBar.setDisplayHomeAsUpEnabled(true);

        // Set layout
        setContentView(R.layout.comments);

        // Get info from the intent scratch board
        try {
            this.eventJSON = new JSONObject(getIntent().getStringExtra("eventJSON"));
        } catch (JSONException e) {
            Log.e(JIActivity.LOG_JOINDIN_APP, "No event passed to activity", e);
        }

        // Set correct text in layout
        getSupportActionBar().setTitle(this.eventJSON.optString("name"));

        // Initialize comment list
        ArrayList<JSONObject> m_eventcomments = new ArrayList<>();
        m_eventCommentAdapter = new EventCommentAdapter(this, R.layout.talkrow, m_eventcomments);
        final PullToRefreshListView eventcommentlist = (PullToRefreshListView) findViewById(R.id.EventDetailComments);
        eventcommentlist.setAdapter(m_eventCommentAdapter);

        // Display the cached event comments
        event_id = EventComments.this.eventJSON.optInt("rowID");
        displayEventComments(event_id);

        // Add handler to button
        Button button = (Button) findViewById(R.id.ButtonNewComment);
        button.setOnClickListener(this);

        eventcommentlist.setOnRefreshListener(new PullToRefreshListView.OnRefreshListener() {
            @Override
            public void onRefresh() {
                try {
                    loadEventComments(event_id, eventJSON.getString("comments_uri"));
                } catch (JSONException e) {
                    Log.e(JIActivity.LOG_JOINDIN_APP, "No comments URI available");
                    eventcommentlist.onRefreshComplete();
                }
            }
        });

        // Load new comments from the joind.in API and display them
        try {
            loadEventComments(event_id, this.eventJSON.getString("comments_uri"));
        } catch (JSONException e) {
            Log.e(JIActivity.LOG_JOINDIN_APP, "No comments URI available");
        }
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

    public void onClick(View v) {
        if (v == findViewById(R.id.ButtonNewComment)) {
            // Start activity to add new comment
            Intent myIntent = new Intent();
            myIntent.setClass(getApplicationContext(), AddComment.class);

            // commentType decides if it's an event or talk comment
            myIntent.putExtra("commentType", "event");
            String s = getIntent().getStringExtra("eventJSON");
            myIntent.putExtra("eventJSON", s);
            startActivityForResult(myIntent, CODE_COMMENT);
        }
    }

    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        switch (requestCode) {
            case CODE_COMMENT:
                if (resultCode == Activity.RESULT_OK) {
                    // reload the comments
                    try {
                        loadEventComments(event_id, this.eventJSON.getString("comments_uri"));
                    } catch (JSONException e) {
                        // nothing
                    }
                }
        }
    }

    // Display all event comments in the event listview/adapter
    public int displayEventComments(int event_id) {
        DataHelper dh = DataHelper.getInstance(this);

        m_eventCommentAdapter.clear();
        int count = dh.populateEventComments(event_id, m_eventCommentAdapter);
        m_eventCommentAdapter.notifyDataSetChanged();

        // Update caption bar
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            if (count == 1) {
                actionBar.setSubtitle(String.format(getString(R.string.generalCommentSingular), count));
            } else {
                actionBar.setSubtitle(String.format(getString(R.string.generalCommentPlural), count));
            }
        }

        ((PullToRefreshListView) findViewById(R.id.EventDetailComments)).onRefreshComplete();

        // Return number of event comments.
        return count;
    }

    // Load all event comments from joind.in API and display them
    public void loadEventComments(final int eventRowID, final String commentsURI) {
        // Display progress bar
        displayProgressBarCircular(true);

        new Thread() {
            public void run() {
                // Load event comments from joind.in API
                String uriToUse = commentsURI;
                JSONObject fullResponse;
                JSONObject metaObj = new JSONObject();
                JIRest rest = new JIRest(EventComments.this);
                boolean isFirst = true;
                DataHelper dh = DataHelper.getInstance(EventComments.this);

                try {
                    do {
                        int error = rest.getJSONFullURI(uriToUse);

                        if (error == JIRest.OK) {
                            // Remove all event comments for this event and insert newly loaded comments
                            fullResponse = rest.getJSONResult();
                            metaObj = fullResponse.getJSONObject("meta");

                            if (isFirst) {
                                dh.deleteCommentsFromEvent(eventRowID);
                                isFirst = false;
                            }
                            JSONArray json = fullResponse.getJSONArray("comments");

                            for (int i = 0; i != json.length(); i++) {
                                JSONObject json_eventComment = json.getJSONObject(i);

                                // Private comments are not returned, so just insert anyway
                                dh.insertEventComment(eventRowID, json_eventComment);
                            }
                            uriToUse = metaObj.getString("next_page");
                        }
                    } while (metaObj.getInt("count") > 0);
                } catch (JSONException e) {

                    // Something when wrong. Just display the current comments
                    runOnUiThread(new Runnable() {
                        public void run() {
                            displayEventComments(eventRowID);
                        }
                    });
                }

                // Remove progress bar
                displayProgressBarCircular(false);
                runOnUiThread(new Runnable() {
                    public void run() {
                        displayEventComments(eventRowID);
                    }
                });
            }
        }.start();
    }

}
