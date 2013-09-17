package com.noxlogic.joindin;

import android.content.Context;
import android.content.res.Resources;
import java.io.InputStream;
import java.util.Properties;

public class OAuthHelper {

    protected static Properties prop = new Properties();
    private static boolean haveTriedLoading = false;

    public static String getApiKey(Context context) {
        if (!haveTriedLoading) {
            try {
                Resources resources = context.getResources();
                InputStream inputStream = resources.openRawResource(R.raw.oauth);
                prop.load(inputStream);
                haveTriedLoading = true;
            } catch (Exception e) {
                return null;
            }
        }

        return prop.getProperty("api_key", null);
    }
}
