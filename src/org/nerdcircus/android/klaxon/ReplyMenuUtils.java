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
import android.content.SharedPreferences.Editor;
import android.preference.PreferenceManager;
import org.nerdcircus.android.klaxon.GcmHelper;
import org.nerdcircus.android.klaxon.Pager;
import org.nerdcircus.android.klaxon.Pager.*;

import android.util.Log;

public class ReplyMenuUtils {
    //menu constants.
    private static int MENU_ACTIONS_GROUP = Menu.FIRST;
    private static int MENU_ALWAYS_GROUP = Menu.FIRST + 1;

    public static MenuItem addMenuItem(final Context c, Menu menu, String label, final String response, final int status, final Uri data_uri){
        //NOTE: these cannot be done with MenuItem.setIntent(), because those
        //intents are called with Context.startActivity()
        MenuItem mi = menu.add(MENU_ACTIONS_GROUP, Menu.NONE, Menu.NONE, label);
        mi.setOnMenuItemClickListener(
            new MenuItem.OnMenuItemClickListener(){
                public boolean onMenuItemClick(MenuItem item){
                    Intent i = new Intent(Pager.REPLY_ACTION);
                    i.setData(data_uri);
                    i.putExtra("response", response);
                    i.putExtra("new_ack_status", status);
                    //TODO: we should not do both. this is an experiment in using Services instead of BroadcastReceivers.
                    c.sendOrderedBroadcast(i, null);
                    c.startService(i);
                    return true;
                }
            }
        );
        return mi;
    }
}