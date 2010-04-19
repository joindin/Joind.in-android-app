package com.noxlogic.joindin;

/*
 * Displays all talks from specified event.
 */

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
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.AdapterView.OnItemClickListener;

public class EventTalks extends JIActivity implements OnClickListener {
    private JITalkAdapter m_talkAdapter;    // adapter for listview
    private JSONObject eventJSON;
    private JSONObject trackJSON = null;

    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Set layout
        setContentView(R.layout.eventtalks);

        // Get event ID from the intent scratch board
        try {
            this.eventJSON = new JSONObject(getIntent().getStringExtra("eventJSON"));
            if (getIntent().hasExtra("eventTrack")) {
                this.trackJSON = new JSONObject(getIntent().getStringExtra("eventTrack"));
            }
        } catch (JSONException e) {
            android.util.Log.e("JoindInApp", "No event passed to activity", e);
        }
        

        // Set all the event information
        TextView t;
        t = (TextView) this.findViewById(R.id.EventTalksCaption);
        t.setText (this.eventJSON.optString("event_name"));

        if (this.trackJSON == null) {
            t = (TextView) this.findViewById(R.id.EventTalksTrackName);
            t.setVisibility(View.INVISIBLE);
        } else {
            t = (TextView) this.findViewById(R.id.EventTalksTrackName);
            t.setVisibility(View.VISIBLE);
            t.setText ("("+this.trackJSON.optString("track_name")+")");
        }

        // Init talk list
        ArrayList<JSONObject> m_talks = new ArrayList<JSONObject>();
        m_talkAdapter = new JITalkAdapter(this, R.layout.talkrow, m_talks);
        ListView talklist =(ListView)findViewById(R.id.ListViewEventTalks);
        talklist.setAdapter(m_talkAdapter);

        // Add listview listener so when we click on an talk, we can display details
        talklist.setOnItemClickListener(new OnItemClickListener() {
            public void onItemClick(AdapterView<?>parent, View view, int pos, long id) {
                // open talk detail activity with event and talk data
                Intent myIntent = new Intent ();
                myIntent.setClass(getBaseContext(), TalkDetail.class);
                myIntent.putExtra("eventJSON", getIntent().getStringExtra("eventJSON"));
                myIntent.putExtra("talkJSON", parent.getAdapter().getItem(pos).toString());
                startActivity(myIntent);
            }
        });

        // Display cached talks
        int event_id = this.eventJSON.optInt("ID");
        int track_id = (this.trackJSON != null) ? this.trackJSON.optInt("ID") : -1;
        displayTalks (event_id, track_id);

        // Load new talks (in background)
        loadTalks (event_id, track_id);
    }


    public void onClick(View v) {
        if (v == findViewById(R.id.ButtonEventDetailsViewComments)) {
            // Open event comment activity
            Intent myIntent = new Intent ();
            myIntent.setClass(getBaseContext(), EventComments.class);
            myIntent.putExtra("eventJSON", getIntent().getStringExtra("eventJSON"));
            startActivity(myIntent);
        }
    };


    // Display talks in the talk list (adapter), depending on the track_id
    public int displayTalks (int event_id, int track_id) {
        DataHelper dh = DataHelper.getInstance();

        m_talkAdapter.clear();
        int talkCount = dh.populateTalks(event_id, track_id, m_talkAdapter);
        m_talkAdapter.notifyDataSetChanged();

        // Set titlebar with number of talks found
        if (talkCount == 1) {
            setTitle (String.format(getString(R.string.generalEventTalksSingular), talkCount));
        } else {
            setTitle (String.format(getString(R.string.generalEventTalksPlural), talkCount));
        }
        return talkCount;
    }


    // Load talks in new thread...
    public void loadTalks (final int event_id, final int track_id) {
        // Display progress bar
        displayProgressBar (true);

        new Thread () {
            public void run () {
                // Fetch talk data from joind.in API
                JIRest rest = new JIRest (EventTalks.this);
                int error = rest.postXML("event", "<request>"+JIRest.getAuthXML(EventTalks.this)+"<action type=\"gettalks\" output=\"json\"><event_id>"+event_id+"</event_id></action></request>");

                // @TODO: we do not handle errors?

                if (error == JIRest.OK) {
                    // Remove all talks from event, and insert new data
                    try {
                        JSONArray json = new JSONArray(rest.getResult());
                        DataHelper dh = DataHelper.getInstance();
                        dh.deleteTalksFromEvent(event_id);
                        for (int i=0; i!=json.length(); i++) {
                            JSONObject json_talk = json.getJSONObject(i);
                            dh.insertTalk (json_talk);
                        }
                    } catch (JSONException e) { }
                        // On error, display new talks like nothing happened
                        runOnUiThread(new Runnable() {
                            public void run() {
                                displayTalks (event_id, track_id);
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
 * Adapter that hold our talk rows. See  JIEventAdapter class in main.java for more info
 */
class JITalkAdapter extends ArrayAdapter<JSONObject> {
      private ArrayList<JSONObject> items;
      private Context context;

      public JITalkAdapter(Context context, int textViewResourceId, ArrayList<JSONObject> mTalks) {
          super(context, textViewResourceId, mTalks);
          this.context = context;
          this.items = mTalks;
      }

      public View getView(int position, View convertview, ViewGroup parent) {
          View v = convertview;
          if (v == null) {
                LayoutInflater vi = (LayoutInflater)this.context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
                v = vi.inflate(R.layout.talkrow, null);
          }

          JSONObject o = items.get(position);
          if (o == null) return v;

          String t2Text;
          int commentCount = o.optInt("comment_count");
          if (commentCount == 1) {
              t2Text = String.format(this.context.getString(R.string.generalCommentSingular), commentCount);
          } else {
              t2Text = String.format(this.context.getString(R.string.generalCommentPlural), commentCount);
          }

          TextView t1 = (TextView) v.findViewById(R.id.TalkRowCaption);
          TextView t2 = (TextView) v.findViewById(R.id.TalkRowComments);
          TextView t3 = (TextView) v.findViewById(R.id.TalkRowSpeaker);
          if (t1 != null) t1.setText(o.optString("talk_title"));
          if (t2 != null) t2.setText(t2Text);
          if (t3 != null) t3.setText(o.optString("speaker"));

          // Set specified talk category image
          ImageView r = (ImageView) v.findViewById(R.id.TalkRowImageType);
          if (o.optString("tcid").compareTo("Talk")==0) r.setBackgroundResource(R.drawable.talk);
          if (o.optString("tcid").compareTo("Social Event")==0) r.setBackgroundResource(R.drawable.socialevent);
          if (o.optString("tcid").compareTo("Workshop")==0) r.setBackgroundResource(R.drawable.workshop);
          if (o.optString("tcid").compareTo("Keynote")==0) r.setBackgroundResource(R.drawable.keynote);

          return v;
      }

}