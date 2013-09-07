package com.noxlogic.joindin;

/*
 * Communication with joind.in API
 */

import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.math.BigInteger;
import java.net.SocketTimeoutException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import android.util.Log;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
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

            try {
                // Post stuff
                HttpResponse response = httpclient.execute(httpget);

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
                this.error = String.format(this.context.getString(R.string.JIRestProtocolError), e.getMessage());
                return ERROR;
            } catch (SocketTimeoutException e) {
                // Socket has timed out
                this.error = this.context.getString(R.string.JIRestSocketTimeout);
                return TIMEOUT;
            } catch (IOException e) {
                // IO exception occurred
                this.error = String.format(this.context.getString(R.string.JIRestIOError), e.getMessage());
                return ERROR;
            }
        } catch (Exception e) {
            // Something else happened
            this.error = String.format(this.context.getString(R.string.JIRestUnknownError), e.getMessage());
            return ERROR;
        }
        return OK;
    }

    public int postJSON(String urlPostfix, JSONObject json) {

        try {
            // Create http client with timeouts so we don't have to wait
            // indefinitely when the internet is kaput
            HttpClient httpclient = new DefaultHttpClient();
            HttpParams params = httpclient.getParams();
            HttpConnectionParams.setConnectionTimeout(params, 30000);
            HttpConnectionParams.setSoTimeout(params, 15000);

            // We POST our data.
            HttpPost httppost = new HttpPost(JOINDIN_URL + urlPostfix);

            StringEntity jsonentity = null;
            try {
                jsonentity = new StringEntity(json.toString());
                jsonentity.setContentType("application/json");
            } catch (UnsupportedEncodingException e) {
                // Ignore exception
            }

            httppost.setEntity(jsonentity);
            httppost.addHeader("Content-type", "application/json");

            // Do not add the "expect: 100-continue" headerline. It will mess up some proxy systems
            httppost.getParams().setBooleanParameter(CoreProtocolPNames.USE_EXPECT_CONTINUE, false);

            try {
                // Post stuff
                HttpResponse response = httpclient.execute(httppost);

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
                    return OK;
                }
            } catch (ClientProtocolException e) {
                // Error during communication
                this.error = String.format(this.context.getString(R.string.JIRestProtocolError), e.getMessage());
                return ERROR;
            } catch (SocketTimeoutException e) {
                // Socket has timed out
                this.error = this.context.getString(R.string.JIRestSocketTimeout);
                return TIMEOUT;
            } catch (IOException e) {
                // IO exception occurred
                this.error = String.format(this.context.getString(R.string.JIRestIOError), e.getMessage());
                return ERROR;
            }
        } catch (Exception e) {
            // Something else happened
            this.error = String.format(this.context.getString(R.string.JIRestUnknownError), e.getMessage());
            return ERROR;
        }
        return OK;
    }

    // Post XML.. funny.. we post XML and we receive JSON.
    public int postXML(String urlPostfix, String xml) {

        try {
            // Create http client with timeouts so we don't have to wait
            // indefinitely when the internet is kaput
            HttpClient httpclient = new DefaultHttpClient();
            HttpParams params = httpclient.getParams();
            HttpConnectionParams.setConnectionTimeout(params, 30000);
            HttpConnectionParams.setSoTimeout(params, 15000);

            // We POST our data.
            HttpPost httppost = new HttpPost(JOINDIN_URL + urlPostfix);

            // Attention: MUST be text/xml. Took a while to figure this one out!
            StringEntity xmlentity = null;
            try {
                xmlentity = new StringEntity(xml);
                xmlentity.setContentType("text/xml");
            } catch (UnsupportedEncodingException e) {
                // Ignore exception
            }

            // Log.d("joindin", JOINDIN_URL+urlPostfix + " --> " + xml);
            httppost.setEntity(xmlentity);
            httppost.addHeader("Content-type", "text/xml");

            // Do not add the "expect: 100-continue" headerline. It will mess up some proxy systems
            httppost.getParams().setBooleanParameter(CoreProtocolPNames.USE_EXPECT_CONTINUE, false);

            try {
                // Post stuff
                HttpResponse response = httpclient.execute(httppost);

                // Get response
                HttpEntity entity = response.getEntity();
                if (entity != null) {
                    // If we receive some data, place it in our result string
                    InputStream instream = entity.getContent();
                    this.result = Main.convertStreamToString(instream);
                    instream.close();
                    // Log.d("joindin", this.result);
                    return OK;
                }
            } catch (ClientProtocolException e) {
                // Error during communication
                this.error = String.format(this.context.getString(R.string.JIRestProtocolError), e.getMessage());
                return ERROR;
            } catch (SocketTimeoutException e) {
                // Socket has timed out
                this.error = this.context.getString(R.string.JIRestSocketTimeout);
                return TIMEOUT;
            } catch (IOException e) {
                // IO exception occurred
                this.error = String.format(this.context.getString(R.string.JIRestIOError), e.getMessage());
                return ERROR;
            }
        } catch (Exception e) {
            // Something else happened
            this.error = String.format(this.context.getString(R.string.JIRestUnknownError), e.getMessage());
            return ERROR;
        }
        return OK;
    }

    // This will return either a empty string, or a xml auth string <auth><user><pass></auth> that can be used in messages.
    // We need an activity because we need to fetch the preference manager which is only fetchable from a context.
    // ( @TODO: find another way to fetch basecontext)
    public static String getAuthXML(Context context) {
        // Make authentication string from the preferences
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        if (prefs.getString("username", "").compareTo("") == 0 &&
                prefs.getString("password", "").compareTo("") == 0) return "";
        return "<auth><user>" + prefs.getString("username", "") + "</user><pass>" + JIRest.md5(prefs.getString("password", "")) + "</pass></auth>";
    }

    // Will return an md5 for specified input
    public static String md5(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] messageDigest = md.digest(input.getBytes());
            BigInteger number = new BigInteger(1, messageDigest);
            String md5 = number.toString(16);
            while (md5.length() < 32) md5 = "0" + md5;
            return md5;
        } catch (NoSuchAlgorithmException e) {
            // MD5 not found, return NULL
            return null;
        }
    }
}
