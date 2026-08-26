#!/usr/bin/env bash
set -euo pipefail
mkdir -p android/app/src/main/java/com/jarvis/mobile android/app/src/main/res/drawable android/app/src/main/res/values android/app/src/main/res/xml
cat > android/settings.gradle <<'EOF'
pluginManagement { repositories { google(); mavenCentral(); gradlePluginPortal() } }
dependencyResolutionManagement { repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS); repositories { google(); mavenCentral() } }
rootProject.name='JARVIS'; include ':app'
EOF
cat > android/build.gradle <<'EOF'
plugins { id 'com.android.application' version '8.10.1' apply false }
EOF
cat > android/gradle.properties <<'EOF'
android.useAndroidX=true
org.gradle.jvmargs=-Xmx2g -Dfile.encoding=UTF-8
EOF
cat > android/app/build.gradle <<'EOF'
plugins { id 'com.android.application' }
android { namespace 'com.jarvis.mobile'; compileSdk 36
 defaultConfig { applicationId 'com.jarvis.mobile'; minSdk 29; targetSdk 36; versionCode 11; versionName '0.1.1' }
 compileOptions { sourceCompatibility JavaVersion.VERSION_17; targetCompatibility JavaVersion.VERSION_17 }
}
EOF
cat > android/app/src/main/res/values/styles.xml <<'EOF'
<resources><style name="AppTheme" parent="android:style/Theme.Material.NoActionBar"><item name="android:fontFamily">sans</item><item name="android:colorAccent">#33D9FF</item><item name="android:navigationBarColor">#06131E</item><item name="android:windowLightStatusBar">false</item><item name="android:statusBarColor">#06131E</item></style></resources>
EOF
cat > android/app/src/main/res/values/strings.xml <<'EOF'
<resources><string name="app_name">JARVIS</string></resources>
EOF
cat > android/app/src/main/res/drawable/icon.xml <<'EOF'
<vector xmlns:android="http://schemas.android.com/apk/res/android" android:width="108dp" android:height="108dp" android:viewportWidth="108" android:viewportHeight="108"><path android:fillColor="#06131E" android:pathData="M0,0h108v108h-108z"/><path android:fillColor="#33D9FF" android:pathData="M54,10 A44,44 0,1 0,54 98 A44,44 0,1 0,54 10 M54,26 A28,28 0,1 1,54 82 A28,28 0,1 1,54 26 M54,42 A12,12 0,1 0,54 66 A12,12 0,1 0,54 42"/></vector>
EOF
cat > android/app/src/main/res/xml/voice_interaction_service.xml <<'EOF'
<voice-interaction-service xmlns:android="http://schemas.android.com/apk/res/android" android:sessionService="com.jarvis.mobile.JarvisVoiceSessionService" android:recognitionService="" android:supportsAssist="true" android:supportsLaunchVoiceAssistFromKeyguard="true" />
EOF
cat > android/app/src/main/res/xml/accessibility_service.xml <<'EOF'
<accessibility-service xmlns:android="http://schemas.android.com/apk/res/android" android:accessibilityEventTypes="typeAllMask" android:accessibilityFeedbackType="feedbackGeneric" android:canRetrieveWindowContent="true" android:canPerformGestures="true" android:notificationTimeout="100" />
EOF
cat > android/app/src/main/AndroidManifest.xml <<'EOF'
<manifest xmlns:android="http://schemas.android.com/apk/res/android"><uses-permission android:name="android.permission.RECORD_AUDIO"/><uses-permission android:name="android.permission.INTERNET"/><uses-permission android:name="android.permission.POST_NOTIFICATIONS"/><application android:theme="@style/AppTheme" android:label="JARVIS" android:icon="@drawable/icon"><activity android:name=".MainActivity" android:exported="true"><intent-filter><action android:name="android.intent.action.MAIN"/><category android:name="android.intent.category.LAUNCHER"/></intent-filter><intent-filter><action android:name="android.intent.action.ASSIST"/><category android:name="android.intent.category.DEFAULT"/></intent-filter></activity><service android:name=".JarvisVoiceInteractionService" android:permission="android.permission.BIND_VOICE_INTERACTION" android:exported="true"><intent-filter><action android:name="android.service.voice.VoiceInteractionService"/></intent-filter><meta-data android:name="android.voice_interaction" android:resource="@xml/voice_interaction_service"/></service><service android:name=".JarvisVoiceSessionService" android:permission="android.permission.BIND_VOICE_INTERACTION" android:exported="true"/><service android:name=".JarvisAccessibilityService" android:permission="android.permission.BIND_ACCESSIBILITY_SERVICE" android:exported="true"><intent-filter><action android:name="android.accessibilityservice.AccessibilityService"/></intent-filter><meta-data android:name="android.accessibilityservice" android:resource="@xml/accessibility_service"/></service></application></manifest>
EOF
cat > android/app/src/main/java/com/jarvis/mobile/MainActivity.java <<'EOF'
package com.jarvis.mobile;
import android.app.*;import android.os.*;import android.Manifest;import android.content.*;import android.provider.Settings;import android.graphics.Color;import android.view.*;import android.widget.*;import java.util.*;
public class MainActivity extends Activity{
 LinearLayout root; TextView status; public void onCreate(Bundle b){super.onCreate(b); if(Build.VERSION.SDK_INT>=23&&checkSelfPermission(Manifest.permission.RECORD_AUDIO)!=0)requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO},7); root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setGravity(Gravity.CENTER);root.setPadding(36,36,36,36);root.setBackgroundColor(Color.rgb(6,19,30)); TextView h=t("J.A.R.V.I.S.",34,Color.rgb(51,217,255));root.addView(h);root.addView(t("ANDROID PRIVATE BETA • V1.1",14,Color.LTGRAY)); status=t("SYSTEM READY\n\nThis is the first installable JARVIS chassis. Set it as the phone assistant, grant microphone access, and test the popup voice session before we transplant the larger brain.",18,Color.WHITE);root.addView(status);Button a=btn("MAKE JARVIS DEFAULT ASSISTANT");a.setOnClickListener(v->startActivity(new Intent(Settings.ACTION_VOICE_INPUT_SETTINGS)));root.addView(a);Button s=btn("OPEN ACCESSIBILITY / DEVICE CONTROL");s.setOnClickListener(v->startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)));root.addView(s);Button m=btn("TEST JARVIS VOICE");m.setOnClickListener(v->startActivity(new Intent(Intent.ACTION_ASSIST)));root.addView(m);setContentView(root);} TextView t(String x,int z,int c){TextView v=new TextView(this);v.setText(x);v.setTextSize(z);v.setTextColor(c);v.setGravity(17);v.setPadding(10,24,10,24);return v;}Button btn(String x){Button b=new Button(this);b.setText(x);b.setAllCaps(false);return b;}}
EOF
cat > android/app/src/main/java/com/jarvis/mobile/JarvisVoiceInteractionService.java <<'EOF'
package com.jarvis.mobile; import android.service.voice.VoiceInteractionService; public class JarvisVoiceInteractionService extends VoiceInteractionService { public void onReady(){super.onReady();} }
EOF
cat > android/app/src/main/java/com/jarvis/mobile/JarvisVoiceSessionService.java <<'EOF'
package com.jarvis.mobile; import android.service.voice.*; public class JarvisVoiceSessionService extends VoiceInteractionSessionService { public VoiceInteractionSession onNewSession(android.os.Bundle b){return new JarvisVoiceSession(this);} }
EOF
cat > android/app/src/main/java/com/jarvis/mobile/JarvisVoiceSession.java <<'EOF'
package com.jarvis.mobile; import android.service.voice.*;import android.content.*;import android.os.*;import android.speech.*;import android.graphics.*;import android.view.*;import android.widget.*;import java.util.*;
public class JarvisVoiceSession extends VoiceInteractionSession{TextView text,state;SpeechRecognizer sr;public JarvisVoiceSession(Context c){super(c);}public void onCreate(){super.onCreate();LinearLayout r=new LinearLayout(getContext());r.setOrientation(LinearLayout.VERTICAL);r.setPadding(30,30,30,30);r.setBackgroundColor(Color.rgb(5,18,29));state=new TextView(getContext());state.setText("JARVIS • READY");state.setTextColor(Color.rgb(51,217,255));state.setTextSize(20);r.addView(state);text=new TextView(getContext());text.setText("At your service.");text.setTextColor(Color.WHITE);text.setTextSize(18);text.setPadding(0,18,0,18);r.addView(text);Button b=new Button(getContext());b.setText("LISTEN");b.setOnClickListener(v->listen());r.addView(b);setContentView(r);}public void onShow(Bundle b,int f){super.onShow(b,f);listen();}void listen(){if(!SpeechRecognizer.isRecognitionAvailable(getContext())){text.setText("Speech recognition unavailable.");return;}if(sr!=null)sr.destroy();sr=SpeechRecognizer.createSpeechRecognizer(getContext());sr.setRecognitionListener(new RecognitionListener(){public void onReadyForSpeech(Bundle b){state.setText("JARVIS • LISTENING");}public void onBeginningOfSpeech(){}public void onRmsChanged(float f){}public void onBufferReceived(byte[]b){}public void onEndOfSpeech(){state.setText("JARVIS • PROCESSING");}public void onError(int e){state.setText("JARVIS • READY");text.setText("I didn't catch that.");}public void onResults(Bundle b){ArrayList<String>x=b.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);String q=x==null||x.isEmpty()?"":x.get(0);reply(q);}public void onPartialResults(Bundle b){}public void onEvent(int t,Bundle b){}});Intent i=new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);i.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);i.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS,4000L);sr.startListening(i);}void reply(String q){state.setText("JARVIS • ACTIVE");if(q.isBlank())text.setText("At your service.");else text.setText("I heard: “"+q+"”\n\nThe Android assistant chassis is responding. The predictive cortex and custom routine engine are the next transplant layer.");}public void onDestroy(){if(sr!=null)sr.destroy();super.onDestroy();}}
EOF
cat > android/app/src/main/java/com/jarvis/mobile/JarvisAccessibilityService.java <<'EOF'
package com.jarvis.mobile; import android.accessibilityservice.AccessibilityService;import android.view.accessibility.AccessibilityEvent; public class JarvisAccessibilityService extends AccessibilityService{public void onAccessibilityEvent(AccessibilityEvent e){}public void onInterrupt(){}}
EOF
