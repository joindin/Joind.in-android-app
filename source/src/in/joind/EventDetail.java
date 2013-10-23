package in.joind;

/*
 * Displays event details (info, talk list)
 */

import java.text.ParseException;
import java.text.SimpleDateFormat;

import in.joind.R;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.content.Intent;
import android.os.Bundle;
import android.text.util.Linkify;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.TextView;
import android.widget.Toast;

import com.crashlytics.android.Crashlytics;

public class EventDetail extends JIActivity implements OnClickListener {
    private JSONObject eventJSON;
    private int eventRowID = 0;

    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Allow ActionBar 'up' navigation
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        // Set layout
        setContentView(R.layout.eventdetail);

        // Get info from the intent scratch board
        try {
            this.eventJSON = new JSONObject(getIntent().getStringExtra("eventJSON"));
            eventRowID = this.eventJSON.getInt("rowID");
        } catch (JSONException e) {
            // No JSON means we can't continue
            android.util.Log.v(JIActivity.LOG_JOINDIN_APP, "No event JSON available to activity");
            Crashlytics.setString("eventDetail_eventJSON", getIntent().getStringExtra("eventJSON"));

            // Tell the user
            showToast(getString(R.string.activityEventDetailFailedJSON), Toast.LENGTH_LONG);
            finish();
            return;
        }
        if (eventRowID == 0) {
            android.util.Log.e(JIActivity.LOG_JOINDIN_APP, "Event row ID is invalid");
        }

        // Add handler to buttons
        Button button = (Button)findViewById(R.id.ButtonEventDetailsViewComments);
        button.setOnClickListener(this);
        button = (Button)findViewById(R.id.ButtonEventDetailsViewTalks);
        button.setOnClickListener(this);
        button = (Button)findViewById(R.id.ButtonEventDetailsViewTracks);
        button.setOnClickListener(this);

        displayDetails(eventRowID);

        // We et the onclick listener for the 'i attended' button AFTER loaded the details.
        // Otherwise we might end up clicking it when it's not in the correct state (disabled when you are
        // attending the event)
        CheckBox checkbox = (CheckBox)findViewById(R.id.CheckBoxEventDetailsAttending);
        checkbox.setOnClickListener(this);

        try {
            loadDetails(eventRowID, eventJSON.getString("verbose_uri"));
        } catch (JSONException e) {
            android.util.Log.e(JIActivity.LOG_JOINDIN_APP, "No verbose URI available");
        }
    }

    public void onResume() {
        super.onResume();

        CheckBox checkbox = (CheckBox)findViewById(R.id.CheckBoxEventDetailsAttending);
        checkbox.setEnabled(isAuthenticated());
    }

    public void displayDetails (int event_row_ID) {
        DataHelper dh = DataHelper.getInstance();
        JSONObject event = dh.getEvent (event_row_ID);
        if (event == null) return;

        // Set all the event information
        getSupportActionBar().setTitle(event.optString("name"));

        TextView t;
        t = (TextView) this.findViewById(R.id.EventDetailsEventLoc);
        t.setText (event.optString("location"));

        String d1 = null;
        String d2 = null;
        SimpleDateFormat dfOutput = new SimpleDateFormat("d LLL yyyy"), dfInput = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ");
        try {
            d1 = dfOutput.format(dfInput.parse(event.optString("start_date")));
            d2 = dfOutput.format(dfInput.parse(event.optString("end_date")));
            getSupportActionBar().setSubtitle(d1.equals(d2) ? d1 : d1 + " - " + d2);
        } catch (ParseException e) {
            e.printStackTrace();
            getSupportActionBar().setSubtitle("");
        }

        String hashtag = event.optString("hashtag");
        this.findViewById(R.id.EventDetailsHashtagsRow).setVisibility(hashtag.length() > 0 && !hashtag.equalsIgnoreCase("null") ? View.VISIBLE : View.GONE);
        t = (TextView) this.findViewById(R.id.EventDetailsStub);
        t.setText (event.optString("hashtag"));

        t = (TextView) this.findViewById(R.id.EventDetailsDescription);
        t.setText (event.optString("description"));
        Linkify.addLinks(t, Linkify.ALL);

        // Add number of comments to the correct button caption
        Button b = (Button) this.findViewById(R.id.ButtonEventDetailsViewComments);
        int commentCount = event.optInt("event_comments_count");
        if (commentCount == 1) {
            b.setText(String.format(getString(R.string.generalViewCommentSingular), commentCount));
        } else {
            b.setText(String.format(getString(R.string.generalViewCommentPlural), commentCount));
        }

        // See if this event has tracks
        b = (Button) this.findViewById(R.id.ButtonEventDetailsViewTracks);
        JSONArray tracks = event.optJSONArray("tracks");
        int trackCount = (tracks == null) ? 0 : tracks.length();
        if (trackCount == 1) {
            b.setText(String.format(getString(R.string.generalViewTrackSingular), trackCount));
        } else {
            b.setText(String.format(getString(R.string.generalViewTrackPlural), trackCount));
        }

        // Set track button enabled when we have at least 1 track
        b.setEnabled((trackCount > 0));

        // Tick the checkbox, depending on if we are attending or not
        CheckBox c = (CheckBox)findViewById(R.id.CheckBoxEventDetailsAttending);
        c.setChecked(event.optBoolean("attending"));
    }


    public void loadDetails (final int eventRowID, final String eventVerboseURI) {
        // Display progress bar
        displayProgressBar (true);

        new Thread () {
            public void run () {
                // Fetch talk data from joind.in API
                JIRest rest = new JIRest (EventDetail.this);
                int error = rest.getJSONFullURI(eventVerboseURI);

                if (error == JIRest.OK) {
                    JSONObject fullResponse = rest.getJSONResult();
                    JSONObject jsonEvent = null;
                    try {
                        jsonEvent = fullResponse.getJSONArray("events").getJSONObject(0);
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }


                    //  Update event details
                    DataHelper dh = DataHelper.getInstance();
                    dh.updateEvent (eventRowID, jsonEvent);
                }

                runOnUiThread(new Runnable() {
                    public void run() {
                        displayDetails (eventRowID);
                    }
                });

                // Remove progress bar
                displayProgressBar (false);
            }

        }.start();
    }


    public void onClick(View v) {
        if (v == findViewById(R.id.ButtonEventDetailsViewComments)) {
            // Display event comments activity
            Intent myIntent = new Intent ();
            myIntent.setClass(getApplicationContext(), EventComments.class);
            myIntent.putExtra("eventJSON", getIntent().getStringExtra("eventJSON"));
            startActivity(myIntent);
        }
        if (v == findViewById(R.id.ButtonEventDetailsViewTalks)) {
            // Display talks activity
            Intent myIntent = new Intent ();
            myIntent.setClass(getApplicationContext(), EventTalks.class);
            myIntent.putExtra("eventJSON", getIntent().getStringExtra("eventJSON"));
            startActivity(myIntent);
        }
        if (v == findViewById(R.id.ButtonEventDetailsViewTracks)) {
            // Display talks activity
            Intent myIntent = new Intent ();
            myIntent.setClass(getApplicationContext(), EventTracks.class);
            myIntent.putExtra("eventJSON", getIntent().getStringExtra("eventJSON"));
            startActivity(myIntent);
        }
        if (v == findViewById(R.id.CheckBoxEventDetailsAttending)) {
            // Check box clicked. This will toggle if we are attending the event or not.
            new Thread() {
                // We run in a background thread
                public void run() {
                    // Display progress bar (@TODO: Check if this works since it's not a UI thread)
                    displayProgressBar (true);

                    // Fetch state of checkbox (on or off)
                    CheckBox cb = (CheckBox)findViewById(R.id.CheckBoxEventDetailsAttending);
                    // Tell joind.in API that we attend (or unattended) this event
                    final String result = attendEvent(cb.isChecked());

                    // Display result, must be done in UI thread
                    runOnUiThread(new Runnable() {
                        public void run() {
                            // Display result from attendEvent
                            Toast toast = Toast.makeText(getApplicationContext(), result, Toast.LENGTH_LONG);
                            toast.show ();
                        }
                    });

                    // Stop displaying progress bar
                    displayProgressBar (false);
                }
            }.start();
        }
    };


    // This function will send to joind.in if we attend (or unattended) specified event.
    private String attendEvent (boolean initialState) {
        // Send data to the joind.in API
        JIRest rest = new JIRest (EventDetail.this);
        int error = rest.requestToFullURI(this.eventJSON.optString("attending_uri"), null, initialState ? JIRest.METHOD_POST : JIRest.METHOD_DELETE);

        if (error != JIRest.OK) {
            // Incorrect result, return error
            return String.format(getString(R.string.generatelAttendingError), rest.getError());
        }
        else {
            // Everything went as expected
            // We update the event, since the even has been changed (attendee count)
            try {
                loadDetails(eventRowID, eventJSON.getString("verbose_uri"));
            } catch (JSONException e) {
                android.util.Log.e(JIActivity.LOG_JOINDIN_APP, "No verbose URI available");
            }

            if (initialState) {
                return getString(R.string.generalSuccessFullyAttended);
            } else {
                return getString(R.string.generalSuccessFullyUnAttended);
            }
        }
     }
}
