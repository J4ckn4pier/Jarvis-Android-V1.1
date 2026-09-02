package com.jarvis.mobile.ui;

import android.app.AlertDialog;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import com.jarvis.brain.DeviceState;
import com.jarvis.mobile.R;

import java.util.List;
import java.util.Map;

/**
 * "Devices" screen: real typed controls (brightness + color for lights, volume + input for a TV,
 * temperature + mode for a thermostat, plain on/off for anything else), matching the canonical
 * prototype's Devices screen. Everything is stored as vendor-neutral attributes on
 * {@code ui.devices()} — the same store the brain's device tools read when a voice command
 * changes a device, so a light dimmed here is dimmed for real, not just on screen.
 */
public final class DevicesActivity extends JarvisChromeActivity {
    private static final String[] COLORS = {"Warm White", "Cool White", "Red", "Green", "Blue", "Purple"};

    @Override protected String screenTitle() { return "DEVICES"; }

    @Override protected void buildBody(LinearLayout body) {
        LinearLayout addRow = new LinearLayout(this);
        addRow.setOrientation(LinearLayout.HORIZONTAL);
        addRow.setPadding(0, dp(8), 0, dp(4));
        TextView add = new TextView(this);
        add.setText("+ ADD DEVICE");
        add.setTextColor(getColor(R.color.jarvis_cyan));
        add.setTextSize(14);
        add.setLetterSpacing(0.08f);
        add.setPadding(dp(6), dp(10), dp(6), dp(10));
        add.setOnClickListener(v -> showAddDeviceDialog());
        addRow.addView(add);
        body.addView(addRow);

        List<DeviceState> devices = ui.devices().all();
        if (devices.isEmpty()) {
            body.addView(emptyState("No devices yet."));
            return;
        }
        for (DeviceState device : devices) body.addView(deviceCard(device));
    }

    private LinearLayout deviceCard(DeviceState device) {
        LinearLayout card = card();
        LinearLayout copy = new LinearLayout(this);
        copy.setOrientation(LinearLayout.VERTICAL);
        TextView name = new TextView(this);
        name.setText(device.name());
        name.setTextColor(Color.WHITE);
        name.setTextSize(16);
        TextView type = new TextView(this);
        type.setText(summarize(device));
        type.setTextColor(getColor(R.color.jarvis_text_dim));
        type.setTextSize(13);
        type.setPadding(0, dp(3), 0, 0);
        copy.addView(name);
        copy.addView(type);
        card.addView(copy, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        Switch power = new Switch(this);
        power.setChecked(device.on());
        power.setContentDescription(device.name() + " power");
        power.setOnCheckedChangeListener((b, checked) -> { ui.devices().setPower(device.id(), checked); render(); });
        card.addView(power, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        card.setOnClickListener(v -> showControls(device));
        return card;
    }

    private String summarize(DeviceState device) {
        Map<String, String> a = device.attributes();
        switch (device.type()) {
            case "light": return (device.on() ? "On" : "Off") + " · " + a.getOrDefault("brightness", "70") + "% · " + a.getOrDefault("color", "Warm White");
            case "tv": return (device.on() ? "On" : "Off") + " · Vol " + a.getOrDefault("volume", "20") + " · " + a.getOrDefault("input", "HDMI 1");
            case "thermostat": return a.getOrDefault("temp", "70") + "°  · " + a.getOrDefault("mode", "Auto");
            default: return device.on() ? "On" : "Off";
        }
    }

    private void showControls(DeviceState device) {
        switch (device.type()) {
            case "light": showLightControls(device); return;
            case "tv": showTvControls(device); return;
            case "thermostat": showThermostatControls(device); return;
            default: showGenericControls(device);
        }
    }

    private void showLightControls(DeviceState device) {
        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(dp(24), dp(8), dp(24), dp(8));

        TextView brightnessLabel = new TextView(this);
        int brightness = parse(device.attributes().get("brightness"), 70);
        brightnessLabel.setText("Brightness: " + brightness + "%");
        brightnessLabel.setTextColor(Color.WHITE);
        SeekBar brightnessBar = new SeekBar(this);
        brightnessBar.setMax(100);
        brightnessBar.setProgress(brightness);
        brightnessBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                brightnessLabel.setText("Brightness: " + progress + "%");
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) { }
            @Override public void onStopTrackingTouch(SeekBar seekBar) {
                ui.devices().setAttribute(device.id(), "brightness", String.valueOf(seekBar.getProgress()));
            }
        });
        form.addView(brightnessLabel);
        form.addView(brightnessBar);

        TextView colorLabel = new TextView(this);
        colorLabel.setText("Color");
        colorLabel.setTextColor(getColor(R.color.jarvis_text_dim));
        colorLabel.setPadding(0, dp(14), 0, dp(4));
        form.addView(colorLabel);

        LinearLayout swatches = new LinearLayout(this);
        swatches.setOrientation(LinearLayout.HORIZONTAL);
        String current = device.attributes().getOrDefault("color", COLORS[0]);
        for (String colorName : COLORS) {
            TextView swatch = new TextView(this);
            swatch.setText(colorName.substring(0, 1));
            swatch.setGravity(Gravity.CENTER);
            swatch.setTextColor(Color.WHITE);
            GradientDrawable bg = new GradientDrawable();
            bg.setColor(swatchColor(colorName));
            bg.setShape(GradientDrawable.OVAL);
            if (colorName.equals(current)) bg.setStroke(dp(2), getColor(R.color.jarvis_cyan));
            swatch.setBackground(bg);
            LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(dp(36), dp(36));
            p.setMargins(0, 0, dp(8), 0);
            swatch.setLayoutParams(p);
            swatch.setContentDescription(colorName);
            swatch.setOnClickListener(v -> { ui.devices().setAttribute(device.id(), "color", colorName); render(); });
            swatches.addView(swatch);
        }
        form.addView(swatches);

        new AlertDialog.Builder(this)
                .setTitle(device.name())
                .setView(form)
                .setPositiveButton("DONE", (d, w) -> render())
                .show();
    }

    private void showTvControls(DeviceState device) {
        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(dp(24), dp(8), dp(24), dp(8));

        TextView volumeLabel = new TextView(this);
        int volume = parse(device.attributes().get("volume"), 20);
        volumeLabel.setText("Volume: " + volume);
        volumeLabel.setTextColor(Color.WHITE);
        SeekBar volumeBar = new SeekBar(this);
        volumeBar.setMax(100);
        volumeBar.setProgress(volume);
        volumeBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) { volumeLabel.setText("Volume: " + progress); }
            @Override public void onStartTrackingTouch(SeekBar seekBar) { }
            @Override public void onStopTrackingTouch(SeekBar seekBar) {
                ui.devices().setAttribute(device.id(), "volume", String.valueOf(seekBar.getProgress()));
            }
        });
        form.addView(volumeLabel);
        form.addView(volumeBar);

        TextView inputLabel = new TextView(this);
        inputLabel.setText("Input");
        inputLabel.setTextColor(getColor(R.color.jarvis_text_dim));
        inputLabel.setPadding(0, dp(14), 0, dp(4));
        form.addView(inputLabel);

        String[] inputs = {"HDMI 1", "HDMI 2", "HDMI 3", "TV", "Streaming"};
        Spinner inputSpinner = new Spinner(this);
        inputSpinner.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, inputs));
        String currentInput = device.attributes().getOrDefault("input", inputs[0]);
        for (int i = 0; i < inputs.length; i++) if (inputs[i].equals(currentInput)) inputSpinner.setSelection(i);
        form.addView(inputSpinner);

        new AlertDialog.Builder(this)
                .setTitle(device.name())
                .setView(form)
                .setPositiveButton("DONE", (d, w) -> {
                    int index = inputSpinner.getSelectedItemPosition();
                    if (index >= 0) ui.devices().setAttribute(device.id(), "input", inputs[index]);
                    render();
                })
                .show();
    }

    private void showThermostatControls(DeviceState device) {
        int[] temp = {parse(device.attributes().get("temp"), 70)};
        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(dp(24), dp(8), dp(24), dp(8));

        LinearLayout stepper = new LinearLayout(this);
        stepper.setOrientation(LinearLayout.HORIZONTAL);
        stepper.setGravity(Gravity.CENTER);
        TextView minus = stepperButton("−");
        TextView tempLabel = new TextView(this);
        tempLabel.setText(temp[0] + "°");
        tempLabel.setTextColor(Color.WHITE);
        tempLabel.setTextSize(28);
        tempLabel.setGravity(Gravity.CENTER);
        tempLabel.setPadding(dp(24), 0, dp(24), 0);
        TextView plus = stepperButton("+");
        minus.setOnClickListener(v -> { temp[0] = Math.max(50, temp[0] - 1); tempLabel.setText(temp[0] + "°"); });
        plus.setOnClickListener(v -> { temp[0] = Math.min(90, temp[0] + 1); tempLabel.setText(temp[0] + "°"); });
        stepper.addView(minus);
        stepper.addView(tempLabel);
        stepper.addView(plus);
        form.addView(stepper);

        TextView modeLabel = new TextView(this);
        modeLabel.setText("Mode");
        modeLabel.setTextColor(getColor(R.color.jarvis_text_dim));
        modeLabel.setPadding(0, dp(14), 0, dp(4));
        form.addView(modeLabel);

        String[] modes = {"Auto", "Heat", "Cool", "Off"};
        Spinner modeSpinner = new Spinner(this);
        modeSpinner.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, modes));
        String currentMode = device.attributes().getOrDefault("mode", modes[0]);
        for (int i = 0; i < modes.length; i++) if (modes[i].equals(currentMode)) modeSpinner.setSelection(i);
        form.addView(modeSpinner);

        new AlertDialog.Builder(this)
                .setTitle(device.name())
                .setView(form)
                .setPositiveButton("DONE", (d, w) -> {
                    ui.devices().setAttribute(device.id(), "temp", String.valueOf(temp[0]));
                    int index = modeSpinner.getSelectedItemPosition();
                    if (index >= 0) ui.devices().setAttribute(device.id(), "mode", modes[index]);
                    render();
                })
                .show();
    }

    private void showGenericControls(DeviceState device) {
        new AlertDialog.Builder(this)
                .setTitle(device.name())
                .setMessage(device.on() ? "This device is on." : "This device is off.")
                .setPositiveButton(device.on() ? "TURN OFF" : "TURN ON", (d, w) -> { ui.devices().setPower(device.id(), !device.on()); render(); })
                .setNeutralButton("REMOVE", (d, w) -> { ui.devices().remove(device.id()); render(); })
                .setNegativeButton("CANCEL", null)
                .show();
    }

    private TextView stepperButton(String label) {
        TextView v = new TextView(this);
        v.setText(label);
        v.setTextColor(getColor(R.color.jarvis_cyan));
        v.setTextSize(28);
        v.setGravity(Gravity.CENTER);
        v.setPadding(dp(18), dp(10), dp(18), dp(10));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(getColor(R.color.jarvis_bg_panel_raised));
        bg.setCornerRadius(dp(8));
        v.setBackground(bg);
        return v;
    }

    private int swatchColor(String colorName) {
        switch (colorName) {
            case "Warm White": return Color.parseColor("#FFE9C7");
            case "Cool White": return Color.parseColor("#E4F3FF");
            case "Red": return Color.parseColor("#FF6B6B");
            case "Green": return Color.parseColor("#6BFFA0");
            case "Blue": return Color.parseColor("#6BB8FF");
            case "Purple": return Color.parseColor("#C08CFF");
            default: return Color.WHITE;
        }
    }

    private void showAddDeviceDialog() {
        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(dp(24), dp(8), dp(24), dp(8));

        EditText nameInput = new EditText(this);
        nameInput.setHint("Device name (e.g. Bedroom Lights)");
        String[] types = {"light", "tv", "thermostat", "generic"};
        Spinner typeSpinner = new Spinner(this);
        typeSpinner.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, types));
        form.addView(nameInput);
        form.addView(typeSpinner);

        new AlertDialog.Builder(this)
                .setTitle("Add device")
                .setView(form)
                .setPositiveButton("SAVE", (d, w) -> {
                    String name = nameInput.getText().toString().trim();
                    if (name.isEmpty()) {
                        Toast.makeText(this, "Name required", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    String type = types[Math.max(0, typeSpinner.getSelectedItemPosition())];
                    String id = "device-" + System.currentTimeMillis();
                    ui.devices().upsert(new DeviceState(id, name, type, false, Map.of()));
                    render();
                })
                .setNegativeButton("CANCEL", null)
                .show();
    }

    private static int parse(String value, int fallback) {
        try { return value == null ? fallback : Integer.parseInt(value); } catch (NumberFormatException e) { return fallback; }
    }
}
