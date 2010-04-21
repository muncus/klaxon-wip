package org.nerdcircus.android.klaxon.tests.pageparser;

import android.test.ActivityInstrumentationTestCase2;
import android.test.InstrumentationTestCase;
import android.os.Bundle;
import junit.framework.TestCase;

import org.nerdcircus.android.klaxon.Alert;
import org.nerdcircus.android.klaxon.pageparser.LabeledFields;

public class LabeledFieldTest extends TestCase {

    public void testMessage(){
        String message = "Msg:alert body";
        Alert expected = new Alert();
        expected.setFrom("1234");
        expected.setDisplayFrom("test@example.com");
        expected.setSubject("subject");
        expected.setBody("alert body");
        Alert observed = (new LabeledFields()).parse("1234", "subject", message);
        assertEquals(expected.getBody(), observed.getBody());
    }

}
