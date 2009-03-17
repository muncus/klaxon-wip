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
import android.telephony.gsm.SmsManager;
import android.telephony.gsm.SmsMessage;
import android.util.Log;

import org.nerdcircus.android.klaxon.Pager;
import org.nerdcircus.android.klaxon.Pager.Pages;
import org.nerdcircus.android.klaxon.PagerProvider;

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

        ContentValues cv = new ContentValues();
        cv.put(Pages.SERVICE_CENTER, msgs[0].getServiceCenterAddress());
        cv.put(Pages.SENDER, msgs[0].getOriginatingAddress());
        cv.put(Pages.SUBJECT, msgs[0].getPseudoSubject());
        cv.put(Pages.ACK_STATUS, 0);
        // FROM_ADDR will be either the email sender, or the same as SENDER above.
        cv.put(Pages.FROM_ADDR, msgs[0].getDisplayOriginatingAddress());
        cv.put(Pages.BODY, msgs[0].getDisplayMessageBody());
        // note that this page was received via sms.
        cv.put(Pages.TRANSPORT, MY_TRANSPORT);

        // if there's no subject, grab the start of the message.
        if(cv.get(Pages.SUBJECT).toString().trim().length() == 0){
            String body = cv.get(Pages.BODY).toString();
            if(body.length() > 41){
                cv.put(Pages.SUBJECT, cv.get(Pages.BODY).toString().substring(0,40));
            }
            else {
                cv.put(Pages.SUBJECT, cv.get(Pages.BODY).toString());
            }
        }

        /* Message cleanups.
         * some carriers add noise to sms contents when gatewaying an email.
         * clean up after them.
         */
        cv = cleanupAttMessage(cv);

        Log.d(TAG, "service center: " + msgs[0].getServiceCenterAddress());
        Log.d(TAG, "email from: " + msgs[0].getEmailFrom());
        Log.d(TAG, "originating addr: " + msgs[0].getOriginatingAddress());
        Log.d(TAG, "message body: " + msgs[0].getMessageBody());
        Log.d(TAG, "email body: " + msgs[0].getEmailBody());


        if( ! isPage(cv, context) ) {
            Log.d(TAG, "message doesnt appear to be a page. skipping");
            return;
        }

        Uri newpage = context.getContentResolver().insert(Pages.CONTENT_URI, cv);
        Log.d(TAG, "new message inserted.");
        Intent annoy = new Intent(Pager.PAGE_RECEIVED);
        annoy.setData(newpage);
        context.sendBroadcast(annoy);
        Log.d(TAG, "sent intent " + annoy.toString() );
        //FIXME: this is an unordered broadcast, and cannot be aborted.
        // this means that all sms-received pages will also appear in the sms app.
        //abortBroadcast();
        Log.d(TAG, "sms broadcast aborted.");

        
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
        return false;
    }

    /** check if we can reply to this page.
     */
    boolean canReply(Context context, Uri data){
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
    //TODO: is this going to work with xmpp? do they have content uris? YES!
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
        String sc = cursor.getString(cursor.getColumnIndex(Pager.Pages.SERVICE_CENTER));

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        if ( ! prefs.getBoolean("use_received_service_center", true)){
            sc = null;
        }
        Intent successIntent = new Intent("org.nerdcircus.android.klaxon.REPLY_OK", data);
        successIntent.putExtra("new status", ack_status); //note what our new status should be set to.
        //TODO: make the null below use the failure intent, but it now has to check result code.
        Intent failureIntent = new Intent("org.nerdcircus.android.klaxon.REPLY_FAILED", data);
        sm.sendTextMessage(dest, sc, reply,
            null,
            PendingIntent.getBroadcast(context, 0, successIntent, 0)
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

}


