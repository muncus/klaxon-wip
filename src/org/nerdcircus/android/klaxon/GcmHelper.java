// Google Cloud Messaging Helper.
// Centralized class for the logic and settings of GCM within Klaxon.
package org.nerdcircus.android.klaxon;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.net.URLEncoder;
import java.net.CookieManager;
import java.net.CookieHandler;

import android.app.Activity;
import android.app.Dialog;
import android.accounts.Account;
import android.accounts.AccountManager;
import android.accounts.AccountManagerCallback;
import android.accounts.AccountManagerFuture;
import android.accounts.AuthenticatorException;
import android.accounts.OperationCanceledException;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.util.Log;

import com.google.android.gms.auth.GoogleAuthUtil;
import com.google.android.gms.auth.GoogleAuthException;
import com.google.android.gms.auth.GooglePlayServicesAvailabilityException;
import com.google.android.gms.auth.UserRecoverableAuthException;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GooglePlayServicesUtil;
import com.google.android.gms.gcm.GoogleCloudMessaging;

public class GcmHelper {

    public static String TAG = "GcmHelper";
    public static String USER_AGENT = "Klaxon-GCM/1.0";
    public static String MY_TRANSPORT = "gcm";

    // Appengine authentication
    private static final String AUTH_URL = "/_ah/login";
    private static final String AUTH_TOKEN_TYPE = "ah";

    // GCM gateway urls.
    private static final String REGISTER_URL = "/register";
    private static final String UNREGISTER_URL = "/unregister";
    private static final String TEST_URL = "/test";

    // Preference Names.
    // TODO: pull these from Resources instead.
    public static final String PREF_URL = "c2dm_register_url";
    public static final String PREF_ACCOUNT = "c2dm_register_account";
    public static final String PREF_TOKEN = "c2dm_token";
    public static final String PREF_SENDER = "c2dm_sender";

    // Request codes.
    public static final int RC_NOPLAY = 1;
    public static final int RC_AUTH_NEEDED = 2;

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
    private Boolean mHaveAnnoyedAboutPlayServices = false;
    
    public GcmHelper(Context context) {
      Log.d(TAG, "initializing new GcmHelper");
      mContext = context;
      mPrefs = PreferenceManager.getDefaultSharedPreferences(mContext);
      //use cookies.
      CookieHandler.setDefault(new CookieManager());
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
    
    /** Request a URL, authenticating as needed. 
     * this method uses HttpURLConnection. */
    private HttpURLConnection HttpRequest(URL requrl){
      HttpURLConnection conn = null;
      try {
        conn = (HttpURLConnection)requrl.openConnection();
        conn.setRequestProperty("User-Agent", "Klaxon");
        Log.d(TAG, "url: " + conn.getURL());
        Log.d(TAG, "Response: " + conn.getResponseCode());
        if(conn.getURL().getHost() != mPrefs.getString(PREF_URL, "")){
            Log.d(TAG, "looks like we were redirected. Auth.");
            conn.disconnect();
            // Get the auth token, and use it.
            String authToken = this.getAuthToken();
            URL authurl = new URL(getAuthUrl() + "?continue=" +
                    URLEncoder.encode(requrl.toString(), "UTF-8") +
                    "&auth=" + authToken);
            conn = (HttpURLConnection)authurl.openConnection();
            conn.getInputStream(); // Dont care what we get back. metadata is enough.
            Log.d(TAG, "url: " + conn.getURL());
            Log.d(TAG, "Response: " + conn.getResponseCode());
        }
        return conn;
      } catch (Exception e) {
          Log.w(TAG, "HTTP Request exception!", e);
      }
      return conn;
    }
      
    // Device has registered with GCM, now send the registration info to our server, and save it.
    public void register(String deviceRegistrationID) {
        try {
            Log.d(TAG, "Registering!");
            URL requrl = new URL(getRegisterUrl() + "?token=" + deviceRegistrationID);

            //make httpurlconnection request
            HttpURLConnection conn = HttpRequest(requrl);
            if(conn.getResponseCode() == 200){
                SharedPreferences.Editor editor = mPrefs.edit();
                editor.putString("c2dm_token", deviceRegistrationID);
                Log.w(TAG, "Commiting token");
                editor.commit();
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
            URL req = new URL(getUnregisterUrl() + "?token=" + deviceRegistrationID);
            HttpRequest(req);
        } catch (Exception e) {
            Log.w(TAG, "Oh well, de-registration failed. no big deal.", e);
        }
    }

    public boolean reply(String url, String reply_text){
      Uri.Builder ub = Uri.parse(url).buildUpon();
      ub.appendQueryParameter("reply", reply_text);
      Log.d(TAG, "Url: " + url);
      try {
        URL req = new URL(ub.build().toString());
        HttpURLConnection res = HttpRequest(req);
        if(res.getResponseCode() == 200)
          return true;
      } catch (Exception e) {
        Log.e(TAG, "something is wrong with reply: " + e.getMessage(), e);
      }
      return false;
    }

    private String getAuthToken(){
      return getAuthTokenBlocking();
    }

    private String getAuthTokenBlocking() {
      SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(mContext);
      String acctname = prefs.getString(PREF_ACCOUNT, "");
      String token = null;
      try {
        token = GoogleAuthUtil.getToken(mContext, acctname, AUTH_TOKEN_TYPE);
      } catch (GooglePlayServicesAvailabilityException playEx) {
        Dialog alert = GooglePlayServicesUtil.getErrorDialog(
            playEx.getConnectionStatusCode(),
            (Activity)mContext,
            GcmHelper.RC_NOPLAY);
        alert.show();
        return null;
      } catch (UserRecoverableAuthException userAuthEx) {
            // Start the user recoverable action using the intent returned by
            // getIntent()
            ((Activity)mContext).startActivityForResult(
                    userAuthEx.getIntent(),
                    RC_AUTH_NEEDED);
            return null;
      } catch (IOException transientEx) {
            // network or server error, the call is expected to succeed if you try again later.
            // Don't attempt to call again immediately - the request is likely to
            // fail, you'll hit quotas or back-off.
            Log.e(TAG, "something is borked.", transientEx);
            return null;
      } catch (GoogleAuthException authEx) {
            // Failure. The call is not expected to ever succeed so it should not be
            // retried.
            Log.e(TAG, "unrecoverable.", authEx);
            return null;
      }
      if(token != null){
        Log.d(TAG, "we got an auth token!");
        return token;
      }
      return null;
    }
    /* Register both legs of GCM registration - GCM, and our own server */
    void registerBothLegsBlocking(String sender){
        GoogleCloudMessaging gcm = GoogleCloudMessaging.getInstance(mContext);
        try{
            String registrationid = gcm.register(sender);
            this.register(registrationid);
        } catch(IOException e){
            Log.e(TAG, "registration failed: " + e.getMessage(), e);
        }
    }

    /* UnRegister both legs of GCM registration - GCM, and our own server */
    void unregisterBothLegsBlocking(String registrationId){
        GoogleCloudMessaging gcm = GoogleCloudMessaging.getInstance(mContext);
        try{
            gcm.unregister();
            this.unregister(registrationId);
        } catch(IOException e){
            Log.e(TAG, "registration failed: " + e.getMessage(), e);
        }
    }

    void registerWithGcmAsync(String... senders){
      //TODO: figure out why <Void,Void,Void> throws ClassCastException
      AsyncTask task = new AsyncTask<String,Object,Boolean>() {
        @Override
        protected Boolean doInBackground(String... params){
          GoogleCloudMessaging gcm = GoogleCloudMessaging.getInstance(mContext);
          try {
            gcm.register(params);
            return true;
          } catch(IOException e){
            Log.e(TAG, "registration failed: " + e.getMessage(), e);
            return false;
          }
        }
      };
      task.execute((Object[])senders);
    }

    void unregisterWithGcmAsync(String... senders){
      //TODO: figure out why <Void,Void,Void> throws ClassCastException
      AsyncTask task = new AsyncTask<Object,Object,Void>() {
        @Override
        protected Void doInBackground(Object... params){
          GoogleCloudMessaging gcm = GoogleCloudMessaging.getInstance(mContext);
          try {
            gcm.unregister();
          } catch(IOException e){
            Log.e(TAG, "registration failed: " + e.getMessage(), e);
          }
          return null;
        }
      };
      task.execute((Object[])null);
    }

    void maybePromptForLoginPermission(){
      // Check for an auth token. if none exists, use the provided activity context to request one.
      String acctname = mPrefs.getString(PREF_ACCOUNT, "");
      String token  = mPrefs.getString(PREF_TOKEN, "");
      if(acctname.length() == 0 || token.length() == 0){
        //user not registered for gcm. do nothing.
        return;
      }
      //TODO: figure out why <Void,Void,Void> throws ClassCastException
      AsyncTask task = new AsyncTask<Object,Object,String>() {
        @Override
        protected String doInBackground(Object... params){
          return getAuthTokenBlocking();
        }
      };
      task.execute();
    }

    private String getAuthToken(Context context, String accountName) {
        //TODO: validate that the account still appears in GetAccountsByType().
        Account account = new Account(accountName, "com.google");
        String authToken = null;
        try {
          AccountManager accountManager = AccountManager.get(context);
          authToken = accountManager.blockingGetAuthToken(account, AUTH_TOKEN_TYPE, true);
          //AccountManagerFuture amf = accountManager.getAuthToken(account, AUTH_TOKEN_TYPE, null, (Activity) context, new OnAuthToken(mContext), null);
          //Bundle result = (Bundle) amf.getResult();
          //Log.d(TAG, "auth: " + result.get(AccountManager.KEY_AUTHTOKEN).toString());
          //authToken = result.get(AccountManager.KEY_AUTHTOKEN).toString();
        } catch (OperationCanceledException e) {
            Log.w(TAG, e.getMessage());
        } catch (AuthenticatorException e) {
            Log.w(TAG, e.getMessage());
        } catch (IOException e) {
            Log.w(TAG, e.getMessage());
        }
        return authToken;
            /*
            AccountManagerFuture amf = accountManager.getAuthToken(
                account, 
                AUTH_TOKEN_TYPE,
                true,
                new OnAuthToken(mContext),
                null);
            Log.d(TAG, "Got amf.");
        return authToken;
        */
    }

    public static void invalidateAuthToken(Context context){
      // For debugging only.
      AccountManager accountManager = AccountManager.get(context);
      //String accountname = PreferenceManager.getDefaultSharedPreferences(context).getString(PREF_ACCOUNT,"");
      //Account account = new Account(accountname, "com.google");
      accountManager.invalidateAuthToken("com.google", null);
    }

    /** Check for the availability of play services. */
    public boolean checkForPlayServices(boolean haveShownDialog){
      int status = GooglePlayServicesUtil.isGooglePlayServicesAvailable(mContext);
      if(status==ConnectionResult.SUCCESS){
        Log.d(TAG, "play services succeeded.");
        return true;
      } else if(!haveShownDialog){
        Log.d(TAG, "trying to show dialog..");
        GooglePlayServicesUtil.getErrorDialog(status, (Activity)mContext, RC_NOPLAY).show();
      }
      Log.d(TAG, "play services FAILED..");
      return false;
    }

}
