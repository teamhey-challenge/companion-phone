#!/bin/bash
# usage: emu.sh <name>  -> screenshot + uiautomator dump into evidence/PhoneA11y/dump
A="ssh goqual-dev@darren-mbp-16 /Volumes/Data/Sdk/android/platform-tools/adb -s emulator-5604"
D=$(dirname "$0")/dump; mkdir -p $D
$A shell uiautomator dump /sdcard/w.xml >/dev/null 2>&1
$A exec-out cat /sdcard/w.xml > $D/$1.xml
$A exec-out screencap -p > $D/$1.png
