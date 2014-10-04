package in.joind;

/*
 * Displays all talks from specified event.
 */

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.TimeZone;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.res.Resources;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.View.OnClickListener;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.Filter;
import android.widget.Filterable;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.AdapterView.OnItemClickListener;

import com.markupartist.android.widget.PullToRefreshListView;

public class EventTalks extends JIActivity implements OnClickListener {
    private JITalkAdapter m_talkAdapter;    // adapter for listview
    private JSONObject eventJSON;
    private JSONObject trackJSON = null;
    private int eventRowID = 0;

    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Allow ActionBar 'up' navigation
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        // Set layout
        setContentView(R.layout.eventtalks);
    }

    public void onResume() {
        super.onResume();

        // Get event ID from the intent scratch board
        try {
            this.eventJSON = new JSONObject(getIntent().getStringExtra("eventJSON"));
            if (getIntent().hasExtra("eventTrack")) {
                this.trackJSON = new JSONObject(getIntent().getStringExtra("eventTrack"));
            }
        } catch (JSONException e) {
            android.util.Log.e(JIActivity.LOG_JOINDIN_APP, "No event passed to activity", e);
        }
        try {
            eventRowID = this.eventJSON.getInt("rowID");
        } catch (JSONException e) {
            android.util.Log.e(JIActivity.LOG_JOINDIN_APP, "No row ID in event JSON");
        }
        if (eventRowID == 0) {
            // TODO alert and stop activity
            android.util.Log.e(JIActivity.LOG_JOINDIN_APP, "Event row ID is invalid");
        }

        // Set titlebar
        getSupportActionBar().setTitle(this.eventJSON.optString("name"));

        // Initialize talk list
        ArrayList<JSONObject> m_talks = new ArrayList<JSONObject>();
        TimeZone tz;
        try {
            String tz_string = this.eventJSON.getString("tz_continent") + '/' + this.eventJSON.getString("tz_place");
            tz = TimeZone.getTimeZone(tz_string);
        } catch (JSONException e) {
            tz = TimeZone.getDefault();
        }
        m_talkAdapter = new JITalkAdapter(this, R.layout.talkrow, m_talks, tz, isAuthenticated());
        final PullToRefreshListView talklist = (PullToRefreshListView) findViewById(R.id.ListViewEventTalks);
        talklist.setAdapter(m_talkAdapter);

        // Display cached talks, optionally filtered by a track (by URI)
        final String trackURI = (this.trackJSON != null) ? this.trackJSON.optString("uri") : "";

        // Add listview listener so when we click on an talk, we can display details
        talklist.setOnItemClickListener(new OnItemClickListener() {
            public void onItemClick(AdapterView<?> parent, View view, int pos, long id) {
                // open talk detail activity with event and talk data
                Intent myIntent = new Intent();
                myIntent.setClass(getApplicationContext(), TalkDetail.class);
                myIntent.putExtra("eventJSON", getIntent().getStringExtra("eventJSON"));
                myIntent.putExtra("talkJSON", parent.getAdapter().getItem(pos).toString());
                startActivity(myIntent);
            }
        });
        talklist.setOnRefreshListener(new PullToRefreshListView.OnRefreshListener() {
            @Override
            public void onRefresh() {
                try {
                    loadTalks(eventRowID, trackURI, eventJSON.getString("talks_uri"));
                } catch (JSONException e) {
                    android.util.Log.e(JIActivity.LOG_JOINDIN_APP, "No talks URI available");
                    talklist.onRefreshComplete();
                }
            }
        });

        displayTalks(eventRowID, trackURI);

        // Load new talks (in background)
        try {
            loadTalks(eventRowID, trackURI, eventJSON.getString("talks_uri"));
        } catch (JSONException e) {
            android.util.Log.e(JIActivity.LOG_JOINDIN_APP, "No talks URI available");
        }
    }


    // Display talks in the talk list (adapter), depending on the track_id
    public int displayTalks(int eventRowID, String trackURI) {
        DataHelper dh = DataHelper.getInstance(this);

        m_talkAdapter.clear();
        dh.populateTalks(eventRowID, m_talkAdapter);
        updateSubtitle(0); // initial
        m_talkAdapter.notifyDataSetChanged();
        m_talkAdapter.getFilter().filter(trackURI, new Filter.FilterListener() {
            @Override
            public void onFilterComplete(int count) {
                updateSubtitle(count);
            }
        });

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
                                m_talkAdapter.getFilter().filter("");
                                findViewById(R.id.filterDetails).setVisibility(View.GONE);
                                break;

                            case 1:
                                // Starred items
                                m_talkAdapter.getFilter().filter("starred");
                                findViewById(R.id.filterDetails).setVisibility(View.VISIBLE);
                                break;
                        }
                    }
                });
                builder.create().show();

                return true;
        }

        return super.onOptionsItemSelected(item);
    }
}


/**
 * Adapter that hold our talk rows. See  JIEventAdapter class in main.java for more info
 */
class JITalkAdapter extends ArrayAdapter<JSONObject> implements Filterable {
    private ArrayList<JSONObject> items, filtered_items;
    private Context context;
    private TimeZone tz;
    private StarredFilter filter;
    private boolean isAuthenticated;

    public JITalkAdapter(Context context, int textViewResourceId, ArrayList<JSONObject> mTalks, TimeZone tz, boolean isAuthenticated) {
        super(context, textViewResourceId, mTalks);
        this.context = context;
        this.items = mTalks;
        this.filtered_items = mTalks;
        this.tz = tz;
        this.isAuthenticated = isAuthenticated;
    }

    public View getView(int position, View convertview, ViewGroup parent) {
        View v = convertview;
        if (v == null) {
            LayoutInflater vi = (LayoutInflater) this.context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
            v = vi.inflate(R.layout.talkrow, null);
        }

        final JSONObject o = filtered_items.get(position);
        if (o == null) return v;

        // Convert the supplied date/time string into something we can use
        Date talkDate = null;
        SimpleDateFormat outputTalkDateFormat = null;
        try {
            SimpleDateFormat inputTalkDateFormat = new SimpleDateFormat(context.getString(R.string.apiDateFormat));
            talkDate = inputTalkDateFormat.parse(o.getString("start_date"));
            String fmt = Build.VERSION.SDK_INT <= 8 ? "E d MMM yyyy" : "E d LLL yyyy";
            outputTalkDateFormat = new SimpleDateFormat(fmt + ", HH:mm");
            outputTalkDateFormat.setTimeZone(tz);
        } catch (Exception e) {
            e.printStackTrace();
            // Nothing here. Date is probably formatted badly
        }
        long cts = System.currentTimeMillis() / 1000;

        // Set a bit of darker color when the talk is currently held (the date_given is less than an hour old)
        if (talkDate != null && cts - talkDate.getTime() <= 3600 && cts - talkDate.getTime() >= 0) {
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
        if (t4 != null) t4.setText(outputTalkDateFormat.format(talkDate));
        if (t5 != null) t5.setText(track);

        // Speaker details
        ArrayList<String> speakerNames = new ArrayList<String>();
        try {
            JSONArray speakerEntries = o.getJSONArray("speakers");
            for (int i = 0; i < speakerEntries.length(); i++) {
                speakerNames.add(speakerEntries.getJSONObject(i).getString("speaker_name"));
            }
        } catch (JSONException e) {
            Log.d(JIActivity.LOG_JOINDIN_APP, "Couldn't get speaker names");
            e.printStackTrace();
        }
        if (speakerNames.size() == 1) {
            t3.setText("Speaker: " + speakerNames.get(0));
        }
        else if (speakerNames.size() > 1) {
            String allSpeakers = TextUtils.join(", ", speakerNames);
            t3.setText("Speakers: " + allSpeakers);
        }
        else {
            t3.setText("");
        }

        // Set specified talk category image
        Resources resources = context.getResources();
        if (o.optString("type").compareTo("Talk") == 0) t1.setCompoundDrawables(resources.getDrawable(R.drawable.talk), null, null, null);
        if (o.optString("type").compareTo("Social Event") == 0) t1.setCompoundDrawables(resources.getDrawable(R.drawable.socialevent), null, null, null);
        if (o.optString("type").compareTo("Workshop") == 0) t1.setCompoundDrawables(resources.getDrawable(R.drawable.workshop), null, null, null);
        if (o.optString("type").compareTo("Keynote") == 0) t1.setCompoundDrawables(resources.getDrawable(R.drawable.keynote), null, null, null);

        ImageView rateview = (ImageView) v.findViewById(R.id.TalkRowRating);
        int rate = o.optInt("average_rating", 0);
        switch (rate) {
            case 0:
                rateview.setBackgroundResource(R.drawable.rating_0);
                break;
            case 1:
                rateview.setBackgroundResource(R.drawable.rating_1);
                break;
            case 2:
                rateview.setBackgroundResource(R.drawable.rating_2);
                break;
            case 3:
                rateview.setBackgroundResource(R.drawable.rating_3);
                break;
            case 4:
                rateview.setBackgroundResource(R.drawable.rating_4);
                break;
            case 5:
                rateview.setBackgroundResource(R.drawable.rating_5);
                break;
        }

        // Show/hide the starred icon if the talk is starred
        boolean starredStatus = o.optBoolean("starred", false);
        CheckBox starredImageButton = (CheckBox) v.findViewById(R.id.TalkRowStarred);
        starredImageButton.setVisibility(isAuthenticated ? View.VISIBLE : View.GONE);
        starredImageButton.setChecked(starredStatus);
        final View finalV = v;
        starredImageButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View view) {
                markTalkStarred(finalV, o.optString("starred_uri"), ((CheckBox) view).isChecked());
            }
        });

        return v;
    }

    /**
     * Mark the talk as starred - update the icon and submit the request
     * @param isStarred
     */
    protected void markTalkStarred(final View parentRow, final String starredURI, final boolean isStarred) {
        final CheckBox starredImageButton = (CheckBox) parentRow.findViewById(R.id.TalkRowStarred);
        final ProgressBar progressBar = (ProgressBar) parentRow.findViewById(R.id.TalkRowProgress);

        new Thread() {
            public void run() {
                updateProgressStatus(progressBar, starredImageButton, true);

                final String result = doStarTalk(isStarred, starredURI);

                // TODO Hide the progress indicator
                updateProgressStatus(progressBar, starredImageButton, false);
            }
        }.start();
    }

    /**
     * CALLED FROM SEPARATE THREAD
     * This shows/hides the progressbar and the checkbox alternately.
     *
     * @param progressBar
     * @param starredImageButton
     * @param showProgress
     */
    private void updateProgressStatus(final ProgressBar progressBar, final CheckBox starredImageButton, final boolean showProgress) {
        progressBar.post(new Runnable() {
            @Override
            public void run() {
                progressBar.setVisibility(showProgress ? View.VISIBLE : View.GONE);
            }
        });
        starredImageButton.post(new Runnable() {
            @Override
            public void run() {
                starredImageButton.setVisibility(showProgress ? View.GONE : View.VISIBLE);
            }
        });
    }

    /**
     * Post/delete the starred status from this talk
     *
     * @param initialState
     * @return
     */
    private String doStarTalk(boolean initialState, String starredURI) {
        JIRest rest = new JIRest(context);
        int error = rest.requestToFullURI(starredURI, null, initialState ? JIRest.METHOD_POST : JIRest.METHOD_DELETE);

        if (error != JIRest.OK) {
            return String.format(context.getString(R.string.generalStarringError), rest.getError());
        }

        // Everything went as expected
        if (initialState) {
            return context.getString(R.string.generalSuccessStarred);
        } else {
            return context.getString(R.string.generalSuccessUnstarred);
        }
    }

    public Filter getFilter() {
        if (filter == null) {
            filter = new StarredFilter();
        }
        return filter;
    }

    public int getCount() {
        return filtered_items.size();
    }

    public JSONObject getItem(int position) {
        return filtered_items.get(position);
    }

    /**
     * Starred filter
     */
    private class StarredFilter extends Filter {
        @SuppressWarnings("unchecked")
        protected void publishResults(CharSequence prefix, FilterResults results) {
            filtered_items = (ArrayList<JSONObject>) results.values;

            notifyDataSetChanged();
        }

        protected FilterResults performFiltering(CharSequence match) {
            FilterResults results = new FilterResults();
            ArrayList<JSONObject> i = new ArrayList<JSONObject>();

            if (match != null && match.toString().equals("starred")) {
                for (int index = 0; index < items.size(); index++) {
                    JSONObject json = items.get(index);
                    if (json.optBoolean("starred", false)) {
                        i.add(json);
                    }
                }
                results.values = i;
                results.count = i.size();
            } else {
                synchronized (items) {
                    results.values = items;
                    results.count = items.size();
                }
            }

            return results;
        }
    }
}
