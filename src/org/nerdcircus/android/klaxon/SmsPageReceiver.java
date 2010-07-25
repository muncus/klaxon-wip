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

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.app.PendingIntent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.telephony.SmsManager;
import android.telephony.SmsMessage;
import android.util.Log;

import org.nerdcircus.android.klaxon.Alert;
import org.nerdcircus.android.klaxon.Pager;
import org.nerdcircus.android.klaxon.Pager.Pages;
import org.nerdcircus.android.klaxon.PagerProvider;
import org.nerdcircus.android.klaxon.pageparser.*;

import java.util.Map;

public class SmsPageReceiver extends BroadcastReceiver
{
    public static String TAG = "SmsPageReceiver";
    private static String MY_TRANSPORT = "sms";

    public void queryAndLog(Context context, Uri u){
        Log.d(TAG, "querying: "+u.toString());
        Cursor c = context.getContentResolver().query(u, null, null, null, null);
        c.moveToFirst();
        for(int i=0; i<c.getColumnCount(); i++){
            Log.d(TAG, c.getColumnName(i)+" : "+c.getString(i));
        }
    }
    
    @Override
    public void onReceive(Context context, Intent intent)
    {

        //check to see if we want to intercept.
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        if( ! prefs.getBoolean("is_oncall", true) ){
            Log.d(TAG, "not oncall. not bothering with incoming sms.");
            return;
        }

        if( intent.getAction().equals(Pager.REPLY_ACTION) ){
            //replying to a received page.
            Uri data = intent.getData();
            Bundle extras = intent.getExtras();
            String response = extras.getString("response");
            Integer new_ack_status = extras.getInt("new_ack_status");
            if( canReply(context, data)){
                replyTo(context, data, response, new_ack_status);
                return;
            }
            else {
                Log.d(TAG, "cannot reply to this message.");
            }
        }

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

        if( ! isPage(incoming.asContentValues(), context) ) {
            Log.d(TAG, "message doesnt appear to be a page. skipping");
            return;
        }

        Uri newpage = context.getContentResolver().insert(Pages.CONTENT_URI, incoming.asContentValues());
        Log.d(TAG, "new message inserted.");
        Intent annoy = new Intent(Pager.PAGE_RECEIVED);
        annoy.setData(newpage);
        context.sendBroadcast(annoy);
        Log.d(TAG, "sent intent " + annoy.toString() );
        //NOTE: as of 1.6, this broadcast can be aborted.
        if (prefs.getBoolean("consume_sms_message", false)){
            abortBroadcast();
            Log.d(TAG, "sms broadcast aborted.");
        }

        
    }

    /** Determine if we care about a particular SMS message.
     */
    boolean isPage(ContentValues cv, Context context){
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        String trigger_string = prefs.getString("sender_match", "");
        if( cv.getAsString(Pages.SENDER).contains(trigger_string) )
            return true;
        if( cv.getAsString(Pages.FROM_ADDR).contains(trigger_string) )
            return true;
        if ( prefs.getBoolean("also_match_body", false) ){
            if( cv.getAsString(Pages.BODY).contains(trigger_string) )
                return true;
        }
        return false;
    }

    /** check if we can reply to this page.
     */
    boolean canReply(Context context, Uri data){
        Log.d(TAG, "attempting to reply to: " + data);
        Cursor cursor = context.getContentResolver().query(data,
                new String[] {Pager.Pages.TRANSPORT, Pager.Pages._ID},
                null,
                null,
                null);
        cursor.moveToFirst();

        String transport = cursor.getString(cursor.getColumnIndex(Pager.Pages.TRANSPORT));
        if (transport.equals(MY_TRANSPORT)){
            return true;
        }
        else {
            return false;
        }
    }

    /** replyTo: Uri, string, int
     * replies to a particular message, specified by Uri.
     */
    void replyTo(Context context, Uri data, String reply, int ack_status){
        Log.d(TAG, "replying from smspagereceiver!");
        SmsManager sm = SmsManager.getDefault();
    
        Cursor cursor = context.getContentResolver().query(data,
                new String[] {Pager.Pages.SENDER, Pager.Pages.SERVICE_CENTER, Pager.Pages._ID},
                null,
                null,
                null);
        cursor.moveToFirst();

        String dest = cursor.getString(cursor.getColumnIndex(Pager.Pages.SENDER));
        Intent successIntent = new Intent("org.nerdcircus.android.klaxon.REPLY_SENT", data);
        Log.d(TAG, "new ack status should be: "+ack_status);
        successIntent.putExtra(Pager.EXTRA_NEW_ACK_STATUS, ack_status); //note what our new status should be set to.
        //TODO: make the null below use the failure intent, but it now has to check result code.
        sm.sendTextMessage(dest, null, reply,
            PendingIntent.getBroadcast(context, 0, successIntent, PendingIntent.FLAG_UPDATE_CURRENT),
            null
        );
        Log.d(TAG, "Message sent.");
    }

    /** Clean up some stupid issues with AT&T
     * Messages from att's sms gateways do some crazy things:
     * * dumb line endings (\r\n)
     * * "(Cont'd)" tags on followup messages
     * * "2 of 5" messages on followup messages.
     * * android doesnt currently recognize these messages as emails.
     */
    ContentValues cleanupAttMessage(ContentValues cv){
        String fullbody = "";
        String body = cv.getAsString(Pages.BODY);
        // clean up "continued" messages.
        if(body.endsWith("\n(Con't)")){
            body = body.substring(0, body.lastIndexOf("\n(Con't)"));
        }
        // fix stupid line-endings.
        if(body.contains("\r")){
            Log.d(TAG, "Message contains \\r. fixing.");
            body = body.replaceAll("\r", "");
        }
        //eat the "m of n" messages at the beginning of the message.
        fullbody += body.replaceFirst(" ?[0-9]+ of [0-9]+\n", "");

        cv.put(Pages.BODY, fullbody);

        //the sms gateway for at&t is not recognized by android's sms parsing
        //slice up their gateway'd messages, and do the Right Thing.
        if( fullbody.length() > 3 && fullbody.substring(0,4).equals("FRM:")){
            String[] mail_fields = fullbody.split("(FRM|SUBJ|MSG):", 4);
            cv.put(Pages.SUBJECT, mail_fields[2].trim());
            cv.put(Pages.BODY, mail_fields[3].trim());
        }

        return cv;
    }

    //some providers (go2mobile) use characters other than \n to end a line.
    //fix them, so we can tell the parts of the message apart.
    ContentValues cleanupLineEndings(ContentValues cv){
        String body = cv.getAsString(Pages.BODY);
        String[] fields = body.split(":", 3);
        cv.put(Pages.FROM_ADDR, fields[0].trim());
        cv.put(Pages.SUBJECT, fields[1].trim());
        cv.put(Pages.BODY, fields[2].trim());
        return cv;
    }

}


