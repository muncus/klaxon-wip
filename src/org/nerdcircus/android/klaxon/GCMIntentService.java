// Google Cloud Messaging Intent Service.
// Handles Intents from GCM, as per developer.android.com/guide/google/gcm/gs.html
package org.nerdcircus.android.klaxon;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.util.Log;
import com.google.android.gcm.GCMBaseIntentService;
import org.nerdcircus.android.klaxon.Pager.Pages;

public class GCMIntentService extends GCMBaseIntentService {

    public static String TAG = "GCMIntentService";
    public static String MY_TRANSPORT = "gcm";

    public GCMIntentService(){
      super();
    }

    public String[] getSenderIds(Context context){
      SharedPreferences p = PreferenceManager.getDefaultSharedPreferences(context);
      return new String[] { p.getString(GcmHelper.PREF_SENDER, "") };
    }

    public void onRegistered(Context context, String regId){
      //Called after the device has registered with GCM, so send the regid to our servers.
      GcmHelper gh = new GcmHelper(context);
      gh.register(regId);
    }

    public void onUnregistered(Context context, String regId){
      // Called after device unregisters with gcm, send regid to us, so we can remove it.
      GcmHelper gh = new GcmHelper(context);
      gh.unregister(regId);
    }

    public void onMessage(Context context, Intent intent){
      // Called when a message has been received. Process the received intent.

      if( intent.getAction().equals(Pager.REPLY_ACTION)){
        Log.d(TAG, "Replying!");
        //TODO: actually reply. in the short term, an email to the DisplayFrom might work.
        return;
      }

      //check to see if we want to intercept.
      SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
      if( ! prefs.getBoolean("is_oncall", true) ){
          Log.d(TAG, "not oncall. not bothering with incoming c2dm push.");
          return;
      }

      Bundle extras = intent.getExtras();
      if (extras == null)
        return;

      Alert incoming = new Alert();
      if(extras.containsKey("url"))
        //incoming.setReplyUri(extras.getString("url"));
        incoming.setFrom(extras.getString("url"));

      if(extras.containsKey("frm"))
        incoming.setDisplayFrom(extras.getString("frm"));
      if(extras.containsKey("from"))
        incoming.setDisplayFrom(extras.getString("from"));

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
    }

    public void onError(Context context, String errorId){
      Log.e(TAG, "Encountered a GCM Error: " + errorId);
    }

    // Optional. only override to display a message to the user.
    //public boolean onRecoverableError(Context context, String errorId){};


}
