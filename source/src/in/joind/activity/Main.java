package in.joind.activity;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.app.SearchManager;
import android.graphics.PorterDuff;
import android.support.v4.view.MenuItemCompat;
import android.support.v4.view.ViewPager;
import android.support.v7.widget.SearchView;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.ProgressBar;

import in.joind.api.DataHelper;
import in.joind.fragment.EventListFragment;
import in.joind.R;
import in.joind.adapter.EventTypePagerAdapter;
import in.joind.fragment.FragmentLifecycle;

/**
 * Main activity - contains the tab host, which we'll load our list fragments into
 */
public class Main extends JIActivity implements SearchView.OnQueryTextListener {

    int currentTabIndex = 0; // Hot

    // Constants for dynamically added menu items
    private static final int MENU_SORT_DATE = 1;
    private static final int MENU_SORT_TITLE = 2;

    ViewPager viewPager;
    EventTypePagerAdapter pagerAdapter;
    ProgressBar progressBar;

    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Set 'main' layout
        setContentView(R.layout.main);

        // Create instance of the database singleton. This needs a context
        DataHelper.createInstance(this.getApplicationContext());

        setTitle(getString(R.string.titleMain));

        initialiseTabs();
    }


    protected void initialiseTabs() {
        pagerAdapter = new EventTypePagerAdapter(getSupportFragmentManager(), this);
        viewPager = (ViewPager) findViewById(R.id.eventTypePager);
        viewPager.setAdapter(pagerAdapter);
        viewPager.addOnPageChangeListener(new ViewPager.OnPageChangeListener() {

            int currentPosition = 0;

            @Override
            public void onPageScrolled(int i, float v, int i1) {
            }

            @Override
            public void onPageSelected(int newPosition) {
                saveState(newPosition);
                FragmentLifecycle fragmentToShow = (FragmentLifecycle) pagerAdapter.getItem(newPosition);
                fragmentToShow.onResumeFragment();

                FragmentLifecycle fragmentToHide = (FragmentLifecycle) pagerAdapter.getItem(currentPosition);
                fragmentToHide.onPauseFragment();

                currentPosition = newPosition;
            }

            @Override
            public void onPageScrollStateChanged(int i) {
            }

            private void saveState(int position) {
                SharedPreferences sp = getSharedPreferences(JIActivity.SHARED_PREF_NAME, Context.MODE_PRIVATE);
                sp.edit().putInt("currentTabIndex", position).apply();
                currentTabIndex = position;
            }
        });
    }

    // Will reload events. Needed when we return to the screen.
    public void onResume() {
        super.onResume();

        SharedPreferences sp = getSharedPreferences(JIActivity.SHARED_PREF_NAME, Context.MODE_PRIVATE);
        if (sp.contains("currentTabIndex")) {
            currentTabIndex = sp.getInt("currentTabIndex", 0);
        }
        viewPager.setCurrentItem(currentTabIndex);

        // Tint the progress bar
        int color = 0xFFFFFFFF;
        progressBar = (ProgressBar) findViewById(R.id.progress_bar);
        progressBar.getIndeterminateDrawable().setColorFilter(color, PorterDuff.Mode.SRC_IN);
        progressBar.getProgressDrawable().setColorFilter(color, PorterDuff.Mode.SRC_IN);
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
        EventListFragment fragment = (EventListFragment) pagerAdapter.instantiateItem(viewPager, viewPager.getCurrentItem());
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

    @Override
    public boolean onQueryTextSubmit(String s) {
        return false;
    }

    @Override
    public boolean onQueryTextChange(String s) {
        EventListFragment fragment = (EventListFragment) pagerAdapter.instantiateItem(viewPager, viewPager.getCurrentItem());
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

    /**
     * Retrieves account data for the signed-in account
     * Returns null if there is no account (user not signed in)
     *
     * @param key
     * @return
     */
    public String getAccountData(String key)
    {
        AccountManager accountManager = AccountManager.get(this);
        Account[] accounts = accountManager.getAccountsByType(getString(R.string.authenticatorAccountType));
        Account thisAccount = (accounts.length > 0 ? accounts[0] : null);

        if (thisAccount == null || thisAccount.name.equals("")) {
            return null;
        }

        return accountManager.getUserData(thisAccount, key);
    }
}
