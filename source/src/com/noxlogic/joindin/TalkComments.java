package com.noxlogic.joindin;

/*
 * Displays detailed information about a talk (info, comments etc)
 */

import java.text.DateFormat;
import java.util.ArrayList;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.text.util.Linkify;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.View.OnClickListener;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;

public class TalkComments extends JIActivity implements OnClickListener {
    private JITalkCommentAdapter m_talkCommentAdapter;  // adapter for listview
    private JSONObject talkJSON;

    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Set comment layout
        setContentView(R.layout.comments);

        // Get info from the intent scratch board
        try {
            this.talkJSON = new JSONObject(getIntent().getStringExtra("talkJSON"));
        } catch (JSONException e) {
            android.util.Log.e("JoindInApp", "No talk passed to activity", e);
        }

        // Set correct text in the layout
        TextView t;
        t = (TextView) this.findViewById(R.id.EventDetailCaption);
        t.setText(this.talkJSON.optString("talk_title"));

        // Add handler to button
        Button button = (Button) findViewById(R.id.ButtonNewComment);
        button.setOnClickListener(this);

        // Initialize comment list
        ArrayList<JSONObject> m_talkcomments = new ArrayList<JSONObject>();
        m_talkCommentAdapter = new JITalkCommentAdapter(this, R.layout.talkrow, m_talkcomments);
        ListView talkcommentlist = (ListView) findViewById(R.id.EventDetailComments);
        talkcommentlist.setAdapter(m_talkCommentAdapter);

        // Display the cached comments
        int talk_id = TalkComments.this.talkJSON.optInt("rowID");
        displayTalkComments(talk_id);

        // Load new comments for this talk and display them
        try {
            loadTalkComments(talk_id, this.talkJSON.getString("comments_uri"));
        } catch (JSONException e) {
            android.util.Log.e("JoindInApp", "No comments URI available (talk comments)");
        }
    }


    public void onClick(View v) {
        if (v == findViewById(R.id.ButtonNewComment)) {
            // Goto new talk comment activity
            Intent myIntent = new Intent();
            myIntent.setClass(getApplicationContext(), AddComment.class);

            myIntent.putExtra("commentType", "talk");
            myIntent.putExtra("talkJSON", getIntent().getStringExtra("talkJSON"));
            startActivity(myIntent);
        }
    }

    ;

    // This will add all comments for specified talk in the talkcomment listview / adapter
    public int displayTalkComments(int talk_id) {
        DataHelper dh = DataHelper.getInstance();

        m_talkCommentAdapter.clear();
        int count = dh.populateTalkComments(talk_id, m_talkCommentAdapter);
        m_talkCommentAdapter.notifyDataSetChanged();

        // Update caption bar
        if (count == 1) {
            setTitle(String.format(getString(R.string.generalCommentSingular), count));
        } else {
            setTitle(String.format(getString(R.string.generalCommentPlural), count));
        }

        // Return the number of comments found
        return count;
    }


    // Load all talks from the joind.in API, populate database and display the new talks
    public void loadTalkComments(final int talk_id, final String commentsURI) {
        // Display progress bar
        displayProgressBar(true);

        // Do loading of talks in separate thread
        new Thread() {
            public void run() {
                // Load event comments from joind.in API
                String uriToUse = commentsURI;
                JSONObject fullResponse;
                JSONObject metaObj = new JSONObject();
                JIRest rest = new JIRest(TalkComments.this);
                boolean isFirst = true;
                DataHelper dh = DataHelper.getInstance();

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
                displayProgressBar(false);
                runOnUiThread(new Runnable() {
                    public void run() {
                        displayTalkComments(talk_id);
                    }
                });
            }
        }.start();
    }
}


/**
 * Adapter that hold our talk comment rows. See  JIEventAdapter class in main.java for more info
 */
class JITalkCommentAdapter extends ArrayAdapter<JSONObject> {
    private ArrayList<JSONObject> items;
    private Context context;
    private ImageLoader image_loader;            // gravatar image loader

    public JITalkCommentAdapter(Context context, int textViewResourceId, ArrayList<JSONObject> items) {
        super(context, textViewResourceId, items);
        this.context = context;
        this.items = items;

        this.image_loader = new ImageLoader(context.getApplicationContext(), "gravatars");
    }

    public View getView(int position, View convertview, ViewGroup parent) {
        View v = convertview;
        if (v == null) {
            LayoutInflater vi = (LayoutInflater) this.context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
            v = vi.inflate(R.layout.commentrow, null);
        }

        JSONObject o = items.get(position);
        if (o == null) return v;

        ImageView el = (ImageView) v.findViewById(R.id.CommentRowGravatar);
        el.setTag("");
        el.setVisibility(View.GONE);

        if (o.optInt("user_id") > 0) {
            String filename = "user" + o.optString("user_id") + ".jpg";
            el.setTag(filename);
            image_loader.displayImage("http://joind.in/inc/img/user_gravatar/", filename, (Activity) context, el);
        }

        String commentDate = DateHelper.parseAndFormat(o.optString("created_date"), "d LLL yyyy");
        TextView t1 = (TextView) v.findViewById(R.id.CommentRowComment);
        TextView t2 = (TextView) v.findViewById(R.id.CommentRowUName);
        TextView t3 = (TextView) v.findViewById(R.id.CommentRowDate);
        if (t1 != null) t1.setText(o.optString("comment"));
        if (t2 != null)
            t2.setText(o.isNull("user_display_name") ? "(" + this.context.getString(R.string.generalAnonymous) + ") " : o.optString("user_display_name") + " ");
        if (t3 != null) t3.setText(commentDate);
        Linkify.addLinks(t1, Linkify.ALL);

        ImageView r = (ImageView) v.findViewById(R.id.CommentRowRate);
        switch (o.optInt("rating")) {
            default:
            case 0:
                r.setBackgroundResource(R.drawable.rating_0);
                break;
            case 1:
                r.setBackgroundResource(R.drawable.rating_1);
                break;
            case 2:
                r.setBackgroundResource(R.drawable.rating_2);
                break;
            case 3:
                r.setBackgroundResource(R.drawable.rating_3);
                break;
            case 4:
                r.setBackgroundResource(R.drawable.rating_4);
                break;
            case 5:
                r.setBackgroundResource(R.drawable.rating_5);
                break;
        }

        return v;
    }
}
