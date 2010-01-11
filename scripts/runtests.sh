#!/bin/bash
ant reinstall
cd tests && ant reinstall
adb shell am instrument -w org.nerdcircus.android.klaxon.tests/android.test.InstrumentationTestRunner
