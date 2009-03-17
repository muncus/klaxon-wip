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
import android.app.ListActivity;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.AdapterView;
import android.widget.ImageView;
import android.widget.ListAdapter;
import android.widget.ListView;
import android.widget.SimpleCursorAdapter;
import android.widget.TextView;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;

import org.nerdcircus.android.klaxon.Pager;

import android.util.Log;

public class KlaxonList extends ListActivity
{
    private String TAG = "KlaxonList";

    //menu constants.
    private int MENU_ACTIONS_GROUP = Menu.FIRST;
    private int MENU_ALWAYS_GROUP = Menu.FIRST + 1;
    private int MENU_ACK = Menu.FIRST;
    private int MENU_NACK = Menu.FIRST + 1;
    private int MENU_DELETE = Menu.FIRST + 2;
    private int MENU_PREFS = Menu.FIRST + 3;

    private Cursor mCursor;

    @Override
    public void onCreate(Bundle icicle)
    {
        super.onCreate(icicle);

        createDefaultPreferences();

        setContentView(R.layout.esclist);
        
        String[] cols = new String[] {Pager.Pages._ID, Pager.Pages.SUBJECT, Pager.Pages.SENDER, Pager.Pages.SERVICE_CENTER, Pager.Pages.ACK_STATUS };
        Log.d(TAG, "querying");
        mCursor = Pager.Pages.query(this.getContentResolver(), cols);
        startManagingCursor(mCursor);
        Log.d(TAG, "found rows:"+ mCursor.getCount());
        Log.d(TAG, "setting adapter");
        ListAdapter adapter = new EscAdapter(this, 
                                             R.layout.esclist_item,
                                             mCursor);
        Log.d(TAG, "adapter created.");
        setListAdapter(adapter);
        Log.d(TAG, "oncreate done.");
    }


    public void onResume(){
        super.onResume();
        //if they're active, cancel any alarms and notifications.
        Intent i = new Intent(Pager.SILENCE_ACTION);
        sendBroadcast(i);
    }

    public void onListItemClick(ListView parent, View v, int position, long id){
        Log.d(TAG, "Item clicked!");
        Uri pageUri = Uri.withAppendedPath(Pager.Pages.CONTENT_URI, ""+id);
        Intent i = new Intent(Intent.ACTION_VIEW, pageUri);
        startActivity(i);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        super.onCreateOptionsMenu(menu);
        final Context appcontext = (Context)this;
        MenuItem mi;
        //TODO: this should come from prefs.
        mi = menu.add(MENU_ACTIONS_GROUP, MENU_ACK, Menu.NONE, "Ack");
        Intent i = new Intent(Pager.REPLY_ACTION,
                    Uri.withAppendedPath(Pager.Pages.CONTENT_URI, ""+getSelectedItemId())
        );
        i.putExtra("response", "Ack");
        i.putExtra("new_ack_status", 1);
        mi.setIntent(i);

        mi = menu.add(MENU_ACTIONS_GROUP, MENU_NACK, Menu.NONE, "Nack");
        i = new Intent(Pager.REPLY_ACTION,
                    Uri.withAppendedPath(Pager.Pages.CONTENT_URI, ""+getSelectedItemId())
        );
        i.putExtra("response", "Nack");
        i.putExtra("new_ack_status", 1);
        mi.setIntent(i);

        mi = menu.add(MENU_ALWAYS_GROUP, MENU_PREFS, Menu.NONE, "Settings");
        i = new Intent(Intent.ACTION_MAIN);
        i.setClassName(this, "org.nerdcircus.android.klaxon.Preferences");
        mi.setIntent(i);

        return true;
    }


    @Override
    public boolean onPrepareOptionsMenu(Menu menu){
        Log.d(TAG, "preparing options menu");
        super.onPrepareOptionsMenu(menu);
        final boolean haveItems = mCursor.getCount() > 0;
        menu.setGroupVisible(MENU_ACTIONS_GROUP, haveItems);
        menu.setGroupVisible(MENU_ALWAYS_GROUP, true);
        return true;
    }

    /** Create default preferences..
     * this creates some basic default preference settings for responses, and
     * alert sounds
     */
    public void createDefaultPreferences(){
        
        //default noise to make:
        SharedPreferences prefs = getSharedPreferences("alertprefs", 0);
        if( prefs.getAll().isEmpty() ){
            Log.d(TAG, "creating alertprefs");
            prefs.edit().putString("alert_sound", "content://media/internal/audio/media/2").commit();
        }

        //preload some default responses
        prefs = getSharedPreferences("responses", 0);
        Editor e = prefs.edit();
        if( prefs.getAll().isEmpty() ){
            Log.d(TAG, "creating initial responses");
            e.putString("Yes", "Yes");
            e.putString("No", "No");
            e.commit();
        }

    }
}

