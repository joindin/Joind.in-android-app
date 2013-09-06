package com.noxlogic.joindin;

/*
 * Communication with joind.in API
 */

import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.net.SocketTimeoutException;

import android.accounts.Account;
import android.accounts.AccountManager;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.*;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.params.CoreProtocolPNames;
import org.apache.http.params.HttpConnectionParams;
import org.apache.http.params.HttpParams;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import org.json.JSONException;
import org.json.JSONObject;

class JIRest {
    public static final int OK = 0;
    public static final int TIMEOUT = 1;
    public static final int ERROR = 2;

    public static String JOINDIN_URL = "";

    private String error = "";
    private String result = "";
    private JSONObject jsonResult = null;

    private Context context;

    public JIRest (Context context) {
        this.context = context;
        JOINDIN_URL = context.getResources().getString(R.string.apiURL);
    }

    // Return the last communication result
    public String getResult () {
        return this.result;
    }

    public JSONObject getJSONResult() {
        return this.jsonResult;
    }

    // Return last communication error
    public String getError () {
        return this.error;
    }

    public String makeFullURI(String postfix) {
        return JOINDIN_URL + postfix;
    }

    public int getJSONFullURI(String fullURI) {

        try {
            // Create http client with timeouts so we don't have to wait
            // indefinitely when the internet is kaput
            HttpClient httpclient = new DefaultHttpClient();
            HttpParams params = httpclient.getParams();
            HttpConnectionParams.setConnectionTimeout(params, 30000);
            HttpConnectionParams.setSoTimeout(params, 15000);

            HttpGet httpget = new HttpGet(fullURI);

            httpget.addHeader("Content-type", "application/json");

            // Do not add the "expect: 100-continue" headerline. It will mess up some proxy systems
            httpget.getParams().setBooleanParameter(CoreProtocolPNames.USE_EXPECT_CONTINUE, false);

            httpget = (HttpGet) addAuthDetailsToRequest(httpget);

            try {
                // Post stuff
                HttpResponse response = httpclient.execute (httpget);

                // Get response
                HttpEntity entity = response.getEntity();
                if (entity != null) {
                    // If we receive some data, place it in our result string
                    // and try and convert it to JSON
                    InputStream instream = entity.getContent();
                    this.result = Main.convertStreamToString(instream);
                    try {
                        this.jsonResult = new JSONObject(this.result);
                    } catch (JSONException e) {
                        // Couldn't parse JSON result; leave as null
                    }
                    instream.close();
                    return OK;
                }
            } catch (ClientProtocolException e) {
                // Error during communication
                this.error = String.format (this.context.getString(R.string.JIRestProtocolError), e.getMessage());
                return ERROR;
            } catch (SocketTimeoutException e) {
                // Socket has timed out
                this.error = this.context.getString(R.string.JIRestSocketTimeout);
                return TIMEOUT;
            } catch (IOException e) {
                // IO exception occurred
                this.error = String.format (this.context.getString(R.string.JIRestIOError), e.getMessage());
                return ERROR;
            }
        } catch (Exception e) {
            // Something else happened
            this.error  = String.format (this.context.getString(R.string.JIRestUnknownError), e.getMessage());
            return ERROR;
        }
        return OK;
    }

    public int postJSONFullURI(String fullURI, JSONObject json, boolean addAuthDetails) {

        try {
            // Create http client with timeouts so we don't have to wait
            // indefinitely when the internet is kaput
            HttpClient httpclient = new DefaultHttpClient();
            HttpParams params = httpclient.getParams();
            HttpConnectionParams.setConnectionTimeout(params, 30000);
            HttpConnectionParams.setSoTimeout(params, 15000);

            // We POST our data.
            HttpPost httppost = new HttpPost(fullURI);

            StringEntity jsonentity = null;
            try {
                jsonentity = new StringEntity(json.toString());
                jsonentity.setContentType("application/json");
            } catch (UnsupportedEncodingException e) {
                // Ignore exception
            }

            httppost.setEntity(jsonentity);
            httppost.addHeader("Content-type", "application/json");

            httppost = (HttpPost) addAuthDetailsToRequest(httppost);

            // Do not add the "expect: 100-continue" headerline. It will mess up some proxy systems
            httppost.getParams().setBooleanParameter(CoreProtocolPNames.USE_EXPECT_CONTINUE, false);

            try {
                // Post stuff
                HttpResponse response = httpclient.execute (httppost);

                // Get response
                HttpEntity entity = response.getEntity();
                if (entity != null) {
                    // If we receive some data, place it in our result string
                    // and try and convert to JSON
                    InputStream instream = entity.getContent();
                    this.result = Main.convertStreamToString(instream);
                    try {
                        this.jsonResult = new JSONObject(this.result);
                    } catch (JSONException e) {
                        // Couldn't parse JSON result; leave as null
                    }
                    instream.close();
                    int statusCode = response.getStatusLine().getStatusCode();
                    switch (statusCode) {
                        case HttpStatus.SC_OK:
                        case HttpStatus.SC_CREATED:
                            return OK;
                        default:
                            this.error = String.format (this.context.getString(R.string.JIRestUnknownError), statusCode);
                            return ERROR;
                    }
                }
            } catch (ClientProtocolException e) {
                // Error during communication
                this.error = String.format (this.context.getString(R.string.JIRestProtocolError), e.getMessage());
                return ERROR;
            } catch (SocketTimeoutException e) {
                // Socket has timed out
                this.error = this.context.getString(R.string.JIRestSocketTimeout);
                return TIMEOUT;
            } catch (IOException e) {
                // IO exception occurred
                this.error = String.format (this.context.getString(R.string.JIRestIOError), e.getMessage());
                return ERROR;
            }
        } catch (Exception e) {
            // Something else happened
            this.error  = String.format (this.context.getString(R.string.JIRestUnknownError), e.getMessage());
            return ERROR;
        }
        return OK;
    }

    protected HttpUriRequest addAuthDetailsToRequest(HttpUriRequest request) {
        AccountManager am = AccountManager.get(context);
        Account[] accounts = am.getAccountsByType(context.getString(R.string.authenticatorAccountType));
        Account thisAccount = (accounts.length > 0 ? accounts[0] : null);
        if (thisAccount != null) {
            String authToken = am.peekAuthToken(thisAccount, context.getString(R.string.authTokenType));

            // Add authentication details
            request.addHeader("Authorization", "OAuth " + authToken);
        }

        return request;
    }
}
