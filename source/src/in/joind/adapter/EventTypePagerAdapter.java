package in.joind.adapter;

import android.content.Context;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentStatePagerAdapter;

import in.joind.fragment.EventListFragment;
import in.joind.R;

public class EventTypePagerAdapter extends FragmentStatePagerAdapter {
    Context context;

    public EventTypePagerAdapter(FragmentManager fm, Context context) {
        super(fm);
        this.context = context;
    }

    @Override
    public Fragment getItem(int position) {
        Fragment fragment = new EventListFragment();
        Bundle args = new Bundle();
        args.putString(EventListFragment.ARG_LIST_TYPE_KEY, getItemTypeForPosition(position));
        fragment.setArguments(args);

        return fragment;
    }

    @Override
    public int getCount() {
        // Hot, Upcoming, My events, Past
        return 4;
    }

    @Override
    public CharSequence getPageTitle(int position) {
        switch (position) {
            case 0:
            default:
                return context.getString(R.string.activityMainEventsHot);
            case 1:
                return context.getString(R.string.activityMainEventsUpcoming);
            case 2:
                return context.getString(R.string.activityMainEventsMyEvent);
            case 3:
                return context.getString(R.string.activityMainEventsPast);
        }
    }

    private String getItemTypeForPosition(int position)
    {
        switch (position) {
            case 0:
            default:
                return EventListFragment.LIST_TYPE_HOT;
            case 1:
                return EventListFragment.LIST_TYPE_UPCOMING;
            case 2:
                return EventListFragment.LIST_TYPE_MY_EVENTS;
            case 3:
                return EventListFragment.LIST_TYPE_PAST;
        }
    }
}
