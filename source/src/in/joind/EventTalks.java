package in.joind;

/*
 * Displays all talks from specified event.
 */

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.AbsListView;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.Filter;

import com.markupartist.android.widget.PullToRefreshListView;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.TimeZone;

public class EventTalks extends JIActivity implements OnClickListener {
    private JITalkAdapter m_talkAdapter;
    private JSONObject eventJSON;
    private JSONObject trackJSON = null;
    private int eventRowID = 0;
    private int firstVisibleItem = 0;
    String trackURI;

    final private String FILTER_ALL = "";
    final private String FILTER_STARRED = "starred";
    PullToRefreshListView talklist;

    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Allow ActionBar 'up' navigation
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        // Set layout
        setContentView(R.layout.eventtalks);
    }

    public void onPause() {
        super.onPause();

        // Save our current list index position for this event
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
        SharedPreferences.Editor editor = sharedPreferences.edit();
        String key = String.format(C.PREFS_TALK_LIST_INDEX, eventRowID);
        editor.putInt(key, firstVisibleItem).apply();
    }

    public void onResume() {
        super.onResume();

        final Intent callingIntent = getIntent();

        // Get event ID from the intent scratch board
        try {
            this.eventJSON = new JSONObject(callingIntent.getStringExtra("eventJSON"));
            if (getIntent().hasExtra("eventTrack")) {
                this.trackJSON = new JSONObject(callingIntent.getStringExtra("eventTrack"));
            }
        } catch (JSONException e) {
            Log.e(JIActivity.LOG_JOINDIN_APP, "No event passed to activity", e);
        }
        try {
            eventRowID = this.eventJSON.getInt("rowID");
        } catch (JSONException e) {
            Log.e(JIActivity.LOG_JOINDIN_APP, "No row ID in event JSON");
        }
        if (eventRowID == 0) {
            // TODO alert and stop activity
            Log.e(JIActivity.LOG_JOINDIN_APP, "Event row ID is invalid");
        }

        // Set titlebar
        getSupportActionBar().setTitle(this.eventJSON.optString("name"));

        // Initialize talk list
        ArrayList<JSONObject> m_talks = new ArrayList<>();
        TimeZone tz;
        try {
            String tz_string = this.eventJSON.getString("tz_continent") + '/' + this.eventJSON.getString("tz_place");
            tz = TimeZone.getTimeZone(tz_string);
        } catch (JSONException e) {
            tz = TimeZone.getDefault();
        }
        m_talkAdapter = new JITalkAdapter(this, R.layout.talkrow, m_talks, tz, isAuthenticated());
        talklist = (PullToRefreshListView) findViewById(R.id.ListViewEventTalks);
        talklist.setAdapter(m_talkAdapter);

        // Display cached talks, optionally filtered by a track (by URI)
        trackURI = (this.trackJSON != null) ? this.trackJSON.optString("uri") : "";

        // Add listview listener so when we click on an talk, we can display details
        talklist.setOnItemClickListener(new OnItemClickListener() {
            public void onItemClick(AdapterView<?> parent, View view, int pos, long id) {
                // open talk detail activity with event and talk data
                Intent myIntent = new Intent();
                myIntent.setClass(getApplicationContext(), TalkDetail.class);
                myIntent.putExtra("eventJSON", callingIntent.getStringExtra("eventJSON"));
                myIntent.putExtra("talkJSON", parent.getAdapter().getItem(pos).toString());
                startActivityForResult(myIntent, C.EVENT_TALKS_SHOW_TALK_DETAILS);
            }
        });
        talklist.setOnRefreshListener(new PullToRefreshListView.OnRefreshListener() {
            @Override
            public void onRefresh() {
                try {
                    loadTalks(eventRowID, trackURI, eventJSON.getString("talks_uri"));
                } catch (JSONException e) {
                    Log.e(JIActivity.LOG_JOINDIN_APP, "No talks URI available");
                    talklist.onRefreshComplete();
                }
            }
        });
        talklist.setOnScrollListener(new AbsListView.OnScrollListener() {
            @Override
            public void onScrollStateChanged(AbsListView absListView, int i) {
            }

            @Override
            public void onScroll(AbsListView view, int firstVisibleItem, int visibleItemCount, int totalItemCount) {
                EventTalks.this.firstVisibleItem = firstVisibleItem;
            }
        });

        displayTalks(eventRowID, trackURI);
        talklist.setSelection(firstVisibleItem);

        // Load new talks (in background)
        try {
            loadTalks(eventRowID, trackURI, eventJSON.getString("talks_uri"));
        } catch (JSONException e) {
            Log.e(JIActivity.LOG_JOINDIN_APP, "No talks URI available");
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == C.EVENT_TALKS_SHOW_TALK_DETAILS) {
            // Resume state if we've been returned to
            SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
            String key = String.format(C.PREFS_TALK_LIST_INDEX, eventRowID);
            firstVisibleItem = sharedPreferences.getInt(key, 0);
        }
    }

    // Display talks in the talk list (adapter), depending on the track_id
    public int displayTalks(int eventRowID, String trackURI) {
        DataHelper dh = DataHelper.getInstance(this);

        m_talkAdapter.clear();
        dh.populateTalks(eventRowID, m_talkAdapter);
        updateSubtitle(0); // initial
        m_talkAdapter.notifyDataSetChanged();
        applyPreferenceStarredFilter();

        return 0;
    }

    protected void updateSubtitle(int talkCount) {
        // Set title bar with number of talks found
        String talksFound = "";
        if (this.trackJSON != null) {
            talksFound = this.trackJSON.optString("track_name") + ": ";
        }

        if (talkCount == 1) {
            talksFound += String.format(getString(R.string.generalEventTalksSingular), talkCount);
        } else {
            talksFound += String.format(getString(R.string.generalEventTalksPlural), talkCount);
        }
        getSupportActionBar().setSubtitle(talksFound);

        ((PullToRefreshListView) findViewById(R.id.ListViewEventTalks)).onRefreshComplete();
    }

    // Load talks in new thread...
    public void loadTalks(final int eventRowID, final String trackURI, final String talkVerboseURI) {
        // Display progress bar
        displayProgressBarCircular(true);


        new Thread() {
            public void run() {
                // This URI may change depending on how many talks are to be loaded
                String uriToUse = talkVerboseURI;
                JSONObject fullResponse;
                JSONObject metaObj = new JSONObject();
                JIRest rest = new JIRest(EventTalks.this);
                boolean isFirst = true;
                DataHelper dh = DataHelper.getInstance(EventTalks.this);

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

                            for (int i = 0; i != json.length(); i++) {
                                JSONObject json_talk = json.getJSONObject(i);
                                dh.insertTalk(eventRowID, json_talk);
                            }
                            uriToUse = metaObj.getString("next_page");
                        }
                    } while (metaObj.getInt("count") > 0);
                } catch (JSONException e) {
                    displayProgressBarCircular(false);
                    // Something went wrong. Just display the current talks.
                    runOnUiThread(new Runnable() {
                        public void run() {
                            displayTalks(eventRowID, trackURI);
                        }
                    });
                }

                // Remove progress bar
                displayProgressBarCircular(false);
                runOnUiThread(new Runnable() {
                    public void run() {
                        displayTalks(eventRowID, trackURI);
                    }
                });
            }
        }.start();
    }

    @Override
    public void onClick(View v) {
    }

    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.talk_listing_menu, menu);

        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.talk_filter_menu_item:
                // Ask the user what they want to filter by
                AlertDialog.Builder builder = new AlertDialog.Builder(this);
                builder.setTitle(R.string.TalkMenuFilter);
                builder.setItems(R.array.TalkMenuFilterArray, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        // The 'which' argument contains the index position
                        // of the selected item
                        switch (which) {
                            case 0:
                                // All items
                                applyStarredFilter(FILTER_ALL);
                                saveFilterPreference(FILTER_ALL);
                                break;

                            case 1:
                                // Starred items
                                applyStarredFilter(FILTER_STARRED);
                                saveFilterPreference(FILTER_STARRED);
                                break;
                        }

                    }
                });
                builder.create().show();

                return true;
        }

        return super.onOptionsItemSelected(item);
    }

    /**
     * Retrieve a user's previously-saved filter preference for
     * this event, and tell the filter what to do
     */
    protected void applyPreferenceStarredFilter() {
        String prefKey = "filter_" + eventRowID;
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
        String filterPreference = prefs.getString(prefKey, "");
        applyStarredFilter(filterPreference);
    }

    /**
     * Configure the filter based on track URI and starred preference
     *
     * @param filterType Filter type
     */
    protected void applyStarredFilter(String filterType) {
        boolean showFilterHeader = false;

        JITalkAdapter.StarredFilter starredFilter = (JITalkAdapter.StarredFilter) m_talkAdapter.getFilter();
        if (filterType.equals("")) {
            starredFilter.setCheckStarredStatus(false);
        }
        if (filterType.equals("starred")) {
            starredFilter.setCheckStarredStatus(true);
            showFilterHeader = true;
        }

        // Apply the track URI string matching filter
        // The starred item is checked within the filter itself, based on the checkStarredStatus call
        starredFilter.filter(trackURI, new Filter.FilterListener() {
            @Override
            public void onFilterComplete(int count) {
                updateSubtitle(count);
            }
        });

        findViewById(R.id.filterDetails).setVisibility(showFilterHeader ? View.VISIBLE : View.GONE);
    }

    /**
     * Save the user's current filter preference to shared preferences
     * for this event
     *
     * @param filterType Filter type
     */
    protected void saveFilterPreference(String filterType) {
        String prefKey = "filter_" + eventRowID;

        // Save filter preference in preferences
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
        SharedPreferences.Editor editor = prefs.edit();
        editor.putString(prefKey, filterType);
        editor.apply();
    }
}

