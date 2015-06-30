package in.joind;

import android.app.SearchManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.support.v4.app.FragmentTabHost;
import android.support.v4.view.MenuItemCompat;
import android.support.v7.app.ActionBar;
import android.support.v7.widget.SearchView;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.TabHost;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

/**
 * Main activity - contains the tab host, which we'll load our list fragments into
 */
public class Main extends JIActivity implements SearchView.OnQueryTextListener {

    // Constants for dynamically added menu items
    private static final int MENU_SORT_DATE = 1;
    private static final int MENU_SORT_TITLE = 2;

    public static final String TAB_HOT = "Hot";
    public static final String TAB_UPCOMING = "Upcoming";
    public static final String TAB_PAST = "Past";

    private static final String CURRENT_TAB = "currentTab";

    private String currentTab = TAB_HOT; // Current selected tab

    private FragmentTabHost tabHost;

    /**
     * Called when the activity is first created.
     */
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Set 'main' layout
        setContentView(R.layout.main);

        // Create instance of the database singleton. This needs a context
        DataHelper.createInstance(this.getApplicationContext());

        initialiseTabs();
    }


    protected void initialiseTabs() {
        tabHost = (FragmentTabHost) findViewById(R.id.tabHost);
        tabHost.setup(this, getSupportFragmentManager(), android.R.id.tabcontent);
        tabHost.addTab(tabHost.newTabSpec(TAB_HOT).setIndicator(getString(R.string.activityMainEventsHotTab)),
                EventListFragment.class, null);
        tabHost.addTab(tabHost.newTabSpec(TAB_UPCOMING).setIndicator(getString(R.string.activityMainEventsUpcomingTab)),
                EventListFragment.class, null);
        tabHost.addTab(tabHost.newTabSpec(TAB_PAST).setIndicator(getString(R.string.activityMainEventsPastTab)),
                EventListFragment.class, null);
        tabHost.setOnTabChangedListener(new TabHost.OnTabChangeListener() {
            @Override
            public void onTabChanged(String tabId) {
                currentTab = tabId;
                SharedPreferences sp = getSharedPreferences(JIActivity.SHARED_PREF_NAME, Context.MODE_PRIVATE);
                sp.edit().putString(CURRENT_TAB, currentTab).apply();
            }
        });
    }

    // Will reload events. Needed when we return to the screen.
    public void onResume() {
        super.onResume();

        SharedPreferences sp = getSharedPreferences(JIActivity.SHARED_PREF_NAME, Context.MODE_PRIVATE);
        if (sp.contains(CURRENT_TAB)) {
            currentTab = sp.getString(CURRENT_TAB, TAB_HOT);
            tabHost.setCurrentTabByTag(currentTab);
        } else {
            currentTab = TAB_HOT;
            tabHost.setCurrentTabByTag(currentTab);
        }
    }

    // Overriding the JIActivity add sort-items
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.home_menu, menu); // Use the custom home menu

        MenuItem menu_date = menu.add(0, MENU_SORT_DATE, 0, R.string.OptionMenuSortDate);
        menu_date.setIcon(android.R.drawable.ic_menu_month);
        MenuItem menu_title = menu.add(0, MENU_SORT_TITLE, 0, R.string.OptionMenuSortTitle);
        menu_title.setIcon(android.R.drawable.ic_menu_sort_alphabetically);

        // Associate searchable configuration with the SearchView
        SearchManager searchManager = (SearchManager) getSystemService(Context.SEARCH_SERVICE);
        MenuItem searchItem = menu.findItem(R.id.search);
        SearchView searchView = (SearchView) MenuItemCompat.getActionView(searchItem);
        if (searchView != null) {
            searchView.setSearchableInfo(searchManager.getSearchableInfo(getComponentName()));
            searchView.setOnQueryTextListener(this);
        }

        return true;
    }

    // Overriding the JIActivity handler to handle the sorting
    public boolean onOptionsItemSelected(MenuItem item) {
        EventListFragment fragment = (EventListFragment) getSupportFragmentManager().findFragmentByTag(currentTab);
        int currentEventSortOrder;
        int newEventSortOrder;

        switch (item.getItemId()) {
            case MENU_SORT_DATE:
                currentEventSortOrder = fragment.getEventSortOrder();
                newEventSortOrder = (currentEventSortOrder == DataHelper.ORDER_DATE_ASC ? DataHelper.ORDER_DATE_DESC : DataHelper.ORDER_DATE_ASC);
                fragment.setEventSortOrder(newEventSortOrder);
                break;
            case MENU_SORT_TITLE:
                currentEventSortOrder = fragment.getEventSortOrder();
                newEventSortOrder = (currentEventSortOrder == DataHelper.ORDER_TITLE_ASC ? DataHelper.ORDER_TITLE_DESC : DataHelper.ORDER_TITLE_ASC);
                fragment.setEventSortOrder(newEventSortOrder);
                break;
        }

        return super.onOptionsItemSelected(item);
    }

    // Converts input stream to a string.
    public static String convertStreamToString(InputStream is) {
        BufferedReader reader = new BufferedReader(new InputStreamReader(is), 8192);
        StringBuilder sb = new StringBuilder();

        String line;
        try {
            while ((line = reader.readLine()) != null) {
                sb.append(line).append("\n");
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

    public void setEventsCountTitle(int eventCount) {
        String subTitle;
        if (eventCount == 1) {
            subTitle = String.format(getString(R.string.generalEventCountSingular), eventCount);
        } else {
            subTitle = String.format(getString(R.string.generalEventCountPlural), eventCount);
        }
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) actionBar.setSubtitle(subTitle);
    }

    public void setEventsTitle(String title, int count) {
        // Set main title to event category plus the number of events found
        setTabTitle(title, count);
    }

    @Override
    public boolean onQueryTextSubmit(String s) {
        return false;
    }

    @Override
    public boolean onQueryTextChange(String s) {
        EventListFragment fragment = (EventListFragment) getSupportFragmentManager().findFragmentByTag(currentTab);
        fragment.filterByString(s);

        return false;
    }

    public void displayHorizontalProgress(final boolean state) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                View v = findViewById(R.id.progress_bar);
                v.setVisibility(state ? View.VISIBLE : View.GONE);
            }
        });
    }

    protected void setTabTitle(String title, int eventCount) {
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) actionBar.setSubtitle(title);
        setEventsCountTitle(eventCount);
    }
}
