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

import android.accounts.Account;
import android.accounts.AccountManager;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.content.Intent;
import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.preference.CheckBoxPreference;
import android.preference.Preference;
import android.preference.ListPreference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceManager;
import android.os.Build;
import android.app.PendingIntent;
import android.view.View;
import android.widget.Toast;

import android.util.Log;

import java.util.Vector;

import org.nerdcircus.android.klaxon.Changelog;
import org.nerdcircus.android.klaxon.ReplyList;

public class Preferences extends PreferenceActivity implements OnSharedPreferenceChangeListener {
    
    private static final Uri CHANGELOG_URI = Uri.parse("http://code.google.com/p/klaxon/wiki/ChangeLog");
    final Handler mHandler = new Handler();

    // Create runnable for posting
    final Runnable mUpdateC2dmPrefs = new Runnable() {
        public void run() {
	      updateC2dmPrefs();
	}
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        // Load the preferences from an XML resource
        addPreferencesFromResource(R.xml.preferences);

        Preference replylist = this.findPreference("edit_replies");
        Intent i = new Intent(Intent.ACTION_MAIN);
        i.setClass(this, ReplyList.class);
        replylist.setIntent(i);

        // rig up the Changelog
        replylist = this.findPreference("changelog");
        i = new Intent(Intent.ACTION_MAIN);
        i.setClass(this, Changelog.class);
        replylist.setIntent(i);

        replylist = this.findPreference("version");
        replylist.setSummary(getAppVersion(this));

        replylist = this.findPreference("sendfeedback");
        replylist.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
                public boolean onPreferenceClick(Preference p){
                    Preferences.sendDebugEmail(p.getContext());
                    return true;
                }});

        replylist = this.findPreference("send_test_message");
        i = new Intent(Intent.ACTION_VIEW);
        i.setData(Uri.parse("https://pagepusher.appspot.com/test"));
        replylist.setIntent(i);

        //disable the "Consume SMS" option if the build is too low
        //NB: there's no code to act on this, since the abortBroadcast() 
        // call will not break anything when called in < 1.6
        Log.d("BUILDVERSION", Build.VERSION.SDK);
        if(Integer.valueOf(Build.VERSION.SDK) <= Integer.valueOf(3)){
            CheckBoxPreference csp = (CheckBoxPreference) this.findPreference("consume_sms_message");
            csp.setChecked(false);
            csp.setEnabled(false);
        }

	ListPreference c2dm_accounts = (ListPreference) this.findPreference("c2dm_register_account");
	Account[] accounts = AccountManager.get(getApplicationContext()).getAccountsByType("com.google");
	Vector<CharSequence> accountNames = new Vector<CharSequence>();
	for (Account account : accounts) {
	   accountNames.add(account.name);
	}
	CharSequence[] accountNamesArray = new CharSequence[accountNames.size()];
	accountNames.toArray(accountNamesArray);
	c2dm_accounts.setEntries(accountNamesArray);
	c2dm_accounts.setEntryValues(accountNamesArray);

	mHandler.post(mUpdateC2dmPrefs);
    }

    @Override
    protected void onPause() {
	    super.onPause();
	    getPreferenceScreen().getSharedPreferences().unregisterOnSharedPreferenceChangeListener(this);
    }

    void updateC2dmPrefs() {
	String token = PreferenceManager
		.getDefaultSharedPreferences(this)
		.getString("c2dm_token", "");
	getPreferenceScreen()
		.findPreference("c2dm_register")
		.setEnabled(token.equals(""));
	getPreferenceScreen()
		.findPreference("c2dm_unregister")
		.setEnabled(!token.equals(""));
	getPreferenceScreen()
		.findPreference("c2dm_token")
		.setSummary(token);
    }

    @Override
    protected void onResume() {
	super.onResume();
	// Setup the initial values
	// Set up a listener whenever a key changes            
	getPreferenceScreen()
		.getSharedPreferences()
		.registerOnSharedPreferenceChangeListener(this);
    }

    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
	if (key.equals("c2dm_token"))
	    mHandler.post(mUpdateC2dmPrefs);
    }


    public void c2dmRegister(View v) {
      Intent registrationIntent = new Intent("com.google.android.c2dm.intent.REGISTER");
      registrationIntent.putExtra("app", PendingIntent.getBroadcast(this, 0, new Intent(), 0)); // boilerplate
      SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(this);
      String c2dmSender = settings.getString("c2dm_sender", "");
      if (c2dmSender.equals("")) {
	    CharSequence text = "Set sender email address first.";
	    int duration = Toast.LENGTH_LONG;
	    Toast toast = Toast.makeText(getApplicationContext(), text,
			    duration);
	    toast.show();
	    return;
      }
      registrationIntent.putExtra("sender", c2dmSender);
      startService(registrationIntent);
    }

    public void c2dmUnregister(View v) {
	Intent unregIntent = new Intent("com.google.android.c2dm.intent.UNREGISTER");
	unregIntent.putExtra("app", PendingIntent.getBroadcast(this, 0, new Intent(), 0));
	startService(unregIntent);
    }

    public void c2dmSendToken(View v) {
	final Intent emailIntent = new Intent(android.content.Intent.ACTION_SEND);
      	
	SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(this);
      	String c2dmToken = settings.getString("c2dm_token", "");
	Account[] accounts = AccountManager.get(this).getAccounts();
	if (accounts.length > 0 && c2dmToken != "") {
		emailIntent .setType("plain/text");
		emailIntent .putExtra(android.content.Intent.EXTRA_EMAIL, new String[]{accounts[0].name});
		emailIntent .putExtra(android.content.Intent.EXTRA_SUBJECT, "My C2DM token");
		emailIntent .putExtra(android.content.Intent.EXTRA_TEXT, c2dmToken);
		this.startActivity(Intent.createChooser(emailIntent, "Send mail..."));
	}
    }

    /** Fires the intent to send an email with some debugging info.
     */
    public static void sendDebugEmail(Context context){
        Intent send = new Intent(Intent.ACTION_SEND);
        send.setType("text/plain");
        send.putExtra(Intent.EXTRA_EMAIL, new String[] {"klaxon-users@googlegroups.com"});
        send.putExtra(Intent.EXTRA_SUBJECT, "Debug Email Report");
        send.putExtra(Intent.EXTRA_TEXT, getDebugMessageBody(context));
        context.startActivity(Intent.createChooser(send, "Send Debugging Email"));
    }
    public static String getAppVersion(Context context){
        String version = "unknown";
        try {
            PackageManager manager = context.getPackageManager();
            PackageInfo info = manager.getPackageInfo(
                context.getPackageName(), 0);
            version = info.versionName;
        }
        catch(Exception e){};
        return version;
    }
    public static String getDebugMessageBody(Context context){
        //Put some useful debug data in here.
        String body = "\n" + 
            "** System Info:\n" + 
            "Android Version: " + Build.VERSION.RELEASE + "\n" + 
            "Device: " + Build.MODEL + "\n" + 
            "Build Info: " + Build.FINGERPRINT + "\n" + 
            "** App Info:\n" + 
            "App Version: " + getAppVersion(context) + "\n";

        return body;
    }

}

