package in.joind;

import java.util.ArrayList;

import in.joind.R;
import org.json.JSONException;
import org.json.JSONObject;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.AdapterView.OnItemClickListener;

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
        try {
            this.eventJSON = new JSONObject(getIntent().getStringExtra("eventJSON"));
        } catch (JSONException e) {
            android.util.Log.e("JoindInApp", "No event passed to activity", e);
        }

        // Set all the event information
        TextView t;
        t = (TextView) this.findViewById(R.id.EventTracksCaption);
        t.setText(this.eventJSON.optString("event_name"));

        // Initialize track list
        ArrayList<JSONObject> m_tracks = new ArrayList<JSONObject>();
        m_trackAdapter = new JITrackAdapter(this, R.layout.trackrow, m_tracks);
        ListView tracklist = (ListView) findViewById(R.id.ListViewEventTracks);
        tracklist.setAdapter(m_trackAdapter);

        // Add listview listener so when we click on an talk, we can display details
        tracklist.setOnItemClickListener(new OnItemClickListener() {
            public void onItemClick(AdapterView<?> parent, View view, int pos, long id) {
                // Open event details with additional eventTrack data.
                Intent myIntent = new Intent();
                myIntent.setClass(getApplicationContext(), EventTalks.class);
                myIntent.putExtra("eventJSON", getIntent().getStringExtra("eventJSON"));
                myIntent.putExtra("eventTrack", parent.getAdapter().getItem(pos).toString());
                startActivity(myIntent);
            }
        });

        // Display cached talks
        int event_id = this.eventJSON.optInt("ID");
        displayTracks(event_id);
    }

    // Display all talks in the talk list (adapter)
    public int displayTracks(int event_id) {
        DataHelper dh = DataHelper.getInstance();

        m_trackAdapter.clear();
        int trackCount = dh.populateTracks(event_id, m_trackAdapter);
        m_trackAdapter.notifyDataSetChanged();

        // Set title bar with number of talks found
        if (trackCount == 1) {
            setTitle(String.format(getString(R.string.generalEventTracksSingular), trackCount));
        } else {
            setTitle(String.format(getString(R.string.generalEventTracksPlural), trackCount));
        }
        return trackCount;
    }
}

/**
 * Adapter that hold our talk rows. See  JIEventAdapter class in main.java for more info
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
            v = vi.inflate(R.layout.trackrow, null);
        }

        JSONObject o = items.get(position);
        if (o == null) return v;

        String t2Text;
        int talkCount = o.optInt("used");
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
