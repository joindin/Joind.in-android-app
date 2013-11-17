package in.joind;

/*
 * MainActivity activity. Displays all events and let the user select one.
 */

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;

import android.support.v4.app.FragmentTabHost;
import android.util.Log;
import in.joind.R;
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
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.View.OnClickListener;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Filter;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TabHost;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.AdapterView.OnItemClickListener;

public class MainActivity extends JIActivity {

    private String currentTab;                   // Current selected tab

    // Constants for dynamically added menu items
    private static final int MENU_SORT_DATE = 1;
    private static final int MENU_SORT_TITLE = 2;

    private int event_sort_order = DataHelper.ORDER_DATE_ASC;

    private EditText filterText;
    private FragmentTabHost tabHost;

    private TextWatcher filterTextWatcher = new TextWatcher() {

        public void beforeTextChanged(CharSequence s, int start, int count, int after) {
        }

        public void onTextChanged(CharSequence s, int start, int before, int count) {
            //m_eventAdapter.getFilter().filter(s);
        }

        public void afterTextChanged(Editable s) {

        }
    };

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

        filterText = (EditText) findViewById(R.id.FilterBar);
        filterText.addTextChangedListener(filterTextWatcher);
    }


    protected void onDestroy() {
        super.onDestroy();
        filterText.removeTextChangedListener(filterTextWatcher);
    }

    protected void initialiseTabs()
    {
        tabHost = (FragmentTabHost) findViewById(R.id.tabHost);
        tabHost.setup(this, getSupportFragmentManager(), R.id.realtabcontent);
        tabHost.addTab(tabHost.newTabSpec("hot").setIndicator("Hot"), EventListFragment.class, null);
        tabHost.addTab(tabHost.newTabSpec("upcoming").setIndicator("Upcoming"), EventListFragment.class, null);
        tabHost.addTab(tabHost.newTabSpec("past").setIndicator("Past"), EventListFragment.class, null);
        tabHost.setOnTabChangedListener(new TabHost.OnTabChangeListener() {
            @Override
            public void onTabChanged(String tabId) {
                SharedPreferences sp = getSharedPreferences(JIActivity.SHARED_PREF_NAME, Context.MODE_PRIVATE);
                sp.edit().putString("currentTab", tabId).commit();
            }
        });
    }

    // Will reload events. Needed when we return to the screen.
    public void onResume() {
        super.onResume();

        SharedPreferences sp = getSharedPreferences(JIActivity.SHARED_PREF_NAME, Context.MODE_PRIVATE);
        if (sp.contains("currentTab")) {
            String currentTab = sp.getString("currentTab", "hot");
            tabHost.setCurrentTabByTag(currentTab);
        }
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
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case MENU_SORT_DATE:
                // Toast.makeText(this, "Sorting by data", Toast.LENGTH_SHORT).show();
                this.event_sort_order = this.event_sort_order == DataHelper.ORDER_DATE_ASC ? DataHelper.ORDER_DATE_DESC : DataHelper.ORDER_DATE_ASC;
                //displayEvents(this.currentTab);
                break;
            case MENU_SORT_TITLE:
                // Toast.makeText(this, "Sorting by title", Toast.LENGTH_SHORT).show();
                this.event_sort_order = this.event_sort_order == DataHelper.ORDER_TITLE_ASC ? DataHelper.ORDER_TITLE_DESC : DataHelper.ORDER_TITLE_ASC;
                //displayEvents(this.currentTab);
                break;
        }

        return super.onOptionsItemSelected(item);
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

    protected void setTabTitle(String title, int eventCount) {
        getSupportActionBar().setTitle(title);
        getSupportActionBar().setSubtitle(eventCount + " event" + (eventCount == 1 ? "" : "s"));
    }

    public void setEventsTitle(String title, int count) {
        // Set main title to event category plus the number of events found
        setTabTitle(title, count);
    }
}
