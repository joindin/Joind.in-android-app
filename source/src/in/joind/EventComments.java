package in.joind;

/*
 * Displays events comments
 */

import java.util.ArrayList;

import in.joind.R;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.app.Activity;
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
    private JSONObject talkJSON;
    private int event_id;

    final public static int CODE_COMMENT = 1;

    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Set layout
        setContentView(R.layout.comments);

        // Get info from the intent scratch board
        try {
            this.eventJSON = new JSONObject(getIntent().getStringExtra("eventJSON"));
        } catch (JSONException e) {
            android.util.Log.e("JoindInApp", "No event passed to activity", e);
        }

        // Set correct text in layout
        TextView t;
        t = (TextView) this.findViewById(R.id.EventDetailCaption);
        t.setText (this.eventJSON.optString("name"));

        // Initialize comment list
        ArrayList<JSONObject> m_eventcomments = new ArrayList<JSONObject>();
        m_eventCommentAdapter = new JIEventCommentAdapter(this, R.layout.talkrow, m_eventcomments);
        ListView eventcommentlist =(ListView)findViewById(R.id.EventDetailComments);
        eventcommentlist.setAdapter(m_eventCommentAdapter);

        // Display the cached event comments
        event_id = EventComments.this.eventJSON.optInt("rowID");
        displayEventComments (event_id);

        // Add handler to button
        Button button = (Button)findViewById(R.id.ButtonNewComment);
        button.setOnClickListener(this);

        // Load new comments from the joind.in API and display them
        try {
            loadEventComments (event_id, this.eventJSON.getString("comments_uri"));
        } catch (JSONException e) {
            android.util.Log.e("JoindInApp", "No comments URI available");
        }
    }

    public void onResume() {
        super.onResume();

        Button button = (Button)findViewById(R.id.ButtonNewComment);

        // Button is only present if we're authenticated
        if (!isAuthenticated()) {
            button.setVisibility(View.GONE);
        }
        else {
            button.setVisibility(View.VISIBLE);
        }
    }

    public void onClick(View v) {
        if (v == findViewById(R.id.ButtonNewComment)) {
            // Start activity to add new comment
            Intent myIntent = new Intent ();
            myIntent.setClass(getApplicationContext(), AddComment.class);

            // commentType decides if it's an event or talk comment
            myIntent.putExtra("commentType", "event");
            String s = getIntent().getStringExtra("eventJSON");
            myIntent.putExtra("eventJSON", s);
            startActivityForResult(myIntent, CODE_COMMENT);
        }
    };

    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        switch(requestCode) {
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
    public int displayEventComments (int event_id) {
        DataHelper dh = DataHelper.getInstance();

        m_eventCommentAdapter.clear();
        int count = dh.populateEventComments(event_id, m_eventCommentAdapter);
        m_eventCommentAdapter.notifyDataSetChanged();

        // Update caption bar
        if (count == 1){
            setTitle (String.format(getString(R.string.generalCommentSingular), count));
        } else {
            setTitle (String.format(getString(R.string.generalCommentPlural), count));
        }

        // Return number of event comments.
        return count;
    }


    // Load all event comments from joind.in API and display them
    public void loadEventComments (final int eventRowID, final String commentsURI) {
        // Display progress bar
        displayProgressBar (true);

        new Thread () {
            public void run() {
                // Load event comments from joind.in API
                String uriToUse = commentsURI;
                JSONObject fullResponse;
                JSONObject metaObj = new JSONObject();
                JIRest rest = new JIRest (EventComments.this);
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
                                dh.deleteCommentsFromEvent(eventRowID);
                                isFirst = false;
                            }
                            JSONArray json = fullResponse.getJSONArray("comments");

                            for (int i=0; i!=json.length(); i++) {
                                JSONObject json_eventComment = json.getJSONObject(i);

                                // Private comments are not returned, so just insert anyway
                                dh.insertEventComment (eventRowID, json_eventComment);
                            }
                            uriToUse = metaObj.getString("next_page");
                        }
                    } while (metaObj.getInt("count") > 0);
                } catch (JSONException e) {

                    // Something when wrong. Just display the current comments
                    runOnUiThread(new Runnable() {
                        public void run() {
                            displayEventComments (eventRowID);
                        }
                    });
                }

                // Remove progress bar
                displayProgressBar (false);
                runOnUiThread(new Runnable() { public void run() { displayEventComments(eventRowID); }});
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
      private ImageLoader image_loader;			// gravatar image loader

      public JIEventCommentAdapter(Context context, int textViewResourceId, ArrayList<JSONObject> items) {
          super(context, textViewResourceId, items);
          this.context = context;
          this.items = items;

          this.image_loader = new ImageLoader(context.getApplicationContext(), "gravatars");
      }

      public View getView(int position, View convertview, ViewGroup parent) {
          View v = convertview;
          if (v == null) {
                LayoutInflater vi = (LayoutInflater)this.context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
                v = vi.inflate(R.layout.commentrow, null);
          }

          JSONObject o = items.get(position);
          if (o == null) return v;

          ImageView el = (ImageView) v.findViewById(R.id.CommentRowGravatar);
          el.setTag("");
          el.setVisibility(View.GONE);

          if (o.optInt("user_id") > 0) {
        	  String filename = "user"+o.optString("user_id")+".jpg";
        	  el.setTag(filename);
        	  image_loader.displayImage("http://joind.in/inc/img/user_gravatar/", filename, (Activity)context, el);
          }

          String commentDate = DateHelper.parseAndFormat(o.optString("created_date"), "d LLL yyyy");
          TextView t1 = (TextView) v.findViewById(R.id.CommentRowComment);
          TextView t2 = (TextView) v.findViewById(R.id.CommentRowUName);
          TextView t3 = (TextView) v.findViewById(R.id.CommentRowDate);
          if (t1 != null) t1.setText(o.optString("comment"));
          if (t2 != null) t2.setText(o.isNull("user_display_name") ? "("+this.context.getString(R.string.generalAnonymous)+") " : o.optString("user_display_name")+" ");
          if (t3 != null) t3.setText(commentDate);

          ImageView r = (ImageView) v.findViewById(R.id.CommentRowRate);
          r.setVisibility(View.GONE);

          return v;
      }
}
