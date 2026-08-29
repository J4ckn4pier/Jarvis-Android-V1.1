package com.jarvis.mobile;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.role.RoleManager;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
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
import android.util.Log;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.TextView;

import com.jarvis.brain.FullAppRuntimeViewState;
import com.jarvis.brain.RuntimeSurfacePresentation;
import com.jarvis.mobile.brain.AndroidBrainRuntime;
import com.jarvis.mobile.assistant.JarvisVoiceInteractionService;
import com.jarvis.mobile.assistant.JarvisVoiceSessionService;
import com.jarvis.mobile.events.JarvisNotificationListener;
import com.jarvis.mobile.hands.JarvisAccessibilityService;
import com.jarvis.mobile.widgets.NotesWidget;
import com.jarvis.mobile.widgets.QuickActivationWidget;
import com.jarvis.mobile.voice.LegacyResponsePlayer;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Modern assistant brain inside the current Android shell. */
public class MainActivity extends Activity implements TextToSpeech.OnInitListener {
    private static final int PERMISSION_REQUEST = 70;
    private static final int ASSISTANT_ROLE_REQUEST = 71;
    private static final long PULSE_MS = 260L;
    private static final String SELF_TEST_TAG = "JARVIS_SELF_TEST";
    private static final String COMMAND_TEST_TAG = "JARVIS_COMMAND_TEST";
    private static final String UI_TEST_TAG = "JARVIS_UI_TEST";
    private static final String SHARED_BRAIN_TAG = "JARVIS_SHARED_BRAIN_ACTIVE";

    private final android.os.Handler ui = new android.os.Handler(android.os.Looper.getMainLooper());
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
    private LegacyResponsePlayer legacyResponses;
    private ImageView background;
    private TextView status;
    private TextView modeStatus;
    private LinearLayout mediaPanel;
    private boolean active;
    private boolean pulseFrame;
    private SharedPreferences preferences;
    private boolean commandTestMode;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);

        preferences = getSharedPreferences("jarvis_shell", MODE_PRIVATE);
        runtime = new AndroidBrainRuntime(this);
        legacyResponses = new LegacyResponsePlayer(this);
        textToSpeech = new TextToSpeech(this, this);
        buildDonorShell();

        if (getIntent() != null && getIntent().getBooleanExtra("jarvis_self_test", false)) {
            ui.postDelayed(this::runEmbeddedSelfTest, 350L);
            return;
        }

        if ((getApplicationInfo().flags & ApplicationInfo.FLAG_DEBUGGABLE) != 0 && getIntent() != null &&
                getIntent().hasExtra("jarvis_test_command")) {
            commandTestMode = true;
            String testCommand = getIntent().getStringExtra("jarvis_test_command");
            ui.postDelayed(() -> runCommand(testCommand), 350L);
            return;
        }

        if ((getApplicationInfo().flags & ApplicationInfo.FLAG_DEBUGGABLE) != 0) {
            Log.i(UI_TEST_TAG, "JARVIS_HOME_READY Mark III Welcome Sir");
        }

        requestRuntimePermissions();

        if (!preferences.getBoolean("introduced", false)) {
            preferences.edit().putBoolean("introduced", true).apply();
            ui.postDelayed(this::playReadyCue, 650L);
        }

        String action = getIntent() == null ? null : getIntent().getAction();
        if (Intent.ACTION_ASSIST.equals(action) || Intent.ACTION_VOICE_COMMAND.equals(action)) {
            ui.postDelayed(this::listen, 220L);
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        String action = intent == null ? null : intent.getAction();
        if (Intent.ACTION_ASSIST.equals(action) || Intent.ACTION_VOICE_COMMAND.equals(action)) listen();
    }

    private void buildDonorShell() {
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.BLACK);

        background = new ImageView(this);
        background.setScaleType(ImageView.ScaleType.CENTER_CROP);
        background.setContentDescription("JARVIS Mark III interface");
        root.addView(background, matchFrame());
        applyThemeFrame(false);

        modeStatus = donorText("", 10, Color.rgb(10, 35, 42));
        modeStatus.setBackgroundColor(Color.WHITE);
        modeStatus.setPadding(dp(6), dp(4), dp(6), dp(4));
        modeStatus.setVisibility(View.GONE);
        FrameLayout.LayoutParams modeParams = wrapFrame(Gravity.TOP | Gravity.END);
        modeParams.setMargins(0, dp(8), dp(8), 0);
        root.addView(modeStatus, modeParams);

        status = donorText("Welcome Sir!", 16, Color.WHITE);
        status.setGravity(Gravity.CENTER);
        status.setMaxLines(6);
        status.setShadowLayer(5f, 0f, 1f, Color.BLACK);
        status.setContentDescription("JARVIS status and response");
        FrameLayout.LayoutParams statusParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        statusParams.setMargins(dp(18), 0, dp(18), dp(218));
        root.addView(status, statusParams);

        View reactorTarget = new View(this);
        reactorTarget.setContentDescription("Speak to JARVIS");
        reactorTarget.setFocusable(true);
        reactorTarget.setOnClickListener(v -> listen());
        reactorTarget.setOnLongClickListener(v -> {
            showTypedCommand();
            return true;
        });
        FrameLayout.LayoutParams reactorParams = new FrameLayout.LayoutParams(
                dp(230), dp(230), Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        reactorParams.bottomMargin = dp(28);
        root.addView(reactorTarget, reactorParams);

        mediaPanel = buildMediaPanel();
        mediaPanel.setVisibility(View.GONE);
        FrameLayout.LayoutParams mediaParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(72), Gravity.BOTTOM);
        root.addView(mediaPanel, mediaParams);

        ImageButton menu = new ImageButton(this);
        menu.setImageResource(R.drawable.menu_dots);
        menu.setBackgroundColor(Color.TRANSPARENT);
        menu.setPadding(dp(8), dp(8), dp(8), dp(8));
        menu.setContentDescription("JARVIS menu");
        menu.setOnClickListener(this::showMenu);
        FrameLayout.LayoutParams menuParams = new FrameLayout.LayoutParams(
                dp(54), dp(54), Gravity.BOTTOM | Gravity.END);
        menuParams.setMargins(0, 0, dp(5), dp(5));
        root.addView(menu, menuParams);

        setContentView(root);
    }

    private LinearLayout buildMediaPanel() {
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setGravity(Gravity.CENTER);
        panel.setBackgroundColor(Color.argb(230, 255, 255, 255));

        TextView title = donorText("Now playing ...", 12, Color.rgb(15, 25, 30));
        title.setSingleLine(true);
        title.setGravity(Gravity.CENTER);
        panel.addView(title, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout controls = new LinearLayout(this);
        controls.setGravity(Gravity.CENTER);
        controls.addView(mediaButton(android.R.drawable.ic_media_previous,
                KeyEvent.KEYCODE_MEDIA_PREVIOUS, "Previous"));
        controls.addView(mediaButton(android.R.drawable.ic_media_play,
                KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, "Play or pause"));
        controls.addView(mediaButton(android.R.drawable.ic_media_next,
                KeyEvent.KEYCODE_MEDIA_NEXT, "Next"));
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
        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener(v -> {
                    String command = input.getText().toString().trim();
                    if (command.isEmpty()) return;
                    dialog.dismiss();
                    runCommand(command);
                }));
        dialog.show();
        input.requestFocus();
        dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE);
    }

    private void runCommand(String raw) {
        String command = raw == null ? "" : raw.trim();
        if (command.isEmpty()) {
            setActive(false, "I’m listening.");
            return;
        }
        setActive(true, "Processing ...");
        status.setText("Heard you say, “" + command + "”");
        ui.postDelayed(() -> deliverPresentation(runtime.handlePresentation(command)), 120L);
    }

    private void runCandidates(ArrayList<String> candidates) {
        if (candidates == null || candidates.isEmpty()) {
            setActive(false, "I didn’t catch that. Tap the reactor to try again.");
            return;
        }
        runCommand(candidates.get(0));
    }

    private void deliverPresentation(RuntimeSurfacePresentation presentation) {
        FullAppRuntimeViewState view = FullAppRuntimeViewState.from(presentation);
        String rendered = view.text();
        if (!view.detail().isBlank() && !view.detail().equals(view.text())) {
            rendered += "\n\n" + view.detail();
        }
        if (view.primaryEnabled()) rendered += "\n\n" + view.primaryAction();
        if (view.secondaryEnabled()) rendered += " / " + view.secondaryAction();
        setActive(false, rendered);
        Log.i(SHARED_BRAIN_TAG, "state=" + view.state() + " primary=" + view.primaryAction());
        if (commandTestMode) Log.i(COMMAND_TEST_TAG, "JARVIS_COMMAND_RESULT " + view.text());
        if (!view.text().isBlank()) speak(view.text());
    }

    private void runEmbeddedSelfTest() {
        setActive(true, "RUNNING JARVIS SELF TEST ...");
        try {
            requireSelfTest("com.jarvis.mobile".equals(getPackageName()),
                    "Unexpected application identity");

            PackageInfo packageInfo = getPackageManager().getPackageInfo(getPackageName(), 0);
            long versionCode = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P
                    ? packageInfo.getLongVersionCode() : packageInfo.versionCode;
            requireSelfTest(versionCode >= 2102L, "Unexpected version code " + versionCode);

            requireSelfTest(getDrawable(R.drawable.background_mk2) != null, "MKII shell missing");
            requireSelfTest(getDrawable(R.drawable.background_mk2_active) != null,
                    "MKII active shell missing");
            requireSelfTest(getDrawable(R.drawable.background_mk3) != null, "MKIII shell missing");
            requireSelfTest(getDrawable(R.drawable.background_mk3_active) != null,
                    "MKIII active shell missing");
            requireSelfTest(getDrawable(R.drawable.menu_dots) != null, "Current menu missing");
            requireSelfTest(getDrawable(R.drawable.jarvis_normal) != null,
                    "Current normal reactor missing");
            requireSelfTest(getDrawable(R.drawable.jarvis_active) != null,
                    "Current active reactor missing");

            PackageManager packages = getPackageManager();
            packages.getServiceInfo(new ComponentName(this, JarvisVoiceInteractionService.class), 0);
            packages.getServiceInfo(new ComponentName(this, JarvisVoiceSessionService.class), 0);
            packages.getServiceInfo(new ComponentName(this, JarvisNotificationListener.class), 0);
            packages.getServiceInfo(new ComponentName(this, JarvisAccessibilityService.class), 0);
            packages.getReceiverInfo(new ComponentName(this, NotesWidget.class), 0);
            packages.getReceiverInfo(new ComponentName(this, QuickActivationWidget.class), 0);

            requireSelfTest(runtime != null, "Shared brain runtime missing");
            setActive(false, "SELF TEST PASSED");
            Log.i(SELF_TEST_TAG, "JARVIS_SELF_TEST_PASS package=" + getPackageName() +
                    " versionCode=" + versionCode);
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
        if (speechRecognizer != null) speechRecognizer.destroy();
        speechRecognizer = Build.VERSION.SDK_INT >= 31 &&
                SpeechRecognizer.isOnDeviceRecognitionAvailable(this)
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
                setActive(false, "Listening stopped: " + speechError(error));
            }
            @Override public void onResults(Bundle results) {
                ArrayList<String> matches = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                if (matches == null || matches.isEmpty()) {
                    setActive(false, "I didn’t catch that. Tap the reactor to try again.");
                    return;
                }
                runCandidates(matches);
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
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault());
        speechRecognizer.startListening(intent);
    }

    private void setActive(boolean value, String message) {
        active = value;
        ui.removeCallbacks(pulse);
        if (message != null) status.setText(message);
        if (value) {
            pulseFrame = false;
            applyThemeFrame(true);
            ui.postDelayed(pulse, PULSE_MS);
        } else {
            pulseFrame = false;
            applyThemeFrame(false);
        }
    }

    private void applyThemeFrame(boolean activated) {
        boolean mark2 = "mk2".equals(preferences.getString("mark_theme", "mk3"));
        int image;
        if (mark2) image = activated ? R.drawable.background_mk2_active : R.drawable.background_mk2;
        else image = activated ? R.drawable.background_mk3_active : R.drawable.background_mk3;
        if (background != null) background.setImageResource(image);
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
        startActivityForResult(roleManager.createRequestRoleIntent(RoleManager.ROLE_ASSISTANT),
                ASSISTANT_ROLE_REQUEST);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == ASSISTANT_ROLE_REQUEST) {
            status.setText(resultCode == RESULT_OK
                    ? "JARVIS is now your default Android Assistant."
                    : "Assistant selection was not completed.");
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        applyThemeFrame(false);
        String mode = preferences.getString("operating_mode", "normal");
        if ("normal".equals(mode)) {
            modeStatus.setVisibility(View.GONE);
        } else {
            modeStatus.setText(("office".equals(mode) ? "Office" : "Quiet") + " Mode Active");
            modeStatus.setVisibility(View.VISIBLE);
        }
    }

    @Override
    public void onInit(int statusCode) {
        if (statusCode == TextToSpeech.SUCCESS && textToSpeech != null) {
            textToSpeech.setLanguage(Locale.getDefault());
            textToSpeech.setSpeechRate(0.92f);
        }
    }

    private void speak(String text) {
        if (preferences.getBoolean("voice_enabled", true) && textToSpeech != null) {
            textToSpeech.speak(text, TextToSpeech.QUEUE_FLUSH, null, "jarvis-response");
        }
    }

    private void playReadyCue() {
        if (legacyResponses != null) legacyResponses.play("ready_operational");
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

    private TextView donorText(String value, float size, int color) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color);
        view.setTypeface(Typeface.create("sans", Typeface.NORMAL));
        return view;
    }

    private FrameLayout.LayoutParams matchFrame() {
        return new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
    }

    private FrameLayout.LayoutParams wrapFrame(int gravity) {
        return new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, gravity);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    @Override
    protected void onDestroy() {
        active = false;
        ui.removeCallbacksAndMessages(null);
        if (speechRecognizer != null) speechRecognizer.destroy();
        if (textToSpeech != null) textToSpeech.shutdown();
        if (legacyResponses != null) legacyResponses.release();
        super.onDestroy();
    }
}
