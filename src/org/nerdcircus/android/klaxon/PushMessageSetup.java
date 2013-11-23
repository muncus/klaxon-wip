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
import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.AsyncTask;
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

import java.util.prefs.PreferenceChangeListener;

public class PushMessageSetup extends PreferenceActivity implements OnSharedPreferenceChangeListener {
    
    private final String TAG = "GCMSetup";
    private final int RC_ACCOUNTPICKER = 1;

    private GcmHelper mHelper;
    private Handler mHandler;
    private Context mContext;

    final Runnable mUpdateC2dmPrefs = new Runnable() {
        public void run() {
            updateC2dmPrefs();
        }
    };


    protected void onActivityResult(int requestCode, int resultCode, Intent data){
      super.onActivityResult(requestCode, resultCode, data);
      if(requestCode == RC_ACCOUNTPICKER){
        if(resultCode == Activity.RESULT_OK){
          String accountName = data.getStringExtra(AccountManager.KEY_ACCOUNT_NAME);
          SharedPreferences.Editor ed =  PreferenceManager.getDefaultSharedPreferences(this).edit();
          Log.d(TAG, "setting pref to " + accountName);
          ed.putString("c2dm_register_account", accountName);
          ed.commit();
        } else if (resultCode == Activity.RESULT_CANCELED){
          Log.d(TAG, "account picking cancelled.");
          SharedPreferences.Editor ed =  PreferenceManager.getDefaultSharedPreferences(this).edit();
          ed.putString("c2dm_register_account", "");
          ed.commit();
        }
      }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mContext = (Context)this;
        mHandler = new Handler();
        mHelper = new GcmHelper(this);

        Intent i = this.getIntent();
        if( i.getData() != null){
          Log.d(TAG, i.getData().toString());
          if ( i.getData().toString().contains("org.nerdcircus.android.klaxon/gcmsetup") ){
            // Set up the gcm prefs, based on the URI params.
            String sender_id = i.getData().getQueryParameter("sender");
            String url = i.getData().getQueryParameter("url");
            String user = i.getData().getQueryParameter("user");

              Log.d(TAG, "This should be where magic happens.");
            // Set the sender and url preferences here.
            SharedPreferences.Editor ed =  PreferenceManager.getDefaultSharedPreferences(this).edit();
            ed.putString("c2dm_sender", sender_id);
            ed.putString("c2dm_register_url", url);
            ed.putString("c2dm_register_account", user);

              // We are registering, so clear any existing token.
            //ed.putString("c2dm_token", "");
            ed.commit();

            // Now, start registration!
            this.c2dmRegister(null);
          }
        }

        PreferenceManager.getDefaultSharedPreferences(this).registerOnSharedPreferenceChangeListener(this);


        // Load the preferences from an XML resource
        addPreferencesFromResource(R.xml.gcmsetup);

        Preference p = this.findPreference("send_test_message");

        i = new Intent(Intent.ACTION_VIEW);
        if(p != null){
            String base_url = PreferenceManager.getDefaultSharedPreferences(this).getString("c2dm_register_url", "");
           i.setData(Uri.parse(base_url + "/test"));
          p.setIntent(i);
        }

        p = this.findPreference("invalidate_token");
        if(p != null){
          p.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
                public boolean onPreferenceClick(Preference pref){
                       GcmHelper.invalidateAuthToken(pref.getContext());
                       return true;
                    }});
        }

        Preference accounts = this.findPreference("c2dm_register_account");
        if(accounts != null){
           accounts.setOnPreferenceClickListener( new Preference.OnPreferenceClickListener() {
             public boolean onPreferenceClick(Preference p){
                Intent acctpicker = AccountPicker.newChooseAccountIntent(null, null, new String[]{"com.google"},
                            false, null, null, null, null);
                   ((Activity)p.getContext()).startActivityForResult(acctpicker, RC_ACCOUNTPICKER);
                  return true;
                    }
            });
        }
        mHandler.post(mUpdateC2dmPrefs);
    }

    // Performs the actual registration
    public void c2dmRegister(View v) {
        SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(this);
        String c2dmSender = settings.getString("c2dm_sender", "");
        final Context context = getApplicationContext();
        final ProgressDialog pd;
        if (c2dmSender.equals("")) {
            CharSequence text = "Set sender id first.";
            int duration = Toast.LENGTH_LONG;
            Toast toast = Toast.makeText(context, text, duration);
            toast.show();
            return;
        }
        new RegisterWithDialogAsyncTask(this).execute(c2dmSender, "");
    }

    public void c2dmUnregister(View v) {
        SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(this);
        String regId = settings.getString("c2dm_token", "");
        if(!regId.isEmpty()){
            new UnregisterWithDialogAsyncTask(this).execute(regId);
        }
    }

    public void c2dmSendToken(View v) {
      final Intent emailIntent = new Intent(android.content.Intent.ACTION_SEND);
      SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(this);
      String c2dmToken = settings.getString("c2dm_token", "");
      if (! c2dmToken.equals("")) {
        emailIntent .setType("plain/text");
        emailIntent .putExtra(android.content.Intent.EXTRA_SUBJECT, "My C2DM token");
        emailIntent .putExtra(android.content.Intent.EXTRA_TEXT, c2dmToken);
        this.startActivity(Intent.createChooser(emailIntent, "Send mail..."));
      }
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


    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        if (key.equals("c2dm_token"))
            mHandler.post(mUpdateC2dmPrefs);
    }

    // Helper class for Registering with GCM and push messaging server, with progress dialog.
    class RegisterWithDialogAsyncTask extends AsyncTask<String, Void, Boolean> {
        ProgressDialog pd;
        Context context;

        public RegisterWithDialogAsyncTask(Context ct){
            context = ct;
        }

        @Override
        protected void onPreExecute(){
            //create progress dialog.
            pd = new ProgressDialog(mContext);
            pd.setTitle("Registering...");
            pd.setCancelable(false);
            pd.setIndeterminate(true);
            pd.show();
        }

        @Override
        protected Boolean doInBackground(String... args){
            mHelper.registerBothLegsBlocking(args[0]);
            return true;
        }

        @Override
        protected void onPostExecute(Boolean arg){
            if(pd!=null){
                pd.dismiss();
            }
        }
    }

    // Helper AsyncTask to unregister. Arg must be a GCM Registration ID.
    class UnregisterWithDialogAsyncTask extends AsyncTask<String, Void, Boolean> {
        ProgressDialog pd;
        Context context;

        public UnregisterWithDialogAsyncTask(Context ct){
            context = ct;
        }

        @Override
        protected void onPreExecute(){
            //create progress dialog.
            pd = new ProgressDialog(mContext);
            pd.setTitle("Un-Registering...");
            pd.setCancelable(false);
            pd.setIndeterminate(true);
            pd.show();
        }

        @Override
        protected Boolean doInBackground(String... args){
            mHelper.unregisterBothLegsBlocking(args[0]);
            return true;
        }

        @Override
        protected void onPostExecute(Boolean arg){
            if(pd!=null){
                pd.dismiss();
            }
        }
    }

}