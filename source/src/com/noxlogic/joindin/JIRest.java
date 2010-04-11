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

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.params.HttpConnectionParams;
import org.apache.http.params.HttpParams;

import android.app.Activity;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;

class JIRest {
    public static final int OK = 0;
    public static final int TIMEOUT = 1;
    public static final int ERROR = 2;

    private String error = "";
    private String result = "";

    // Return the last communciation result
    public String getResult () {
        return this.result;
    }

    // Return last communication error
    public String getError () {
        return this.error;
    }

    // Post XML.. funny.. we post XML and we receive JSON.
    public int postXML (String url, String xml) {
        try {
            // Create http client with timeouts so we dont have to wait
            // indefiniatly when internet is kaput
            HttpClient httpclient = new DefaultHttpClient();
            HttpParams params = httpclient.getParams();
            HttpConnectionParams.setConnectionTimeout(params, 30000);
            HttpConnectionParams.setSoTimeout(params, 15000);

            // We POST our data.
            HttpPost httppost = new HttpPost(url);

            // Attention: MUST be text/xml. Took a while to figure this one out!
            StringEntity xmlentity = null;
            try {
                xmlentity = new StringEntity(xml);
                xmlentity.setContentType("text/xml");
            } catch (UnsupportedEncodingException e) { }

            httppost.setEntity(xmlentity);
            httppost.addHeader("Content-type", "text/xml");

            try {
                // Post stuff
                HttpResponse response = httpclient.execute (httppost);

                // Get repsonse
                HttpEntity entity = response.getEntity();
                if (entity != null) {
                    // If we receive some data, place it in our result string
                    InputStream instream = entity.getContent();
                    this.result = main.convertStreamToString(instream);
                    instream.close();

                    return OK;
                }
            } catch (ClientProtocolException e) {
                this.error = "There was a protocol based error: "+e.getMessage();
                return ERROR;
            } catch (SocketTimeoutException e) {
                this.error = "Timeout while connecting to joind.in website.";
                return TIMEOUT;
            } catch (IOException e) {
                this.error = "There was an IO stream related error: "+e.getMessage();
                return ERROR;
            }
        } catch (Exception e) {
            this.error  = "Unknown error: "+e.getMessage();
            return ERROR;
        }
        return OK;
    }

    // This will return either a empty string, or a xml auth string <auth><user><pass></auth> that can be used in messages.
    // We need an activity because we need to fetch the preference manager which is only gettable from a context.
    // ( @TODO: find another way to fetch basecontext)
    public static String getAuthXML (Activity activity) {
        // Make authentication string from the preferences
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(activity.getBaseContext());
        if (prefs.getString("username", "").compareTo("") == 0 &&
            prefs.getString("password", "").compareTo("") == 0) return "";
        return "<auth><user>"+prefs.getString("username", "")+"</user><pass>"+JIRest.md5(prefs.getString("password", ""))+"</pass></auth>";
    }

    // Will return an md5 for specified input
    public static String md5(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] messageDigest = md.digest(input.getBytes());
            BigInteger number = new BigInteger(1,messageDigest);
            String md5 = number.toString(16);
            while (md5.length() < 32) md5 = "0" + md5;
            return md5;
        } catch(NoSuchAlgorithmException e) {
            return null;
        }
    }
}
