package com.jarvis.mobile.assistant;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Build;
import android.os.Bundle;
import android.service.voice.VoiceInteractionSession;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.speech.tts.TextToSpeech;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.jarvis.brain.AssistantSurfaceState;
import com.jarvis.brain.RuntimeSurfacePresentation;
import com.jarvis.mobile.MainActivity;
import com.jarvis.mobile.R;
import com.jarvis.mobile.brain.AndroidBrainRuntime;

import java.util.ArrayList;
import java.util.Locale;

public class JarvisVoiceSession extends VoiceInteractionSession implements TextToSpeech.OnInitListener {
    private AndroidBrainRuntime brain;
    private SpeechRecognizer speechRecognizer;
    private TextToSpeech textToSpeech;
    private ImageView background;
    private ImageView face;
    private TextView output;
    private Button approveButton;
    private Button cancelButton;
    private boolean viewReady;
    private boolean autoListenTriggered;
    private String lastCommand = "";

    public JarvisVoiceSession(Context context) { super(context); }

    @Override public void onCreate() {
        super.onCreate();
        brain = new AndroidBrainRuntime(getContext());
        textToSpeech = new TextToSpeech(getContext(), this);
    }

    @Override public View onCreateContentView() {
        Log.i("JARVIS_ASSISTANT_TEST", "JARVIS_ASSISTANT_READY");
        FrameLayout root = new FrameLayout(getContext());
        background = new ImageView(getContext());
        background.setImageResource(R.drawable.background_mk3_active);
        background.setScaleType(ImageView.ScaleType.CENTER_CROP);
        root.addView(background, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        face = new ImageView(getContext());
        face.setImageResource(R.drawable.jarvis_normal);
        face.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        FrameLayout.LayoutParams faceParams = new FrameLayout.LayoutParams(dp(120), dp(120), Gravity.CENTER_HORIZONTAL | Gravity.TOP);
        faceParams.topMargin = dp(48);
        root.addView(face, faceParams);

        output = new TextView(getContext());
        output.setText("Listening…");
        output.setTextColor(Color.WHITE);
        output.setTextSize(16);
        output.setGravity(Gravity.CENTER);
        output.setShadowLayer(5f, 0f, 1f, Color.BLACK);
        output.setPadding(dp(18), dp(12), dp(18), dp(12));
        FrameLayout.LayoutParams outputParams = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        outputParams.bottomMargin = dp(112);
        root.addView(output, outputParams);

        LinearLayout buttons = new LinearLayout(getContext());
        buttons.setGravity(Gravity.CENTER);
        Button listen = button("LISTEN");
        listen.setOnClickListener(v -> startListening());
        buttons.addView(listen);

        approveButton = button("APPROVE");
        approveButton.setVisibility(View.GONE);
        approveButton.setOnClickListener(v -> deliver(brain.approvePresentation()));
        buttons.addView(approveButton);

        cancelButton = button("NOT YET");
        cancelButton.setVisibility(View.GONE);
        cancelButton.setOnClickListener(v -> deliver(brain.cancelPresentation()));
        buttons.addView(cancelButton);

        Button open = button("OPEN FULL JARVIS");
        open.setOnClickListener(v -> getContext().startActivity(new Intent(getContext(), MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)));
        buttons.addView(open);
        FrameLayout.LayoutParams buttonParams = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        buttonParams.bottomMargin = dp(10);
        root.addView(buttons, buttonParams);

        viewReady = true;
        root.postDelayed(this::triggerAutoListen, 180);
        return root;
    }

    @Override public void onShow(Bundle args, int showFlags) {
        super.onShow(args, showFlags);
        if (viewReady && output != null) output.postDelayed(this::triggerAutoListen, 120);
    }

    private void triggerAutoListen() {
        if (autoListenTriggered) return;
        autoListenTriggered = true;
        startListening();
    }

    private void startListening() {
        if (getContext().checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            output.setText("Open JARVIS once and grant Microphone permission.");
            setActive(false); return;
        }
        if (!SpeechRecognizer.isRecognitionAvailable(getContext())) {
            output.setText("Android speech recognition is unavailable.");
            setActive(false); return;
        }
        if (speechRecognizer != null) speechRecognizer.destroy();
        speechRecognizer = Build.VERSION.SDK_INT >= 31 && SpeechRecognizer.isOnDeviceRecognitionAvailable(getContext())
                ? SpeechRecognizer.createOnDeviceSpeechRecognizer(getContext())
                : SpeechRecognizer.createSpeechRecognizer(getContext());
        speechRecognizer.setRecognitionListener(new RecognitionListener() {
            @Override public void onReadyForSpeech(Bundle params) { output.setText("Listening…"); setActive(true); }
            @Override public void onBeginningOfSpeech() { output.setText("I’m listening."); }
            @Override public void onRmsChanged(float rmsdB) { }
            @Override public void onBufferReceived(byte[] buffer) { }
            @Override public void onEndOfSpeech() { output.setText("Thinking…"); }
            @Override public void onError(int error) {
                output.setText(error == SpeechRecognizer.ERROR_NO_MATCH || error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT
                        ? "I didn’t catch that. Tap LISTEN to try again."
                        : "Listening stopped. Tap LISTEN to try again.");
                setActive(false);
            }
            @Override public void onResults(Bundle results) {
                ArrayList<String> matches = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                if (matches == null || matches.isEmpty()) { output.setText("I didn’t catch that."); setActive(false); return; }
                execute(matches.get(0));
            }
            @Override public void onPartialResults(Bundle partialResults) {
                ArrayList<String> partial = partialResults.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                if (partial != null && !partial.isEmpty()) output.setText(partial.get(0));
            }
            @Override public void onEvent(int eventType, Bundle params) { }
        });
        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault());
        intent.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true);
        speechRecognizer.startListening(intent);
    }

    private void execute(String command) {
        lastCommand = command == null ? "" : command;
        output.setText("YOU: " + lastCommand + "\n\nJARVIS: Thinking…");
        deliver(brain.handlePresentation(lastCommand));
    }

    private void deliver(RuntimeSurfacePresentation presentation) {
        String text = presentation.text();
        String detail = presentation.detail();
        String rendered = text;
        if (!detail.isBlank() && !detail.equals(text)) rendered += "\n\n" + detail;
        output.setText((lastCommand.isBlank() ? "" : "YOU: " + lastCommand + "\n\n") + "JARVIS: " + rendered);

        boolean approval = presentation.state() == AssistantSurfaceState.AWAITING_APPROVAL;
        approveButton.setVisibility(approval ? View.VISIBLE : View.GONE);
        cancelButton.setVisibility(approval || presentation.state() == AssistantSurfaceState.NEEDS_INPUT ? View.VISIBLE : View.GONE);

        if (!text.isBlank() && textToSpeech != null) {
            textToSpeech.speak(text, TextToSpeech.QUEUE_FLUSH, null, "jarvis-session");
        }
        setActive(false);
    }

    private void setActive(boolean active) {
        if (face != null) face.setImageResource(active ? R.drawable.jarvis_active : R.drawable.jarvis_normal);
        if (background != null) background.setImageResource(active ? R.drawable.background_mk3_active : R.drawable.background_mk3);
    }

    private Button button(String label) {
        Button button = new Button(getContext());
        button.setText(label);
        button.setTextColor(Color.rgb(115, 235, 255));
        button.setTextSize(11);
        button.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        button.setBackgroundColor(Color.argb(220, 3, 30, 40));
        return button;
    }

    private int dp(int value) { return Math.round(value * getContext().getResources().getDisplayMetrics().density); }

    @Override public void onInit(int status) {
        if (status == TextToSpeech.SUCCESS && textToSpeech != null) {
            textToSpeech.setLanguage(Locale.getDefault());
            textToSpeech.setSpeechRate(0.95f);
        }
    }

    @Override public void onDestroy() {
        if (speechRecognizer != null) speechRecognizer.destroy();
        if (textToSpeech != null) textToSpeech.shutdown();
        super.onDestroy();
    }
}
