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

import android.app.Activity;
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
import android.net.http.AndroidHttpClient;
import android.os.AsyncTask;
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
    public static final String PREF_TOKEN = "c2dm_token";
    public static final String PREF_SENDER = "c2dm_sender";

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

    public void setFollowRedirects(boolean follow){
      mClient.getParams().setBooleanParameter(ClientPNames.HANDLE_REDIRECTS, follow);
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
          // dont follow the initial 302.
          //setFollowRedirects(false);
          HttpResponse res = mClient.execute(req);

          // If we are not logged in, we will get a 302 redirect to the Login page.
          if(res.getStatusLine().getStatusCode() == 401 || res.getStatusLine().getStatusCode() == 302){
            Log.d(TAG, "403'd - Authenticating.");
            res.getEntity().consumeContent(); //closes the connection.
            if(retry_auth){
              //Log.d(TAG, "302'd - Authenticating.");
              //Authenticate, and continue to our destination.
              String authToken = this.getAuthToken();
              String continueURL = req.getURI().toString();
              URI uri = new URI(getAuthUrl() + "?continue=" +
                      URLEncoder.encode(continueURL, "UTF-8") +
                      "&auth=" + authToken);
              //setFollowRedirects(true);
              return this.makeHttpRequest(new HttpGet(uri), false);
            } else {
              Log.d(TAG, "302'd a second time. now what?");
              // Invalidate, and try again.
              Log.d(TAG, "*** INVALIDATING TOKEN? ***");
              AccountManager accountManager = AccountManager.get(mContext);
              Account account = new Account(this.getAccountName(), "com.google");
              accountManager.invalidateAuthToken(account.type, this.getAuthToken());
              return this.makeHttpRequest(req, false);
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
          // check to see if we were redirected to the login screen.
          Log.d(TAG, "Success!?");
          Log.d(TAG, res.getStatusLine().getReasonPhrase());
          Log.d(TAG, res.getStatusLine().getStatusCode() + " ");
          Log.d(TAG, req.getURI().toString());
          return res;
        } catch (Exception e) {
          Log.e(TAG, "http request exception!", e);
          return null;
        }
    };

    private String getAuthToken(){
      //fetch an auth token for the account we registered with.
      String accountName = mPrefs.getString(PREF_ACCOUNT, "");
      return this.getAuthToken(mContext, accountName);
      //new AsyncAuthTask(AccountManager.get(mContext)).execute(accountName);
      //return null;

    }

    public static void maybePromptForPassword(Activity ac){
      // Check for an auth token. if none exists, use the provided activity context to request one.
      SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(ac);
      String acctname = prefs.getString(PREF_ACCOUNT, "");
      String token  = prefs.getString(PREF_TOKEN, "");
      if(acctname.length() == 0 || token.length() == 0){
        //user not registered for gcm. do nothing.
        return;
      }
      Account acct = new Account(acctname, "com.google");
      AccountManager accountManager = AccountManager.get(ac);
      //authToken = accountManager.blockingGetAuthToken(account, AUTH_TOKEN_TYPE, true);
      //AccountManagerFuture amf = accountManager.getAuthToken(acct, AUTH_TOKEN_TYPE, null, ac, new OnAuthToken(ac), null);
      AccountManagerFuture amf = accountManager.getAuthToken(acct, AUTH_TOKEN_TYPE, null, ac, null, null);
      //amf.getResult();
      return;
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


    private class AsyncReplyTask extends AsyncTask<String, Void, Boolean>{
      protected Boolean doInBackground(String... args){
        String url = args[0];
        String reply = args[1];

        Uri.Builder ub = Uri.parse(url).buildUpon();
        ub.appendQueryParameter("reply", reply);
        Log.d(TAG, "Url: " + url);
        HttpGet req = new HttpGet(ub.build().toString());
        HttpResponse res = makeHttpRequest(req);
        if(res.getStatusLine().getStatusCode() == 200)
          return true;
        return false;
      }
    }

    private class AsyncAuthTask extends AsyncTask<String, Void, Void>{
      AccountManager mAccountManager;
      public AsyncAuthTask(AccountManager am){
        mAccountManager = am;
      }
      protected Void doInBackground(String... args){
        try {
          String authToken = null;
          Account account = new Account(args[0], "com.google");
          //AccountManager accountManager = AccountManager.get(context);
          authToken = mAccountManager.blockingGetAuthToken(account, AUTH_TOKEN_TYPE, true);
        } catch (OperationCanceledException e) {
            Log.w(TAG, e.getMessage());
        } catch (AuthenticatorException e) {
            Log.w(TAG, e.getMessage());
        } catch (IOException e) {
            Log.w(TAG, e.getMessage());
        }
        return null;
      };
    };

    private class OnAuthToken implements AccountManagerCallback<Bundle> {
      Context mContext;
      public OnAuthToken(Context context){
        mContext = context;
      }
      @Override
      public void run(AccountManagerFuture<Bundle> amf) {
        try {
          Bundle result = amf.getResult();
          if(result.get(AccountManager.KEY_INTENT) != null){
            Log.d(TAG, "Key Intent received.");
            Intent launch = (Intent) result.get(AccountManager.KEY_INTENT);
            mContext.startActivity(launch);
          }
          if(result.get(AccountManager.KEY_AUTHTOKEN) != null){
            Log.d(TAG, "Got authtoken!" + result.get(AccountManager.KEY_AUTHTOKEN).toString());
            return;
          }
          else {
            Log.d(TAG, "no idea dog.");
          }
        } catch (OperationCanceledException e) {
            Log.w(TAG, e.getMessage());
        } catch (AuthenticatorException e) {
            Log.w(TAG, e.getMessage());
        } catch (IOException e) {
            Log.w(TAG, e.getMessage());
        }
      }
    }


};
