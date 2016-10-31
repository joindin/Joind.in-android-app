package in.joind;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v4.app.ListFragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.Filter;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.markupartist.android.widget.PullToRefreshListView;
import com.squareup.picasso.Picasso;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Locale;

import in.joind.activity.SettingsActivity;
import in.joind.adapter.EventAdapter;
import in.joind.fragment.FragmentLifecycle;
import in.joind.fragment.LogInDialogFragment;
import in.joind.user.UserManager;

/**
 * The list fragment that is shown in our tabbed view.
 * Lists events depending on event type (in our case, the fragment's Tag value)
 */
public class EventListFragment extends ListFragment implements EventListFragmentInterface, FragmentLifecycle {

    final static public String ARG_LIST_TYPE_KEY = "listType";
    final static public String LIST_TYPE_HOT = "hot";
    final static public String LIST_TYPE_UPCOMING = "upcoming";
    final static public String LIST_TYPE_MY_EVENTS = "my_events";
    final static public String LIST_TYPE_PAST = "past";

    private EventAdapter m_eventAdapter;
    private EventLoaderThread eventLoaderThread;
    int eventSortOrder = DataHelper.ORDER_DATE_ASC;
    Main parentActivity;
    JIRest rest;
    LogInReceiver logInReceiver;
    ListView listView;
    View emptyView;
    LinearLayout notSignedInView;
    Button signInButton;
    String eventType;

    public void onAttach(Activity activity) {
        super.onAttach(activity);
    }

    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        eventType = getArguments().getString(ARG_LIST_TYPE_KEY);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {

        ViewGroup viewGroup = (ViewGroup) inflater.inflate(R.layout.event_list_fragment, container, false);
        if (viewGroup != null) {
            // Populate our list adapter
            ArrayList<JSONObject> m_events = new ArrayList<>();
            m_eventAdapter = new EventAdapter(getActivity(), R.layout.eventrow, m_events);
            setListAdapter(m_eventAdapter);

            signInButton = (Button) viewGroup.findViewById(R.id.myEventsSignInButton);
        }

        return viewGroup;
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        listView = getListView();
        emptyView = listView.getEmptyView();
        notSignedInView = (LinearLayout) view.findViewById(R.id.notSignedInList);

        setViewVisibility(false, false);
        setupEvents();
    }

    @Override
    public void onActivityCreated(android.os.Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        parentActivity = (Main) getActivity();
    }

    @Override
    public void onPause() {
        super.onPause();
        if (logInReceiver != null && parentActivity != null) {
            parentActivity.unregisterReceiver(logInReceiver);
        }
        pauseLoading();
    }

    public void pauseLoading() {
        if (eventLoaderThread != null) {
            eventLoaderThread.stopThread();
        }
        eventLoaderThread = null;
        listView = null;
    }

    public void onResume() {
        super.onResume();
        listView = getListView();

        if (parentActivity != null) {
            logInReceiver = new LogInReceiver();
            IntentFilter intentFilter = new IntentFilter(SettingsActivity.ACTION_USER_LOGGED_IN);
            parentActivity.registerReceiver(logInReceiver, intentFilter);
        }

        if (getUserVisibleHint() && parentActivity != null) {
            performEventListUpdate();
        }
    }

    @Override
    public void onPauseFragment() {
        pauseLoading();
    }

    @Override
    public void onResumeFragment() {
    }

    @Override
    public void setUserVisibleHint(boolean isVisibleToUser) {
        super.setUserVisibleHint(isVisibleToUser);
        if (eventType == null) {
            return;
        }
        if (isVisibleToUser && parentActivity != null) {
            performEventListUpdate();
        }
    }

    private void setViewVisibility(boolean showList, boolean showNotSignedIn) {
        // List and empty view are opposites
        listView.setVisibility(showList ? View.VISIBLE : View.GONE);
        emptyView.setVisibility(showList ? View.GONE : View.VISIBLE);

        notSignedInView.setVisibility(showNotSignedIn ? View.VISIBLE : View.GONE);
        if (showNotSignedIn) {
            emptyView.setVisibility(View.GONE);
        }
    }

    private void performEventListUpdate() {
        // My Events - check our signed-in status
        // We explicitly request a refresh of the authenticated status
        boolean isAuthenticated = parentActivity.isAuthenticated(true);
        if (eventType.equals(LIST_TYPE_MY_EVENTS)) {
            if (!isAuthenticated) {
                setViewVisibility(false, true);

                // Not signed in, no need to carry on
                return;
            }

            setViewVisibility(true, false);
        }

        // If we don't have any events in the adapter, then try and load some
        if (m_eventAdapter != null && m_eventAdapter.getCount() == 0) {
            if (listView != null) {
                setViewVisibility(false, false);
            }

            loadEvents(eventType);
        }
    }

    void loadEvents(String type) {
        if (eventLoaderThread != null) {
            // Stop event loading thread, we're going to start a new one
            eventLoaderThread.stopThread();
        }

        // Create a event loader thread
        eventLoaderThread = new EventLoaderThread();
        eventLoaderThread.setDaemon(true);
        eventLoaderThread.setPriority(Thread.NORM_PRIORITY - 1);
        eventLoaderThread.startThread(type, parentActivity);
    }

    protected void setupEvents() {
        listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            public void onItemClick(AdapterView<?> parent, View view, int pos, long id) {
                Intent myIntent = new Intent();
                myIntent.setClass(getActivity().getApplicationContext(), EventDetail.class);

                // pass the JSON data for specified event to the next activity
                myIntent.putExtra("eventJSON", parent.getAdapter().getItem(pos).toString());
                startActivity(myIntent);
            }
        });
        ((PullToRefreshListView) listView).setOnRefreshListener(new PullToRefreshListView.OnRefreshListener() {
            @Override
            public void onRefresh() {
                loadEvents(eventType);
            }
        });
        signInButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                LogInDialogFragment dlg = new LogInDialogFragment();
                dlg.show(getChildFragmentManager(), "login");
            }
        });
    }

    /**
     *  Display events by populating the m_eventAdapter (custom list) with items loaded from DB
     */
    public int displayEvents(String eventType) {
        if (listView != null) {
            setViewVisibility(true, false);
        }

        // Clear all events
        m_eventAdapter.clear();

        // add events and return count
        DataHelper dh = DataHelper.getInstance(parentActivity);
        int count = dh.populateEvents(eventType, m_eventAdapter, eventSortOrder);

        // Tell the adapter that our data set has changed so it can update it
        m_eventAdapter.notifyDataSetChanged();
        if (listView != null) {
            ((PullToRefreshListView) getListView()).onRefreshComplete();
        }

        return count;
    }

    public void setEventSortOrder(int sortOrder) {
        eventSortOrder = sortOrder;
        displayEvents(eventType);
    }

    public int getEventSortOrder() {
        return eventSortOrder;
    }

    public void filterByString(CharSequence s) {
        m_eventAdapter.getFilter().filter(s);
    }

    private void checkForUserData(JSONObject jsonResult)
    {
        JSONObject metaBlock = jsonResult.optJSONObject("meta");
        if (metaBlock == null || metaBlock.length() == 0) {
            return;
        }

        String userURI = metaBlock.optString("user_uri");
        if (userURI == null || userURI.length() == 0) {
            return;
        }

        UserManager userManager = new UserManager(getActivity());
        if (!userManager.accountRequiresFurtherDetails()) {
            return;
        }
        userManager.updateSavedUserDetails(userURI);
    }

    /**
     * Inner class: The thread that loads events in, and updates the fragment accordingly
     */
    class EventLoaderThread extends Thread {
        private volatile Thread runner;

        private String eventType;
        private Main parentActivity;

        public synchronized void startThread(String type, Main parentActivity) {
            eventType = type;
            this.parentActivity = parentActivity;

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
            String uriToUse;
            rest = new JIRest(parentActivity);
            String urlPostfix = "events";
            if (!eventType.equals(EventListFragment.LIST_TYPE_MY_EVENTS)) {
                if (eventType.length() > 0) {
                    urlPostfix += "?filter=" + eventType;
                }
                uriToUse = rest.makeFullURI(urlPostfix);
            } else {
                // Use the "attended_events_uri" property on the user metadata
                uriToUse = parentActivity.getAccountData(getString(R.string.authUserURIAttendedEvents));
            }

            JSONObject fullResponse;
            JSONObject metaObj;
            DataHelper dh = DataHelper.getInstance(parentActivity);
            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(parentActivity.getApplicationContext());
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
                            dh.deleteAllEventsFromType(eventType);
                            isFirst = false;

                            // Update user details on first hit if we need to
                            checkForUserData(fullResponse);
                        }
                        JSONArray json = fullResponse.getJSONArray("events");

                        for (int i = 0; i != json.length(); i++) {
                            JSONObject json_event = json.getJSONObject(i);

                            // Don't add when we are adding to the Past AND we want to display "attended only"
                            if (eventType.equals("past")
                                    && prefs.getBoolean("attendonly", false)
                                    && !json_event.optBoolean("user_attending")) {
                                continue;
                            }
                            dh.insertEvent(eventType, json_event);
                        }
                        uriToUse = metaObj.getString("next_page");

                        // Yield to the view, so some display
                        uiDisplayEvents();

                        // If we're looking at "hot" events, this API call just
                        // returns events, and more events, and more events....
                        // so we'll just stop after the first round
                        if (eventType.equals("hot")) {
                            break;
                        }
                    } else {
                        break;
                    }
                } while (Thread.currentThread() == runner && metaObj.getInt("count") > 0 && !interrupted());
            } catch (JSONException e) {
                uiDisplayEvents();
            }

            // Something bad happened? :(
            if (error != JIRest.OK) {
                if (parentActivity != null && rest.getError().length() > 0) {
                    parentActivity.runOnUiThread(new Runnable() {
                        public void run() {
                            // Display result from the rest to the user
                            Toast toast = Toast.makeText(parentActivity, rest.getError(), Toast.LENGTH_LONG);
                            toast.show();
                        }
                    });
                }
            }

            // Show the events
            uiDisplayEvents();
            if (parentActivity != null) {
                parentActivity.displayHorizontalProgress(false);
            }
        }
    }

    private void uiDisplayEvents() {
        if (parentActivity != null) {
            parentActivity.runOnUiThread(new Runnable() {
                public void run() {
                    displayEvents(eventType);
                }
            });
        }
    }

    /**
     * Handle login intents
     */
    private class LogInReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (action == null) {
                return;
            }
            if (action.equals(SettingsActivity.ACTION_USER_LOGGED_IN)) {
                performEventListUpdate();
            }
        }
    }
}
