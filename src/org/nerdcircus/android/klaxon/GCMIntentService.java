// Google Cloud Messaging Intent Service.
// Handles Intents from GCM, as per developer.android.com/guide/google/gcm/gs.html
package org.nerdcircus.android.klaxon;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;

import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpRequestBase;
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
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.util.Log;
import android.widget.Toast;

import com.google.android.gcm.GCMBaseIntentService;

import org.nerdcircus.android.klaxon.Alert;
import org.nerdcircus.android.klaxon.Pager.Pages;

public class GCMIntentService extends GCMBaseIntentService {

    public static String TAG = "GCMIntentService";
    public static String GCM_SENDER_ID = "100533447903";
    public static String MY_TRANSPORT = "gcm";

    // Appengine authentication
    private static final String AUTH_URL = "/_ah/login";
    private static final String AUTH_TOKEN_TYPE = "ah";

    // GCM gateway urls.
    private static final String REGISTER_URL = "/register";
    private static final String UNREGISTER_URL = "/unregister";
    private static final String REPLY_URL = "/reply";


    public GCMIntentService(){
      super(GCM_SENDER_ID); //my project id.
    }

    // This inherits from IntentService, which allows us to handle the reply action here.
    // XXX: Can't do this, because this method is final in GCMBaseIntentService
    //public void onHandleIntent(Intent intent){
    //  if(Pager.REPLY_ACTION == Intent.getAction()){
    //    Log.d(TAG, "** SHOULD REPLY HERE***");
    //  }
    //  else {
    //    super(intent);
    //  }
    //};

    public void onRegistered(Context context, String regId){
      //Called after the device has registered with GCM, so send the regid to our servers.
      registerWithServer(context, regId);
    };
    public void onUnregistered(Context context, String regId){
      // Called after device unregisters with gcm, send regid to us, so we can remove it.
      unregisterWithServer(context, regId);
    };
    public void onMessage(Context context, Intent intent){
      // Called when a message has been received. Process the received intent.

      Log.d(TAG, "CAUGHT AN INTENT! WHEEEE!");

      //check to see if we want to intercept.
      SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
      if( ! prefs.getBoolean("is_oncall", true) ){
          Log.d(TAG, "not oncall. not bothering with incoming c2dm push.");
          return;
      }

      Bundle extras = intent.getExtras();
      if (extras == null)
        return;

//      String from = extras.getString("from");
//      if (from == null)
//        from = "Unknown Sender";
//
//      String subject = extras.getString("subject");
//      if (subject == null)
//        subject = "subject not specified";
//
//      String body = extras.getString("body");
//      if (body == null)
//        body = "body not specifed";

//      Log.d(TAG, "From: " + from);
      //Alert incoming = new Alert(from, subject, body);
      Alert incoming = new Alert();
      if(extras.containsKey("from"))
        incoming.setFrom(extras.getString("from"));
      if(extras.containsKey("subject"))
        incoming.setSubject(extras.getString("subject"));
      if(extras.containsKey("body"))
        incoming.setBody(extras.getString("body"));
      incoming.setTransport(MY_TRANSPORT);

      Uri newpage = context.getContentResolver().insert(Pages.CONTENT_URI, incoming.asContentValues());
      Log.d(TAG, "new message inserted.");
      Intent annoy = new Intent(Pager.PAGE_RECEIVED);
      annoy.setData(newpage);
      context.sendBroadcast(annoy);
      Log.d(TAG, "sent intent " + annoy.toString() );
    };

    public void onError(Context context, String errorId){
      Log.e(TAG, "Encountered a GCM Error: " + errorId);
    };

    // Optional. only override to display a message to the user.
    //public boolean onRecoverableError(Context context, String errorId){};
  
    public static void registerWithServer(final Context context, final String deviceRegistrationID) {
        try {
            HttpResponse res = makeRequest(context, deviceRegistrationID, REGISTER_URL);
            if (res == null)
            return;
            if (res.getStatusLine().getStatusCode() == 200) {
                SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(context);
                SharedPreferences.Editor editor = settings.edit();
                editor.putString("c2dm_token", deviceRegistrationID);
                Log.w(TAG, "Commiting token");
                editor.commit();
            } else {
                Log.w(TAG, "Registration error " + String.valueOf(res.getStatusLine()));
            }
        } catch (Exception e) {
            Log.w(TAG, "Registration exception " + e.getMessage());
        }
    }

    public static void unregisterWithServer(final Context context, final String deviceRegistrationID) {
        CharSequence text;
        try {
            HttpResponse res = makeRequest(context, deviceRegistrationID, UNREGISTER_URL);
            if (res.getStatusLine().getStatusCode() == 200) {
                SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(context);
                SharedPreferences.Editor editor = settings.edit();
                editor.remove("c2dm_token");
                editor.commit();
                text = "Successfully unregistered.";
            } else {
                Log.w(TAG, "Unregistration error " +
                        String.valueOf(res.getStatusLine()));
                text = "Unregistration error " +
                        String.valueOf(res.getStatusLine());
            }
        } catch (Exception e) {
            Log.w(TAG, "Unregistration error " + e.getMessage());
            text = "Unregistration error " + e.getMessage();
        }
        int duration = Toast.LENGTH_LONG;
        Toast toast = Toast.makeText(context, text, duration);
        toast.show();
    }

    /*
    public static HttpResponse sendReply(Context context, String reply, String messageId){
        //TODO: write this.
        Log.f(TAG, "REPLY NOT IMPLEMENTED.");
        return;
        SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(context);
        String base_url = settings.getString("c2dm_register_url", null);
        HttpGet getreq = new HttpGet(base_url + REPLY_URL + 
                                     "?msg=" + messageId +
                                     "&reply=" + URLEncoder.encode(reply));
        return makeHttpRequest(context, getreq);
    };
    */


    // Used to make a general HTTP request, and auth as needed.
    private static HttpResponse makeHttpRequest(Context context, HttpGet req){
        try {
          Log.d(TAG, "Attempting to fetch: " + req.getURI().toString());
          HttpResponse res = makeAuthenticatedRequest(context, req);
          if(res.getStatusLine().getStatusCode() == 500){
          Log.d(TAG, res.getStatusLine().toString());
              return makeAuthenticatedRequest(context, req, true);
          }
          return res;
        } catch (Exception e) {
          Log.e(TAG, "http request exception!", e);
          return null;
        }
    };

    // convenience method for below.
    private static HttpResponse makeAuthenticatedRequest(Context context, HttpGet req) throws Exception{
        return makeAuthenticatedRequest(context, req, false);
    };

    // Handles the details of getting the auth token, and making the http request.
    private static HttpResponse makeAuthenticatedRequest(Context context, HttpGet req, boolean invalidateToken) throws Exception {
        // Make the request, and auth as needed.
        SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(context);
        String accountName = settings.getString("c2dm_register_account", null);
        if (accountName == null) throw new Exception("No account");

        // Get auth token for account
        String authToken = getAuthToken(context, accountName);
        if (invalidateToken) {
            Log.w(TAG, "****TOKEN INVALIDATION REQUESTED. INVESTIGATE WHY****");
            // Invalidate the cached token
            AccountManager accountManager = AccountManager.get(context);
            Account account = new Account(accountName, "com.google");
            accountManager.invalidateAuthToken(account.type, authToken);
            authToken = getAuthToken(context, accountName);
        }
        DefaultHttpClient client = new DefaultHttpClient();
        HttpResponse res = client.execute(req);
        return res;
    };

    private static HttpResponse makeRequest(Context context, String deviceRegistrationID,
                                            String url) throws Exception {
        HttpResponse res = makeRequestNoRetry(context, deviceRegistrationID, url,
                false);
        if (res.getStatusLine().getStatusCode() == 500) {
            res = makeRequestNoRetry(context, deviceRegistrationID, url,
                    true);
        }
        return res;
    }
    private static HttpResponse makeRequestNoRetry(Context context, String deviceRegistrationID,
            String url, boolean requestNewToken) throws Exception {
        // Register device with server 
        // TODO: remove sender. no longer needed.
        SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(context);
        String accountName = settings.getString("c2dm_register_account", null);
        if (accountName == null) throw new Exception("No account");

        // Get auth token for account
        String authToken = getAuthToken(context, accountName);

        String sender = settings.getString("c2dm_sender", null);
        String base_url = settings.getString("c2dm_register_url", null);

        if (sender != null && base_url != null) {
            DefaultHttpClient client = new DefaultHttpClient();

            String continueURL = base_url + url + "?token=" +
                    deviceRegistrationID + "&sender=" + sender;

            URI uri = new URI(base_url + AUTH_URL + "?continue=" +
                    URLEncoder.encode(continueURL, "UTF-8") +
                    "&auth=" + authToken);

            HttpGet method = new HttpGet(uri);
            return makeHttpRequest(context, method);
        }
        return null;
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
