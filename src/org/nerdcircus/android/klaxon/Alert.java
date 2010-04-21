package org.nerdcircus.android.klaxon;

/** Class representing an Alert Object
 * abstraction used to get from a received message to an item in PagerProvider
 */
public class Alert {
    private ContentValues cv;

    public Alert(){
        cv = new ContentValues();
    }

    // "raw" from address, for use in replying.
    public void setFrom(String from){
        cv.put(Pages.FROM, from);
    }
    public String getFrom(){
        return cv.getString(Pages.FROM);
    }

    // "DisplayName" analog - phone number, or email addr.
    public void setDisplayFrom(String from){
        cv.put(Pages.FROM_ADDR, from);
    }
    public String getDisplayFrom(){
        return cv.getString(Pages.FROM_ADDR);
    }

    // subject line of the alert
    public void setSubject(String from){
        cv.put(Pages.SUBJECT, from);
    }
    public String getSubject(){
        return cv.getString(Pages.SUBJECT);
    }

    // body of the alert.
    public void setBody(String body){
        cv.put(Pages.BODY, body);
    }
    public String getBody(){
        return cv.getString(Pages.BODY);
    }

    // used for inserting this alert into our contentprovider
    public ContentValues asContentValues(){
        return cv;
    }
}
