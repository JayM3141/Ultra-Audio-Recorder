#!/bin/bash
set -e

export JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64
export ANDROID_HOME=/home/ubuntu/android-sdk
export PATH=$PATH:$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/platform-tools

cd /home/ubuntu/UltraAudioRecorder

echo "Cleaning project..."
./gradlew clean

echo "Building Debug APK..."
./gradlew assembleDebug

if [ -f "app/build/outputs/apk/debug/app-debug.apk" ]; then
    echo "Build successful!"
    cp app/build/outputs/apk/debug/app-debug.apk /home/ubuntu/UltraAudioRecorder.apk
    echo "APK saved to /home/ubuntu/UltraAudioRecorder.apk"
else
    echo "Build failed!"
    exit 1
fi
