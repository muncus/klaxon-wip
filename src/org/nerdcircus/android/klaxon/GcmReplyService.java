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

import android.app.IntentService;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import org.nerdcircus.android.klaxon.Pager;
import org.nerdcircus.android.klaxon.PageReceiver;

public class GcmReplyService extends PageReceiver
{
    public static String TAG = "GcmReplyService";
    private static String MY_TRANSPORT = "gcm";

    public String getTransport(){
      return MY_TRANSPORT;
    }

    public void onReplyIntent(Intent intent){
      Log.d(TAG, "Replying!");

      //replying to a received page.
      if( canReply(intent.getData())){
          replyTo(intent);
          return;
      }
      else {
          Log.d(TAG, "cannot reply to this message.");
          return;
      }
    }

    // This service doesnt receive alerts via intent. see GCMIntentService.java
    public void onAlertReceived(Intent i){
      return;
    }

    /** replyTo: Uri, string, int
     * replies to a particular message, specified by Uri.
     */
    void replyTo(Intent intent){
      //parse data from the intent.
      Uri data = intent.getData();
      Bundle extras = intent.getExtras();
      String response = extras.getString("response");
      Integer new_ack_status = extras.getInt("new_ack_status");
      // Then reply.
      Log.d(TAG, "replying from C2dmPageReceiver!");
      SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
      Cursor cursor = getContentResolver().query(data,
              new String[] {Pager.Pages.SENDER, Pager.Pages.SERVICE_CENTER, Pager.Pages._ID, Pager.Pages.FROM_ADDR, Pager.Pages.SUBJECT},
              null,
              null,
              null);
      cursor.moveToFirst();
      Intent successIntent = new Intent("org.nerdcircus.android.klaxon.REPLY_SENT", data);
      successIntent.putExtra(Pager.EXTRA_NEW_ACK_STATUS, new_ack_status);
      GcmHelper gh = new GcmHelper(this);
      if( gh.reply(cursor.getString(cursor.getColumnIndex(Pager.Pages.SENDER)), response))
        this.sendBroadcast(successIntent);
    }

}


