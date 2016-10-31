package in.joind.activity;

/*
 * Displays detailed information about a talk (info, comments etc)
 */

import android.content.Intent;
import android.os.Bundle;
import android.support.v4.widget.SwipeRefreshLayout;
import android.support.v7.app.ActionBar;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.ListView;
import android.widget.Toast;

import com.crashlytics.android.Crashlytics;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;

import in.joind.api.DataHelper;
import in.joind.api.JIRest;
import in.joind.R;
import in.joind.adapter.TalkCommentAdapter;

public class TalkComments extends JIActivity implements OnClickListener {
    private TalkCommentAdapter m_talkCommentAdapter;  // adapter for listView
    private JSONObject talkJSON;

    SwipeRefreshLayout refreshLayout;

    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Allow ActionBar 'up' navigation
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) actionBar.setDisplayHomeAsUpEnabled(true);

        // Set comment layout
        setContentView(R.layout.comments);

        // Get info from the intent scratch board
        JSONObject eventJSON;
        try {
            this.talkJSON = new JSONObject(getIntent().getStringExtra("talkJSON"));
            eventJSON = new JSONObject(getIntent().getStringExtra("eventJSON"));
        } catch (JSONException e) {
            Log.e(JIActivity.LOG_JOINDIN_APP, "No talk passed to activity", e);
            Crashlytics.setString("talkComments_talkJSON", getIntent().getStringExtra("talkJSON"));
            Crashlytics.setString("talkComments_eventJSON", getIntent().getStringExtra("eventJSON"));

            // Tell the user
            showToast(getString(R.string.activityTalkCommentsFailedJSON), Toast.LENGTH_LONG);
            finish();
            return;
        }

        // Set correct text in the layout
        getSupportActionBar().setTitle(eventJSON.optString("name"));
        getSupportActionBar().setSubtitle(talkJSON.optString("talk_title"));

        // Add handler to button
        Button button = (Button) findViewById(R.id.ButtonNewComment);
        button.setOnClickListener(this);

        // Initialize comment list
        ArrayList<JSONObject> m_talkcomments = new ArrayList<>();
        m_talkCommentAdapter = new TalkCommentAdapter(this, R.layout.talkrow, m_talkcomments);
        refreshLayout = (SwipeRefreshLayout) findViewById(R.id.commentRefreshLayout);
        refreshLayout.setColorSchemeResources(R.color.joindin_turquoise);
        final ListView talkCommentList = (ListView) findViewById(R.id.EventDetailComments);
        talkCommentList.setAdapter(m_talkCommentAdapter);

        // Display the cached comments
        final int talk_id = talkJSON.optInt("rowID");
        displayTalkComments(talk_id);

        refreshLayout.setOnRefreshListener(new SwipeRefreshLayout.OnRefreshListener() {
            @Override
            public void onRefresh() {
                try {
                    loadTalkComments(talk_id, talkJSON.getString("verbose_comments_uri"));
                } catch (JSONException e) {
                    Log.e(JIActivity.LOG_JOINDIN_APP, "No comments URI available (talk comments)");
                    refreshLayout.setRefreshing(false);
                }
            }
        });

        // Load new comments for this talk and display them
        try {
            loadTalkComments(talk_id, this.talkJSON.getString("verbose_comments_uri"));
        } catch (JSONException e) {
            Log.e(JIActivity.LOG_JOINDIN_APP, "No comments URI available (talk comments)");
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
            // Goto new talk comment activity
            Intent myIntent = new Intent();
            myIntent.setClass(getApplicationContext(), AddComment.class);

            myIntent.putExtra("commentType", "talk");
            myIntent.putExtra("talkJSON", getIntent().getStringExtra("talkJSON"));
            startActivityForResult(myIntent, AddComment.CODE_COMMENT);
        }
    }

    // This will add all comments for specified talk in the talkcomment listview / adapter
    public int displayTalkComments(int talk_id) {
        DataHelper dh = DataHelper.getInstance(this);

        m_talkCommentAdapter.clear();
        int count = dh.populateTalkComments(talk_id, m_talkCommentAdapter);
        m_talkCommentAdapter.notifyDataSetChanged();

        // Update caption bar
        if (count == 1) {
            setTitle(String.format(getString(R.string.generalCommentSingular), count));
        } else {
            setTitle(String.format(getString(R.string.generalCommentPlural), count));
        }

        refreshLayout.setRefreshing(false);

        // Return the number of comments found
        return count;
    }

    // Load all talks from the joind.in API, populate database and display the new talks
    public void loadTalkComments(final int talk_id, final String commentsURI) {
        // Display progress bar
        displayProgressBarCircular(true);

        // Do loading of talks in separate thread
        new Thread() {
            public void run() {
                // Load event comments from joind.in API
                String uriToUse = commentsURI;
                JSONObject fullResponse;
                JSONObject metaObj = new JSONObject();
                JIRest rest = new JIRest(TalkComments.this);
                boolean isFirst = true;
                DataHelper dh = DataHelper.getInstance(TalkComments.this);

                try {
                    do {
                        int error = rest.getJSONFullURI(uriToUse);

                        if (error == JIRest.OK) {
                            // Remove all event comments for this event and insert newly loaded comments
                            fullResponse = rest.getJSONResult();
                            metaObj = fullResponse.getJSONObject("meta");

                            if (isFirst) {
                                dh.deleteCommentsFromTalk(talk_id);
                                isFirst = false;
                            }
                            JSONArray json = fullResponse.getJSONArray("comments");

                            for (int i = 0; i != json.length(); i++) {
                                JSONObject json_eventComment = json.getJSONObject(i);

                                // Private comments are not returned, so just insert anyway
                                dh.insertTalkComment(talk_id, json_eventComment);
                            }
                            uriToUse = metaObj.getString("next_page");
                        }
                    } while (metaObj.getInt("count") > 0);
                } catch (JSONException e) {

                    // Something when wrong. Just display the current comments
                    runOnUiThread(new Runnable() {
                        public void run() {
                            displayTalkComments(talk_id);
                        }
                    });
                }

                // Remove progress bar
                displayProgressBarCircular(false);
                runOnUiThread(new Runnable() {
                    public void run() {
                        displayTalkComments(talk_id);
                    }
                });
            }
        }.start();
    }

    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        switch (requestCode) {
            case AddComment.CODE_COMMENT:
                if (resultCode == RESULT_OK) {
                    // reload the comments
                    try {
                        loadTalkComments(this.talkJSON.getInt("rowID"), this.talkJSON.getString("verbose_comments_uri"));
                    } catch (JSONException e) {
                        // nothing
                    }
                }
        }
    }
}
