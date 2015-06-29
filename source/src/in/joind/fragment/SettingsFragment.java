package in.joind.fragment;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.preference.PreferenceScreen;

import in.joind.R;

public class SettingsFragment extends PreferenceListFragment implements SharedPreferences.OnSharedPreferenceChangeListener, PreferenceListFragment.OnPreferenceAttachedListener {
    @Override
    public void onCreate(Bundle bundle) {
        super.onCreate(bundle);

        PreferenceManager preferenceManager = getPreferenceManager();
        addPreferencesFromResource(R.xml.preferences);
        preferenceManager.getSharedPreferences().registerOnSharedPreferenceChangeListener(this);
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
    }

    @Override
    public void onPreferenceAttached(PreferenceScreen root, int xmlId) {
    }
}
