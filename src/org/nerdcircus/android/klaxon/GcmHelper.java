// Google Cloud Messaging Helper.
// Centralized class for the logic and settings of GCM within Klaxon.
package org.nerdcircus.android.klaxon;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;

import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.client.params.ClientPNames;
import org.apache.http.impl.client.DefaultHttpClient;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.accounts.AccountManagerFuture;
import android.accounts.AuthenticatorException;
import android.accounts.OperationCanceledException;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.net.http.AndroidHttpClient;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.util.Log;
import android.widget.Toast;

import org.nerdcircus.android.klaxon.Alert;
import org.nerdcircus.android.klaxon.Pager;
import org.nerdcircus.android.klaxon.Pager.Pages;

public class GcmHelper {

    public static String TAG = "GcmHelper";
    public static String USER_AGENT = "Klaxon-GCM/1.0";

    public static String GCM_SENDER_ID = "100533447903";
    public static String MY_TRANSPORT = "gcm";

    // Appengine authentication
    private static final String AUTH_URL = "/_ah/login";
    private static final String AUTH_TOKEN_TYPE = "ah";

    // GCM gateway urls.
    private static final String REGISTER_URL = "/register";
    private static final String UNREGISTER_URL = "/unregister";
    private static final String REPLY_URL = "/reply";
    private static final String TEST_URL = "/test";

    // Preference Names.
    public static final String PREF_URL = "c2dm_register_url";
    public static final String PREF_ACCOUNT = "c2dm_register_account";

    // Desired Interface
    // getRegisterUrl: base_url + /register
    // etc.
    // Register
    // Unregister
    // SendTestMessage
    // Reply

    // Members.
    private Context mContext;
    private SharedPreferences mPrefs;
    private DefaultHttpClient mClient;
    
    public GcmHelper(Context context) {
      Log.d(TAG, "initializing new GcmHelper");
      mContext = context;
      mPrefs = PreferenceManager.getDefaultSharedPreferences(mContext);
      //mClient = AndroidHttpClient.newInstance(USER_AGENT);
      mClient = new DefaultHttpClient();
      //disable redirects.
      //mClient.getParams().setBooleanParameter(ClientPNames.HANDLE_REDIRECTS, false);
    }

    public String getRegisterUrl(){
      return (mPrefs.getString(PREF_URL, "") + REGISTER_URL);
    }
    public String getUnregisterUrl(){
      return (mPrefs.getString(PREF_URL, "") + UNREGISTER_URL);
    }
    public String getTestUrl(){
      return mPrefs.getString(PREF_URL, "") + TEST_URL;
    }
    public String getAuthUrl(){
      return mPrefs.getString(PREF_URL,"") + AUTH_URL;
    }

    public String getAccountName(){
      return mPrefs.getString(PREF_ACCOUNT,"");
    }
      
    // Device has registered with GCM, now send the registration info to our server, and save it.
    public void register(String deviceRegistrationID) {
        try {
            Log.d(TAG, "Registering!");
            HttpGet req = new HttpGet(getRegisterUrl() + "?token=" + deviceRegistrationID);
            HttpResponse res = this.makeHttpRequest(req);
            if (res.getStatusLine().getStatusCode() == 200) {
                SharedPreferences.Editor editor = mPrefs.edit();
                editor.putString("c2dm_token", deviceRegistrationID);
                Log.w(TAG, "Commiting token");
                editor.commit();
            } else {
                Log.w(TAG, "Registration error " + String.valueOf(res.getStatusLine()));
            }
        } catch (Exception e) {
            Log.w(TAG, "Registration exception!",e);
        }
    }

    public void unregister(String deviceRegistrationID) {
        try {
            Log.d(TAG, "UN-Registering!");
            // Drop the old token on the floor.
            SharedPreferences.Editor editor = mPrefs.edit();
            editor.remove("c2dm_token");
            editor.commit();
            // Try to notify the server to drop it, too.
            HttpGet req = new HttpGet(getUnregisterUrl() + "?token=" + deviceRegistrationID);
            HttpResponse res = this.makeHttpRequest(req);
        } catch (Exception e) {
            Log.w(TAG, "Oh well, de-registration failed. no big deal.", e);
        }
    }

    public boolean reply(String url, String reply_text){
      Uri.Builder ub = Uri.parse(url).buildUpon();
      ub.appendQueryParameter("reply", reply_text);
      Log.d(TAG, "Url: " + url);
      HttpGet req = new HttpGet(ub.build().toString());
      HttpResponse res = makeHttpRequest(req);
      if(res.getStatusLine().getStatusCode() == 200)
        return true;
      return false;
    }

    private HttpResponse makeHttpRequest(HttpGet req){
      return makeHttpRequest(req, true);
    }
    // Used to make a general HTTP request, and auth as needed.
    private HttpResponse makeHttpRequest(HttpGet req, boolean retry_auth){
        try {
          Log.d(TAG, "Attempting to fetch: " + req.getURI().toString());
          HttpResponse res = mClient.execute(req);

          // If we are not logged in, we will get a 302 redirect to the Login page.
          if(res.getStatusLine().getStatusCode() == 302){
            if(retry_auth){
              Log.d(TAG, "302'd - Authenticating.");
              //Authenticate, and continue to our destination.
              String authToken = this.getAuthToken();
              String continueURL = req.getURI().toString();
              URI uri = new URI(getAuthUrl() + "?continue=" +
                      URLEncoder.encode(continueURL, "UTF-8") +
                      "&auth=" + authToken);
              return this.makeHttpRequest(new HttpGet(uri), false);
            } else {
              Log.d(TAG, "302'd a second time. now what?");
              // Invalidate, and try again.
              Log.d(TAG, "*** INVALIDATING TOKEN? ***");
              AccountManager accountManager = AccountManager.get(mContext);
              Account account = new Account(this.getAccountName(), "com.google");
              accountManager.invalidateAuthToken(account.type, this.getAuthToken());
              return this.makeHttpRequest(req);
            }
          }
          // If our token is invalid, we get a 500.
          if(res.getStatusLine().getStatusCode() == 500){
            Log.d(TAG, "500'd - Invalidate token and try again.");
            Log.d(TAG, res.getStatusLine().toString());
              // Invalidate, and try again.
              Log.d(TAG, "*** INVALIDATING TOKEN ***");
              AccountManager accountManager = AccountManager.get(mContext);
              Account account = new Account(this.getAccountName(), "com.google");
              accountManager.invalidateAuthToken(account.type, this.getAuthToken());
              return this.makeHttpRequest(req);
          }
          Log.d(TAG, "Success!?");
          Log.d(TAG, res.getStatusLine().getReasonPhrase());
          return res;
        } catch (Exception e) {
          Log.e(TAG, "http request exception!", e);
          return null;
        }
    };

    private String getAuthToken(){
      //fetch an auth token for the account we registered with.
      String accountName = mPrefs.getString(PREF_ACCOUNT, "");
      return GcmHelper.getAuthToken(mContext, accountName);
    }

    private static String getAuthToken(Context context, String accountName) {
        //TODO: validate that the account still appears in GetAccountsByType().
        Account account = new Account(accountName, "com.google");

        String authToken = null;
        AccountManager accountManager = AccountManager.get(context);
        try {
            authToken = accountManager.blockingGetAuthToken(account, AUTH_TOKEN_TYPE, true);
        } catch (OperationCanceledException e) {
            Log.w(TAG, e.getMessage());
        } catch (AuthenticatorException e) {
            Log.w(TAG, e.getMessage());
        } catch (IOException e) {
            Log.w(TAG, e.getMessage());
        }
        return authToken;
    }

};
