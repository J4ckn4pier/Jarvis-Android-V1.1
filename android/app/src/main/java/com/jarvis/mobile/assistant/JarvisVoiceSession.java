package com.jarvis.mobile.assistant;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.service.voice.VoiceInteractionSession;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.speech.tts.TextToSpeech;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.jarvis.mobile.MainActivity;
import com.jarvis.mobile.R;
import com.jarvis.mobile.brain.JarvisBrain;

import java.util.ArrayList;
import java.util.Locale;

public class JarvisVoiceSession extends VoiceInteractionSession implements TextToSpeech.OnInitListener {
    private JarvisBrain brain;
    private SpeechRecognizer speechRecognizer;
    private TextToSpeech textToSpeech;
    private ImageView background;
    private ImageView face;
    private TextView output;
    private boolean viewReady;

    public JarvisVoiceSession(Context context) {
        super(context);
    }

    @Override
    public void onCreate() {
        super.onCreate();
        brain = new JarvisBrain(getContext());
        textToSpeech = new TextToSpeech(getContext(), this);
    }

    @Override
    public View onCreateContentView() {
        FrameLayout root = new FrameLayout(getContext());
        background = new ImageView(getContext());
        background.setImageResource(R.drawable.background_mk3_active);
        background.setScaleType(ImageView.ScaleType.CENTER_CROP);
        root.addView(background, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        View veil = new View(getContext());
        veil.setBackgroundColor(Color.argb(175, 0, 5, 9));
        root.addView(veil, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        LinearLayout panel = new LinearLayout(getContext());
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setGravity(Gravity.CENTER_HORIZONTAL);
        panel.setPadding(dp(24), dp(20), dp(24), dp(24));
        root.addView(panel, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        face = new ImageView(getContext());
        face.setImageResource(R.drawable.jarvis_active);
        face.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        panel.addView(face, new LinearLayout.LayoutParams(dp(122), dp(122)));

        TextView title = new TextView(getContext());
        title.setText("J.A.R.V.I.S.");
        title.setTextColor(Color.rgb(115, 235, 255));
        title.setTextSize(23);
        title.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        title.setGravity(Gravity.CENTER);
        panel.addView(title);

        output = new TextView(getContext());
        output.setText("Listening…");
        output.setTextColor(Color.WHITE);
        output.setTextSize(17);
        output.setTypeface(Typeface.MONOSPACE);
        output.setGravity(Gravity.CENTER);
        output.setPadding(dp(8), dp(12), dp(8), dp(12));
        panel.addView(output, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout buttons = new LinearLayout(getContext());
        buttons.setGravity(Gravity.CENTER);
        Button listen = button("LISTEN");
        listen.setOnClickListener(v -> startListening());
        buttons.addView(listen);
        Button open = button("OPEN FULL JARVIS");
        open.setOnClickListener(v -> {
            Intent intent = new Intent(getContext(), MainActivity.class)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            getContext().startActivity(intent);
        });
        buttons.addView(open);
        panel.addView(buttons);

        viewReady = true;
        root.postDelayed(this::startListening, 180);
        return root;
    }

    @Override
    public void onShow(Bundle args, int showFlags) {
        super.onShow(args, showFlags);
        if (viewReady && output != null) output.postDelayed(this::startListening, 120);
    }

    private void startListening() {
        if (getContext().checkSelfPermission(Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
            output.setText("Open JARVIS once and grant Microphone permission.");
            setActive(false);
            return;
        }
        if (!SpeechRecognizer.isRecognitionAvailable(getContext())) {
            output.setText("Android speech recognition is unavailable.");
            setActive(false);
            return;
        }
        if (speechRecognizer != null) speechRecognizer.destroy();
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(getContext());
        speechRecognizer.setRecognitionListener(new RecognitionListener() {
            @Override public void onReadyForSpeech(Bundle params) {
                output.setText("Listening…");
                setActive(true);
            }
            @Override public void onBeginningOfSpeech() { output.setText("I’m listening."); }
            @Override public void onRmsChanged(float rmsdB) { }
            @Override public void onBufferReceived(byte[] buffer) { }
            @Override public void onEndOfSpeech() { output.setText("Thinking…"); }
            @Override public void onError(int error) {
                output.setText(error == SpeechRecognizer.ERROR_NO_MATCH ||
                        error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT
                        ? "I didn’t catch that. Tap LISTEN to try again."
                        : "Listening stopped. Tap LISTEN to try again.");
                setActive(false);
            }
            @Override public void onResults(Bundle results) {
                ArrayList<String> matches = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                if (matches == null || matches.isEmpty()) {
                    output.setText("I didn’t catch that.");
                    setActive(false);
                    return;
                }
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
        String answer = brain.handle(command);
        output.setText("YOU: " + command + "\n\nJARVIS: " + answer);
        if (textToSpeech != null) {
            textToSpeech.speak(answer, TextToSpeech.QUEUE_FLUSH, null, "jarvis-session");
        }
        setActive(false);
    }

    private void setActive(boolean active) {
        if (face != null) face.setImageResource(active ? R.drawable.jarvis_active : R.drawable.jarvis_normal);
        if (background != null) {
            background.setImageResource(active ? R.drawable.background_mk3_active : R.drawable.background_mk3);
        }
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

    private int dp(int value) {
        return Math.round(value * getContext().getResources().getDisplayMetrics().density);
    }

    @Override
    public void onInit(int status) {
        if (status == TextToSpeech.SUCCESS && textToSpeech != null) {
            textToSpeech.setLanguage(Locale.getDefault());
            textToSpeech.setSpeechRate(0.95f);
        }
    }

    @Override
    public void onDestroy() {
        if (speechRecognizer != null) speechRecognizer.destroy();
        if (textToSpeech != null) textToSpeech.shutdown();
        super.onDestroy();
    }
}
