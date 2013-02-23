package com.noxlogic.joindin;

/*
 * Main activity. Displays all events and let the user select one.
 */

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;

import android.util.Log;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.View.OnClickListener;
import android.widget.AdapterView;
import android.widget.AdapterView.AdapterContextMenuInfo;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Filter;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.AdapterView.OnItemClickListener;

public class Main extends JIActivity implements OnClickListener {
    private JIEventAdapter m_eventAdapter;       // Adapter for displaying all the events

    private String currentTab;                   // Current selected tab

    // Constants for dynamically added menu items
    private static final int MENU_SORT_DATE      = 1;
    private static final int MENU_SORT_TITLE     = 2;

    private int event_sort_order = DataHelper.ORDER_DATE_ASC;

    JIRest rest;    // Our rest object to communicate with joind.in API

    private EditText filterText;

    EventLoaderThread event_loader_thread = null;

    private TextWatcher filterTextWatcher = new TextWatcher() {

        public void beforeTextChanged(CharSequence s, int start, int count, int after) {
        }

        public void onTextChanged(CharSequence s, int start, int before, int count) {
            m_eventAdapter.getFilter().filter(s);
        }

        public void afterTextChanged(Editable s) {

        }
    };

    /** Called when the activity is first created. */
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Set 'main' layout
        setContentView(R.layout.main);

        // Create instance of the database singleton. This needs a context
        DataHelper.createInstance (this.getApplicationContext());

        // Add listeners to the button. All buttons use the same listener
        Button button;
        button = (Button)findViewById(R.id.ButtonMainEventHot);
        button.setOnClickListener(this);
        button = (Button)findViewById(R.id.ButtonMainEventPast);
        button.setOnClickListener(this);
        button = (Button)findViewById(R.id.ButtonMainEventUpcoming);
        button.setOnClickListener(this);
        button = (Button)findViewById(R.id.ButtonMainEventFavorites);
        button.setOnClickListener(this);

        // Set default tab from preferences
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
        currentTab = prefs.getString("defaultEventTab", "upcoming");

        // Create array with all found events and add it to the event list
        ArrayList<JSONObject> m_events = new ArrayList<JSONObject>();
        m_eventAdapter = new JIEventAdapter(this, R.layout.eventrow, m_events);

        ListView eventlist =(ListView)findViewById(R.id.ListViewMainEvents);
        eventlist.setAdapter(m_eventAdapter);

        // Add contextmenu to event list itemsllo
        registerForContextMenu(eventlist);

        filterText = (EditText) findViewById(R.id.FilterBar);
        filterText.addTextChangedListener(filterTextWatcher);


        displayEvents(this.currentTab);

        // When clicked on a event, check which one it is, and go to event detail class/activity
        eventlist.setOnItemClickListener(new OnItemClickListener() {
            public void onItemClick(AdapterView<?>parent, View view, int pos, long id) {
                Intent myIntent = new Intent ();
                myIntent.setClass(getApplicationContext(), EventDetail.class);

                // pass the JSON data for specified event to the next activity
                myIntent.putExtra("eventJSON", parent.getAdapter().getItem(pos).toString());
                startActivity(myIntent);
            }
        });
    }



    protected void onDestroy() {
        super.onDestroy();
        filterText.removeTextChangedListener(filterTextWatcher);
    }



    void loadEvents(String type) {
        if (event_loader_thread != null) {
            // Stop event loading thread.. we are going to start a new one...
            event_loader_thread.stopThread ();
        }

        // Create a event loader thread
        event_loader_thread = new EventLoaderThread ();
        event_loader_thread.setDaemon(true);
        event_loader_thread.setPriority(Thread.NORM_PRIORITY-1);
        event_loader_thread.startThread(type);
    }


    // Will reload events. Needed when we return to the screen.
    public void onResume () {
        loadEvents(this.currentTab);
        super.onResume();
    }


    // Overriding the JIActivity add sort-items
    public boolean onCreateOptionsMenu(Menu menu) {
        super.onCreateOptionsMenu(menu);

        MenuItem menu_date = menu.add(0, MENU_SORT_DATE, 0, R.string.OptionMenuSortDate);
        menu_date.setIcon(android.R.drawable.ic_menu_month);
        MenuItem menu_title = menu.add(0, MENU_SORT_TITLE, 0, R.string.OptionMenuSortTitle);
        menu_title.setIcon(android.R.drawable.ic_menu_sort_alphabetically);

        return true;
    }


    // Overriding the JIActivity handler to handle the sorting
    public boolean onOptionsItemSelected (MenuItem item) {
        switch (item.getItemId()) {
            case MENU_SORT_DATE :
                // Toast.makeText(this, "Sorting by data", Toast.LENGTH_SHORT).show();
                this.event_sort_order = this.event_sort_order == DataHelper.ORDER_DATE_ASC ? DataHelper.ORDER_DATE_DESC : DataHelper.ORDER_DATE_ASC;
                displayEvents(this.currentTab);
                break;
            case MENU_SORT_TITLE :
                // Toast.makeText(this, "Sorting by title", Toast.LENGTH_SHORT).show();
                this.event_sort_order = this.event_sort_order == DataHelper.ORDER_TITLE_ASC ? DataHelper.ORDER_TITLE_DESC : DataHelper.ORDER_TITLE_ASC;
                displayEvents(this.currentTab);
                break;
        }

        return super.onOptionsItemSelected (item);
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


    // Display events by populating the m_eventAdapter (custom list) with items loaded from DB
    public int displayEvents (final String eventType) {
        // Clear all events
        m_eventAdapter.clear();

        // add events and return count
        DataHelper dh = DataHelper.getInstance ();
        int count = dh.populateEvents(eventType, m_eventAdapter, this.event_sort_order);

        // Tell the adapter that our data set has changed so it can update it
        m_eventAdapter.notifyDataSetChanged();

        String title = "";
        if (eventType.equals("hot")) title = this.getString(R.string.activityMainEventsHot);
        if (eventType.equals("past")) title = this.getString(R.string.activityMainEventsPast);
        if (eventType.equals("upcoming")) title = this.getString(R.string.activityMainEventsUpcoming);
        if (eventType.equals("favorites")) title = this.getString(R.string.activityMainEventsFavorites);

        // Set main title to event category plus the number of events found
        setTabTitle(title, count);
        return count;
    }

    protected void setTabTitle(String title, int eventCount) {
        setTitle(title + " (" + eventCount + " event" + (eventCount == 1 ? "" : "s") + ")");
    }



    class EventLoaderThread extends Thread {
        private volatile Thread runner;

        private String event_type;

        public synchronized void startThread(String type){
            event_type = type;

            displayProgressBar (true);

            if (runner == null){
                runner = new Thread(this);
                runner.start();
            }
        }

        public synchronized void stopThread(){
            // Already stopped
            if (runner == null) return;

            // We are done. So remove our progress-circle
            displayProgressBar (false);

            Thread moribund = runner;
            runner = null;
            moribund.interrupt();
        }

        public void run() {
            // We do not need to reload favorite list from the server. It's not there :)
            if (event_type.equals("favorites")) {
                displayProgressBar (false);
                return;
            }

            // Get some event data from the joind.in API
            rest = new JIRest (Main.this);
            String urlPostfix = "events";
            String uriToUse = "";
            if (event_type.length() > 0) {
                urlPostfix += "?filter=" + event_type;
            }
            uriToUse = rest.makeFullURI(urlPostfix);

            JSONObject fullResponse;
            JSONObject metaObj = new JSONObject();
            DataHelper dh = DataHelper.getInstance();
            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
            boolean isFirst = true;
            int error = JIRest.OK; // default

            try {
                do {
                    error = rest.getJSONFullURI(uriToUse);

                    if (error == JIRest.OK) {

                        // Remove all event comments for this event and insert newly loaded comments
                        fullResponse = rest.getJSONResult();
                        metaObj = fullResponse.getJSONObject("meta");

                        if (isFirst) {
                            dh.deleteAllEventsFromType(event_type);
                            isFirst = false;
                        }
                        JSONArray json = fullResponse.getJSONArray("events");

                        for (int i=0; i!=json.length(); i++) {
                            JSONObject json_event = json.getJSONObject(i);

                            // Don't add when we are adding to the Past AND we want to display "attended only"
                            if (event_type.compareTo("past") == 0 && prefs.getBoolean("attendonly", true) && ! json_event.optBoolean("user_attending")) continue;
                            dh.insertEvent (event_type, json_event);
                        }
                        uriToUse = metaObj.getString("next_page");

                        // If we're looking at "hot" events, this API call just
                        // returns events, and more events, and more events....
                        // so we'll just stop after the first round
                        if (event_type == "hot") {
                            break;
                        }
                    }
                    else {
                        break;
                    }
                } while (metaObj.getInt("count") > 0);
            } catch (JSONException e) {
                displayProgressBar (false);
                runOnUiThread(new Runnable() {
                    public void run() {
                        displayEvents (event_type);
                    }
                });
                e.printStackTrace();
            }
            Log.d("JoindInApp", "Event loading loop finished!");

            // Something bad happened? :(
            if (error != JIRest.OK) {
                // We can only modify the UI in a UIThread, so we create another thread
                runOnUiThread(new Runnable() {
                    public void run() {
                        // Display result from the rest to the user
                        Toast toast = Toast.makeText (getApplicationContext(), rest.getError(), Toast.LENGTH_LONG);
                        toast.show ();
                    }
                });
            }

            // Show the events
            runOnUiThread(new Runnable() {
                public void run() {
                    displayEvents (event_type);
                }
            });
            displayProgressBar (false);
        }
    }




    // Called when user clicked on one of the buttons
    public void onClick(View v) {
        // Hot events clicked?
        if (v == findViewById(R.id.ButtonMainEventHot)) {
            // Load hot events

            // Set correct tab and title
            this.currentTab = "hot";

            // Display events that are currently in the database
            displayEvents(this.currentTab);

            // And in the meantime, load new events from the API (which are
            // displayed when loaded).
            loadEvents(this.currentTab);
        }

        // Past events clicked?
        if (v == findViewById(R.id.ButtonMainEventPast)) {
            // Load past events
            this.currentTab = "past";
            displayEvents(this.currentTab);
            loadEvents(this.currentTab);
        }

        // Upcoming events clicked?
        if (v == findViewById(R.id.ButtonMainEventUpcoming)) {
            // Load upcoming events
            this.currentTab = "upcoming";
            displayEvents(this.currentTab);
            loadEvents(this.currentTab);
        }


        if (v == findViewById(R.id.ButtonMainEventFavorites)) {
            // Load favorite list
            this.currentTab = "favorites";
            displayEvents(this.currentTab);
        }
    };


    // Creates contextmenu for items
    public void onCreateContextMenu(ContextMenu menu, View v, ContextMenuInfo menuInfo) {
        // Only register on the listview of the main events
        if (v.getId()==R.id.ListViewMainEvents) {
            AdapterView.AdapterContextMenuInfo info = (AdapterView.AdapterContextMenuInfo)menuInfo;

            JSONObject json = m_eventAdapter.getItem(info.position);
            menu.setHeaderTitle(json.optString("name"));

            MenuInflater inflater = getMenuInflater();
            inflater.inflate(R.menu.main_context_menu, menu);

            // Are we on the Favourites tab? Hide 'Add to favourites' and show 'remove', otherwise do the reverse
            menu.findItem(R.id.context_main_addtofavorite).setVisible(this.currentTab != "favorites");
            menu.findItem(R.id.context_main_removefromfavorite).setVisible(this.currentTab == "favorites");
        }
    }


    // Called when item is selected
    public boolean onContextItemSelected(MenuItem item) {
        AdapterContextMenuInfo info = (AdapterContextMenuInfo) item.getMenuInfo();
        JSONObject json = m_eventAdapter.getItem(info.position);
        int eventRowID = 0;

        try {
            eventRowID = json.getInt("rowID");
        } catch (JSONException e) {
            Log.d("JoindInApp", "Couldn't add event to favorites list: " + e.getMessage());
            Toast.makeText(getApplicationContext(), "Couldn't add to favorite list", Toast.LENGTH_SHORT).show();
            return false;
        }

        DataHelper dh = DataHelper.getInstance ();

        switch (item.getItemId()) {
            case R.id.context_main_addtofavorite:
                dh.addToFavorites(eventRowID);
                Toast.makeText(getApplicationContext(), "Added to favorite list: "+json.optString("name"), Toast.LENGTH_SHORT).show();
                return true;
            case R.id.context_main_removefromfavorite:
                // We are on the Favourites tab here, so we can remove and update
                JSONObject eventItem = m_eventAdapter.getItem(info.position);
                m_eventAdapter.remove(eventItem);
                m_eventAdapter.notifyDataSetChanged();
                setTabTitle(this.getString(R.string.activityMainEventsFavorites), m_eventAdapter.getCount());

                dh.removeFromFavorites(eventRowID);
                Toast.makeText(getApplicationContext(), "Removed from favorite list: "+json.optString("name"), Toast.LENGTH_SHORT).show();
                return true;
            default:
                return super.onContextItemSelected(item);
        }
    }

}


/**
 * This is an adapter that will load JSON event objects into a listview. Since we
 * want a customized view, we need to do it this way. It looks to me it's a pretty
 * dificult way of handling custom rows in a listview, but it works, although it's
 * very VERY slow.. :(
 */
class JIEventAdapter extends ArrayAdapter<JSONObject> {
      private ArrayList<JSONObject> all_items;        // The all items for the listview
      private ArrayList<JSONObject> filtered_items;   // The current currently viewed in the listview
      private Context context;
      LayoutInflater inflator;
      public ImageLoader image_loader;            // eventlogo image loader
      private PTypeFilter filter;

      public int getCount () {
          return filtered_items.size();
      }

      public JSONObject getItem (int position) {
          return filtered_items.get(position);
      }


      public JIEventAdapter(Context context, int textViewResourceId, ArrayList<JSONObject> items) {
          super(context, textViewResourceId, items);
          this.context = context;       // Saving context, sincve we need it on other places where the context is not known.
          this.all_items = items;
          this.filtered_items = items;
          this.inflator = (LayoutInflater)this.context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);

          this.image_loader = new ImageLoader(context.getApplicationContext(), "events");
      }

      // This function will create a custom row with our event data.
      public View getView(int position, View convertView, ViewGroup parent) {
          if (convertView == null) {
              convertView = this.inflator.inflate(R.layout.eventrow, null);
          }

          // Get the (JSON) data we need
          JSONObject o = filtered_items.get(position);
          if (o == null) return convertView;

          // Display (or load in the background if needed) the event logo

          // Remove logo (this could be a recycled row)
          ImageView el = (ImageView) convertView.findViewById(R.id.EventDetailLogo);
          el.setTag("");
          el.setVisibility(View.GONE);

          // Display (or load in the background if needed) the event logo
          if (! o.isNull("icon")) {
              String filename = o.optString("icon");
              el.setTag(filename);
              image_loader.displayImage("http://joind.in/inc/img/event_icons/", filename, (Activity)context, el);
          }

          // Set a darker color when the event is currently running.
          long event_start = 0;
          long event_end = 0;
          try {
              event_start = new SimpleDateFormat().parse(o.optString("start_date")).getTime();
              event_end = new SimpleDateFormat().parse(o.optString("end_date")).getTime();
          } catch (ParseException e) {
              e.printStackTrace();
          }
          long cts = System.currentTimeMillis() / 1000;
          if (event_start <= cts && cts <= event_end) {
              convertView.setBackgroundColor(Color.rgb(218, 218, 204));
          } else {
              // This isn't right. We shouldn't set a white color, but the default color
              convertView.setBackgroundColor(Color.rgb(255, 255, 255));
          }

          // Find our textviews we need to fill
          TextView tt = (TextView) convertView.findViewById(R.id.EventDetailCaption);
          TextView bt = (TextView) convertView.findViewById(R.id.EventDetailDate);
          TextView at = (TextView) convertView.findViewById(R.id.EventDetailAttending);

          // When the user is attending this event, we display our "attending" image.
          ImageView im = (ImageView)convertView.findViewById(R.id.EventDetailAttendingImg);
          if (o.optBoolean("attending") == false) {
              im.setVisibility(View.INVISIBLE);
          } else {
              im.setVisibility(View.VISIBLE);
          }

          // Set our texts
          if (at != null) at.setText(String.format(this.context.getString(R.string.activityMainAttending), o.optInt("attendee_count")));
          if (tt != null) tt.setText(o.optString("name"));
          if (bt != null) {
              // Display start date. Only display end date when it differs (ie: it's multiple day event)
              String d1 = null;
              String d2 = null;
              SimpleDateFormat dfOutput = new SimpleDateFormat("d LLL yyyy"), dfInput = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ");
              d1 = DateHelper.parseAndFormat(o.optString("start_date"), "d LLL yyyy");
              d2 = DateHelper.parseAndFormat(o.optString("end_date"), "d LLL yyyy");
              bt.setText(d1.equals(d2) ? d1 : d1 + " - " + d2);
          }

          return convertView;
      }


      public Filter getFilter() {
          if (filter == null) {
              filter  = new PTypeFilter();
          }
          return filter;
      }

      private class PTypeFilter extends Filter {
            @SuppressWarnings("unchecked")
            protected void publishResults(CharSequence prefix, FilterResults results) {
                filtered_items =  (ArrayList<JSONObject>)results.values;
                notifyDataSetChanged();
            }

            protected FilterResults performFiltering(CharSequence prefix) {
                  FilterResults results = new FilterResults();
                  ArrayList<JSONObject> i = new ArrayList<JSONObject>();

                  if (prefix!= null && prefix.toString().length() > 0) {

                      for (int index = 0; index < all_items.size(); index++) {
                          JSONObject json = all_items.get(index);
                          String title = json.optString("event_name");
                          // Add to the filtered result list when our string is found in the event_name
                          if (title.toUpperCase().indexOf(prefix.toString().toUpperCase()) >= 0) i.add(json);
                      }
                      results.values = i;
                      results.count = i.size();
                  } else {
                      // No more filtering, display all items
                      synchronized (all_items){
                          results.values = all_items;
                          results.count = all_items.size();
                      }
                  }

                  return results;
            }
        }
}