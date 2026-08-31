package com.jarvis.mobile;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.role.RoleManager;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.media.AudioManager;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.util.Log;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.TextView;

import com.jarvis.brain.AssistantSurfaceState;
import com.jarvis.brain.FullAppRuntimeViewState;
import com.jarvis.brain.RuntimeSurfaceAction;
import com.jarvis.brain.RuntimeSurfacePresentation;
import com.jarvis.mobile.assistant.JarvisVoiceInteractionService;
import com.jarvis.mobile.assistant.JarvisVoiceSessionService;
import com.jarvis.mobile.brain.AndroidBrainRuntime;
import com.jarvis.mobile.events.JarvisNotificationListener;
import com.jarvis.mobile.hands.JarvisAccessibilityService;
import com.jarvis.mobile.widgets.NotesWidget;
import com.jarvis.mobile.widgets.QuickActivationWidget;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Supplier;

/** Original Android shell backed by the shared JARVIS runtime. */
public class MainActivity extends Activity implements TextToSpeech.OnInitListener {
    private static final int PERMISSION_REQUEST = 70;
    private static final int ASSISTANT_ROLE_REQUEST = 71;
    private static final long PULSE_MS = 260L;
    private static final long FOLLOW_UP_DELAY_MS = 260L;
    private static final String SELF_TEST_TAG = "JARVIS_SELF_TEST";
    private static final String COMMAND_TEST_TAG = "JARVIS_COMMAND_TEST";
    private static final String UI_TEST_TAG = "JARVIS_UI_TEST";
    private static final String SHARED_BRAIN_TAG = "JARVIS_SHARED_BRAIN_ACTIVE";
    private static final String RUNTIME_FAILURE_TAG = "JARVIS_RUNTIME_FAILURE";

    private final android.os.Handler ui = new android.os.Handler(android.os.Looper.getMainLooper());
    private final ExecutorService brainExecutor = Executors.newSingleThreadExecutor();
    private final Runnable pulse = new Runnable() {
        @Override public void run() {
            pulseFrame = !pulseFrame;
            applyThemeFrame(pulseFrame);
            if (active) ui.postDelayed(this, PULSE_MS);
        }
    };

    private AndroidBrainRuntime runtime;
    private SpeechRecognizer speechRecognizer;
    private TextToSpeech textToSpeech;
    private ImageView background;
    private TextView status;
    private TextView modeStatus;
    private LinearLayout mediaPanel;
    private LinearLayout decisionPanel;
    private Button primaryActionButton;
    private Button secondaryActionButton;
    private RuntimeSurfaceAction currentPrimaryAction = RuntimeSurfaceAction.NONE;
    private RuntimeSurfaceAction currentSecondaryAction = RuntimeSurfaceAction.NONE;
    private boolean active;
    private boolean pulseFrame;
    private boolean destroyed;
    private boolean continuedConversation;
    private SharedPreferences preferences;
    private boolean commandTestMode;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);

        preferences = getSharedPreferences("jarvis_shell", MODE_PRIVATE);
        runtime = new AndroidBrainRuntime(this);
        textToSpeech = new TextToSpeech(this, this);
        buildCurrentShell();

        if (getIntent() != null && getIntent().getBooleanExtra("jarvis_self_test", false)) {
            ui.postDelayed(this::runEmbeddedSelfTest, 350L);
            return;
        }
        if ((getApplicationInfo().flags & ApplicationInfo.FLAG_DEBUGGABLE) != 0 && getIntent() != null &&
                getIntent().hasExtra("jarvis_test_command")) {
            commandTestMode = true;
            String testCommand = getIntent().getStringExtra("jarvis_test_command");
            ui.postDelayed(() -> runCommand(testCommand, 1.0), 350L);
            return;
        }
        if ((getApplicationInfo().flags & ApplicationInfo.FLAG_DEBUGGABLE) != 0) {
            Log.i(UI_TEST_TAG, "JARVIS_HOME_READY Original HUD " + profileGreeting());
        }

        requestRuntimePermissions();
        if (!preferences.getBoolean("introduced", false)) {
            preferences.edit().putBoolean("introduced", true).apply();
        }

        String action = getIntent() == null ? null : getIntent().getAction();
        if (Intent.ACTION_ASSIST.equals(action) || Intent.ACTION_VOICE_COMMAND.equals(action)) {
            ui.postDelayed(this::listen, 220L);
        }
    }

    @Override protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        String action = intent == null ? null : intent.getAction();
        if (Intent.ACTION_ASSIST.equals(action) || Intent.ACTION_VOICE_COMMAND.equals(action)) listen();
    }

    @Override protected void onStart() {
        super.onStart();
        submitRemoteResumeCheck();
    }

    private void submitRemoteResumeCheck() {
        if (runtime == null || destroyed || commandTestMode) return;
        Intent intent = getIntent();
        if (intent != null && intent.getBooleanExtra("jarvis_self_test", false)) return;
        brainExecutor.execute(() -> {
            try {
                Optional<RuntimeSurfacePresentation> resumed = runtime.resumeRemoteGoalPresentation();
                resumed.ifPresent(presentation -> ui.post(() -> deliverPresentation(presentation)));
            } catch (RuntimeException failure) {
                Log.w(RUNTIME_FAILURE_TAG, "Remote goal resume check unavailable", failure);
            }
        });
    }

    private void buildCurrentShell() {
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(getColor(R.color.jarvis_bg));

        background = new ImageView(this);
        background.setContentDescription("JARVIS interface background");
        root.addView(background, matchFrame());
        applyThemeFrame(false);

        TextView brand = hudText("J A R V I S", 13, getColor(R.color.jarvis_cyan));
        brand.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        FrameLayout.LayoutParams brandParams = wrapFrame(Gravity.TOP | Gravity.START);
        brandParams.setMargins(dp(18), dp(20), 0, 0);
        root.addView(brand, brandParams);

        modeStatus = hudText("", 10, getColor(R.color.jarvis_cyan));
        modeStatus.setBackgroundColor(getColor(R.color.jarvis_bg_panel));
        modeStatus.setPadding(dp(8), dp(5), dp(8), dp(5));
        modeStatus.setVisibility(View.GONE);
        FrameLayout.LayoutParams modeParams = wrapFrame(Gravity.TOP | Gravity.END);
        modeParams.setMargins(0, dp(16), dp(16), 0);
        root.addView(modeStatus, modeParams);

        TextView core = hudText("◉", 94, getColor(R.color.jarvis_cyan));
        core.setGravity(Gravity.CENTER);
        core.setContentDescription("Speak to JARVIS");
        core.setOnClickListener(v -> listen());
        core.setOnLongClickListener(v -> { showTypedCommand(); return true; });
        FrameLayout.LayoutParams coreParams = new FrameLayout.LayoutParams(
                dp(220), dp(220), Gravity.CENTER);
        root.addView(core, coreParams);

        status = hudText(profileGreeting(), 16, Color.WHITE);
        status.setGravity(Gravity.CENTER);
        status.setMaxLines(8);
        status.setPadding(dp(20), dp(14), dp(20), dp(14));
        status.setBackgroundColor(getColor(R.color.jarvis_bg_panel));
        status.setContentDescription("JARVIS status and response");
        FrameLayout.LayoutParams statusParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        statusParams.setMargins(dp(18), 0, dp(18), dp(118));
        root.addView(status, statusParams);

        decisionPanel = buildDecisionPanel();
        decisionPanel.setVisibility(View.GONE);
        FrameLayout.LayoutParams decisionParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(54), Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        decisionParams.setMargins(dp(18), 0, dp(18), dp(62));
        root.addView(decisionPanel, decisionParams);

        mediaPanel = buildMediaPanel();
        mediaPanel.setVisibility(View.GONE);
        FrameLayout.LayoutParams mediaParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(72), Gravity.BOTTOM);
        root.addView(mediaPanel, mediaParams);

        ImageButton menu = new ImageButton(this);
        menu.setImageResource(android.R.drawable.ic_menu_more);
        menu.setBackgroundColor(Color.TRANSPARENT);
        menu.setPadding(dp(8), dp(8), dp(8), dp(8));
        menu.setContentDescription("JARVIS menu");
        menu.setOnClickListener(this::showMenu);
        FrameLayout.LayoutParams menuParams = new FrameLayout.LayoutParams(dp(54), dp(54), Gravity.BOTTOM | Gravity.END);
        menuParams.setMargins(0, 0, dp(5), dp(5));
        root.addView(menu, menuParams);

        setContentView(root);
    }

    private LinearLayout buildDecisionPanel() {
        LinearLayout panel = new LinearLayout(this);
        panel.setGravity(Gravity.CENTER);
        panel.setBackgroundColor(getColor(R.color.jarvis_bg_panel));

        primaryActionButton = decisionButton();
        primaryActionButton.setOnClickListener(v -> runDecisionAction(true));
        panel.addView(primaryActionButton);

        secondaryActionButton = decisionButton();
        secondaryActionButton.setOnClickListener(v -> runDecisionAction(false));
        panel.addView(secondaryActionButton);
        return panel;
    }

    private Button decisionButton() {
        Button button = new Button(this);
        button.setTextColor(getColor(R.color.jarvis_cyan));
        button.setTextSize(11);
        button.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        button.setBackgroundColor(getColor(R.color.jarvis_bg_panel));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, dp(48), 1f);
        params.setMargins(dp(6), dp(3), dp(6), dp(3));
        button.setLayoutParams(params);
        return button;
    }

    private LinearLayout buildMediaPanel() {
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setGravity(Gravity.CENTER);
        panel.setBackgroundColor(getColor(R.color.jarvis_bg_panel));

        TextView title = hudText("Now playing ...", 12, Color.WHITE);
        title.setSingleLine(true);
        title.setGravity(Gravity.CENTER);
        panel.addView(title, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout controls = new LinearLayout(this);
        controls.setGravity(Gravity.CENTER);
        controls.addView(mediaButton(android.R.drawable.ic_media_previous, KeyEvent.KEYCODE_MEDIA_PREVIOUS, "Previous"));
        controls.addView(mediaButton(android.R.drawable.ic_media_play, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, "Play or pause"));
        controls.addView(mediaButton(android.R.drawable.ic_media_next, KeyEvent.KEYCODE_MEDIA_NEXT, "Next"));
        panel.addView(controls);
        return panel;
    }

    private ImageButton mediaButton(int drawable, int keyCode, String description) {
        ImageButton button = new ImageButton(this);
        button.setImageResource(drawable);
        button.setBackgroundColor(Color.TRANSPARENT);
        button.setContentDescription(description);
        button.setOnClickListener(v -> {
            AudioManager audio = (AudioManager) getSystemService(AUDIO_SERVICE);
            audio.dispatchMediaKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, keyCode));
            audio.dispatchMediaKeyEvent(new KeyEvent(KeyEvent.ACTION_UP, keyCode));
        });
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(dp(56), dp(40));
        params.setMargins(dp(8), 0, dp(8), 0);
        button.setLayoutParams(params);
        return button;
    }

    private void showMenu(View anchor) {
        PopupMenu popup = new PopupMenu(this, anchor);
        popup.getMenu().add(0, 1, 1, "Help & Features");
        popup.getMenu().add(0, 2, 2, "Notes & Memory");
        popup.getMenu().add(0, 3, 3, "Settings");
        popup.getMenu().add(0, 4, 4, "Make JARVIS Default Assistant");
        popup.getMenu().add(0, 5, 5, "Enable Notification Awareness");
        popup.getMenu().add(0, 6, 6, "Enable Device Control");
        popup.getMenu().add(0, 7, 7, "Type a command");
        popup.getMenu().add(0, 8, 8, "Media controls");
        popup.setOnMenuItemClickListener(item -> {
            switch (item.getItemId()) {
                case 1: startActivity(new Intent(this, CommandsActivity.class)); return true;
                case 2: startActivity(new Intent(this, NotesActivity.class)); return true;
                case 3: startActivity(new Intent(this, SettingsActivity.class)); return true;
                case 4: requestAssistantRole(); return true;
                case 5: startActivity(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)); return true;
                case 6: startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)); return true;
                case 7: showTypedCommand(); return true;
                case 8:
                    mediaPanel.setVisibility(mediaPanel.getVisibility() == View.VISIBLE ? View.GONE : View.VISIBLE);
                    return true;
                default: return false;
            }
        });
        popup.show();
    }

    private void showTypedCommand() {
        EditText input = new EditText(this);
        input.setHint("What do you need?");
        input.setSingleLine(false);
        input.setMinLines(2);
        int pad = dp(18);
        FrameLayout frame = new FrameLayout(this);
        frame.setPadding(pad, dp(6), pad, 0);
        frame.addView(input, matchFrame());
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("JARVIS")
                .setView(frame)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Run", null)
                .create();
        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String command = input.getText().toString().trim();
            if (command.isEmpty()) return;
            dialog.dismiss();
            runCommand(command, 1.0);
        }));
        dialog.show();
        input.requestFocus();
        dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE);
    }

    private void runCommand(String raw, double speechConfidence) {
        String command = raw == null ? "" : raw.trim();
        if (command.isEmpty()) { setActive(false, "I’m listening."); return; }
        if (isConversationEndCommand(command)) continuedConversation = false;
        setActive(true, "Processing ...");
        if (decisionPanel != null) decisionPanel.setVisibility(View.GONE);
        status.setText("Heard you say, “" + command + "”");
        ui.postDelayed(() -> submitBrainWork(() -> runtime.handlePresentation(command, speechConfidence)), 120L);
    }

    private void runCandidates(ArrayList<String> candidates, float[] scores) {
        if (candidates == null || candidates.isEmpty()) {
            setActive(false, "I didn’t catch that. Tap the core to try again.");
            return;
        }
        double confidence = scores != null && scores.length > 0 && scores[0] >= 0.0f
                ? Math.min(1.0, scores[0])
                : 0.0;
        runCommand(candidates.get(0), confidence);
    }

    private void submitBrainWork(Supplier<RuntimeSurfacePresentation> work) {
        brainExecutor.execute(() -> {
            try {
                RuntimeSurfacePresentation presentation = work.get();
                ui.post(() -> deliverPresentation(presentation));
            } catch (RuntimeException failure) {
                Log.e(RUNTIME_FAILURE_TAG, "Unexpected shared brain runtime failure", failure);
                RuntimeSurfacePresentation presentation = new RuntimeSurfacePresentation(
                        AssistantSurfaceState.ERROR,
                        "I hit an unexpected problem while handling that.",
                        RUNTIME_FAILURE_TAG,
                        RuntimeSurfaceAction.NONE,
                        RuntimeSurfaceAction.NONE);
                ui.post(() -> deliverPresentation(presentation));
            }
        });
    }

    private void deliverPresentation(RuntimeSurfacePresentation presentation) {
        if (destroyed) return;
        FullAppRuntimeViewState view = FullAppRuntimeViewState.from(presentation);
        String rendered = view.text();
        if (!view.detail().isBlank() && !view.detail().equals(view.text())) rendered += "\n\n" + view.detail();
        applyDecisionActions(view);
        setActive(false, rendered);
        Log.i(SHARED_BRAIN_TAG, "state=" + view.state() + " primary=" + view.primaryAction());
        if (commandTestMode) Log.i(COMMAND_TEST_TAG, "JARVIS_COMMAND_RESULT " + view.text());
        if (!view.text().isBlank()) speak(view.text());
        else resumeListeningAfterSpeech();
    }

    private void applyDecisionActions(FullAppRuntimeViewState view) {
        currentPrimaryAction = view.primaryAction();
        currentSecondaryAction = view.secondaryAction();
        boolean visible = view.primaryEnabled() || view.secondaryEnabled();
        decisionPanel.setVisibility(visible ? View.VISIBLE : View.GONE);

        primaryActionButton.setVisibility(view.primaryEnabled() ? View.VISIBLE : View.GONE);
        primaryActionButton.setEnabled(view.primaryEnabled());
        primaryActionButton.setText(view.primaryAction().name());
        primaryActionButton.setContentDescription("JARVIS " + view.primaryAction().name() + " action");

        secondaryActionButton.setVisibility(view.secondaryEnabled() ? View.VISIBLE : View.GONE);
        secondaryActionButton.setEnabled(view.secondaryEnabled());
        secondaryActionButton.setText(view.secondaryAction().name());
        secondaryActionButton.setContentDescription("JARVIS " + view.secondaryAction().name() + " action");
    }

    private void runDecisionAction(boolean primary) {
        RuntimeSurfaceAction action = primary ? currentPrimaryAction : currentSecondaryAction;
        switch (action) {
            case APPROVE: submitBrainWork(runtime::approvePresentation); break;
            case RETRY: submitBrainWork(runtime::retryPresentation); break;
            case CANCEL: submitBrainWork(runtime::cancelPresentation); break;
            default: break;
        }
    }

    private void runEmbeddedSelfTest() {
        setActive(true, "RUNNING JARVIS SELF TEST ...");
        try {
            requireSelfTest("com.jarvis.mobile".equals(getPackageName()), "Unexpected application identity");
            PackageInfo packageInfo = getPackageManager().getPackageInfo(getPackageName(), 0);
            long versionCode = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P ? packageInfo.getLongVersionCode() : packageInfo.versionCode;
            requireSelfTest(versionCode >= 2102L, "Unexpected version code " + versionCode);

            PackageManager packages = getPackageManager();
            packages.getServiceInfo(new ComponentName(this, JarvisVoiceInteractionService.class), 0);
            packages.getServiceInfo(new ComponentName(this, JarvisVoiceSessionService.class), 0);
            packages.getServiceInfo(new ComponentName(this, JarvisNotificationListener.class), 0);
            packages.getServiceInfo(new ComponentName(this, JarvisAccessibilityService.class), 0);
            packages.getReceiverInfo(new ComponentName(this, NotesWidget.class), 0);
            packages.getReceiverInfo(new ComponentName(this, QuickActivationWidget.class), 0);
            requireSelfTest(runtime != null, "Shared brain runtime missing");

            setActive(false, "SELF TEST PASSED");
            Log.i(SELF_TEST_TAG, "JARVIS_SELF_TEST_PASS package=" + getPackageName() + " versionCode=" + versionCode);
        } catch (Throwable failure) {
            setActive(false, "SELF TEST FAILED");
            Log.e(SELF_TEST_TAG, "JARVIS_SELF_TEST_FAIL", failure);
        }
    }

    private void requireSelfTest(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }

    private void listen() {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            status.setText("Microphone permission is required. Grant it to speak with me.");
            requestRuntimePermissions();
            return;
        }
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            status.setText("Android speech recognition is unavailable on this device.");
            return;
        }
        continuedConversation = !commandTestMode;
        if (speechRecognizer != null) speechRecognizer.destroy();
        speechRecognizer = Build.VERSION.SDK_INT >= 31 && SpeechRecognizer.isOnDeviceRecognitionAvailable(this)
                ? SpeechRecognizer.createOnDeviceSpeechRecognizer(this)
                : SpeechRecognizer.createSpeechRecognizer(this);
        speechRecognizer.setRecognitionListener(new RecognitionListener() {
            @Override public void onReadyForSpeech(Bundle params) { setActive(true, "Listening ..."); }
            @Override public void onBeginningOfSpeech() {
                if (textToSpeech != null) textToSpeech.stop();
                setActive(true, "Hearing you ...");
            }
            @Override public void onRmsChanged(float rmsdB) { }
            @Override public void onBufferReceived(byte[] buffer) { }
            @Override public void onEndOfSpeech() { setActive(true, "Processing ..."); }
            @Override public void onError(int error) {
                if (error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT || error == SpeechRecognizer.ERROR_NO_MATCH) {
                    continuedConversation = false;
                    setActive(false, "Conversation paused. Say “Jarvis” when you need me again.");
                } else {
                    setActive(false, "Listening stopped: " + speechError(error));
                }
            }
            @Override public void onResults(Bundle results) {
                ArrayList<String> matches = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                if (matches == null || matches.isEmpty()) {
                    continuedConversation = false;
                    setActive(false, "I didn’t catch that. Say “Jarvis” when you need me again.");
                    return;
                }
                float[] scores = results.getFloatArray(SpeechRecognizer.CONFIDENCE_SCORES);
                runCandidates(matches, scores);
            }
            @Override public void onPartialResults(Bundle partialResults) {
                ArrayList<String> partial = partialResults.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                if (partial != null && !partial.isEmpty()) status.setText("Hearing,\n" + partial.get(0) + " ...");
            }
            @Override public void onEvent(int eventType, Bundle params) { }
        });
        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        intent.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true);
        intent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, configuredLanguage().toLanguageTag());
        speechRecognizer.startListening(intent);
    }

    private void setActive(boolean value, String message) {
        active = value;
        ui.removeCallbacks(pulse);
        if (message != null) status.setText(message);
        pulseFrame = false;
        applyThemeFrame(value);
        if (value) ui.postDelayed(pulse, PULSE_MS);
    }

    private void applyThemeFrame(boolean activated) {
        if (background == null) return;
        background.setImageDrawable(null);
        background.setBackgroundColor(activated ? getColor(R.color.jarvis_bg_panel_raised) : getColor(R.color.jarvis_bg));
        background.setAlpha(activated ? 1.0f : 0.96f);
    }

    private void requestRuntimePermissions() {
        List<String> missing = new ArrayList<>();
        addIfMissing(missing, Manifest.permission.RECORD_AUDIO);
        addIfMissing(missing, Manifest.permission.READ_CONTACTS);
        addIfMissing(missing, Manifest.permission.CALL_PHONE);
        addIfMissing(missing, Manifest.permission.CAMERA);
        if (Build.VERSION.SDK_INT >= 33) addIfMissing(missing, Manifest.permission.POST_NOTIFICATIONS);
        if (!missing.isEmpty()) requestPermissions(missing.toArray(new String[0]), PERMISSION_REQUEST);
    }

    private void addIfMissing(List<String> missing, String permission) {
        if (checkSelfPermission(permission) != PackageManager.PERMISSION_GRANTED) missing.add(permission);
    }

    private void requestAssistantRole() {
        RoleManager roleManager = getSystemService(RoleManager.class);
        if (roleManager == null || !roleManager.isRoleAvailable(RoleManager.ROLE_ASSISTANT)) {
            status.setText("Android did not expose the Assistant role on this device.");
            return;
        }
        if (roleManager.isRoleHeld(RoleManager.ROLE_ASSISTANT)) {
            status.setText("JARVIS is already your default Android Assistant.");
            return;
        }
        startActivityForResult(roleManager.createRequestRoleIntent(RoleManager.ROLE_ASSISTANT), ASSISTANT_ROLE_REQUEST);
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == ASSISTANT_ROLE_REQUEST) {
            status.setText(resultCode == RESULT_OK ? "JARVIS is now your default Android Assistant." : "Assistant selection was not completed.");
        }
    }

    @Override protected void onResume() {
        super.onResume();
        applyThemeFrame(false);
        refreshProfileGreetingIfIdle();
        String mode = preferences.getString("operating_mode", "normal");
        if ("normal".equals(mode)) {
            modeStatus.setVisibility(View.GONE);
        } else {
            modeStatus.setText(("office".equals(mode) ? "Office" : "Quiet") + " Mode Active");
            modeStatus.setVisibility(View.VISIBLE);
        }
        applyVoicePreferences();
    }

    @Override public void onInit(int statusCode) {
        if (statusCode == TextToSpeech.SUCCESS && textToSpeech != null) {
            applyVoicePreferences();
            textToSpeech.setOnUtteranceProgressListener(new UtteranceProgressListener() {
                @Override public void onStart(String utteranceId) { }
                @Override public void onDone(String utteranceId) {
                    if ("jarvis-response".equals(utteranceId)) ui.post(MainActivity.this::resumeListeningAfterSpeech);
                }
                @Override public void onError(String utteranceId) {
                    if ("jarvis-response".equals(utteranceId)) ui.post(MainActivity.this::resumeListeningAfterSpeech);
                }
            });
        }
    }

    private void applyVoicePreferences() {
        if (textToSpeech == null || preferences == null) return;
        textToSpeech.setLanguage(configuredLanguage());
        float rate = preferences.getFloat("voice_rate", 1.0f);
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

    private String profileGreeting() {
        String profile = preferences == null ? "Sir" : preferences.getString("profile_name", "Sir");
        if (profile == null || profile.isBlank()) profile = "Sir";
        return "Welcome " + profile.trim() + "!";
    }

    private void refreshProfileGreetingIfIdle() {
        if (status == null || active || decisionPanel == null || decisionPanel.getVisibility() == View.VISIBLE) return;
        CharSequence current = status.getText();
        if (current == null || current.toString().startsWith("Welcome ")) status.setText(profileGreeting());
    }

    private void speak(String text) {
        if (preferences.getBoolean("voice_enabled", true) && textToSpeech != null) {
            applyVoicePreferences();
            int result = textToSpeech.speak(text, TextToSpeech.QUEUE_FLUSH, null, "jarvis-response");
            if (result == TextToSpeech.ERROR) resumeListeningAfterSpeech();
        } else {
            resumeListeningAfterSpeech();
        }
    }

    private void resumeListeningAfterSpeech() {
        if (destroyed || commandTestMode || !continuedConversation) return;
        ui.postDelayed(() -> {
            if (!destroyed && continuedConversation) listen();
        }, FOLLOW_UP_DELAY_MS);
    }

    private static boolean isConversationEndCommand(String command) {
        if (command == null) return false;
        String normalized = command.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9' ]", " ").replaceAll("\\s+", " ").trim();
        return normalized.matches("(?:go to )?sleep|stop listening|that's all|that is all|thanks jarvis|thank you jarvis");
    }

    private String speechError(int code) {
        switch (code) {
            case SpeechRecognizer.ERROR_AUDIO: return "audio input error";
            case SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS: return "microphone permission denied";
            case SpeechRecognizer.ERROR_NETWORK:
            case SpeechRecognizer.ERROR_NETWORK_TIMEOUT: return "the selected recognizer needs a connection";
            case SpeechRecognizer.ERROR_NO_MATCH: return "no speech match";
            case SpeechRecognizer.ERROR_RECOGNIZER_BUSY: return "recognizer busy";
            case SpeechRecognizer.ERROR_SPEECH_TIMEOUT: return "no speech detected";
            default: return "error " + code;
        }
    }

    private TextView hudText(String value, float size, int color) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color);
        view.setTypeface(Typeface.create("sans", Typeface.NORMAL));
        return view;
    }

    private FrameLayout.LayoutParams matchFrame() {
        return new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
    }

    private FrameLayout.LayoutParams wrapFrame(int gravity) {
        return new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, gravity);
    }

    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }

    @Override protected void onDestroy() {
        destroyed = true;
        active = false;
        continuedConversation = false;
        ui.removeCallbacksAndMessages(null);
        brainExecutor.shutdownNow();
        if (speechRecognizer != null) speechRecognizer.destroy();
        if (textToSpeech != null) textToSpeech.shutdown();
        super.onDestroy();
    }
}
