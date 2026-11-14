#!/bin/sh
# ROM "TV": builds the phone-remote daemon: static, 32-bit ARM (armv7, hard float). The Android TV 9
# base runs a 32-bit ARM kernel (its uImage says arch 2, ARM) and 32-bit userspace on the S905W.
set -e
cd "$(dirname "$0")"
arm-linux-gnueabihf-gcc -static -Os -s -Wall -march=armv7-a -mfpu=vfpv3-d16 -o tvremote tvremote.c
