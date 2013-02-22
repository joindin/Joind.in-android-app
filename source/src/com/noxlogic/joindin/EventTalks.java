package com.noxlogic.joindin;

/*
 * Displays all talks from specified event.
 */

import java.sql.Timestamp;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;

import android.util.Log;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
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
    private int eventRowID = 0;

    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Set layout
        setContentView(R.layout.eventtalks);

        // Get event ID from the intent scratch board
        try {
            this.eventJSON = new JSONObject(getIntent().getStringExtra("eventJSON"));
            if (getIntent().hasExtra("tracks")) {
                this.trackJSON = new JSONObject(getIntent().getStringExtra("eventTrack"));
            }
        } catch (JSONException e) {
            android.util.Log.e("JoindInApp", "No event passed to activity", e);
        }
        try {
            eventRowID = this.eventJSON.getInt("rowID");
        } catch (JSONException e) {
            android.util.Log.e("JoindInApp", "No row ID in event JSON");
        }
        if (eventRowID == 0) {
            // TODO alert and stop activity
            android.util.Log.e("JoindInApp", "Event row ID is invalid");
        }


        // Set all the event information
        TextView t;
        t = (TextView) this.findViewById(R.id.EventTalksCaption);
        t.setText (this.eventJSON.optString("name"));

        if (this.trackJSON == null) {
            t = (TextView) this.findViewById(R.id.EventTalksTrackName);
            t.setVisibility(View.INVISIBLE);
        } else {
            t = (TextView) this.findViewById(R.id.EventTalksTrackName);
            t.setVisibility(View.VISIBLE);
            t.setText ("("+this.trackJSON.optString("track_name")+")");
        }

        // Initialize talk list
        ArrayList<JSONObject> m_talks = new ArrayList<JSONObject>();
        m_talkAdapter = new JITalkAdapter(this, R.layout.talkrow, m_talks);
        ListView talklist =(ListView)findViewById(R.id.ListViewEventTalks);
        talklist.setAdapter(m_talkAdapter);

        // Add listview listener so when we click on an talk, we can display details
        talklist.setOnItemClickListener(new OnItemClickListener() {
            public void onItemClick(AdapterView<?>parent, View view, int pos, long id) {
                // open talk detail activity with event and talk data
                Intent myIntent = new Intent ();
                myIntent.setClass(getApplicationContext(), TalkDetail.class);
                myIntent.putExtra("eventJSON", getIntent().getStringExtra("eventJSON"));
                myIntent.putExtra("talkJSON", parent.getAdapter().getItem(pos).toString());
                startActivity(myIntent);
            }
        });

        // Display cached talks
        int track_id = (this.trackJSON != null) ? this.trackJSON.optInt("ID") : -1;
        displayTalks (eventRowID, track_id);

        // Load new talks (in background)
        try {
            loadTalks (eventRowID, track_id, eventJSON.getString("talks_uri"));
        } catch (JSONException e) {
            android.util.Log.e("JoindInApp", "No talks URI available");
        }
    }


    // Display talks in the talk list (adapter), depending on the track_id
    public int displayTalks (int eventRowID, int track_id) {
        DataHelper dh = DataHelper.getInstance();

        m_talkAdapter.clear();
        int talkCount = dh.populateTalks(eventRowID, track_id, m_talkAdapter);
        m_talkAdapter.notifyDataSetChanged();

        // Set title bar with number of talks found
        if (talkCount == 1) {
            setTitle (String.format(getString(R.string.generalEventTalksSingular), talkCount));
        } else {
            setTitle (String.format(getString(R.string.generalEventTalksPlural), talkCount));
        }
        return talkCount;
    }


    // Load talks in new thread...
    public void loadTalks (final int eventRowID, final int track_id, final String talkVerboseURI) {
        // Display progress bar
        displayProgressBar (true);


        new Thread () {
            public void run () {
                // This URI may change depending on how many talks are to be loaded
                String uriToUse = talkVerboseURI;
                JSONObject fullResponse;
                JSONObject metaObj = new JSONObject();
                JIRest rest = new JIRest (EventTalks.this);
                boolean isFirst = true;
                DataHelper dh = DataHelper.getInstance();

                try {
                    do {
                        // Fetch talk data from joind.in API
                        int error = rest.getJSONFullURI(uriToUse);

                        // @TODO: We do not handle errors?

                        if (error == JIRest.OK) {
                            fullResponse = rest.getJSONResult();
                            metaObj = fullResponse.getJSONObject("meta");

                            if (isFirst) {
                                dh.deleteTalksFromEvent(eventRowID);
                                isFirst = false;
                            }

                            // Remove all talks from event, and insert new data
                            JSONArray json = fullResponse.getJSONArray("talks");

                            for (int i=0; i!=json.length(); i++) {
                                JSONObject json_talk = json.getJSONObject(i);
                                dh.insertTalk (eventRowID, json_talk);
                            }
                            uriToUse = metaObj.getString("next_page");
                        }
                    } while (metaObj.getInt("count") != 0);
                } catch (JSONException e) {
                    displayProgressBar(false);
                    // Something went wrong. Just display the current talks.
                    runOnUiThread(new Runnable() {
                        public void run() {
                            displayTalks (eventRowID, track_id);
                        }
                    });
                }

                // Remove progress bar
                displayProgressBar (false);
                runOnUiThread(new Runnable() { public void run() { displayTalks(eventRowID, track_id); }});
            }
        }.start();
    }


	@Override
	public void onClick(View v) {
		// TODO Auto-generated method stub
		
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

          // Convert the supplied date/time string into something we can use
          Date talkDate = null;
          try {
              talkDate = new SimpleDateFormat(context.getString(R.string.apiDateFormat)).parse(o.getString("start_date"));
          } catch (Exception e) {
              e.printStackTrace();
              // Nothing here. Date is probably formatted badly
          }
          long cts = System.currentTimeMillis() / 1000;

          // Set a bit of darker color when the talk is currently held (the date_given is less than an hour old)
          if (talkDate != null && cts-talkDate.getTime() <= 3600 && cts-talkDate.getTime() >= 0) {
        	  v.setBackgroundColor(Color.rgb(218, 218, 204));
          } else {
        	  // This isn't right. We shouldn't set a white color, but the default color
        	  v.setBackgroundColor(Color.rgb(255, 255, 255));
          }

          String t2Text;
          int commentCount = o.optInt("comment_count");
          if (commentCount == 1) {
              t2Text = String.format(this.context.getString(R.string.generalCommentSingular), commentCount);
          } else {
              t2Text = String.format(this.context.getString(R.string.generalCommentPlural), commentCount);
          }

          String track = "";
          try {
        	  track = o.optJSONArray("tracks").getJSONObject(0).optString("track_name");
          } catch (JSONException e) {
        	  // Ignore if no track is available
          }

          TextView t1 = (TextView) v.findViewById(R.id.TalkRowCaption);
          TextView t2 = (TextView) v.findViewById(R.id.TalkRowComments);
          TextView t3 = (TextView) v.findViewById(R.id.TalkRowSpeaker);
          TextView t4 = (TextView) v.findViewById(R.id.TalkRowTime);
          TextView t5 = (TextView) v.findViewById(R.id.TalkRowTrack);
          if (t1 != null) t1.setText(o.optString("talk_title"));
          if (t2 != null) t2.setText(t2Text);
          if (t3 != null) t3.setText(o.optString("speaker"));
          if (t4 != null) t4.setText(String.format(this.context.getString(R.string.generalTime), talkDate.getHours(), talkDate.getMinutes()));
          if (t5 != null) t5.setText(track);

          // Set specified talk category image
          ImageView r = (ImageView) v.findViewById(R.id.TalkRowImageType);
          if (o.optString("type").compareTo("Talk")==0) r.setBackgroundResource(R.drawable.talk);
          if (o.optString("type").compareTo("Social Event")==0) r.setBackgroundResource(R.drawable.socialevent);
          if (o.optString("type").compareTo("Workshop")==0) r.setBackgroundResource(R.drawable.workshop);
          if (o.optString("type").compareTo("Keynote")==0) r.setBackgroundResource(R.drawable.keynote);

          ImageView rateview = (ImageView) v.findViewById(R.id.TalkRowRating);
          int rate = o.optInt("average_rating", 0);
          switch (rate) {
          		case 0 : rateview.setBackgroundResource(R.drawable.rating_0); break;
          		case 1 : rateview.setBackgroundResource(R.drawable.rating_1); break;
          		case 2 : rateview.setBackgroundResource(R.drawable.rating_2); break;
          		case 3 : rateview.setBackgroundResource(R.drawable.rating_3); break;
          		case 4 : rateview.setBackgroundResource(R.drawable.rating_4); break;
          		case 5 : rateview.setBackgroundResource(R.drawable.rating_5); break;
          }

          return v;
      }

}