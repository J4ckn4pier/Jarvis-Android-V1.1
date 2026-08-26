package com.jarvis.mobile;

import android.Manifest;
import android.app.Activity;
import android.app.role.RoleManager;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.speech.tts.TextToSpeech;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.jarvis.mobile.brain.JarvisBrain;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class MainActivity extends Activity implements TextToSpeech.OnInitListener {
    private static final int PERMISSION_REQUEST = 70;
    private static final int ASSISTANT_ROLE_REQUEST = 71;

    private JarvisBrain brain;
    private SpeechRecognizer speechRecognizer;
    private TextToSpeech textToSpeech;
    private ImageView background;
    private ImageView jarvisFace;
    private TextView status;
    private TextView output;
    private EditText commandInput;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        brain = new JarvisBrain(this);
        textToSpeech = new TextToSpeech(this, this);
        buildInterface();
        requestRuntimePermissions();
    }

    private void buildInterface() {
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.rgb(1, 6, 10));

        background = new ImageView(this);
        background.setImageResource(R.drawable.background_mk3);
        background.setScaleType(ImageView.ScaleType.CENTER_CROP);
        root.addView(background, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        View veil = new View(this);
        veil.setBackgroundColor(Color.argb(150, 0, 4, 8));
        root.addView(veil, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setGravity(Gravity.CENTER_HORIZONTAL);
        int pad = dp(20);
        panel.setPadding(pad, dp(28), pad, dp(28));
        scroll.addView(panel, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        root.addView(scroll, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        jarvisFace = new ImageView(this);
        jarvisFace.setImageResource(R.drawable.jarvis_normal);
        jarvisFace.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        panel.addView(jarvisFace, new LinearLayout.LayoutParams(dp(168), dp(168)));

        TextView title = text("J.A.R.V.I.S.", 28, Color.rgb(120, 240, 255));
        title.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        title.setGravity(Gravity.CENTER);
        panel.addView(title, matchWrap());

        status = text("ANDROID V1.1 DONOR BETA  •  ONLINE", 12, Color.rgb(255, 208, 72));
        status.setTypeface(Typeface.MONOSPACE);
        status.setGravity(Gravity.CENTER);
        panel.addView(status, margins(matchWrap(), 0, dp(4), 0, dp(16)));

        output = text("Ready. Tap the microphone or type a command.", 17, Color.WHITE);
        output.setTypeface(Typeface.MONOSPACE);
        output.setTextIsSelectable(true);
        output.setBackgroundColor(Color.argb(175, 0, 12, 18));
        output.setPadding(dp(14), dp(14), dp(14), dp(14));
        panel.addView(output, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(170)));

        commandInput = new EditText(this);
        commandInput.setTextColor(Color.WHITE);
        commandInput.setHintTextColor(Color.rgb(145, 165, 175));
        commandInput.setHint("What do you need?");
        commandInput.setMinLines(2);
        commandInput.setMaxLines(4);
        commandInput.setBackgroundColor(Color.argb(210, 4, 22, 30));
        commandInput.setPadding(dp(12), dp(10), dp(12), dp(10));
        panel.addView(commandInput, margins(matchWrap(), 0, dp(12), 0, dp(10)));

        LinearLayout primary = new LinearLayout(this);
        primary.setGravity(Gravity.CENTER);
        primary.setOrientation(LinearLayout.HORIZONTAL);
        panel.addView(primary, matchWrap());

        ImageButton listen = donorButton(R.drawable.jarvis_widget_icon, "Listen");
        listen.setOnClickListener(v -> listen());
        primary.addView(listen, new LinearLayout.LayoutParams(dp(92), dp(92)));

        ImageButton run = donorButton(R.drawable.ic_reactor, "Run command");
        run.setOnClickListener(v -> runCommand(commandInput.getText().toString()));
        primary.addView(run, margins(new LinearLayout.LayoutParams(dp(92), dp(92)), dp(18), 0, 0, 0));

        TextView primaryLabels = text("LISTEN                         RUN", 12, Color.rgb(120, 240, 255));
        primaryLabels.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        primaryLabels.setGravity(Gravity.CENTER);
        panel.addView(primaryLabels, margins(matchWrap(), 0, dp(3), 0, dp(16)));

        panel.addView(actionButton("MAKE JARVIS DEFAULT ASSISTANT", v -> requestAssistantRole()), matchWrap());
        panel.addView(actionButton("ENABLE NOTIFICATION AWARENESS", v ->
                        startActivity(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))),
                margins(matchWrap(), 0, dp(8), 0, 0));
        panel.addView(actionButton("ENABLE DEVICE CONTROL", v ->
                        startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))),
                margins(matchWrap(), 0, dp(8), 0, 0));

        TextView examples = text(
                "Try: call Mom • text Alex saying I’m on my way • set a timer for 10 minutes\n" +
                        "schedule lunch with Maria tomorrow at 1 PM • read my notifications • add task buy groceries",
                12, Color.rgb(165, 190, 200));
        examples.setTypeface(Typeface.MONOSPACE);
        examples.setGravity(Gravity.CENTER);
        panel.addView(examples, margins(matchWrap(), 0, dp(18), 0, 0));

        setContentView(root);
    }

    private void runCommand(String raw) {
        String command = raw == null ? "" : raw.trim();
        if (command.isEmpty()) {
            setStatus("WAITING FOR A COMMAND", false);
            return;
        }
        setStatus("PROCESSING", true);
        String answer = brain.handle(command);
        output.setText("YOU: " + command + "\n\nJARVIS: " + answer);
        commandInput.setText("");
        speak(answer);
        setStatus("ONLINE", false);
    }

    private void listen() {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            output.setText("Microphone permission is required for voice commands.");
            requestRuntimePermissions();
            return;
        }
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            output.setText("Android speech recognition is unavailable on this device.");
            return;
        }
        if (speechRecognizer != null) speechRecognizer.destroy();
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this);
        speechRecognizer.setRecognitionListener(new RecognitionListener() {
            @Override public void onReadyForSpeech(Bundle params) { setStatus("LISTENING", true); }
            @Override public void onBeginningOfSpeech() { setStatus("HEARING YOU", true); }
            @Override public void onRmsChanged(float rmsdB) { }
            @Override public void onBufferReceived(byte[] buffer) { }
            @Override public void onEndOfSpeech() { setStatus("THINKING", true); }
            @Override public void onError(int error) {
                output.setText("Listening stopped: " + speechError(error));
                setStatus("ONLINE", false);
            }
            @Override public void onResults(Bundle results) {
                ArrayList<String> matches = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                if (matches == null || matches.isEmpty()) {
                    output.setText("I didn’t catch that.");
                    setStatus("ONLINE", false);
                    return;
                }
                runCommand(matches.get(0));
            }
            @Override public void onPartialResults(Bundle partialResults) {
                ArrayList<String> partial = partialResults.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                if (partial != null && !partial.isEmpty()) output.setText("HEARING: " + partial.get(0));
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
            output.setText("Android did not expose the Assistant role on this device.");
            return;
        }
        if (roleManager.isRoleHeld(RoleManager.ROLE_ASSISTANT)) {
            output.setText("JARVIS is already the default Assistant.");
            return;
        }
        startActivityForResult(roleManager.createRequestRoleIntent(RoleManager.ROLE_ASSISTANT), ASSISTANT_ROLE_REQUEST);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == ASSISTANT_ROLE_REQUEST) {
            output.setText(resultCode == RESULT_OK
                    ? "JARVIS is now your default Android Assistant."
                    : "Assistant selection was not completed.");
        }
    }

    @Override
    public void onInit(int statusCode) {
        if (statusCode == TextToSpeech.SUCCESS && textToSpeech != null) {
            textToSpeech.setLanguage(Locale.getDefault());
            textToSpeech.setSpeechRate(0.95f);
        }
    }

    private void speak(String text) {
        if (textToSpeech != null) {
            textToSpeech.speak(text, TextToSpeech.QUEUE_FLUSH, null, "jarvis-response");
        }
    }

    private void setStatus(String message, boolean active) {
        status.setText("ANDROID V1.1 DONOR BETA  •  " + message);
        jarvisFace.setImageResource(active ? R.drawable.jarvis_active : R.drawable.jarvis_normal);
        background.setImageResource(active ? R.drawable.background_mk3_active : R.drawable.background_mk3);
    }

    private String speechError(int code) {
        switch (code) {
            case SpeechRecognizer.ERROR_AUDIO: return "audio input error";
            case SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS: return "microphone permission denied";
            case SpeechRecognizer.ERROR_NETWORK:
            case SpeechRecognizer.ERROR_NETWORK_TIMEOUT: return "the selected recognizer needs a network connection";
            case SpeechRecognizer.ERROR_NO_MATCH: return "no speech match";
            case SpeechRecognizer.ERROR_RECOGNIZER_BUSY: return "recognizer busy";
            case SpeechRecognizer.ERROR_SPEECH_TIMEOUT: return "no speech detected";
            default: return "error " + code;
        }
    }

    private ImageButton donorButton(int icon, String description) {
        ImageButton button = new ImageButton(this);
        button.setBackgroundResource(R.drawable.button_normal);
        button.setImageResource(icon);
        button.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        button.setPadding(dp(20), dp(20), dp(20), dp(20));
        button.setContentDescription(description);
        return button;
    }

    private Button actionButton(String label, View.OnClickListener listener) {
        Button button = new Button(this);
        button.setText(label);
        button.setTextColor(Color.rgb(115, 235, 255));
        button.setTextSize(12);
        button.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        button.setBackgroundColor(Color.argb(210, 3, 30, 40));
        button.setOnClickListener(listener);
        return button;
    }

    private TextView text(String value, float size, int color) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color);
        return view;
    }

    private LinearLayout.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    private LinearLayout.LayoutParams margins(
            LinearLayout.LayoutParams params, int left, int top, int right, int bottom) {
        params.setMargins(left, top, right, bottom);
        return params;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    @Override
    protected void onDestroy() {
        if (speechRecognizer != null) speechRecognizer.destroy();
        if (textToSpeech != null) textToSpeech.shutdown();
        super.onDestroy();
    }
}
