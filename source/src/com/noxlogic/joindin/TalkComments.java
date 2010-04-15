package com.noxlogic.joindin;

/*
 * Displays detailed information about a talk (info, comments etc)
 */

import java.text.DateFormat;
import java.util.ArrayList;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.View.OnClickListener;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;

public class TalkComments extends JIActivity implements OnClickListener  {
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
        t.setText (this.talkJSON.optString("talk_title"));

        // Update caption bar
        int commentCount = this.talkJSON.optInt("comment_count");
        if (commentCount == 1){
            setTitle (String.format(getString(R.string.generalCommentSingular), commentCount));
        } else {
            setTitle (String.format(getString(R.string.generalCommentPlural), commentCount));
        }

        // Add handler to button
        Button button = (Button)findViewById(R.id.ButtonNewComment);
        button.setOnClickListener(this);

        // Init comment list
        ArrayList<JSONObject> m_talkcomments = new ArrayList<JSONObject>();
        m_talkCommentAdapter = new JITalkCommentAdapter(this, R.layout.talkrow, m_talkcomments);
        ListView talkcommentlist =(ListView)findViewById(R.id.EventDetailComments);
        talkcommentlist.setAdapter(m_talkCommentAdapter);

        // Display the cached comments
        int talk_id = TalkComments.this.talkJSON.optInt("ID");
        displayTalkComments (talk_id);

        // Load new comments for this talk and display them
        loadTalkComments (talk_id);
    }


    public void onClick(View v) {
        if (v == findViewById(R.id.ButtonNewComment)) {
            // Goto new talk comment activity
            Intent myIntent = new Intent ();
            myIntent.setClass(getBaseContext(), AddComment.class);

            myIntent.putExtra("commentType", "talk");
            myIntent.putExtra("talkJSON", getIntent().getStringExtra("talkJSON"));
            startActivity(myIntent);
        }
    };

    // This will add all comments for specified talk int he talkcomment listview / adapter
    public int displayTalkComments (int talk_id) {
        m_talkCommentAdapter.clear();
        int count = this.dh.populateTalkComments(talk_id, m_talkCommentAdapter);
        m_talkCommentAdapter.notifyDataSetChanged();

        // Return the number of comments found
        return count;
    }


    // Load all talks from the joind.in API, populate database and display the new talks
    public void loadTalkComments (final int talk_id) {
        // Display progress bar
        displayProgressBar (true);

        // Do loading of talks in separate thread
        new Thread () {
            public void run() {
                // Connect to joind.in API and fetch all comments for this talk
                JIRest rest = new JIRest (TalkComments.this);
                int error = rest.postXML("talk", "<request>"+JIRest.getAuthXML(TalkComments.this)+"<action type=\"getcomments\" output=\"json\"><talk_id>"+talk_id+"</talk_id></action></request>");

                // @TODO I see we do not catch errors?

                if (error == JIRest.OK) {
                    // Remove all comments from this talk from the DB and insert all new loaded comments (except private comments)
                    try {
                        JSONArray json = new JSONArray(rest.getResult());
                        TalkComments.this.dh.deleteCommentsFromTalk(talk_id);
                        for (int i=0; i!=json.length(); i++) {
                            JSONObject json_talkcomment = json.getJSONObject(i);
                            // If the comment is private, do not add it. We get them anyway from the joind.in API
                            if (json_talkcomment.optInt("private") == 0)
                                TalkComments.this.dh.insertTalkComment (json_talkcomment);
                        }
                    } catch (JSONException e) { }
                        // Something went wrong, display the talk comments like nothing happened
                        runOnUiThread(new Runnable() {
                            public void run() {
                                displayTalkComments (talk_id);
                            }
                    });
                }
                // Remove progress bar
                displayProgressBar (false);
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

      public JITalkCommentAdapter(Context context, int textViewResourceId, ArrayList<JSONObject> items) {
          super(context, textViewResourceId, items);
          this.context = context;
          this.items = items;
      }

      public View getView(int position, View convertview, ViewGroup parent) {
          View v = convertview;
          if (v == null) {
                LayoutInflater vi = (LayoutInflater)this.context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
                v = vi.inflate(R.layout.commentrow, null);
          }

          JSONObject o = items.get(position);
          if (o == null) return v;

          TextView t1 = (TextView) v.findViewById(R.id.CommentRowComment);
          TextView t2 = (TextView) v.findViewById(R.id.CommentRowUName);
          TextView t3 = (TextView) v.findViewById(R.id.CommentRowDate);
          if (t1 != null) t1.setText(o.optString("comment"));
          if (t2 != null) t2.setText(o.isNull("uname") ? "("+this.context.getString(R.string.generalAnonymous)+") " : o.optString("uname")+" ");
          if (t3 != null) t3.setText(DateFormat.getDateInstance().format(o.optLong("date_made")*1000));

          // TODO: Do dynamically.. but troubles finding getBaseContext()
          ImageView r = (ImageView) v.findViewById(R.id.CommentRowRate);
          switch (o.optInt("rating")) {
            default :
            case 0 : r.setBackgroundResource(R.drawable.rating_0); break;
            case 1 : r.setBackgroundResource(R.drawable.rating_1); break;
            case 2 : r.setBackgroundResource(R.drawable.rating_2); break;
            case 3 : r.setBackgroundResource(R.drawable.rating_3); break;
            case 4 : r.setBackgroundResource(R.drawable.rating_4); break;
            case 5 : r.setBackgroundResource(R.drawable.rating_5); break;
          }

          return v;
      }
}