package in.joind;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.crashlytics.android.Crashlytics;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;

/*
 * Displays all tracks from specified event.
 */

public class EventTracks extends JIActivity {
    private JITrackAdapter m_trackAdapter;    // adapter for listview
    private JSONObject eventJSON;

    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Allow ActionBar 'up' navigation
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        // Set layout
        setContentView(R.layout.eventtracks);

        // Get event ID from the intent scratch board
        int eventRowID;
        try {
            this.eventJSON = new JSONObject(getIntent().getStringExtra("eventJSON"));
            eventRowID = this.eventJSON.getInt("rowID");
        } catch (JSONException e) {
            android.util.Log.e(JIActivity.LOG_JOINDIN_APP, "No event passed to activity", e);
            Crashlytics.setString("eventTracks_eventJSON", getIntent().getStringExtra("eventJSON"));

            // Tell the user
            showToast(getString(R.string.activityEventDetailFailedJSON), Toast.LENGTH_LONG);
            finish();
            return;
        }

        // Set all the event information
        setTitle(this.eventJSON.optString("name"));

        // Initialize track list
        ArrayList<JSONObject> m_tracks = new ArrayList<>();
        m_trackAdapter = new JITrackAdapter(this, R.layout.trackrow, m_tracks);
        ListView tracklist = (ListView) findViewById(R.id.ListViewEventTracks);
        tracklist.setAdapter(m_trackAdapter);

        // Add listview listener so when we click on an talk, we can display details
        tracklist.setOnItemClickListener(new OnItemClickListener() {
            public void onItemClick(AdapterView<?> parent, View view, int pos, long id) {
                // Open event details with additional eventTrack data.
                Intent myIntent = new Intent();
                myIntent.setClass(getApplicationContext(), EventTalks.class);
                myIntent.putExtra("eventJSON", eventJSON.toString());
                myIntent.putExtra("eventTrack", parent.getAdapter().getItem(pos).toString());
                startActivity(myIntent);
            }
        });

        // Display cached tracks
        displayTracks(eventRowID);

        // Load new tracks (in background)
        try {
            loadTracks(eventRowID, eventJSON.getString("tracks_uri"));
        } catch (JSONException e) {
            android.util.Log.e(JIActivity.LOG_JOINDIN_APP, "No tracks URI available");
        }
    }

    // Display all tracks in the track list (adapter)
    public int displayTracks(int event_id) {
        DataHelper dh = DataHelper.getInstance(this);

        m_trackAdapter.clear();
        int trackCount = dh.populateTracks(event_id, m_trackAdapter);
        m_trackAdapter.notifyDataSetChanged();

        // Set title bar with number of talks found
        String tracksFound;
        if (trackCount == 1) {
            tracksFound = String.format(getString(R.string.generalEventTracksSingular), trackCount);
        } else {
            tracksFound = String.format(getString(R.string.generalEventTracksPlural), trackCount);
        }
        getSupportActionBar().setSubtitle(tracksFound);
        return trackCount;
    }

    public void loadTracks(final int eventRowID, final String trackVerboseURI) {
        // Display progress bar
        displayProgressBarCircular(true);


        new Thread() {
            public void run() {
                // This URI may change depending on how many talks are to be loaded
                String uriToUse = trackVerboseURI;
                JSONObject fullResponse;
                JSONObject metaObj = new JSONObject();
                JIRest rest = new JIRest(EventTracks.this);
                boolean isFirst = true;
                DataHelper dh = DataHelper.getInstance();

                if (dh != null) {
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
                                JSONArray json = fullResponse.getJSONArray("tracks");

                                for (int i = 0; i != json.length(); i++) {
                                    JSONObject json_track = json.getJSONObject(i);
                                    dh.insertTrack(eventRowID, json_track);
                                }
                                uriToUse = metaObj.getString("next_page");
                            }
                        } while (metaObj.getInt("count") > 0);
                    } catch (JSONException e) {
                        displayProgressBarCircular(false);
                        // Something went wrong. Just display the current talks.
                        runOnUiThread(new Runnable() {
                            public void run() {
                                displayTracks(eventRowID);
                            }
                        });
                    }

                    // Remove progress bar
                    displayProgressBarCircular(false);
                    runOnUiThread(new Runnable() {
                        public void run() {
                            displayTracks(eventRowID);
                        }
                    });
                }
            }
        }.start();
    }
}

/**
 * Adapter that holds our track rows. See  JIEventAdapter class in main.java for more info
 */
class JITrackAdapter extends ArrayAdapter<JSONObject> {
    private ArrayList<JSONObject> items;
    private Context context;

    public JITrackAdapter(Context context, int textViewResourceId, ArrayList<JSONObject> mTracks) {
        super(context, textViewResourceId, mTracks);
        this.context = context;
        this.items = mTracks;
    }

    public View getView(int position, View convertview, ViewGroup parent) {
        View v = convertview;
        if (v == null) {
            LayoutInflater vi = (LayoutInflater) this.context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
            v = vi.inflate(R.layout.trackrow, parent, false);
        }

        JSONObject o = items.get(position);
        if (o == null) return v;

        String t2Text;
        int talkCount = o.optInt("talks_count");
        if (talkCount == 1) {
            t2Text = String.format(this.context.getString(R.string.generalEventTalksSingular), talkCount);
        } else {
            t2Text = String.format(this.context.getString(R.string.generalEventTalksPlural), talkCount);
        }

        TextView t1 = (TextView) v.findViewById(R.id.TrackRowCaption);
        TextView t2 = (TextView) v.findViewById(R.id.TrackRowTalkCount);
        TextView t3 = (TextView) v.findViewById(R.id.TrackRowDescription);
        if (t1 != null) t1.setText(o.optString("track_name"));
        if (t2 != null) t2.setText(t2Text);
        if (t3 != null) t3.setText(o.optString("track_desc"));

        return v;
    }
}
