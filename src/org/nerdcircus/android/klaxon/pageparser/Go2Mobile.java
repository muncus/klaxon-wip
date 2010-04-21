package org.nerdcircus.android.klaxon.pageparser;

import android.content.ContentValues;
import android.telephony.gsm.SmsMessage;
import android.util.Log;

import org.nerdcircus.android.klaxon.Alert;
import org.nerdcircus.android.klaxon.Pager.Pages;

public class Go2Mobile extends Standard {
    /* pageparser for go2mobile.com
     * basically just Standard, but with ':' as a linebreak
     *
     * Messages for this parser should look like this:
     * "sender@example.com:subject:body"
     */

    public static String TAG = "PageParser-Go2Mobile";

    public Alert parse(SmsMessage[] msgs){
        Alert a = super.parse(msgs);
        //XXX: this should probably just use the Alert
        return new Alert(doCleanup(a.asContentValues()));
    }

    public Alert parse(String from, String subj, String message_text){
        Alert a = super.parse(from, subj, message_text);
        return new Alert(doCleanup(a.asContentValues()));
    }


    protected ContentValues doCleanup(ContentValues cv){
        Log.d("G2M", "doing the Right Thing");
        cv = super.doCleanup(cv);
        cv = parseColonLineEndings(cv);
        return cv;
    }

    /*
     * Message Cleanup Functions
     * functions below are intended to "clean up" messages that may not parse correctly.
     * they should be called in the doCleanup() function above
     */

    private ContentValues parseColonLineEndings(ContentValues cv){
        String body = cv.getAsString(Pages.BODY);
        String[] fields = body.split(":", 3);
        if(fields.length < 3){
            return cv; //not enough fields. abort.
        }
        cv.put(Pages.FROM_ADDR, fields[0]);
        cv.put(Pages.SUBJECT, fields[1]);
        cv.put(Pages.BODY, fields[2]);
        return cv;
    }
}
