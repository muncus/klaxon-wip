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
    private static String MY_TRANSPORT = "c2dm";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent.getAction().equals(Pager.REPLY_ACTION)){
          Log.d(TAG, "REPLYING!");
          //GCMIntentService.onMessage(context, intent);
          return;
        }
    }

}


