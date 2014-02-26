package in.joind;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v4.app.ListFragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Filter;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.markupartist.android.widget.PullToRefreshListView;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;

/**
 * The list fragment that is shown in our tabbed view.
 * Lists events depending on event type (in our case, the fragment's Tag value)
 */
public class EventListFragment extends ListFragment implements EventListFragmentInterface {

    private JIEventAdapter m_eventAdapter;
    private EventLoaderThread event_loader_thread;
    int eventSortOrder = DataHelper.ORDER_DATE_ASC;
    Main parentActivity;
    JIRest rest;
    ListView listView;

    public void onAttach(Activity activity) {
        super.onAttach(activity);
        parentActivity = (Main) activity;
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        ViewGroup viewGroup = (ViewGroup) super.onCreateView(inflater, container, savedInstanceState);

        View lvOld = viewGroup.findViewById(android.R.id.list);

        // Use the pull-to-refresh ListView, instead of a normal ListView
        final PullToRefreshListView listView = new PullToRefreshListView(getActivity());
        listView.setId(android.R.id.list);
        listView.setLayoutParams(new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        listView.setDrawSelectorOnTop(false);

        FrameLayout parent = (FrameLayout) lvOld.getParent();
        parent.removeView(lvOld);
        lvOld.setVisibility(View.GONE);

        parent.addView(listView, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        // Populate our list adapter
        ArrayList<JSONObject> m_events = new ArrayList<JSONObject>();
        m_eventAdapter = new JIEventAdapter(getActivity(), R.layout.eventrow, m_events);
        setListAdapter(m_eventAdapter);

        return viewGroup;
    }

    @Override
    public void onActivityCreated(android.os.Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        listView = getListView();
    }

    @Override
    public void onPause() {
        super.onPause();

        if (event_loader_thread != null) {
            event_loader_thread.stopThread();
        }
        event_loader_thread = null;
        listView = null;
    }

    public void onResume() {
        super.onResume();

        if (listView != null) {
            setListShown(false);
        }
        loadEvents(this.getTag());
        setTitle(this.getTag());

        setupEvents();
    }

    void loadEvents(String type) {
        if (event_loader_thread != null) {
            // Stop event loading thread, we're going to start a new one
            event_loader_thread.stopThread();
        }

        // Create a event loader thread
        event_loader_thread = new EventLoaderThread();
        event_loader_thread.setDaemon(true);
        event_loader_thread.setPriority(Thread.NORM_PRIORITY - 1);
        event_loader_thread.startThread(type);
    }

    protected void setupEvents() {
        getListView().setOnItemClickListener(new AdapterView.OnItemClickListener() {
            public void onItemClick(AdapterView<?> parent, View view, int pos, long id) {
                Intent myIntent = new Intent();
                myIntent.setClass(getActivity().getApplicationContext(), EventDetail.class);

                // pass the JSON data for specified event to the next activity
                myIntent.putExtra("eventJSON", parent.getAdapter().getItem(pos).toString());
                startActivity(myIntent);
            }
        });
        ((PullToRefreshListView) getListView()).setOnRefreshListener(new PullToRefreshListView.OnRefreshListener() {
            @Override
            public void onRefresh() {
                loadEvents(EventListFragment.this.getTag());
            }
        });
    }

    // Display events by populating the m_eventAdapter (custom list) with items loaded from DB
    public int displayEvents(final String eventType) {
        if (listView != null) {
            setListShown(true);
        }

        // Clear all events
        m_eventAdapter.clear();

        // add events and return count
        DataHelper dh = DataHelper.getInstance(getActivity());
        int count = dh.populateEvents(eventType, m_eventAdapter, eventSortOrder);

        // Tell the adapter that our data set has changed so it can update it
        m_eventAdapter.notifyDataSetChanged();
        if (listView != null) {
            ((PullToRefreshListView) getListView()).onRefreshComplete();
        }

        parentActivity.setEventsCountTitle(count);

        return count;
    }

    public void setEventSortOrder(int sortOrder) {
        this.eventSortOrder = sortOrder;
        displayEvents(this.getTag());
    }

    public int getEventSortOrder() {
        return this.eventSortOrder;
    }

    public void filterByString(CharSequence s) {
        m_eventAdapter.getFilter().filter(s);
    }

    protected void setTitle(String eventType) {
        String title = "";
        if (eventType.equals("hot")) title = this.getString(R.string.activityMainEventsHot);
        if (eventType.equals("past")) title = this.getString(R.string.activityMainEventsPast);
        if (eventType.equals("upcoming")) title = this.getString(R.string.activityMainEventsUpcoming);

        parentActivity.setEventsTitle(title, 0);
    }

    class EventLoaderThread extends Thread {
        private volatile Thread runner;

        private String event_type;

        public synchronized void startThread(String type) {
            event_type = type;

            if (runner == null) {
                runner = new Thread(this);
                runner.start();
            }
        }

        public synchronized void stopThread() {
            // Already stopped
            if (runner == null) {
                return;
            }

            Thread moribund = runner;
            runner = null;
            moribund.interrupt();
        }

        public void run() {

            // Get some event data from the joind.in API
            rest = new JIRest(EventListFragment.this.getActivity());
            String urlPostfix = "events";
            if (event_type.length() > 0) {
                urlPostfix += "?filter=" + event_type;
            }
            String uriToUse = rest.makeFullURI(urlPostfix);

            JSONObject fullResponse;
            JSONObject metaObj;
            DataHelper dh = DataHelper.getInstance(EventListFragment.this.getActivity());
            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getActivity().getApplicationContext());
            boolean isFirst = true;
            int error = JIRest.OK; // default

            parentActivity.displayHorizontalProgress(true);
            try {
                do {
                    error = rest.getJSONFullURI(uriToUse);

                    if (error == JIRest.OK) {

                        fullResponse = rest.getJSONResult();
                        if (fullResponse == null) {
                            break;
                        }
                        metaObj = fullResponse.getJSONObject("meta");

                        if (isFirst) {
                            dh.deleteAllEventsFromType(event_type);
                            isFirst = false;
                        }
                        JSONArray json = fullResponse.getJSONArray("events");

                        for (int i = 0; i != json.length(); i++) {
                            JSONObject json_event = json.getJSONObject(i);

                            // Don't add when we are adding to the Past AND we want to display "attended only"
                            if (event_type.equals("past") && prefs.getBoolean("attendonly", false) && !json_event.optBoolean("user_attending")) {
                                continue;
                            }
                            dh.insertEvent(event_type, json_event);
                        }
                        uriToUse = metaObj.getString("next_page");

                        // Yield to the view, so some display
                        getActivity().runOnUiThread(new Runnable() {
                            public void run() {
                                displayEvents(event_type);
                            }
                        });

                        // If we're looking at "hot" events, this API call just
                        // returns events, and more events, and more events....
                        // so we'll just stop after the first round
                        if (event_type.equals("hot")) {
                            break;
                        }
                    } else {
                        break;
                    }
                } while (Thread.currentThread() == runner && metaObj.getInt("count") > 0 && !interrupted());
            } catch (JSONException e) {
                getActivity().runOnUiThread(new Runnable() {
                    public void run() {
                        displayEvents(event_type);
                    }
                });
            }

            // Something bad happened? :(
            if (error != JIRest.OK) {
                // We can only modify the UI in a UIThread, so we create another thread
                getActivity().runOnUiThread(new Runnable() {
                    public void run() {
                        // Display result from the rest to the user
                        Toast toast = Toast.makeText(getActivity().getApplicationContext(), rest.getError(), Toast.LENGTH_LONG);
                        toast.show();
                    }
                });
            }

            // Show the events
            getActivity().runOnUiThread(new Runnable() {
                public void run() {
                    displayEvents(event_type);
                }
            });
            parentActivity.displayHorizontalProgress(false);
        }
    }
}


class JIEventAdapter extends ArrayAdapter<JSONObject> {
    private ArrayList<JSONObject> all_items;
    private ArrayList<JSONObject> filtered_items;
    private Context context;
    LayoutInflater inflater;
    public ImageLoader image_loader;
    private PTypeFilter filter;

    public int getCount() {
        return filtered_items.size();
    }

    public JSONObject getItem(int position) {
        return filtered_items.get(position);
    }

    public JIEventAdapter(Context context, int textViewResourceId, ArrayList<JSONObject> items) {
        super(context, textViewResourceId, items);
        this.context = context;
        this.all_items = items;
        this.filtered_items = items;
        this.inflater = (LayoutInflater) this.context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        this.image_loader = new ImageLoader(context.getApplicationContext(), "events");
    }

    // This function will create a custom row with our event data.
    public View getView(int position, View convertView, ViewGroup parent) {
        if (convertView == null) {
            convertView = this.inflater.inflate(R.layout.eventrow, null);
        }

        // Get the (JSON) data we need
        JSONObject o = filtered_items.get(position);
        if (o == null) return convertView;

        // Display (or load in the background if needed) the event logo
        // We temporarily set the view to GONE to ensure that the row
        // gets recycled (and the image gets updated if required)
        ImageView el = (ImageView) convertView.findViewById(R.id.EventDetailLogo);
        el.setTag("");
        el.setVisibility(View.GONE);
        el.setImageDrawable(null);

        // Display (or load in the background if needed) the event logo
        if (!o.isNull("icon")) {
            String filename = o.optString("icon");
            el.setTag(filename);
            image_loader.displayImage("http://joind.in/inc/img/event_icons/", filename, (Activity) context, el);
        }
        else {
            el.setImageResource(R.drawable.event_icon_none);
        }
        el.setVisibility(View.VISIBLE);

        // Set a darker color when the event is currently running.
        long event_start = 0;
        long event_end = 0;
        try {
            event_start = new SimpleDateFormat(context.getString(R.string.apiDateFormat)).parse(o.optString("start_date")).getTime();
            event_end = new SimpleDateFormat(context.getString(R.string.apiDateFormat)).parse(o.optString("end_date")).getTime();
        } catch (ParseException e) {
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
        ImageView im = (ImageView) convertView.findViewById(R.id.EventDetailAttendingImg);
        if (!o.optBoolean("attending")) {
            im.setVisibility(View.GONE);
        } else {
            im.setVisibility(View.VISIBLE);
        }

        // Set our texts
        if (at != null)
            at.setText(String.format(this.context.getString(R.string.activityMainAttending), o.optInt("attendee_count")));
        if (tt != null) tt.setText(o.optString("name"));
        if (bt != null) {
            // Display start date. Only display end date when it differs (ie: it's multiple day event)
            // Android 2.2 and below don't support the "L" pattern character
            String fmt = Build.VERSION.SDK_INT <= 8 ? "d MMM yyyy" : "d LLL yyyy";
            String d1 = DateHelper.parseAndFormat(o.optString("start_date"), fmt);
            String d2 = DateHelper.parseAndFormat(o.optString("end_date"), fmt);
            bt.setText(d1.equals(d2) ? d1 : d1 + " - " + d2);
        }

        return convertView;
    }


    public Filter getFilter() {
        if (filter == null) {
            filter = new PTypeFilter();
        }
        return filter;
    }

    private class PTypeFilter extends Filter {
        @SuppressWarnings("unchecked")
        protected void publishResults(CharSequence prefix, FilterResults results) {
            filtered_items = (ArrayList<JSONObject>) results.values;
            notifyDataSetChanged();
        }

        protected FilterResults performFiltering(CharSequence prefix) {
            FilterResults results = new FilterResults();
            ArrayList<JSONObject> i = new ArrayList<JSONObject>();

            if (prefix != null && prefix.toString().length() > 0) {

                for (int index = 0; index < all_items.size(); index++) {
                    JSONObject json = all_items.get(index);
                    String title = json.optString("name");
                    // Add to the filtered result list when our string is found in the event_name
                    if (title.toUpperCase().contains(prefix.toString().toUpperCase()))
                        i.add(json);
                }
                results.values = i;
                results.count = i.size();
            } else {
                // No more filtering, display all items
                synchronized (all_items) {
                    results.values = all_items;
                    results.count = all_items.size();
                }
            }

            return results;
        }
    }
}