package com.jarvis.mobile.assistant;

import android.Manifest;
import android.app.KeyguardManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Build;
import android.os.Bundle;
import android.os.SystemClock;
import android.service.voice.VoiceInteractionSession;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.jarvis.brain.AdaptiveEndpointingPolicy;
import com.jarvis.brain.AssistantSurfaceState;
import com.jarvis.brain.RuntimeSurfaceAction;
import com.jarvis.brain.RuntimeSurfacePresentation;
import com.jarvis.mobile.MainActivity;
import com.jarvis.mobile.R;
import com.jarvis.mobile.brain.AndroidBrainRuntime;

import java.util.ArrayList;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Supplier;

public class JarvisVoiceSession extends VoiceInteractionSession implements TextToSpeech.OnInitListener {
    private static final long CONVERSATION_WINDOW_MILLIS = 10 * 60 * 1000L;
    private static final long NEXT_LISTEN_DELAY_MILLIS = 180L;
    private static final String TEST_TAG = "JARVIS_ASSISTANT_TEST";
    private static final String SHARED_BRAIN_TAG = "JARVIS_SHARED_BRAIN_ACTIVE";
    private static final String RUNTIME_FAILURE_TAG = "JARVIS_RUNTIME_FAILURE";
    private static final String VOICE_RECOGNIZER_TAG = "JARVIS_VOICE_RECOGNIZER";
    private static final String TEST_COMMAND_EXTRA = "jarvis_test_command";

    private final AdaptiveEndpointingPolicy endpointing = new AdaptiveEndpointingPolicy();
    private final AndroidAecBargeInMonitor bargeInMonitor;
    private final ExecutorService brainExecutor = Executors.newSingleThreadExecutor();
    private AndroidBrainRuntime brain;
    private SpeechRecognizer speechRecognizer;
    private TextToSpeech textToSpeech;
    private SharedPreferences preferences;
    private FrameLayout background;
    private TextView core;
    private TextView output;
    private Button primaryButton;
    private Button cancelButton;
    private boolean viewReady;
    private boolean sessionVisible;
    private boolean autoListenTriggered;
    private boolean resumeAfterSpeech;
    private boolean destroyed;
    private long conversationDeadlineElapsedRealtime;
    private long sessionGeneration;
    private long recognitionGeneration;
    private long speechGeneration;
    private long listenScheduleGeneration;
    private volatile String activeUtteranceId = "";
    private String lastCommand = "";
    private String lastPartial = "";

    public JarvisVoiceSession(Context context) {
        super(context);
        bargeInMonitor = new AndroidAecBargeInMonitor(context);
    }

    @Override public void onCreate() {
        super.onCreate();
        destroyed = false;
        preferences = getContext().getSharedPreferences("jarvis_shell", Context.MODE_PRIVATE);
        brain = new AndroidBrainRuntime(getContext());
        textToSpeech = new TextToSpeech(getContext(), this);
    }

    @Override public View onCreateContentView() {
        Log.i(TEST_TAG, "JARVIS_ASSISTANT_READY");
        FrameLayout root = new FrameLayout(getContext());
        final int horizontalPadding = dp(12);
        final int topPadding = dp(12);
        final int bottomPadding = dp(10);
        root.setPadding(horizontalPadding, topPadding, horizontalPadding, bottomPadding);
        root.setOnApplyWindowInsetsListener((v, insets) -> {
            int navigationBottom = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R
                    ? insets.getInsets(WindowInsets.Type.navigationBars()).bottom
                    : insets.getSystemWindowInsetBottom();
            v.setPadding(horizontalPadding, topPadding, horizontalPadding, bottomPadding + navigationBottom);
            return insets;
        });

        background = new FrameLayout(getContext());
        background.setBackgroundColor(color(R.color.jarvis_bg));
        root.addView(background, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        TextView brand = text("J A R V I S", 11, color(R.color.jarvis_cyan));
        brand.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        FrameLayout.LayoutParams brandParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.TOP | Gravity.CENTER_HORIZONTAL);
        brandParams.topMargin = dp(18);
        root.addView(brand, brandParams);

        core = text("◉", 64, color(R.color.jarvis_cyan));
        core.setGravity(Gravity.CENTER);
        FrameLayout.LayoutParams coreParams = new FrameLayout.LayoutParams(
                dp(120), dp(120), Gravity.CENTER_HORIZONTAL | Gravity.TOP);
        coreParams.topMargin = dp(42);
        root.addView(core, coreParams);

        output = text("Listening…", 16, Color.WHITE);
        output.setGravity(Gravity.CENTER);
        output.setPadding(dp(18), dp(12), dp(18), dp(12));
        output.setBackgroundColor(color(R.color.jarvis_bg_panel));
        FrameLayout.LayoutParams outputParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        outputParams.bottomMargin = dp(112);
        root.addView(output, outputParams);

        LinearLayout buttons = new LinearLayout(getContext());
        buttons.setGravity(Gravity.CENTER);
        Button listen = button("LISTEN");
        listen.setOnClickListener(v -> interruptSpeechAndListen());
        buttons.addView(listen);

        primaryButton = button("APPROVE");
        primaryButton.setContentDescription("JARVIS APPROVE action");
        primaryButton.setVisibility(View.GONE);
        primaryButton.setOnClickListener(v -> submitBrainWork(brain::approvePresentation));
        buttons.addView(primaryButton);

        cancelButton = button("NOT YET");
        cancelButton.setContentDescription("JARVIS CANCEL action");
        cancelButton.setVisibility(View.GONE);
        cancelButton.setOnClickListener(v -> submitBrainWork(brain::cancelPresentation));
        buttons.addView(cancelButton);

        Button open = button("OPEN FULL JARVIS");
        open.setOnClickListener(v -> getContext().startActivity(
                new Intent(getContext(), MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)));
        buttons.addView(open);

        FrameLayout.LayoutParams buttonParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        buttonParams.bottomMargin = dp(10);
        root.addView(buttons, buttonParams);

        viewReady = true;
        root.postDelayed(this::triggerAutoListen, NEXT_LISTEN_DELAY_MILLIS);
        return root;
    }

    @Override public void onShow(Bundle args, int flags) {
        super.onShow(args, flags);
        if (!lockScreenAssistantAllowed()) {
            Log.i(TEST_TAG, "JARVIS_LOCK_SCREEN_BLOCKED");
            sessionVisible = false;
            hide();
            return;
        }
        sessionGeneration++;
        sessionVisible = true;
        beginConversationWindowIfNeeded();
        applyVoicePreferences();
        Log.i(TEST_TAG, "JARVIS_OVERLAY_SESSION_SHOWN");
        String testCommand = debugTestCommand(args);
        if (viewReady && output != null && !testCommand.isBlank()) {
            autoListenTriggered = true;
            output.post(() -> {
                if (sessionVisible) execute(testCommand, 1.0);
            });
        } else if (viewReady && output != null) {
            output.postDelayed(this::triggerAutoListen, 120);
        }
    }

    private boolean lockScreenAssistantAllowed() {
        boolean enabled = preferences == null || preferences.getBoolean("lock_screen_assistant_enabled", true);
        if (enabled) return true;
        KeyguardManager keyguard = (KeyguardManager) getContext().getSystemService(Context.KEYGUARD_SERVICE);
        return keyguard == null || !keyguard.isDeviceLocked();
    }

    private String debugTestCommand(Bundle args) {
        if ((getContext().getApplicationInfo().flags & ApplicationInfo.FLAG_DEBUGGABLE) == 0 || args == null) return "";
        String command = args.getString(TEST_COMMAND_EXTRA, "");
        return command == null ? "" : command.trim();
    }

    @Override public void onHide() {
        sessionGeneration++;
        recognitionGeneration++;
        sessionVisible = false;
        autoListenTriggered = false;
        resumeAfterSpeech = false;
        conversationDeadlineElapsedRealtime = 0L;
        invalidateScheduledListen();
        bargeInMonitor.stop();
        releaseSpeechRecognizerSafely();
        invalidateSpeechCallback();
        if (textToSpeech != null) textToSpeech.stop();
        setActive(false);
        super.onHide();
    }

    private void beginConversationWindowIfNeeded() {
        long now = SystemClock.elapsedRealtime();
        if (conversationDeadlineElapsedRealtime <= now) conversationDeadlineElapsedRealtime = now + CONVERSATION_WINDOW_MILLIS;
    }

    private void interruptSpeechAndListen() {
        beginConversationWindowIfNeeded();
        resumeAfterSpeech = false;
        invalidateScheduledListen();
        bargeInMonitor.stop();
        invalidateSpeechCallback();
        if (textToSpeech != null) textToSpeech.stop();
        startListening();
    }

    private void handleHandsFreeBargeIn() {
        TextView surface = output;
        if (surface == null) return;
        surface.post(() -> {
            if (!conversationWindowOpen()) return;
            resumeAfterSpeech = false;
            invalidateScheduledListen();
            bargeInMonitor.stop();
            invalidateSpeechCallback();
            if (textToSpeech != null) textToSpeech.stop();
            startListening();
        });
    }

    private boolean conversationWindowOpen() {
        return sessionVisible && SystemClock.elapsedRealtime() < conversationDeadlineElapsedRealtime;
    }

    private void triggerAutoListen() {
        if (autoListenTriggered || !sessionVisible) return;
        autoListenTriggered = true;
        beginConversationWindowIfNeeded();
        startListening();
    }

    private void invalidateScheduledListen() {
        listenScheduleGeneration++;
    }

    private void scheduleNextListen() {
        long scheduledGeneration = ++listenScheduleGeneration;
        if (!viewReady || !conversationWindowOpen() || output == null) {
            setActive(false);
            return;
        }
        output.postDelayed(() -> {
            if (scheduledGeneration != listenScheduleGeneration) return;
            if (conversationWindowOpen()) startListening();
            else setActive(false);
        }, NEXT_LISTEN_DELAY_MILLIS);
    }

    private void startListening() {
        invalidateScheduledListen();
        bargeInMonitor.stop();
        if (!conversationWindowOpen()) {
            if (output != null) output.setText("Conversation paused. Tap LISTEN when you want me again.");
            setActive(false);
            return;
        }
        if (getContext().checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            output.setText("Open JARVIS once and grant Microphone permission.");
            setActive(false);
            return;
        }
        if (!SpeechRecognizer.isRecognitionAvailable(getContext())) {
            output.setText("Android speech recognition is unavailable.");
            setActive(false);
            return;
        }
        lastPartial = "";
        long listeningGeneration = ++recognitionGeneration;
        try {
            if (speechRecognizer != null) speechRecognizer.destroy();
            speechRecognizer = Build.VERSION.SDK_INT >= 31 && SpeechRecognizer.isOnDeviceRecognitionAvailable(getContext())
                    ? SpeechRecognizer.createOnDeviceSpeechRecognizer(getContext())
                    : SpeechRecognizer.createSpeechRecognizer(getContext());
            speechRecognizer.setRecognitionListener(new RecognitionListener() {
                private boolean terminalDelivered;

                private boolean stale() {
                    return listeningGeneration != recognitionGeneration || !sessionVisible || terminalDelivered;
                }

                private boolean claimTerminal() {
                    if (stale()) return false;
                    terminalDelivered = true;
                    return true;
                }

                @Override public void onReadyForSpeech(Bundle params) {
                    if (stale()) return;
                    output.setText("Listening…");
                    setActive(true);
                }
                @Override public void onBeginningOfSpeech() {
                    if (stale()) return;
                    invalidateSpeechCallback();
                    if (textToSpeech != null) textToSpeech.stop();
                    resumeAfterSpeech = false;
                    output.setText("I’m listening.");
                }
                @Override public void onRmsChanged(float rmsdB) { }
                @Override public void onBufferReceived(byte[] buffer) { }
                @Override public void onEndOfSpeech() {
                    if (stale()) return;
                    output.setText("Thinking…");
                }
                @Override public void onError(int error) {
                    if (!claimTerminal()) return;
                    output.setText(error == SpeechRecognizer.ERROR_NO_MATCH || error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT
                            ? "I didn’t catch that. I’m still listening."
                            : "Listening paused briefly; I’ll reopen it.");
                    setActive(false);
                    scheduleNextListen();
                }
                @Override public void onResults(Bundle results) {
                    if (!claimTerminal()) return;
                    ArrayList<String> matches = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                    if (matches == null || matches.isEmpty()) {
                        output.setText("I didn’t catch that.");
                        setActive(false);
                        scheduleNextListen();
                        return;
                    }
                    float[] scores = results.getFloatArray(SpeechRecognizer.CONFIDENCE_SCORES);
                    double confidence = scores != null && scores.length > 0 && scores[0] >= 0.0f ? Math.min(1.0, scores[0]) : 0.0;
                    execute(matches.get(0), confidence);
                }
                @Override public void onPartialResults(Bundle partialResults) {
                    if (stale()) return;
                    ArrayList<String> partial = partialResults.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                    if (partial != null && !partial.isEmpty()) {
                        lastPartial = partial.get(0);
                        output.setText(lastPartial);
                        Log.d("JARVIS_ENDPOINTING", "partial completeSilenceHintMs=" + endpointing.completeSilenceMillis(lastPartial));
                    }
                }
                @Override public void onEvent(int eventType, Bundle params) { }
            });
            Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
            intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
            intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, configuredLanguage().toLanguageTag());
            intent.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true);
            intent.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, endpointing.minimumUtteranceMillis());
            intent.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, endpointing.possiblyCompleteSilenceMillis(""));
            intent.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, endpointing.completeSilenceMillis(""));
            speechRecognizer.startListening(intent);
        } catch (RuntimeException recognitionFailure) {
            recoverRecognitionStartFailure(recognitionFailure);
        }
    }

    private void releaseSpeechRecognizerSafely() {
        SpeechRecognizer recognizer = speechRecognizer;
        speechRecognizer = null;
        if (recognizer == null) return;
        try { recognizer.cancel(); } catch (RuntimeException cleanupFailure) {
            Log.w(VOICE_RECOGNIZER_TAG, "Active recognizer cancel failed during lifecycle cleanup", cleanupFailure);
        }
        try { recognizer.destroy(); } catch (RuntimeException cleanupFailure) {
            Log.w(VOICE_RECOGNIZER_TAG, "Active recognizer destroy failed during lifecycle cleanup", cleanupFailure);
        }
    }

    private void recoverRecognitionStartFailure(RuntimeException recognitionFailure) {
        Log.w(VOICE_RECOGNIZER_TAG, "Active recognizer failed to start; retrying", recognitionFailure);
        recognitionGeneration++;
        SpeechRecognizer failedRecognizer = speechRecognizer;
        speechRecognizer = null;
        if (failedRecognizer != null) {
            try { failedRecognizer.cancel(); } catch (RuntimeException ignored) { }
            try { failedRecognizer.destroy(); } catch (RuntimeException ignored) { }
        }
        setActive(false);
        if (output != null) output.setText("Listening paused briefly; I’ll reopen it.");
        scheduleNextListen();
    }

    private void execute(String command, double confidence) {
        String submittedCommand = command == null ? "" : command.trim();
        if (isConversationEndCommand(submittedCommand)) conversationDeadlineElapsedRealtime = 0L;
        lastCommand = submittedCommand;
        output.setText("YOU: " + lastCommand + "\n\nJARVIS: Thinking…");
        submitBrainWork(() -> brain.handlePresentation(submittedCommand, confidence));
    }

    private void submitBrainWork(Supplier<RuntimeSurfacePresentation> work) {
        long submittedGeneration = sessionGeneration;
        brainExecutor.execute(() -> {
            RuntimeSurfacePresentation presentation = safeBrainWork(work);
            if (output != null) output.post(() -> {
                if (!sessionVisible || submittedGeneration != sessionGeneration) return;
                deliver(presentation);
            });
        });
    }

    private RuntimeSurfacePresentation safeBrainWork(Supplier<RuntimeSurfacePresentation> work) {
        try {
            return work.get();
        } catch (RuntimeException failure) {
            Log.e(RUNTIME_FAILURE_TAG, "Unexpected shared brain runtime failure", failure);
            return new RuntimeSurfacePresentation(AssistantSurfaceState.ERROR, "I hit an unexpected problem while handling that.", RUNTIME_FAILURE_TAG, RuntimeSurfaceAction.NONE, RuntimeSurfaceAction.NONE);
        }
    }

    private void deliver(RuntimeSurfacePresentation presentation) {
        Log.i(SHARED_BRAIN_TAG, "state=" + presentation.state());
        String text = presentation.text();
        String detail = presentation.detail();
        String rendered = text;
        if (!detail.isBlank() && !detail.equals(text)) rendered += "\n\n" + detail;
        output.setText((lastCommand.isBlank() ? "" : "YOU: " + lastCommand + "\n\n") + "JARVIS: " + rendered);

        boolean approval = presentation.state() == AssistantSurfaceState.AWAITING_APPROVAL;
        boolean recovery = presentation.state() == AssistantSurfaceState.NEEDS_INPUT;
        primaryButton.setVisibility(approval || recovery ? View.VISIBLE : View.GONE);
        primaryButton.setText(recovery ? "RETRY" : "APPROVE");
        primaryButton.setContentDescription(recovery ? "JARVIS RETRY action" : "JARVIS APPROVE action");
        primaryButton.setOnClickListener(v -> submitBrainWork(recovery ? brain::retryPresentation : brain::approvePresentation));
        cancelButton.setContentDescription("JARVIS CANCEL action");
        cancelButton.setVisibility(approval || recovery ? View.VISIBLE : View.GONE);

        invalidateScheduledListen();
        bargeInMonitor.stop();
        resumeAfterSpeech = conversationWindowOpen();
        if (!text.isBlank() && textToSpeech != null && voiceEnabled()) {
            applyVoicePreferences();
            String utteranceId = beginSpeechCallback();
            if (speakResponseSafely(text, utteranceId)) bargeInMonitor.start(this::handleHandsFreeBargeIn);
            else {
                invalidateSpeechCallback();
                if (resumeAfterSpeech) { resumeAfterSpeech = false; scheduleNextListen(); }
            }
        } else if (resumeAfterSpeech) {
            resumeAfterSpeech = false;
            scheduleNextListen();
        }
        setActive(false);
    }

    private boolean speakResponseSafely(String text, String utteranceId) {
        TextToSpeech engine = textToSpeech;
        if (engine == null) return false;
        try {
            return engine.speak(text, TextToSpeech.QUEUE_FLUSH, null, utteranceId) == TextToSpeech.SUCCESS;
        } catch (RuntimeException speechFailure) {
            Log.w(VOICE_RECOGNIZER_TAG, "TTS playback failed; continuing without spoken output", speechFailure);
            return false;
        }
    }

    private String beginSpeechCallback() {
        speechGeneration++;
        activeUtteranceId = "jarvis-session-" + speechGeneration;
        return activeUtteranceId;
    }

    private void invalidateSpeechCallback() {
        speechGeneration++;
        activeUtteranceId = "";
    }

    private boolean isCurrentSpeechCallback(String utteranceId) {
        return utteranceId != null && !utteranceId.isBlank() && utteranceId.equals(activeUtteranceId);
    }

    private void finishSpeechCallback(String utteranceId) {
        if (!isCurrentSpeechCallback(utteranceId)) return;
        invalidateSpeechCallback();
        bargeInMonitor.stop();
        if (!resumeAfterSpeech) return;
        resumeAfterSpeech = false;
        scheduleNextListen();
    }

    private boolean voiceEnabled() {
        return preferences == null || preferences.getBoolean("voice_enabled", true);
    }

    private void applyVoicePreferences() {
        if (textToSpeech == null) return;
        textToSpeech.setLanguage(configuredLanguage());
        float rate = preferences == null ? 1.0f : preferences.getFloat("voice_rate", 1.0f);
        if (rate < 0.5f) rate = 0.5f;
        if (rate > 1.5f) rate = 1.5f;
        textToSpeech.setSpeechRate(rate);
    }

    private Locale configuredLanguage() {
        String tag = preferences == null ? "system" : preferences.getString("language", "system");
        if (tag == null || tag.isBlank() || "system".equalsIgnoreCase(tag)) return Locale.getDefault();
        Locale configured = Locale.forLanguageTag(tag);
        return configured.getLanguage().isBlank() ? Locale.getDefault() : configured;
    }

    private static boolean isConversationEndCommand(String command) {
        if (command == null) return false;
        String normalized = command.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9' ]", " ").replaceAll("\\s+", " ").trim();
        return normalized.matches("(?:go to )?sleep|stop listening|that's all|that is all|thanks jarvis|thank you jarvis");
    }

    private void setActive(boolean active) {
        if (core != null) {
            core.setText(active ? "◎" : "◉");
            core.setTextColor(active ? Color.WHITE : color(R.color.jarvis_cyan));
        }
        if (background != null) background.setBackgroundColor(active ? color(R.color.jarvis_bg_panel_raised) : color(R.color.jarvis_bg));
    }

    private TextView text(String value, float size, int color) {
        TextView view = new TextView(getContext());
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color);
        return view;
    }

    private Button button(String label) {
        Button button = new Button(getContext());
        button.setText(label);
        button.setTextColor(color(R.color.jarvis_cyan));
        button.setTextSize(11);
        button.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        button.setBackgroundColor(color(R.color.jarvis_bg_panel));
        return button;
    }

    private int color(int resourceId) { return getContext().getColor(resourceId); }
    private int dp(int value) { return Math.round(value * getContext().getResources().getDisplayMetrics().density); }

    private void releaseTextToSpeechSafely() {
        TextToSpeech engine = textToSpeech;
        textToSpeech = null;
        if (engine == null) return;
        try { engine.stop(); } catch (RuntimeException cleanupFailure) {
            Log.w(VOICE_RECOGNIZER_TAG, "TTS stop failed during voice-session cleanup", cleanupFailure);
        }
        try { engine.shutdown(); } catch (RuntimeException cleanupFailure) {
            Log.w(VOICE_RECOGNIZER_TAG, "TTS shutdown failed during voice-session cleanup", cleanupFailure);
        }
    }

    @Override public void onInit(int status) {
        if (destroyed) return;
        if (status == TextToSpeech.SUCCESS && textToSpeech != null) {
            applyVoicePreferences();
            textToSpeech.setOnUtteranceProgressListener(new UtteranceProgressListener() {
                @Override public void onStart(String utteranceId) {
                    if (!isCurrentSpeechCallback(utteranceId)) return;
                }
                @Override public void onDone(String utteranceId) {
                    if (output != null) output.post(() -> finishSpeechCallback(utteranceId));
                }
                @Override public void onError(String utteranceId) {
                    if (output != null) output.post(() -> finishSpeechCallback(utteranceId));
                }
            });
        }
    }

    @Override public void onDestroy() {
        destroyed = true;
        sessionGeneration++;
        recognitionGeneration++;
        sessionVisible = false;
        resumeAfterSpeech = false;
        conversationDeadlineElapsedRealtime = 0L;
        invalidateScheduledListen();
        bargeInMonitor.stop();
        brainExecutor.shutdownNow();
        releaseSpeechRecognizerSafely();
        invalidateSpeechCallback();
        releaseTextToSpeechSafely();
        super.onDestroy();
    }
}
