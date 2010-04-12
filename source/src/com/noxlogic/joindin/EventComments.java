package com.noxlogic.joindin;

/*
 * Displays events comments
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

public class EventComments extends JIActivity implements OnClickListener {
    private JIEventCommentAdapter m_eventCommentAdapter;    // adapter for listview
    private JSONObject eventJSON;
    private DataHelper dh;

    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Set layout
        setContentView(R.layout.comments);

        this.dh = new DataHelper(this);

        // Get info from the intent scratch board
        try {
            this.eventJSON = new JSONObject(getIntent().getStringExtra("eventJSON"));
        } catch (JSONException e) {
            e.printStackTrace();
        }

        // Set correct text in layout
        TextView t;
        t = (TextView) this.findViewById(R.id.EventDetailCaption);
        t.setText (this.eventJSON.optString("event_name"));

        // Update caption bar
        int commentCount = this.eventJSON.optInt("num_comments");
        if (commentCount == 1){
            setTitle (String.format(getString(R.string.generalCommentSingular), commentCount));
        } else {
            setTitle (String.format(getString(R.string.generalCommentPlural), commentCount));
        }

        // Init comment list
        ArrayList<JSONObject> m_eventcomments = new ArrayList<JSONObject>();
        m_eventCommentAdapter = new JIEventCommentAdapter(this, R.layout.talkrow, m_eventcomments);
        ListView eventcommentlist =(ListView)findViewById(R.id.EventDetailComments);
        eventcommentlist.setAdapter(m_eventCommentAdapter);

        // Display the cached event comments
        int event_id = EventComments.this.eventJSON.optInt("ID");
        displayEventComments (event_id);

        // Add handler to button
        Button button = (Button)findViewById(R.id.ButtonNewComment);
        button.setOnClickListener(this);

        // Load new comments from the joind.in API and display them
        loadEventComments (event_id);
    }

    public void onClick(View v) {
        if (v == findViewById(R.id.ButtonNewComment)) {
            // Start activity to add new comment
            Intent myIntent = new Intent ();
            myIntent.setClass(getBaseContext(), AddComment.class);

            // commentType decides if it's an event or talk comment
            myIntent.putExtra("commentType", "event");
            String s = getIntent().getStringExtra("eventJSON");
            myIntent.putExtra("eventJSON", s);
            startActivity(myIntent);
        }
    };


    // Display all event comments in the event listview/adapter
    public int displayEventComments (int event_id) {
        m_eventCommentAdapter.clear();
        int count = this.dh.populateEventComments(event_id, m_eventCommentAdapter);
        m_eventCommentAdapter.notifyDataSetChanged();

        // Return number of event comments.
        return count;
    }


    // Load all event comments from joind.in API and display them
    public void loadEventComments (final int event_id) {
        // Display progress bar
        displayProgressBar (true);

        new Thread () {
            public void run() {
                // Load event comments from joind.in API
                JIRest rest = new JIRest(EventComments.this);
                int error = rest.postXML("http://joind.in/api/event", "<request>"+JIRest.getAuthXML(EventComments.this)+"<action type=\"getcomments\" output=\"json\"><event_id>"+event_id+"</event_id></action></request>");
                // @TODO: Still no error handling

                if (error == JIRest.OK) {
                    // Remove all event comments for this event and insert newly loaded comments
                    try {
                        JSONArray json = new JSONArray(rest.getResult());
                        EventComments.this.dh.deleteCommentsFromEvent(event_id);
                        for (int i=0; i!=json.length(); i++) {
                            JSONObject json_eventcomment = json.getJSONObject(i);

                            // Don't add private events
                            if (json_eventcomment.optInt("private") == 0)
                                EventComments.this.dh.insertEventComment (json_eventcomment);
                        }
                    } catch (JSONException e) { }
                        // Something went wrong. Just act like nothing happened
                        runOnUiThread(new Runnable() {
                            public void run() {
                                displayEventComments (event_id);
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
 * Adapter that hold our event comment rows. See  JIEventAdapter class in main.java for more info
 */
class JIEventCommentAdapter extends ArrayAdapter<JSONObject> {
      private ArrayList<JSONObject> items;
      private Context context;

      public JIEventCommentAdapter(Context context, int textViewResourceId, ArrayList<JSONObject> items) {
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
          if (t2 != null) t2.setText(o.isNull("cname") ? "("+this.context.getString(R.string.generalAnonymous)+") " : o.optString("cname")+" ");
          if (t3 != null) t3.setText(DateFormat.getDateInstance().format(o.optLong("date_made")*1000));

          ImageView r = (ImageView) v.findViewById(R.id.CommentRowRate);
          r.setVisibility(View.GONE);

          return v;
      }
}