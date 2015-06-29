package in.joind;

/*
 * Communication with joind.in API
 */

import android.accounts.Account;
import android.accounts.AccountManager;
import android.content.Context;
import android.util.Log;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.ProtocolException;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.nio.charset.Charset;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLSession;

public class JIRest {
    // Number of times to attempt a connection
    // This is partially to work around a bug in HTTPUrlConnection
    private static final int MAX_RETRIES = 5;

    public static final int OK = 0;
    public static final int TIMEOUT = 1;
    public static final int ERROR = 2;

    public static String JOINDIN_URL = "";

    public static final String METHOD_POST = "POST";
    public static final String METHOD_DELETE = "DELETE";

    private String error = "";
    private String result = "";
    private JSONObject jsonResult = null;

    private Context context;

    public JIRest(Context context) {
        this.context = context;
        JOINDIN_URL = context.getResources().getString(R.string.apiURL);
    }

    // Return the last communication result
    public String getResult() {
        return this.result;
    }

    public JSONObject getJSONResult() {
        return this.jsonResult;
    }

    // Return last communication error
    public String getError() {
        return this.error;
    }

    public String makeFullURI(String postfix) {
        return JOINDIN_URL + postfix;
    }

    public int getJSONFullURI(String fullURI) {

        HttpURLConnection connection = getConnection(fullURI);
        if (connection == null) {
            this.error = this.context.getString(R.string.JIRestValidConnectionError);

            return ERROR;
        }

        connection.addRequestProperty("Content-type", "application/json");
        connection = addAuthDetailsToRequest(connection);

        try {
            // Create http client with timeouts so we don't have to wait
            // indefinitely when the internet is kaput

            // Get response
            InputStream inStream = new BufferedInputStream(connection.getInputStream());
            this.result = Main.convertStreamToString(inStream);
            try {
                this.jsonResult = new JSONObject(this.result);
            } catch (JSONException e) {
                // Couldn't parse JSON result; leave as null
            }
            inStream.close();

        } catch (SocketTimeoutException e) {
            // Socket timeout occurred
            this.error = this.context.getString(R.string.JIRestSocketTimeout);
            connection.disconnect();

            return TIMEOUT;
        } catch (IOException e) {
            // IO exception occurred
            this.error = String.format(this.context.getString(R.string.JIRestIOError), e.getMessage());
            connection.disconnect();

            return ERROR;
        } catch (Exception e) {
            // Something else happened
            this.error = String.format(this.context.getString(R.string.JIRestUnknownError), e.getMessage());
            connection.disconnect();

            return ERROR;
        }

        connection.disconnect();

        return OK;
    }

    public int requestToFullURI(String fullURI, JSONObject json, String method) {
        HttpURLConnection connection = getConnection(fullURI);
        if (method.equals(METHOD_POST)) {
            if (json != null) {
                String content = json.toString();
                connection.setFixedLengthStreamingMode(content.length());
            }
            connection.setDoOutput(true);
            try {
                connection.setRequestMethod("POST");
            } catch (ProtocolException e) {
                e.printStackTrace();
                return ERROR;
            }

            return doMethodRequest(connection, json);
        }
        if (method.equals(METHOD_DELETE)) {
            try {
                connection.setRequestMethod("DELETE");
            } catch (ProtocolException e) {
                e.printStackTrace();
                return ERROR;
            }

            return doMethodRequest(connection, null);
        }

        Log.d(JIActivity.LOG_JOINDIN_APP, "Some error!!");
        return ERROR;
    }

    protected int doMethodRequest(HttpURLConnection connection, JSONObject content) {

        connection.setRequestProperty("Content-type", "application/json");
        connection = addAuthDetailsToRequest(connection);

        int resultCode = ERROR;
        int numberOfTries = 0;
        while (numberOfTries < MAX_RETRIES) {
            try {
                if (content != null) {
                    OutputStream outputStream = new BufferedOutputStream(connection.getOutputStream());
                    outputStream.write(content.toString().getBytes(Charset.forName("UTF-8")));
                    outputStream.close();
                }

                // Get response
                InputStream inStream = new BufferedInputStream(connection.getInputStream());
                this.result = Main.convertStreamToString(inStream);
                try {
                    this.jsonResult = new JSONObject(this.result);
                } catch (JSONException e) {
                    e.printStackTrace();
                    // Couldn't parse JSON result; leave as null
                }
                inStream.close();

                int statusCode = connection.getResponseCode();

                switch (statusCode) {
                    case HttpURLConnection.HTTP_OK:
                    case HttpURLConnection.HTTP_CREATED:
                        return OK;
                    default:
                        this.error = String.format(this.context.getString(R.string.JIRestUnknownError), statusCode);
                        return ERROR;
                }
            } catch (SocketTimeoutException e) {
                // Socket timeout occurred
                this.error = this.context.getString(R.string.JIRestSocketTimeout);
            } catch (IOException e) {
                e.printStackTrace();
                // IO exception occurred. Get the result anyway
                InputStream instream = new BufferedInputStream(connection.getErrorStream());
                this.result = Main.convertStreamToString(instream);
                this.error = String.format(this.context.getString(R.string.JIRestIOError), e.getMessage());
            } catch (Exception e) {
                // Something else happened
                e.printStackTrace();
                this.error = String.format(this.context.getString(R.string.JIRestUnknownError), e.getMessage());
            } finally {
                connection.disconnect();
            }

            numberOfTries++;
        }

        return resultCode;
    }

    protected HttpURLConnection addAuthDetailsToRequest(HttpURLConnection connection) {
        AccountManager am = AccountManager.get(context);
        Account[] accounts = am.getAccountsByType(context.getString(R.string.authenticatorAccountType));
        Account thisAccount = (accounts.length > 0 ? accounts[0] : null);
        if (thisAccount != null) {
            String authToken = am.peekAuthToken(thisAccount, context.getString(R.string.authTokenType));

            // Add authentication details
            connection.setRequestProperty("Authorization", "OAuth " + authToken);
        }

        return connection;
    }

    protected HttpURLConnection getConnection(String hostURL) {
        try {
            final URL url = new URL(hostURL);
            int timeoutLength = 30000;
            if (url.getProtocol().equals("https")) {
                HttpsURLConnection.setDefaultHostnameVerifier(new HostnameVerifier() {
                    @Override
                    public boolean verify(String hostname, SSLSession session) {
                        return hostname.equals(url.getHost());
                    }
                });

                HttpsURLConnection connection = (HttpsURLConnection) url.openConnection();
                connection.setRequestProperty("Connection", "close");
                connection.setConnectTimeout(timeoutLength);

                return connection;
            } else {
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                connection.setRequestProperty("Connection", "close");
                connection.setConnectTimeout(timeoutLength);

                return connection;
            }
        } catch (MalformedURLException e) {
            return null;
        } catch (IOException e) {
            return null;
        }
    }
}
