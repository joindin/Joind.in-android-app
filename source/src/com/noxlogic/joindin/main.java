package com.noxlogic.joindin;

/*
 * Main activity. Displays all events and let the user select one.
 */

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.text.DateFormat;
import java.util.ArrayList;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.View.OnClickListener;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.AdapterView.OnItemClickListener;

public class main extends JIActivity implements OnClickListener {
    private JIEventAdapter m_eventAdapter;       // Adapter for displaying all the events

    private String currentTab;                   // Current selected tab
    private String currentTitle;                 // Current name

    JIRest rest;    // Our rest object to communicate with joind.in API


    /** Called when the activity is first created. */
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Set 'main' layout
        setContentView(R.layout.main);

        // Add listeners to the button. All buttons use the same listener
        Button button;
        button = (Button)findViewById(R.id.ButtonMainEventHot);
        button.setOnClickListener(this);
        button = (Button)findViewById(R.id.ButtonMainEventPending);
        button.setOnClickListener(this);
        button = (Button)findViewById(R.id.ButtonMainEventPast);
        button.setOnClickListener(this);
        button = (Button)findViewById(R.id.ButtonMainEventUpcoming);
        button.setOnClickListener(this);

        // Set default tab
        currentTab = "hot";
        currentTitle = getString(R.string.activityMainEventsHot);

        // Create array with all found events and add it to the eventlist
        ArrayList<JSONObject> m_events = new ArrayList<JSONObject>();
        m_eventAdapter = new JIEventAdapter(this, R.layout.eventrow, m_events);
        ListView eventlist =(ListView)findViewById(R.id.ListViewMainEvents);
        eventlist.setAdapter(m_eventAdapter);

        // When clicked on a event, check which one it is, and go to eventdetail class/activity
        eventlist.setOnItemClickListener(new OnItemClickListener() {
            public void onItemClick(AdapterView<?>parent, View view, int pos, long id) {
                Intent myIntent = new Intent ();
                myIntent.setClass(getBaseContext(), EventDetail.class);

                // pass the JSON data for specified event to the next activity
                myIntent.putExtra("eventJSON", parent.getAdapter().getItem(pos).toString());
                startActivity(myIntent);
            }
        });
    }


    // Will reload events. Needed when we return to the screen.
    public void onResume () {
        super.onResume();
        loadEvents(this.currentTab, this.currentTitle);
    }


    // Converts input stream to a string.
    public static String convertStreamToString(InputStream is) {
        BufferedReader reader = new BufferedReader(new InputStreamReader(is), 8192);
        StringBuilder sb = new StringBuilder();

        String line = null;
        try {
            while ((line = reader.readLine()) != null) {
                sb.append(line + "\n");
            }
        } catch (IOException e) {
            // ignored
        } finally {
            try {
                is.close();
            } catch (IOException e) {
                // Ignored
            }
        }
        return sb.toString();
    }


    // Display events by populating thje m_eventAdapter (customlist) with items loaded from DB
    public int displayEvents (final String eventType, final String eventCategory) {
        // Clear all eventes
        m_eventAdapter.clear();

        // add events and return count
        int count = this.dh.populateEvents(eventType, m_eventAdapter);

        // Tell the adapter that our dataset has changed so it can update it
        m_eventAdapter.notifyDataSetChanged();

        // Set main title to event category plus the number of events found
        setTitle (eventCategory+" ("+count+")");
        return count;
    }


    // Load events from API into the DB
    public void loadEvents (final String event_type, final String event_name) {
        // This will display a small progress circle in the top right corner
        displayProgressBar (true);

        // We need to run this in a new thread, otherwise the progressbar does not show
        new Thread () {
            public void run() {
                // Get some event data from the joind.in API
                rest = new JIRest (main.this);
                int error = rest.postXML ("event", "<request>"+JIRest.getAuthXML(main.this)+"<action type=\"getlist\" output=\"json\"><event_type>"+event_type+"</event_type></action></request>");

                // Something bad happened :(
                if (error != JIRest.OK) {
                    // We can only modify the UI in a UIThread, so we create another thread
                    runOnUiThread(new Runnable() {
                        public void run() {
                            // Done displaying the progress circle
                            displayProgressBar (false);

                            // Display result from the rest to the user
                            Toast toast = Toast.makeText (getBaseContext(), rest.getError(), Toast.LENGTH_LONG);
                            toast.show ();
                        }
                    });

                } else {
                    /*
                     * We just receievd new event data from joind.in API. Instead of modifying the current data
                     * already present in our database, we just remove all data and insert the new data. Makes
                     * life much easier :)
                     */
                    JSONArray json;
                    try {
                        // Get preferences
                        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getBaseContext());

                        // Delete all our events for specified type/category
                        main.this.dh.deleteAllEventsFromType(event_type);

                        // Add new events
                        json = new JSONArray(rest.getResult());
                        for (int i=0; i!=json.length(); i++) {
                            JSONObject json_event = json.getJSONObject(i);

                            // Don't add when we are adding to the Past AND we want to display "attended only"
                            if (event_type.compareTo("past") == 0 && prefs.getBoolean("attendonly", true) && ! json_event.optBoolean("user_attending")) continue;
                            main.this.dh.insertEvent (event_type, json_event);
                        }
                    } catch (JSONException e) { }
                        // Something when wrong. Just display the current events.
                        runOnUiThread(new Runnable() {
                            public void run() {
                                displayEvents (event_type, event_name);
                            }
                        });
                }

                // We are done. So remove our progress-circle
                displayProgressBar (false);
            }
        }.start();
    }


    // Called when user clicked on one of the buttons
    public void onClick(View v) {
        // Hot events clicked?
        if (v == findViewById(R.id.ButtonMainEventHot)) {
            // Load hot events

            // Set correct tab and title
            this.currentTab = "hot";
            this.currentTitle = getString(R.string.activityMainEventsHot);

            // Display events that are currently in the database
            displayEvents(this.currentTab, this.currentTitle);

            // And in the meantime, load new events from the API (which are
            // displayed when loaded).
            loadEvents(this.currentTab, this.currentTitle);
        }

        // Pending events clicked? (NOTE: this does not work, problem in the
        // joind.in API that does not return "pending event" data
        if (v == findViewById(R.id.ButtonMainEventPending)) {
            // Load pending events
            this.currentTab = "pending";
            this.currentTitle = getString(R.string.activityMainEventsPending);
            displayEvents(this.currentTab, this.currentTitle);
            loadEvents(this.currentTab, this.currentTitle);
        }

        // Past events clicked?
        if (v == findViewById(R.id.ButtonMainEventPast)) {
            // Load past events
            this.currentTab = "past";
            this.currentTitle = getString(R.string.activityMainEventsPast);
            displayEvents(this.currentTab, this.currentTitle);
            loadEvents(this.currentTab, this.currentTitle);
        }

        // Upcoming events clicked?
        if (v == findViewById(R.id.ButtonMainEventUpcoming)) {
            // Load upcoming events
            this.currentTab = "upcoming";
            this.currentTitle = getString(R.string.activityMainEventsUpcoming);
            displayEvents(this.currentTab, this.currentTitle);
            loadEvents(this.currentTab, this.currentTitle);
        }
    };
}


/**
 * This is an adapter that will load JSON event objects into a listview. Since we
 * want a customized view, we need to do it this way. It looks to me it's a pretty
 * dificult way of handling custom rows in a listview, but it works, although it's
 * very VERY slow.. :(
 */
class JIEventAdapter extends ArrayAdapter<JSONObject> {
      private ArrayList<JSONObject> items;      // The current items in the listview
      private Context context;
      LayoutInflater inflator;

      public JIEventAdapter(Context context, int textViewResourceId, ArrayList<JSONObject> items) {
          super(context, textViewResourceId, items);
          this.context = context;       // Saving context, sincve we need it on other places where the context is not known.
          this.items = items;
          this.inflator = (LayoutInflater)this.context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
      }

      // This function will create a custom row with our event data.
      public View getView(int position, View convertView, ViewGroup parent) {
          if (convertView == null) {
              convertView = this.inflator.inflate(R.layout.eventrow, null);
          }

          // Get the (JSON) data we need
          JSONObject o = items.get(position);
          if (o == null) return convertView;

          // Find our textviews we need to fill
          TextView tt = (TextView) convertView.findViewById(R.id.EventDetailCaption);
          TextView bt = (TextView) convertView.findViewById(R.id.EventDetailDate);
          TextView at = (TextView) convertView.findViewById(R.id.EventDetailAttending);

          // When the user is attending this event, we display our "attending" image.
          ImageView im = (ImageView)convertView.findViewById(R.id.EventDetailAttendingImg);
          if (o.optBoolean("user_attending") == false) {
              im.setVisibility(View.INVISIBLE);
          } else {
              im.setVisibility(View.VISIBLE);
          }

          // Set our texts
          if (at != null) at.setText(String.format(this.context.getString(R.string.activityMainAttending), o.optInt("num_attend")));
          if (tt != null) tt.setText(o.optString("event_name"));
          if (bt != null) {
              // Display start date. Only display end date when it differs (ie: it's multiple day event)
              String d1 = DateFormat.getDateInstance().format(o.optLong("event_start")*1000);
              String d2 = DateFormat.getDateInstance().format(o.optLong("event_end")*1000);
              bt.setText(d1.equals(d2) ? d1 : d1 + " - " + d2);
          }
          
          return convertView;
      }
}