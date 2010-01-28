#!/bin/bash
ant install
cd tests && ant install
adb shell am instrument -w org.nerdcircus.android.klaxon.tests/android.test.InstrumentationTestRunner
