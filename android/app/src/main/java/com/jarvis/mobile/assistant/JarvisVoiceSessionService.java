package com.jarvis.mobile.assistant; import android.service.voice.*; public class JarvisVoiceSessionService extends VoiceInteractionSessionService { public VoiceInteractionSession onNewSession(android.os.Bundle b){return new JarvisVoiceSession(this);} }

