package com.noxlogic.joindin;

import android.widget.Toast;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.Properties;

public class OAuthHelper {

    protected static Properties prop = new Properties();
    private static boolean haveTriedLoading = false;

    public static String getApiKey() {
        if (!haveTriedLoading) {
            try {
                prop.load(new FileInputStream("oauth.properties"));
            } catch (IOException e) {
                return null;
            }
        }
        return prop.getProperty("api_key", null);
    }
}
