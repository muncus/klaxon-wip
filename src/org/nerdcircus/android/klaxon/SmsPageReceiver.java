/* 
 * Copyright 2008 Google Inc.
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.nerdcircus.android.klaxon;

import android.app.PendingIntent;
import android.content.*;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.telephony.SmsManager;
import android.telephony.SmsMessage;
import android.util.Log;
import org.nerdcircus.android.klaxon.Pager.Pages;
import org.nerdcircus.android.klaxon.pageparser.Go2Mobile;
import org.nerdcircus.android.klaxon.pageparser.LabeledFields;
import org.nerdcircus.android.klaxon.pageparser.Standard;

public class SmsPageReceiver extends BroadcastReceiver
{
    public static String TAG = "SmsPageReceiver";
    private static String MY_TRANSPORT = "sms";

    private Context mContext;

    public String getTransport(){
      return MY_TRANSPORT;
    }
    
    // Duplicate the flow currently present in PageReceiver, since we cannot use an IntentService here.
    public void onReceive(Context context, Intent intent){
      //save this, so we can present the same interface as PageReceiver.
      mContext = context;
      if(intent.getAction().equals(Pager.REPLY_ACTION)){
        onReplyIntent(intent);
      }
      else {
        onAlertReceived(intent);
      }
    }

    public void onAlertReceived(Intent intent){
      SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(mContext);
      Log.d(TAG, "fetching messages...");
      SmsMessage[] msgs = {};
      try{
          //assemble messages from raw pdus.
          if(! intent.getExtras().isEmpty()){
              Object[] pduObjs = (Object[]) intent.getExtras().get("pdus");
              msgs = new SmsMessage[pduObjs.length];
              for (int i=0;i<pduObjs.length;i++){
                  msgs[i] = SmsMessage.createFromPdu((byte[])pduObjs[i]);
              }
          }
      }
      //XXX: this probably shouldnt throw an NPE.
      catch(NullPointerException e){
          Log.e(TAG, "No data associated with new sms intent!");
      }

      Alert incoming = null;
      String parser = prefs.getString("pageparser", "Standard");
      if(parser.equals("Standard")){
          Log.d(TAG, "using Standard pageparser");
          incoming = (new Standard()).parse(msgs);
      }
      else if (parser.equals("Go2Mobile")){
          Log.d(TAG, "using go2mobile pageparser");
          incoming = (new Go2Mobile()).parse(msgs);
      }
      else if (parser.equals("Labeled Fields")){
          Log.d(TAG, "using labeled pageparser");
          incoming = (new LabeledFields()).parse(msgs);
      }
      else {
          Log.e(TAG, "unknown page parser:" + parser);
      }

      // note that this page was received via sms.
      incoming.setTransport(MY_TRANSPORT);

      //Log some bits.
      Log.d(TAG, "from: " + incoming.getFrom());
      Log.d(TAG, "display from: " + incoming.getDisplayFrom());
      Log.d(TAG, "subject: " + incoming.getSubject());
      Log.d(TAG, "body: " + incoming.getBody());

      if( ! isPage(incoming.asContentValues(), mContext) ) {
          Log.d(TAG, "message doesnt appear to be a page. skipping");
          return;
      }

      Uri newpage = mContext.getContentResolver().insert(Pages.CONTENT_URI, incoming.asContentValues());
      Log.d(TAG, "new message inserted.");
      Intent annoy = new Intent(Pager.PAGE_RECEIVED);
      annoy.setData(newpage);
      mContext.sendBroadcast(annoy);
      Log.d(TAG, "sent intent " + annoy.toString() );
      //NOTE: as of 1.6, this broadcast can be aborted.
      if (prefs.getBoolean("consume_sms_message", false)){
          //FIXME: this breaks if we no longer use a BroadcastReceiver.
          abortBroadcast();
          Log.d(TAG, "sms broadcast aborted.");
      }
    }

    public void onReplyIntent(Intent intent){

      //replying to a received page.
      Uri data = intent.getData();
      Bundle extras = intent.getExtras();
      String response = extras.getString("response");
      Integer new_ack_status = extras.getInt("new_ack_status");
      if( canReply(data)){
          replyTo(intent);
      }
      else {
          Log.d(TAG, "cannot reply to this message.");
      }
    }

    /** Determine if we care about a particular SMS message.
     */
    boolean isPage(ContentValues cv, Context context){
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        String trigger_string = prefs.getString("sender_match", "").toLowerCase().trim();
        Log.d(TAG, "Trigger: " + trigger_string);
        Log.d(TAG, "sender: " + cv.getAsString(Pages.SENDER).toLowerCase());
        Log.d(TAG, "from_addr: " + cv.getAsString(Pages.FROM_ADDR).toLowerCase());
        return matchesPageCriteria(cv, trigger_string, prefs.getBoolean("also_match_body", false));
    }

    /* For testing. */
    public static boolean matchesPageCriteria(ContentValues cv, String trigger_string, boolean match_body){
        //split on commas, with optional spaces.
        for( String trigger : trigger_string.split("[ ]*,[ ]*")){
          trigger = trigger.trim(); // Remove leading/trailing whitespace.
          if( cv.getAsString(Pages.SENDER).toLowerCase().contains(trigger) )
              return true;
          if( cv.getAsString(Pages.FROM_ADDR).toLowerCase().contains(trigger) )
              return true;
          if ( match_body ){
              if( cv.getAsString(Pages.SUBJECT).toLowerCase().contains(trigger) )
                  return true;
              if( cv.getAsString(Pages.BODY).toLowerCase().contains(trigger) )
                  return true;
          }
        }
        return false;
    }

    /** check if we can reply to this page.
     */
    boolean canReply(Uri data){
        Log.d(TAG, "attempting to reply to: " + data);
        Cursor cursor = mContext.getContentResolver().query(data,
                new String[] {Pager.Pages.TRANSPORT, Pager.Pages._ID},
                null,
                null,
                null);
        cursor.moveToFirst();

        String transport = cursor.getString(cursor.getColumnIndex(Pager.Pages.TRANSPORT));
        return transport.equals(getTransport());
    }

    /** replyTo: Uri, string, int
     * replies to a particular message, specified by Uri.
     */
    void replyTo(Intent intent){
      replyTo(
          mContext,
          intent.getData(),
          intent.getExtras().getString("response"),
          intent.getExtras().getInt("new_ack_status"));
    }
    void replyTo(Context context, Uri data, String reply, int ack_status){
        Log.d(TAG, "replying from smspagereceiver!");
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        SmsManager sm = SmsManager.getDefault();
    
        Cursor cursor = context.getContentResolver().query(data,
                new String[] {Pager.Pages.SENDER, Pager.Pages.SERVICE_CENTER, Pager.Pages._ID, Pager.Pages.FROM_ADDR, Pager.Pages.SUBJECT},
                null,
                null,
                null);
        cursor.moveToFirst();

        String sc = null;
        if(prefs.getBoolean("use_received_service_center", false)){
            sc = cursor.getString(cursor.getColumnIndex(Pager.Pages.SERVICE_CENTER));
        }
        if(prefs.getBoolean("include_subject", false)){
            reply = "(" + cursor.getString(cursor.getColumnIndex(Pager.Pages.SUBJECT)) + ") " + reply;
        }
        if(prefs.getBoolean("include_dest_address", false)){
            //send the destination address, and subject, with the reply.
            String email_addr = cursor.getString(cursor.getColumnIndex(Pager.Pages.FROM_ADDR));
            reply = email_addr + " " + reply;
            Log.d(TAG, "reply text: " + reply);
        }
        String dest = cursor.getString(cursor.getColumnIndex(Pager.Pages.SENDER));
        Intent successIntent = new Intent("org.nerdcircus.android.klaxon.REPLY_SENT", data);
        Log.d(TAG, "new ack status should be: "+ack_status);
        successIntent.putExtra(Pager.EXTRA_NEW_ACK_STATUS, ack_status); //note what our new status should be set to.
        sm.sendTextMessage(dest, sc, reply,
            PendingIntent.getBroadcast(context, 0, successIntent, PendingIntent.FLAG_UPDATE_CURRENT),
            null
        );
        Log.d(TAG, "Message sent.");
    }

    /* Exposed for testing only. */
    public static boolean testIsPage(Alert alert, String trigger){
        return matchesPageCriteria(alert.asContentValues(), trigger, false);
    }
      

}


