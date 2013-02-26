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
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.preference.PreferenceManager;

/** Partial implementation of a PageReceiver.
 * covers some of the basics, like ensuring that the user is currently oncall
 */
public abstract class PageReceiver extends IntentService
{

    // Returns a string identifying which pages this service receives.
    public abstract String getTransport();

    /** Called when a reply should be sent.
     * intent contains the reply string, and the new ack status as extras.
     */
    public abstract void onReplyIntent(Intent intent);

    /** When a page arrives, it should be inserted into the PageProvider, and alert.
     * any non-reply action is considered to be an alert, and this will only be called if the user is oncall.
     */
    public abstract void onAlertReceived(Intent intent);

    /** called when the registered intent is received.
     * this method should handle extracting the relevant data from the
     * transport mechanism, and turning into the appropriate form and insert
     * into the PagerProvider
     */
    protected void onHandleIntent(Intent intent){
      if(intent.getAction().equals(Pager.REPLY_ACTION)){
        if( this.canReply(intent.getContext(), intent.getData())){
          //Call the reply method.
          this.onReplyIntent(intent);
          return;
        } else {
          Log.d(TAG, "could not reply to message: " + intent.getDataString());
        }
        return;
      }
      if(isOnCall(intent.getContext())){
        this.onAlertReceived(intent);
      }
    }

    /** Determine if the user is on call, based on our preference.
     */
    public boolean isOnCall(Context context){
        return PreferenceManager.getDefaultSharedPreferences(context).getBoolean("is_oncall", true);
    }

    /** check if we can reply to this page.
     * this is typically done by checking the transport mechanism, as stored in
     * the PagerProvider
     */
    public boolean canReply(Context context, Uri data){
        Cursor cursor = context.getContentResolver().query(data,
                new String[] {Pager.Pages.TRANSPORT, Pager.Pages._ID},
                null,
                null,
                null);
        cursor.moveToFirst();

        String transport = cursor.getString(cursor.getColumnIndex(Pager.Pages.TRANSPORT));
        if (transport.equals(getTransport())){
            return true;
        }
        else {
            return false;
        }
    }

    /** replies to a particular message, specified by Uri.
     * this must be overridden by PageReceiver subclasses.
     */
    //public abstract void replyTo(Context context, Uri data, String reply, int ack_status);

}


