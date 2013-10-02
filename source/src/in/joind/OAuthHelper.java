package in.joind;

import android.content.Context;
import android.content.res.Resources;
import in.joind.R;

import java.io.InputStream;
import java.util.Properties;

public class OAuthHelper {

    protected static Properties prop = new Properties();
    private static boolean haveTriedLoading = false;

    public static String getApiKey(Context context) {

        if (!loadResources(context)) {
            return null;
        }

        return prop.getProperty("api_key", null);
    }

    public static String getCallback(Context context) {

        if (!loadResources(context)) {
            return null;
        }

        return prop.getProperty("callback", null);
    }

    private static boolean loadResources(Context context) {
        if (!haveTriedLoading) {
            try {
                Resources resources = context.getResources();
                InputStream inputStream = resources.openRawResource(R.raw.oauth);
                prop.load(inputStream);
                haveTriedLoading = true;
            } catch (Exception e) {
                return false;
            }
        }

        return true;
    }
}
