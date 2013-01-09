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
import android.widget.Toast;

import org.nerdcircus.android.klaxon.Alert;
import org.nerdcircus.android.klaxon.Pager;
import org.nerdcircus.android.klaxon.Pager.Pages;
import org.nerdcircus.android.klaxon.PagerProvider;
import org.nerdcircus.android.klaxon.pageparser.*;
import org.nerdcircus.android.klaxon.GCMIntentService;

import java.util.Map;
import java.util.Iterator;
import java.lang.StringBuffer;

public class C2dmPageReceiver extends BroadcastReceiver
{
    public static String TAG = "C2dmPageReceiver";
    private static String MY_TRANSPORT = "gcm";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent.getAction().equals(Pager.REPLY_ACTION)){
          Log.d(TAG, "REPLYING!");

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
              return;
          }

        }
    }

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
    void replyTo(Context context, Uri data, String reply, int ack_status){
        Log.d(TAG, "replying from C2dmPageReceiver!");
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        Cursor cursor = context.getContentResolver().query(data,
                new String[] {Pager.Pages.SENDER, Pager.Pages.SERVICE_CENTER, Pager.Pages._ID, Pager.Pages.FROM_ADDR, Pager.Pages.SUBJECT},
                null,
                null,
                null);
        cursor.moveToFirst();

        Intent successIntent = new Intent("org.nerdcircus.android.klaxon.REPLY_SENT", data);
        successIntent.putExtra(Pager.EXTRA_NEW_ACK_STATUS, ack_status);
        GcmHelper gh = new GcmHelper(context);
        if( gh.reply(cursor.getString(cursor.getColumnIndex(Pager.Pages.SENDER)), reply))
          context.sendBroadcast(successIntent);
    }

}


