#!/usr/bin/env bash
set -euo pipefail
rm -rf android
mkdir -p android
mkdir -p android
cat > android/README_ANDROID.md <<'JARVISEOF_4990357575239060180'
# JARVIS — Android-first architecture

The Android phone is now the primary JARVIS node.

## Framework choice

We are **not** trying to clone proprietary Bixby or Google Assistant code.

We are using the same *operating-system architecture pattern* through public Android APIs:

1. Android `ROLE_ASSISTANT`
2. `VoiceInteractionService`
3. native speech recognition / TTS for the first build
4. notification awareness
5. Android intents and explicit app capabilities
6. local SQLite durable memory
7. local event/salience system
8. replaceable local LLM provider
9. PocketPal/llama.cpp-style GGUF inference as the target language cortex

PocketPal AI is the preferred UI/inference foundation to fork because it is MIT licensed and
already provides on-device Android GGUF inference, model management, chat, TTS and a mature mobile UI.

The custom Java module in this directory is intentionally written mostly against platform Android
APIs so the JARVIS system layer is not welded to React Native or one model engine.

## What is already implemented here

- installable Android project source
- JARVIS UI shell
- voice input + TTS output
- request to become Android default Assistant
- `VoiceInteractionService`
- local SQLite memory
- tasks
- event log
- notification awareness
- salience engine
- permission policy
- Android action router
- local cortex interface
- rule-based offline fallback
- boot initialization
- zero paid API dependency

## Local model plan

The next integration is **not** to add Ollama to the phone.
The phone should call llama.cpp/llama.rn directly, following the same proven approach used by
PocketPal AI / Maid.

Suggested model classes depend on phone RAM:

- 4–6 GB: 1B–2B quantized model
- 8 GB: 3B–4B quantized model
- 12+ GB: 7B/8B may be practical depending on context and device

JARVIS should use the smallest model that can solve the current task and reserve larger inference
for difficult reasoning.

## Build

Open the `android/` directory in Android Studio and build the APK.

This environment did not contain Android SDK/Gradle, so an APK binary is not included in this archive.
The project is structured for Android Studio with compile/target SDK 36 and min SDK 29.

## Assistant role

After installation:

1. Open JARVIS.
2. Tap **Make JARVIS Default Assistant**.
3. Approve Android's Assistant-role dialog.
4. Enable notification awareness if desired.
5. Grant microphone permission.

The OEM controls which gestures/hotwords can invoke a third-party assistant. JARVIS can be the
selected Android Assistant, but the proprietary "Hey Google"/Bixby hotword pipeline is not ours.
A dedicated free local wake-word engine can be added later.
JARVISEOF_4990357575239060180
mkdir -p android
cat > android/README_TRANSPLANT.md <<'JARVISEOF_8886298923858099167'
# JARVIS Android V1 — Donor APK Transplant

This is a clean Android 16-targeted rebuild that uses selected visual resources from the uploaded legacy
`com.itsmylab.jarvis` APK as temporary private-beta donor assets. Its old DEX code, advertising SDKs,
trackers, and command engine are **not** included.

## V1 body

Reused temporary donor assets:
- reactor icon
- JARVIS normal/active sprites
- MK2/MK3 backgrounds
- temporary button graphics
- legacy JARVIS icon

Not reused:
- old DEX logic
- PocketSphinx binaries
- ad SDK DEX payloads
- tracking/analytics SDKs
- obsolete network code
- legacy licensing code

## Assistant popup

JARVIS implements Android's Assistant role + `VoiceInteractionService` + a custom
`VoiceInteractionSession`.

When invoked as the default assistant, Android owns the assistant window and JARVIS injects the
compact holographic panel into that session. It is therefore an assistant overlay rather than merely
launching the full app.

The HUD itself is programmatic and animates rings around the donor reactor asset so it can later be
replaced by a 3D renderer without rewriting the brain.

## Advanced hands

An optional `AccessibilityService` provides user-enabled agentic phone control:
- read accessibility-visible screen text
- locate/tap controls by label
- enter text into focused fields
- scroll
- Back/Home
- infer active package

This is intentionally opt-in and should be used with the JARVIS permission engine.

## Deterministic actions implemented

- open installed apps by name
- open URLs/settings/camera
- flashlight
- volume
- timers
- alarms
- web search
- read screen accessibility context
- tap visible control
- type into field
- back/home/scroll
- local memories
- local tasks
- notification observation

## Cortex

The architecture includes a free local cortex bridge targeting an OpenAI-compatible llama.cpp server
at `127.0.0.1:8080`.

This is an interim bridge so the Android shell and brain are not coupled to a model engine. The next
build step is to embed llama.cpp/llama.rn directly so no local server process is required.

On a 12 GB Galaxy S26, target model classes are:
- fast executive: ~3B/4B Q4 GGUF
- heavier reasoning: ~7B/8B Q4 GGUF loaded on demand

Do not count RAM Plus as physical model RAM.

## What makes this V1 more capable than a traditional assistant architecture

The differentiator is not a claim of universal benchmark superiority. It is the architecture:
- persistent local memory
- screen context through user-enabled Accessibility
- generic UI actions
- replaceable local LLM
- notification/event awareness
- deterministic tool layer
- assistant overlay
- permission boundary
- offline-first operation
- future specialist-agent routing

## Build status

This environment does not contain the Android SDK/NDK/Gradle toolchain, so this archive contains the
rewritten Android Studio project, not a signed APK binary.

Open `android/` in Android Studio and build `app-debug.apk`. The next engineering pass should merge
direct llama.cpp inference and then produce the signed beta APK.
JARVISEOF_8886298923858099167
mkdir -p android/app
cat > android/app/build.gradle <<'JARVISEOF_789137202360053280'
plugins {
    id 'com.android.application'
}

android {
    namespace 'com.jarvis.mobile'
    compileSdk 36

    defaultConfig {
        applicationId "com.jarvis.mobile"
        minSdk 29
        targetSdk 36
        versionCode 1
        versionName "0.1.0"
    }

    buildTypes {
        debug {
            debuggable true
        }
        release {
            minifyEnabled false
            shrinkResources false
        }
    }
}
JARVISEOF_789137202360053280
mkdir -p android/app/src/main
cat > android/app/src/main/AndroidManifest.xml <<'JARVISEOF_3238823459987615250'
<manifest xmlns:android="http://schemas.android.com/apk/res/android">
    <uses-permission android:name="android.permission.RECORD_AUDIO"/>
    <uses-permission android:name="android.permission.POST_NOTIFICATIONS"/>
    <uses-permission android:name="android.permission.RECEIVE_BOOT_COMPLETED"/>
    <uses-permission android:name="android.permission.VIBRATE"/>
    <uses-permission android:name="android.permission.INTERNET"/>
    <uses-permission android:name="android.permission.CAMERA"/>
    <uses-permission android:name="android.permission.FLASHLIGHT"/>
    <uses-permission android:name="android.permission.SET_ALARM"/>
    <uses-permission android:name="android.permission.READ_CONTACTS"/>
    <uses-permission android:name="android.permission.CALL_PHONE"/>
    <uses-permission android:name="android.permission.READ_CALENDAR"/>
    <uses-permission android:name="android.permission.WRITE_CALENDAR"/>

    <queries>
        <intent>
            <action android:name="android.intent.action.MAIN"/>
            <category android:name="android.intent.category.LAUNCHER"/>
        </intent>
    </queries>

    <application
        android:allowBackup="true"
        android:label="JARVIS"
        android:icon="@drawable/jarvis_widget_icon"
        android:theme="@style/AppTheme"
        android:supportsRtl="true">

        <activity
            android:name=".MainActivity"
            android:exported="true"
            android:launchMode="singleTop">
            <intent-filter>
                <action android:name="android.intent.action.MAIN"/>
                <category android:name="android.intent.category.LAUNCHER"/>
            </intent-filter>
            <intent-filter>
                <action android:name="android.intent.action.ASSIST"/>
                <category android:name="android.intent.category.DEFAULT"/>
            </intent-filter>
        </activity>

        <service
            android:name=".assistant.JarvisVoiceInteractionService"
            android:permission="android.permission.BIND_VOICE_INTERACTION"
            android:exported="true">
            <intent-filter>
                <action android:name="android.service.voice.VoiceInteractionService"/>
            </intent-filter>
            <meta-data
                android:name="android.voice_interaction"
                android:resource="@xml/voice_interaction_service"/>
        </service>

        <service
            android:name=".assistant.JarvisVoiceSessionService"
            android:permission="android.permission.BIND_VOICE_INTERACTION" android:exported="true"/>

        <service
            android:name=".events.JarvisNotificationListener"
            android:label="JARVIS Notification Awareness"
            android:permission="android.permission.BIND_NOTIFICATION_LISTENER_SERVICE"
            android:exported="true">
            <intent-filter>
                <action android:name="android.service.notification.NotificationListenerService"/>
            </intent-filter>
        </service>

        <service
            android:name=".hands.JarvisAccessibilityService"
            android:permission="android.permission.BIND_ACCESSIBILITY_SERVICE"
            android:exported="true"
            android:label="JARVIS Device Control">
            <intent-filter>
                <action android:name="android.accessibilityservice.AccessibilityService"/>
            </intent-filter>
            <meta-data
                android:name="android.accessibilityservice"
                android:resource="@xml/accessibility_service"/>
        </service>

        <receiver
            android:name=".events.BootReceiver"
            android:enabled="true"
            android:exported="true">
            <intent-filter>
                <action android:name="android.intent.action.BOOT_COMPLETED"/>
            </intent-filter>
        </receiver>
    </application>
</manifest>
JARVISEOF_3238823459987615250
mkdir -p android/app/src/main/java/com/jarvis/mobile
cat > android/app/src/main/java/com/jarvis/mobile/MainActivity.java <<'JARVISEOF_561950133379731899'
package com.jarvis.mobile;

import android.Manifest;
import android.app.*;
import android.app.role.RoleManager;
import android.content.*;
import android.content.pm.PackageManager;
import android.graphics.*;
import android.os.Bundle;
import android.provider.Settings;
import android.speech.*;
import android.speech.tts.TextToSpeech;
import android.view.*;
import android.widget.*;
import java.util.*;
import com.jarvis.mobile.brain.JarvisBrain;
import com.jarvis.mobile.ui.HudView;

public class MainActivity extends Activity implements TextToSpeech.OnInit