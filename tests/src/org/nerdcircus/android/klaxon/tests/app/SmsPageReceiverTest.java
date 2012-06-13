package org.nerdcircus.android.klaxon.tests.app;

import android.test.AndroidTestCase;
import android.test.InstrumentationTestCase;
import android.os.Bundle;

import org.nerdcircus.android.klaxon.Alert;
import org.nerdcircus.android.klaxon.SmsPageReceiver;

public class SmsPageReceiverTest extends AndroidTestCase {

    public void testSimplePage(){
        Alert a = new Alert();
        a.setFrom("1234");
        a.setDisplayFrom("1234");
        a.setSubject("subj");
        a.setBody("body");
        assertTrue(SmsPageReceiver.matchesPageCriteria(a.asContentValues(), "subj", true));
    }
    public void testMultipleTriggers(){
        Alert a = new Alert();
        a.setFrom("1234");
        a.setDisplayFrom("1234");
        a.setSubject("subj");
        a.setBody("body");
        // Match sender, with and without spaces.
        assertTrue(SmsPageReceiver.matchesPageCriteria(a.asContentValues(), "1234,foo,bar", false));
        assertTrue(SmsPageReceiver.matchesPageCriteria(a.asContentValues(), "1234, foo, bar", false));
        // Match body/subject, with and without spaces.
        assertTrue(SmsPageReceiver.matchesPageCriteria(a.asContentValues(), "subj,foo,bar", true));
        assertTrue(SmsPageReceiver.matchesPageCriteria(a.asContentValues(), "subj, foo, bar", true));
        assertTrue(SmsPageReceiver.matchesPageCriteria(a.asContentValues(), "foo,body,bar", true));
        assertTrue(SmsPageReceiver.matchesPageCriteria(a.asContentValues(), "body, foo, bar", true));
        // With trailing whitespace.
        assertTrue(SmsPageReceiver.matchesPageCriteria(a.asContentValues(), "foo, bar, body ", true));
    }
    
}
