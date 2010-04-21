package org.nerdcircus.android.klaxon.pageparser;

import android.content.ContentValues;
import android.telephony.gsm.SmsMessage;
import android.util.Log;

import org.nerdcircus.android.klaxon.Alert;
import org.nerdcircus.android.klaxon.Pager.Pages;

public class Standard {
    /* The Standard Sms Parser
     *
     * Messages for this parser should look like this:
     * "sender@example.com\nsubject\nbody"
     * (\n may be \r\n as well)
     */

    public static String TAG = "PageParser-Standard";

    public static Alert parse(SmsMessage[] msgs){
        ContentValues cv = new ContentValues();
        cv.put(Pages.SERVICE_CENTER, msgs[0].getServiceCenterAddress());
        cv.put(Pages.SENDER, msgs[0].getOriginatingAddress());
        cv.put(Pages.SUBJECT, msgs[0].getPseudoSubject());
        cv.put(Pages.ACK_STATUS, 0);
        // FROM_ADDR will be either the email sender, or the same as SENDER above.
        cv.put(Pages.FROM_ADDR, msgs[0].getDisplayOriginatingAddress());
        cv.put(Pages.BODY, msgs[0].getDisplayMessageBody());

        return new Alert(cv);
    }

    public static Alert parse(String from, String subj, String message_text){
        ContentValues cv = new ContentValues();
        cv.put(Pages.SENDER, from);
        cv.put(Pages.FROM_ADDR, from);
        cv.put(Pages.SUBJECT, subj);
        cv.put(Pages.BODY, message_text);
        // sane defaults needed because i'm doing it wrong.
        cv.put(Pages.ACK_STATUS, 0);

        return new Alert(cv);
    }

    private ContentValues fixLineEndings(ContentValues cv){
        String body = cv.getAsString(Pages.BODY);
        // fix stupid line-endings.
        if(body.contains("\r")){
            Log.d(TAG, "Message contains \\r. fixing.");
            body = body.replaceAll("\r", "");
        }
        cv.put(Pages.BODY, body);
        return cv;
    }
}
