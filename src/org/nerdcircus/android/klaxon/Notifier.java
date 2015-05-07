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

import android.app.*;
import android.content.*;
import android.database.Cursor;
import android.media.AudioManager;
import android.net.Uri;
import android.preference.PreferenceManager;
import android.provider.Settings;
import android.support.v4.app.NotificationCompat;
import android.util.Log;
import android.widget.Toast;

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
            n.sound = null; //no noise initially. wait for the delayed ANNOY action below.
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
            
            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);

            Log.d(TAG, "notifcation interval: " + prefs.getString("notification_interval", "unknown"));
            long repeat_interval_ms = Integer.valueOf(prefs.getString("notification_interval", "20000")).longValue();
            Log.d(TAG, "notifcation interval: " + repeat_interval_ms);

            // 500 ms delay, to prevent the regular text message noise from stomping on us.
            am.setRepeating(AlarmManager.RTC_WAKEUP, System.currentTimeMillis()+500, repeat_interval_ms, annoyintent);
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
        else if(intent.getAction().equals("org.nerdcircus.android.klaxon.REPLY_SENT")){
            //a reply was sent. update state in the db.
            if(Activity.RESULT_OK == getResultCode()){
                Log.d(TAG, "reply successful. updating ack status..");
                //result was sent. update state.
                updateAckStatus(context, intent.getData(), intent.getIntExtra(Pager.EXTRA_NEW_ACK_STATUS, 0));
            }
            else {
                Log.e(TAG, "reply failed!!! doing nothing.");
            }
        }
        else {
            Log.e(TAG, "Uncaught Action:" + intent.getAction());
        }
        
    }

    /** return our notification object.
     */
    Notification getNotification(Context context, String subject) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        Uri alertsound = Uri.parse(prefs.getString("alert_sound","DEFAULT"));
        if( alertsound.toString().equals("DEFAULT")){
            //no setting. use default.
            alertsound = Settings.System.DEFAULT_NOTIFICATION_URI;
        }

        Intent listIntent = new Intent(Intent.ACTION_VIEW);
        listIntent.setType("vnd.android.cursor.dir/pages");
        Intent cancelIntent = new Intent(Pager.SILENCE_ACTION);

        NotificationCompat.Builder nb = new NotificationCompat.Builder(context);
        nb.setSmallIcon(R.drawable.bar_icon);
        nb.setPriority(NotificationCompat.PRIORITY_MAX);
        nb.setLights(R.color.red, 1000, 100);
        nb.setColor(R.color.red);

        int streamtype = Notification.STREAM_DEFAULT;
        if(prefs.getBoolean("use_alarm_stream", false)){
            streamtype = AudioManager.STREAM_ALARM;
            nb.setCategory(NotificationCompat.CATEGORY_ALARM);
        }
        nb.setSound(alertsound, streamtype);
        nb.setContentTitle(subject);
        nb.setContentIntent(PendingIntent.getActivity(context, 0, listIntent, 0));
        nb.setDeleteIntent(PendingIntent.getBroadcast(context, 0, cancelIntent, 0));
        nb.setAutoCancel(true);


        //vibrate!
        if (prefs.getBoolean("vibrate", true)){
            nb.setVibrate(new long[] {0, 800, 500, 800});
        }

        return nb.build();
    }

    private void updateAckStatus(Context c, Uri data, int ack_status){
        Log.d(TAG, "updating acks status for "+data.toString()+" to "+ ack_status);
        ContentValues cv = new ContentValues();
        cv.put(Pager.Pages.ACK_STATUS, Integer.valueOf(ack_status));
        int rows = c.getContentResolver().update(data, cv, null, null);
        Log.d(TAG, "Updated rows: "+rows);
        Toast.makeText(c, R.string.reply_ok, Toast.LENGTH_LONG).show();
    }

}
