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

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.util.Log;

class JIRest {
    public static final int OK = 0;
    public static final int TIMEOUT = 1;
    public static final int ERROR = 2;

    public static final String JOINDIN_URL = "http://joind.in/api/";

    private String error = "";
    private String result = "";

    private Context context;

    public JIRest (Context context) {
        this.context = context;
    }

    // Return the last communication result
    public String getResult () {
        return this.result;
    }

    // Return last communication error
    public String getError () {
        return this.error;
    }

    // Post XML.. funny.. we post XML and we receive JSON.
    public int postXML (String urlPostfix, String xml) {

        try {
            // Create http client with timeouts so we don't have to wait
            // indefinitely when the internet is kaput
            HttpClient httpclient = new DefaultHttpClient();
            HttpParams params = httpclient.getParams();
            HttpConnectionParams.setConnectionTimeout(params, 30000);
            HttpConnectionParams.setSoTimeout(params, 15000);

            // We POST our data.
            HttpPost httppost = new HttpPost(JOINDIN_URL+urlPostfix);

            // Attention: MUST be text/xml. Took a while to figure this one out!
            StringEntity xmlentity = null;
            try {
                xmlentity = new StringEntity(xml);
                xmlentity.setContentType("text/xml");
            } catch (UnsupportedEncodingException e) { 
                // Ignore exception
            }

//            Log.d("joindin", JOINDIN_URL+urlPostfix + " --> " + xml);
            
            httppost.setEntity(xmlentity);
            httppost.addHeader("Content-type", "text/xml");

            try {
                // Post stuff
                HttpResponse response = httpclient.execute (httppost);

                // Get response
                HttpEntity entity = response.getEntity();
                if (entity != null) {
                    // If we receive some data, place it in our result string
                    InputStream instream = entity.getContent();
                    this.result = Main.convertStreamToString(instream);
                    instream.close();
//                    Log.d("joindin", this.result);
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

    // This will return either a empty string, or a xml auth string <auth><user><pass></auth> that can be used in messages.
    // We need an activity because we need to fetch the preference manager which is only fetchable from a context.
    // ( @TODO: find another way to fetch basecontext)
    public static String getAuthXML (Context context) {
        // Make authentication string from the preferences
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
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
            // MD5 not found, return NULL
            return null;
        }
    }
}
