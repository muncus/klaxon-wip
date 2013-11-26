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

import android.accounts.AccountManager;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.preference.CheckBoxPreference;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.View;
import android.widget.Toast;
import com.google.android.gms.common.AccountPicker;

public class Preferences extends PreferenceActivity {
    
    private final String TAG = "KlaxonPreferences";
    private GcmHelper mHelper;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mHelper = new GcmHelper(this);
        
        // Load the preferences from an XML resource
        addPreferencesFromResource(R.xml.preferences);

        Preference replylist = this.findPreference("edit_replies");
        if(replylist != null){
          Intent i = new Intent(Intent.ACTION_MAIN);
          i.setClass(this, ReplyList.class);
         replylist.setIntent(i);
        }

        // rig up the Changelog
        replylist = this.findPreference("changelog");
        if(replylist != null){
            Intent i = new Intent(Intent.ACTION_MAIN);
            i.setClass(this, Changelog.class);
           replylist.setIntent(i);
        }

        replylist = this.findPreference("gcm_prefs");
        if(replylist != null){
            Intent i = new Intent(Intent.ACTION_MAIN);
            i.setClass(this, PushMessageSetup.class);
          replylist.setIntent(i);
        }

        replylist = this.findPreference("version");
        replylist.setSummary(getAppVersion(this));

        replylist = this.findPreference("sendfeedback");
        replylist.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
                public boolean onPreferenceClick(Preference p){
                    Preferences.sendDebugEmail(p.getContext());
                    return true;
                }});

        //disable the "Consume SMS" option if the build is too low
        //NB: there's no code to act on this, since the abortBroadcast() 
        // call will not break anything when called in < 1.6
        Log.d("BUILDVERSION", Build.VERSION.SDK);
        if(Integer.valueOf(Build.VERSION.SDK) <= Integer.valueOf(3)){
            CheckBoxPreference csp = (CheckBoxPreference) this.findPreference("consume_sms_message");
            csp.setChecked(false);
            csp.setEnabled(false);
        }
    }

    /** Fires the intent to send an email with some debugging info.
     */
    public static void sendDebugEmail(Context context){
        Intent send = new Intent(Intent.ACTION_SEND);
        send.setType("message/rfc822");
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
        catch(Exception e){}
        return version;
    }
    public static String getDebugMessageBody(Context context){
        //Put some useful debug data in here.
        return "\n" +
            "** System Info:\n" + 
            "Android Version: " + Build.VERSION.RELEASE + "\n" + 
            "Device: " + Build.MODEL + "\n" + 
            "Build Info: " + Build.FINGERPRINT + "\n" + 
            "** App Info:\n" + 
            "App Version: " + getAppVersion(context) + "\n";
    }

}

