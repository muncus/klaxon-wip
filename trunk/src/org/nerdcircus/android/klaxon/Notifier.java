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
import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.media.AudioManager;
import android.net.Uri;
import android.os.SystemClock;
import android.preference.PreferenceManager;
import android.util.Log;
import android.widget.RemoteViews;

import org.nerdcircus.android.klaxon.Pager;

public class Notifier extends BroadcastReceiver
{
    public static String TAG = "KlaxonNotifier";

    @Override
    public void onReceive(Context context, Intent intent)
    {
        NotificationManager nm = (NotificationManager)
            context.getSystemService(Context.NOTIFICATION_SERVICE);
        AlarmManager am = (AlarmManager)context.getSystemService(Activity.ALARM_SERVICE);

        if(intent.getAction().equals(Pager.PAGE_RECEIVED)){
            Log.d(TAG, "new page received. notifying.");

            //get subject line of page, for notification.
            Cursor cursor = context.getContentResolver().query(intent.getData(),
                    new String[] {Pager.Pages._ID, Pager.Pages.SUBJECT},
                    null, null, null);
            cursor.moveToFirst();
            String page_subj = cursor.getString(cursor.getColumnIndex(Pager.Pages.SUBJECT));

            Notification n = getNotification(context, page_subj);
            nm.notify(R.string.notify_page, n);

            Intent i = new Intent(Pager.ANNOY_ACTION);
            //we cant use data here, because it makes the silencing fail.
            i.putExtra("notification_text", page_subj);
            PendingIntent annoyintent = PendingIntent.getBroadcast(
                                          context,
                                          0,
                                          i,
                                          PendingIntent.FLAG_CANCEL_CURRENT
                                        );

            am.setRepeating(AlarmManager.RTC_WAKEUP, System.currentTimeMillis()+20000, 20000, annoyintent);
        }
        else if(intent.getAction().equals(Pager.SILENCE_ACTION)){
            Log.d(TAG, "cancelling the notification...");
            Intent i = new Intent(Pager.ANNOY_ACTION);
            PendingIntent annoyintent = PendingIntent.getBroadcast(
                                          context,
                                          0,
                                          i,
                                          PendingIntent.FLAG_CANCEL_CURRENT
                                        );
            am.cancel(annoyintent);
            nm.cancel(R.string.notify_page);
        }
        else if(intent.getAction().equals(Pager.ANNOY_ACTION)){
            Log.e(TAG, "got annoy intent. annoying.");
            //just be annoying.
            Notification n = getNotification(context, intent.getStringExtra("notification_text"));
            nm.notify(R.string.notify_page, n);
        }
        else {
            Log.e(TAG, "Uncaught Action:" + intent.getAction());
        }
        
    }

    /** return our notification object.
     */
    Notification getNotification(Context context, String subject) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        Uri alertsound = Uri.parse(prefs.getString("alert_sound", ""));

        Intent listIntent = new Intent(Intent.ACTION_VIEW);
        listIntent.setType("vnd.android.cursor.dir/pages");
        Intent cancelIntent = new Intent(Pager.SILENCE_ACTION);


        Notification n = new Notification(
            R.drawable.bar_icon,
            "you have been paged", //TODO: make this be "n pages waiting"
            System.currentTimeMillis()
        );
        n.ledARGB=R.color.red;
        n.ledOnMS=1000;
        n.ledOffMS=100;
        n.vibrate = new long[] {0, 800, 500, 800};
        n.sound = alertsound;
        n.flags = Notification.FLAG_AUTO_CANCEL | 
                  Notification.FLAG_INSISTENT |
                  Notification.FLAG_SHOW_LIGHTS;
        n.contentIntent = PendingIntent.getActivity(context, 0, listIntent, 0);
        n.deleteIntent = PendingIntent.getBroadcast(context, 0, cancelIntent, 0);

        n.tickerText = subject;
        n.contentView = new RemoteViews(context.getPackageName(), R.layout.notification);

        n.contentView.setTextViewText(R.id.text, subject);

        // default is RING. this will override.
        if (prefs.getBoolean("use_alert_stream", false)){
            n.audioStreamType = AudioManager.STREAM_ALARM;
        }

        return n;
    }

}
