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

import android.app.AlertDialog;
import android.app.Dialog;
import android.app.ListActivity;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.view.ContextMenu;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ListAdapter;
import android.widget.ListView;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import org.nerdcircus.android.klaxon.GcmHelper;
import org.nerdcircus.android.klaxon.ReplyMenuUtils;
import org.nerdcircus.android.klaxon.Pager;
import org.nerdcircus.android.klaxon.Pager.*;

import android.util.Log;

public class KlaxonList extends ListActivity
{
    private String TAG = "KlaxonList";
    public static final String AUTH_PERMISSION_ACTION = "org.nerdcircus.android.klaxon.AUTH_PERMISSION";

    //menu constants.
    private int MENU_ACTIONS_GROUP = Menu.FIRST;
    private int MENU_ALWAYS_GROUP = Menu.FIRST + 1;

    private int DIALOG_DELETE_ALL_CONFIRMATION = 1;

    private int REQUEST_PICK_REPLY = 1;

    private boolean mPlayServicesMessageShown = false;

    private GcmHelper mGcmHelper;
    private Cursor mCursor;
    protected Dialog onCreateDialog(int id){
        if(id == DIALOG_DELETE_ALL_CONFIRMATION){
            //Confirm deletion.
            AlertDialog.OnClickListener delete_all_confirm_listener =
                new AlertDialog.OnClickListener(){
                    public void onClick(DialogInterface dialog, int which){
                        if(which == AlertDialog.BUTTON_POSITIVE){
                            getContentResolver().delete(Pages.CONTENT_URI, null, null);
                        }	
                        else {
                            dialog.dismiss();
                        }
                    }
                };
            AlertDialog.Builder confirm_dialog = new AlertDialog.Builder((Context)this); 
            confirm_dialog.setMessage(R.string.confirm_delete);
            confirm_dialog.setNegativeButton(R.string.no, delete_all_confirm_listener );
            confirm_dialog.setPositiveButton(R.string.yes, delete_all_confirm_listener );
            return confirm_dialog.create();
        }
        return null;
    }

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
        registerForContextMenu(getListView());
        mGcmHelper = new GcmHelper(this);
    }

    public void onResume(){
        if(mGcmHelper.checkForPlayServices(mPlayServicesMessageShown)){
          Log.d(TAG, "play services found.");
          mGcmHelper.maybePromptForLoginPermission();
        } else {
          Log.d(TAG, "play services not found.");
        }
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

        Cursor c = managedQuery(Replies.CONTENT_URI,  
                    new String[] {Replies._ID, Replies.NAME, Replies.BODY, Replies.ACK_STATUS},
                    "show_in_menu == 1", null, null);
        c.moveToFirst();
        while ( ! c.isAfterLast() ){
            ReplyMenuUtils.addMenuItem(
                    this,
                    menu,
                    c.getString(c.getColumnIndex(Replies.NAME)),
                    c.getString(c.getColumnIndex(Replies.BODY)),
                    c.getInt(c.getColumnIndex(Replies.ACK_STATUS)),
                    Uri.withAppendedPath(Pager.Pages.CONTENT_URI,
                         c.getString(c.getColumnIndex(Replies._ID)))
                    );
            c.moveToNext();
        }
        Intent i = new Intent(Intent.ACTION_PICK, Replies.CONTENT_URI);
        i.setType("vnd.android.cursor.item/reply");
        menu.add(MENU_ACTIONS_GROUP, Menu.NONE, Menu.NONE, R.string.other).setIntent(i);

        //make delete be last
        menu.add(MENU_ACTIONS_GROUP, Menu.NONE, Menu.NONE, R.string.delete).setOnMenuItemClickListener(
            new MenuItem.OnMenuItemClickListener(){
                public boolean onMenuItemClick(MenuItem item){
                    //delete.
                    Uri itemurl = Uri.withAppendedPath(Pages.CONTENT_URI, ""+getSelectedItemId());
                    getContentResolver().delete(itemurl, null, null);
                    return true;
                }
            }
        );

        MenuItem mi = menu.add(MENU_ALWAYS_GROUP, Menu.NONE, Menu.NONE, R.string.prefs_activity);
        mi.setIcon(android.R.drawable.ic_menu_preferences);
        i = new Intent(Intent.ACTION_MAIN);
        i.setClassName(this, "org.nerdcircus.android.klaxon.Preferences");
        mi.setIntent(i);

        mi = menu.add((MENU_ALWAYS_GROUP|Menu.CATEGORY_SECONDARY), Menu.NONE, Menu.NONE, R.string.delete_all);
        mi.setIcon(android.R.drawable.ic_menu_delete);
        mi.setOnMenuItemClickListener(
            new MenuItem.OnMenuItemClickListener(){
                public boolean onMenuItemClick(MenuItem item){
                    showDialog(DIALOG_DELETE_ALL_CONFIRMATION);
                    return true;
                }
            }
        );
                    

        return true;
    }


    @Override
    public boolean onPrepareOptionsMenu(Menu menu){
        Log.d(TAG, "preparing options menu");
        super.onPrepareOptionsMenu(menu);
        final boolean haveItems = getSelectedItemId() >= 0;
        menu.setGroupVisible(MENU_ACTIONS_GROUP, haveItems);
        menu.setGroupVisible(MENU_ALWAYS_GROUP, true);
        return true;
    }

    @Override
    public void onCreateContextMenu(ContextMenu menu, View v, ContextMenu.ContextMenuInfo menuInfo) {
        Log.d(TAG, "Menu info position: " + ((AdapterView.AdapterContextMenuInfo)menuInfo).position);

        Cursor c = managedQuery(Replies.CONTENT_URI,  
                    new String[] {Replies._ID, Replies.NAME, Replies.BODY, Replies.ACK_STATUS},
                    "show_in_menu == 1", null, null);
        c.moveToFirst();
        while ( ! c.isAfterLast() ){
            MenuItem mi = ReplyMenuUtils.addMenuItem(
                    this,
                    menu,
                    c.getString(c.getColumnIndex(Replies.NAME)),
                    c.getString(c.getColumnIndex(Replies.BODY)),
                    c.getInt(c.getColumnIndex(Replies.ACK_STATUS)),
                    Uri.withAppendedPath(Pager.Pages.CONTENT_URI,
                                         ""+getListAdapter().getItemId(((AdapterView.AdapterContextMenuInfo)menuInfo).position))
                    );
            c.moveToNext();
        }
        // Add the "Other" menu option.
        menu.add(MENU_ACTIONS_GROUP, Menu.NONE, Menu.NONE, R.string.other).setOnMenuItemClickListener(
            new MenuItem.OnMenuItemClickListener(){
                public boolean onMenuItemClick(MenuItem item){
                    Intent i = new Intent(Intent.ACTION_PICK, Replies.CONTENT_URI);
                    i.setType("vnd.android.cursor.item/reply");
                    long itemId = getListAdapter().getItemId(((AdapterView.AdapterContextMenuInfo)item.getMenuInfo()).position);
                    i.putExtra("page_uri", Uri.withAppendedPath(Pages.CONTENT_URI, ""+itemId).toString() );
                    startActivityForResult(i, REQUEST_PICK_REPLY);
                    return true;
                }
            }
        );
        // Add the "delete" option.
        menu.add(MENU_ACTIONS_GROUP, Menu.NONE, Menu.NONE, R.string.delete).setOnMenuItemClickListener(
            new MenuItem.OnMenuItemClickListener(){
                public boolean onMenuItemClick(MenuItem item){
                    long itemId = getListAdapter().getItemId(((AdapterView.AdapterContextMenuInfo)item.getMenuInfo()).position);
                    //delete.
                    Uri itemurl = Uri.withAppendedPath(Pages.CONTENT_URI, ""+itemId);
                    getContentResolver().delete(itemurl, null, null);
                    return true;
                }
            }
        );

    }

    //XXX: this is copied from PageViewer. factor it out.
    protected void onActivityResult(int requestCode, int resultCode, Intent data){
        if( requestCode == REQUEST_PICK_REPLY ){
           if(resultCode == RESULT_OK){
               //send a reply.
               Cursor c = managedQuery(data.getData(),  
                           new String[] {Replies._ID, Replies.BODY, Replies.ACK_STATUS},
                           null, null, null);
               c.moveToFirst();
               Intent i = new Intent(Pager.REPLY_ACTION);
               i.setData(Uri.parse(data.getStringExtra("page_uri")));
               i.putExtra("response", c.getString(c.getColumnIndex(Replies.BODY)));
               i.putExtra("new_ack_status", c.getInt(c.getColumnIndex(Replies.ACK_STATUS)));
               sendBroadcast(i);
               return;
           } else if (requestCode == GcmHelper.RC_NOPLAY){
              mPlayServicesMessageShown = true;
              if(resultCode == RESULT_CANCELED){
                  Log.d(TAG, "play install cancelled. some things may not work.");
              }
              return;
           }
        }
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
            //prefs.edit().putString("alert_sound", "content://media/internal/audio/media/2").commit();
        }

        PreferenceManager.setDefaultValues(this, R.xml.preferences, false);
    }
}

