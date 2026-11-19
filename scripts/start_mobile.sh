#!/bin/bash
set -euo pipefail
mkdir -p /workspace/evidence/android
adb start-server
emulator -avd ipz_phone -no-window -no-audio -no-boot-anim -gpu swiftshader_indirect -accel off -memory 1024 -cores 2 -skin 480x800 -dpi-device 160 -no-snapshot > /workspace/evidence/android/emulator.log 2>&1 &
exec appium --address 0.0.0.0 --port 4723 --log /workspace/evidence/android/appium.log
