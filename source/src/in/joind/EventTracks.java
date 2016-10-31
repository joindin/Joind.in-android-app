package in.joind;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.support.v7.app.ActionBar;
import android.util.Log;
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

import in.joind.adapter.TrackAdapter;

/*
 * Displays all tracks from specified event.
 */

public class EventTracks extends JIActivity {
    private TrackAdapter m_trackAdapter;    // adapter for listview
    private JSONObject eventJSON;

    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Allow ActionBar 'up' navigation
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) actionBar.setDisplayHomeAsUpEnabled(true);

        // Set layout
        setContentView(R.layout.eventtracks);

        // Get event ID from the intent scratch board
        int eventRowID;
        try {
            this.eventJSON = new JSONObject(getIntent().getStringExtra("eventJSON"));
            eventRowID = this.eventJSON.getInt("rowID");
        } catch (JSONException e) {
            Log.e(JIActivity.LOG_JOINDIN_APP, "No event passed to activity", e);
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
        m_trackAdapter = new TrackAdapter(this, R.layout.trackrow, m_tracks);
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
            Log.e(JIActivity.LOG_JOINDIN_APP, "No tracks URI available");
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
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) actionBar.setSubtitle(tracksFound);
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

                            // TODO: We do not handle errors?

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
