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

import java.util.Map;
import java.util.Iterator;
import java.lang.StringBuffer;

public class C2dmPageReceiver extends BroadcastReceiver
{
    public static String TAG = "C2dmPageReceiver";
    private static String MY_TRANSPORT = "c2dm";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent.getAction().equals(
                "com.google.android.c2dm.intent.REGISTRATION")) {
            handleRegistration(context, intent);
        } else if (intent.getAction().equals(
                "com.google.android.c2dm.intent.RECEIVE")) {
            handleMessage(context, intent);
        }
    }

    private void handleRegistration(Context context, Intent intent) {
        String registration = intent.getStringExtra("registration_id"); 
        if (intent.getStringExtra("error") != null) {
	    CharSequence text = "Registration error. Try again. " + intent.getStringExtra("error");
	    int duration = Toast.LENGTH_LONG;
	    Toast toast = Toast.makeText(context, text, duration);
	    toast.show();

            // Registration failed, should try again later.
        } else if (intent.getStringExtra("unregistered") != null) {
            // unregistration done, new messages from the authorized sender will be rejected
            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
	    String token = prefs.getString("c2dm_token", "");
	    DeviceRegistrar.unregisterWithServer(context, token);
        } else if (registration != null) {
            // Send the registration ID to the 3rd party site that is sending the messages.
	    DeviceRegistrar.registerWithServer(context, registration);
        }
    }

    public void handleMessage(Context context, Intent intent)
    {

        //check to see if we want to intercept.
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        if( ! prefs.getBoolean("is_oncall", true) ){
            Log.d(TAG, "not oncall. not bothering with incoming c2dm push.");
            return;
        }

	Bundle extras = intent.getExtras();
	if (extras == null)
		return;

	String from = extras.getString("frm");
	if (from == null)
		from = extras.getString("from");
	String subject = extras.getString("subject");
	if (subject == null)
		subject = "subject not specified";
	String body = extras.getString("body");
	if (body == null)
		body = "body not speicifed";

       	Alert incoming = (new Standard()).parse(from, subject, body);
        // note that this page was received via sms.
       	incoming.setTransport(MY_TRANSPORT);

        Uri newpage = context.getContentResolver().insert(Pages.CONTENT_URI, incoming.asContentValues());
        Log.d(TAG, "new message inserted.");
        Intent annoy = new Intent(Pager.PAGE_RECEIVED);
        annoy.setData(newpage);
        context.sendBroadcast(annoy);
        Log.d(TAG, "sent intent " + annoy.toString() );
    }
}


