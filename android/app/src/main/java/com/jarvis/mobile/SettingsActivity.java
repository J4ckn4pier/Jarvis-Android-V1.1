package com.jarvis.mobile;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.role.RoleManager;
import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ResolveInfo;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import com.jarvis.mobile.assistant.JarvisVoiceInteractionService;
import com.jarvis.mobile.brain.AndroidDefaultAppPreferencePersistence;
import com.jarvis.mobile.brain.providers.CortexProviderFactory;
import com.jarvis.mobile.brain.providers.LocalAiEndpointPolicy;
import com.jarvis.mobile.brain.providers.SecureSecretStore;
import com.jarvis.mobile.remote.RemoteGoalStateStore;
import com.jarvis.mobile.widgets.QuickActivationWidget;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Canonical user-facing JARVIS Settings. Raw expert provider fields remain available in DeveloperSettingsActivity. */
public class SettingsActivity extends Activity {
    private static final String DEFAULT_LOCAL_AI_ENDPOINT = "http://jarvis-cortex.local:11434/v1/chat/completions";
    private static final String DEFAULT_LOCAL_AI_MODEL = "qwen3:4b";
    private SharedPreferences preferences;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        preferences = getSharedPreferences("jarvis_shell", MODE_PRIVATE);
        getWindow().setStatusBarColor(getColor(R.color.jarvis_bg));
        getWindow().setNavigationBarColor(getColor(R.color.jarvis_bg));
        render();
    }

    @Override protected void onResume() { super.onResume(); if (preferences != null) render(); }

    private void render() {
        LinearLayout page = new LinearLayout(this); page.setOrientation(LinearLayout.VERTICAL); page.setBackgroundColor(getColor(R.color.jarvis_bg));
        page.addView(toolbar(), new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(68)));
        LinearLayout body = new LinearLayout(this); body.setOrientation(LinearLayout.VERTICAL); body.setPadding(dp(18), dp(8), dp(18), dp(40)); body.setBackgroundColor(getColor(R.color.jarvis_bg));
        body.addView(section("VOICE & INVOCATION"));
        body.addView(toggleRow("Voice", "Speak responses aloud", "voice_enabled", true, null));
        body.addView(toggleRow("Wake Word", wakeSummary(), "wake_enabled", true, checked -> { if (checked && !isAssistantRoleHeld()) requestAssistant(); JarvisVoiceInteractionService.refreshPassiveWakePreference(); }));
        body.addView(row("Voice Model", voiceModelSummary(), this::showVoiceModelPicker));
        body.addView(row("Language", languageSummary(), this::showLanguageSettings));
        body.addView(section("JARVIS & APPS"));
        body.addView(row("App Permissions", permissionSummary(), this::showPermissionChoices));
        body.addView(row("AI Providers", providerSummary(), this::showProviderConnections));
        body.addView(row("Backup & Sync", backupSummary(), this::showBackupSyncSettings));
        body.addView(row("Profile", preferences.getString("profile_name", "Sir"), this::showProfileEditor));
        body.addView(row("Default Apps", defaultAppsSummary(), this::showDefaultAppSettings));
        body.addView(row("Personality", preferences.getString("personality_label", "Humble Butler"), this::showPersonalityPicker));
        body.addView(row("Widgets & Lock Screen", widgetLockSummary(), this::showWidgetLockSettings));
        body.addView(section("ANDROID INTEGRATION"));
        body.addView(row("Default Assistant", assistantSummary(), this::requestAssistant));
        body.addView(row("Notifications", "Allow JARVIS to understand notification context", () -> launch(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)));
        body.addView(row("Screen Controls", "Accessibility-powered screen reading and navigation", () -> launch(Settings.ACTION_ACCESSIBILITY_SETTINGS)));
        body.addView(section("ADVANCED"));
        body.addView(row("Developer Options", "Expert endpoints, credentials and diagnostics", () -> startActivity(new Intent(this, DeveloperSettingsActivity.class))));
        ScrollView scroll = new ScrollView(this); scroll.setFillViewport(true); scroll.setBackgroundColor(getColor(R.color.jarvis_bg)); scroll.addView(body);
        page.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f)); setContentView(page);
    }

    private View toolbar() { LinearLayout bar=new LinearLayout(this);bar.setOrientation(LinearLayout.HORIZONTAL);bar.setGravity(Gravity.CENTER_VERTICAL);bar.setPadding(dp(8),0,dp(18),0);bar.setBackgroundColor(getColor(R.color.jarvis_bg));TextView back=new TextView(this);back.setText("‹");back.setTextColor(getColor(R.color.jarvis_cyan));back.setTextSize(38);back.setGravity(Gravity.CENTER);back.setContentDescription("Back");back.setOnClickListener(v->finish());bar.addView(back,new LinearLayout.LayoutParams(dp(48),ViewGroup.LayoutParams.MATCH_PARENT));TextView title=new TextView(this);title.setText("SETTINGS");title.setTextColor(Color.WHITE);title.setTextSize(20);title.setLetterSpacing(0.16f);title.setGravity(Gravity.CENTER_VERTICAL);bar.addView(title,new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.MATCH_PARENT,1f));return bar; }
    private TextView section(String title){TextView v=new TextView(this);v.setText(title);v.setTextColor(getColor(R.color.jarvis_cyan));v.setTextSize(12);v.setLetterSpacing(0.12f);v.setPadding(dp(6),dp(22),dp(6),dp(8));return v;}
    private View row(String title,String subtitle,Runnable action){LinearLayout card=card();LinearLayout copy=new LinearLayout(this);copy.setOrientation(LinearLayout.VERTICAL);TextView name=new TextView(this);name.setText(title);name.setTextColor(Color.WHITE);name.setTextSize(17);TextView detail=new TextView(this);detail.setText(subtitle);detail.setTextColor(getColor(R.color.jarvis_text_dim));detail.setTextSize(13);detail.setPadding(0,dp(3),0,0);copy.addView(name);copy.addView(detail);card.addView(copy,new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1f));TextView arrow=new TextView(this);arrow.setText("›");arrow.setTextColor(getColor(R.color.jarvis_cyan));arrow.setTextSize(28);arrow.setGravity(Gravity.CENTER);card.addView(arrow,new LinearLayout.LayoutParams(dp(32),ViewGroup.LayoutParams.MATCH_PARENT));card.setContentDescription(title+". "+subtitle);card.setOnClickListener(v->action.run());return card;}
    private interface ToggleAction{void changed(boolean checked);}
    private View toggleRow(String title,String subtitle,String key,boolean defaultValue,ToggleAction action){LinearLayout card=card();LinearLayout copy=new LinearLayout(this);copy.setOrientation(LinearLayout.VERTICAL);TextView name=new TextView(this);name.setText(title);name.setTextColor(Color.WHITE);name.setTextSize(17);TextView detail=new TextView(this);detail.setText(subtitle);detail.setTextColor(getColor(R.color.jarvis_text_dim));detail.setTextSize(13);detail.setPadding(0,dp(3),0,0);copy.addView(name);copy.addView(detail);card.addView(copy,new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1f));Switch toggle=new Switch(this);toggle.setChecked(preferences.getBoolean(key,defaultValue));toggle.setContentDescription(title+" toggle");toggle.setOnCheckedChangeListener((button,checked)->{preferences.edit().putBoolean(key,checked).apply();if(action!=null)action.changed(checked);});card.setOnClickListener(v->toggle.setChecked(!toggle.isChecked()));card.addView(toggle,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,ViewGroup.LayoutParams.WRAP_CONTENT));return card;}
    private LinearLayout card(){LinearLayout card=new LinearLayout(this);card.setOrientation(LinearLayout.HORIZONTAL);card.setGravity(Gravity.CENTER_VERTICAL);card.setPadding(dp(16),dp(13),dp(12),dp(13));card.setBackgroundColor(getColor(R.color.jarvis_bg_panel));LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.WRAP_CONTENT);p.setMargins(0,0,0,dp(2));card.setLayoutParams(p);card.setMinimumHeight(dp(68));return card;}

    private void showProfileEditor(){EditText input=new EditText(this);input.setSingleLine(true);input.setText(preferences.getString("profile_name","Sir"));input.setSelectAllOnFocus(true);input.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_CAP_WORDS);new AlertDialog.Builder(this).setTitle("Profile name").setMessage("What should JARVIS call you?").setView(input).setPositiveButton("SAVE",(dialog,which)->{String value=input.getText().toString().trim();if(value.isEmpty())value="Sir";preferences.edit().putString("profile_name",value).apply();render();}).setNegativeButton("CANCEL",null).show();}
    private void showPersonalityPicker(){String[] labels={"Humble Butler","Concise Executive","Warm Companion","Dry & Witty"};String current=preferences.getString("personality_label",labels[0]);int selected=0;for(int i=0;i<labels.length;i++)if(labels[i].equals(current))selected=i;final int initial=selected;new AlertDialog.Builder(this).setTitle("JARVIS Personality").setSingleChoiceItems(labels,selected,null).setPositiveButton("SAVE",(dialog,which)->{AlertDialog d=(AlertDialog)dialog;int index=d.getListView().getCheckedItemPosition();if(index<0)index=initial;preferences.edit().putString("personality_label",labels[index]).apply();render();}).setNegativeButton("CANCEL",null).show();}
    private void showVoiceModelPicker(){String[] labels={"System voice","Measured","Natural","Quick"};float[] rates={1.0f,0.88f,0.96f,1.08f};String current=preferences.getString("voice_model_label",labels[0]);int selected=0;for(int i=0;i<labels.length;i++)if(labels[i].equals(current))selected=i;final int initial=selected;new AlertDialog.Builder(this).setTitle("Voice Model").setSingleChoiceItems(labels,selected,null).setPositiveButton("SAVE",(dialog,which)->{AlertDialog d=(AlertDialog)dialog;int index=d.getListView().getCheckedItemPosition();if(index<0)index=initial;preferences.edit().putString("voice_model_label",labels[index]).putFloat("voice_rate",rates[index]).apply();render();}).setNeutralButton("ANDROID VOICE SETTINGS",(dialog,which)->launch("com.android.settings.TTS_SETTINGS")).setNegativeButton("CANCEL",null).show();}

    private void showLanguageSettings() {
        String[] labels={"Follow Android system","English (United States)","English (United Kingdom)","English (Australia)","Spanish","French","German"};
        String[] tags={"system","en-US","en-GB","en-AU","es","fr","de"};
        String current=preferences.getString("language","system");
        int selected=0;
        for(int i=0;i<tags.length;i++) if(tags[i].equalsIgnoreCase(current)) selected=i;
        final int initial=selected;
        new AlertDialog.Builder(this).setTitle("Language").setSingleChoiceItems(labels,selected,null)
                .setPositiveButton("SAVE",(dialog,which)->{
                    AlertDialog d=(AlertDialog)dialog;
                    int index=d.getListView().getCheckedItemPosition();
                    if(index<0) index=initial;
                    preferences.edit().putString("language", tags[index]).apply();
                    JarvisVoiceInteractionService.refreshPassiveWakePreference();
                    render();
                })
                .setNeutralButton("ANDROID LANGUAGE SETTINGS",(dialog,which)->launch(Settings.ACTION_LOCALE_SETTINGS))
                .setNegativeButton("CANCEL",null).show();
    }

    private void showPermissionChoices(){String[] items={"App permissions","Notification access","Screen controls (Accessibility)"};new AlertDialog.Builder(this).setTitle("JARVIS Permissions").setItems(items,(dialog,which)->{if(which==0)launchAppDetails();else if(which==1)launch(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS);else launch(Settings.ACTION_ACCESSIBILITY_SETTINGS);}).setNegativeButton("CANCEL",null).show();}
    private void showBackupSyncSettings(){boolean remoteConfigured=new RemoteGoalStateStore(this).loadConnection()!=null;String message=remoteConfigured?"Remote JARVIS is configured. Choose whether this phone may use that connection for synchronization-capable features.":"No remote JARVIS connection is configured. Local memory remains on this phone until you explicitly configure one.";boolean enabled=preferences.getBoolean("backup_sync_enabled",false);int initial=enabled&&remoteConfigured?1:0;new AlertDialog.Builder(this).setTitle("Backup & Sync").setMessage(message).setSingleChoiceItems(new String[]{"Local only","Allow configured remote sync"},initial,null).setPositiveButton(remoteConfigured?"SAVE":"CONFIGURE CONNECTION",(dialog,which)->{if(remoteConfigured){AlertDialog d=(AlertDialog)dialog;int selected=d.getListView().getCheckedItemPosition();if(selected<0)selected=initial;preferences.edit().putBoolean("backup_sync_enabled",selected==1).apply();}else{startActivity(new Intent(this,DeveloperSettingsActivity.class));}render();}).setNegativeButton("CANCEL",null).show();}

    private void showDefaultAppSettings(){
        String[] choices={"Browser used by JARVIS","Android system default apps"};
        new AlertDialog.Builder(this).setTitle("Default Apps").setMessage(defaultAppsSummary())
                .setItems(choices,(dialog,which)->{if(which==0)showBrowserPicker();else launch(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS);})
                .setNegativeButton("CANCEL",null).show();
    }

    private void showBrowserPicker(){
        Intent probe=new Intent(Intent.ACTION_VIEW,Uri.parse("https://example.com"));
        List<ResolveInfo> matches=getPackageManager().queryIntentActivities(probe,0);
        ArrayList<String> labels=new ArrayList<>();
        ArrayList<String> packages=new ArrayList<>();
        labels.add("Use Android default"); packages.add("");
        for(ResolveInfo match:matches){
            if(match.activityInfo==null||match.activityInfo.packageName==null)continue;
            String packageName=match.activityInfo.packageName;
            if(packages.contains(packageName))continue;
            CharSequence label=match.loadLabel(getPackageManager());
            labels.add(label==null||label.toString().isBlank()?packageName:label.toString());
            packages.add(packageName);
        }
        AndroidDefaultAppPreferencePersistence store=new AndroidDefaultAppPreferencePersistence(this);
        String current=store.load().getOrDefault("browser","");
        int selected=Math.max(0,packages.indexOf(current));
        new AlertDialog.Builder(this).setTitle("Browser used by JARVIS").setSingleChoiceItems(labels.toArray(new String[0]),selected,null)
                .setPositiveButton("SAVE",(dialog,which)->{AlertDialog d=(AlertDialog)dialog;int index=d.getListView().getCheckedItemPosition();if(index<0)index=selected;String packageName=packages.get(index);if(packageName.isBlank())store.remove("browser");else store.put("browser",packageName);Toast.makeText(this,packageName.isBlank()?"JARVIS will follow Android's default browser.":"JARVIS browser updated.",Toast.LENGTH_SHORT).show();render();})
                .setNeutralButton("ANDROID DEFAULT APPS",(dialog,which)->launch(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS))
                .setNegativeButton("CANCEL",null).show();
    }

    private String defaultAppsSummary(){
        Map<String,String> saved=new AndroidDefaultAppPreferencePersistence(this).load();
        String packageName=saved.get("browser");
        if(packageName==null||packageName.isBlank())return "Browser: follow Android default";
        try{CharSequence label=getPackageManager().getApplicationLabel(getPackageManager().getApplicationInfo(packageName,0));return "Browser: "+label;}
        catch(Exception missing){return "Browser preference unavailable — tap to repair";}
    }

    private void showWidgetLockSettings(){
        boolean lock=preferences.getBoolean("lock_screen_assistant_enabled",true);
        boolean[] pendingLockScreen={lock};
        String[] labels={"Allow assistant access from lock screen","Add JARVIS Quick Access widget","Open Android display / lock-screen settings"};
        new AlertDialog.Builder(this).setTitle("Widgets & Lock Screen")
                .setMultiChoiceItems(labels,new boolean[]{lock,false,false},(dialog,which,checked)->{
                    if(which==0)pendingLockScreen[0]=checked;
                    if(which==1&&checked){dialog.dismiss();requestQuickAccessWidget();}
                    if(which==2&&checked){dialog.dismiss();launch(Settings.ACTION_DISPLAY_SETTINGS);}
                })
                .setPositiveButton("SAVE",(dialog,which)->{
                    preferences.edit().putBoolean("lock_screen_assistant_enabled",pendingLockScreen[0]).apply();
                    render();
                })
                .setNegativeButton("CANCEL",null)
                .show();
    }
    private void requestQuickAccessWidget(){AppWidgetManager manager=AppWidgetManager.getInstance(this);if(Build.VERSION.SDK_INT>=Build.VERSION_CODES.O&&manager.isRequestPinAppWidgetSupported()){boolean requested=manager.requestPinAppWidget(new ComponentName(this,QuickActivationWidget.class),null,null);Toast.makeText(this,requested?"Android opened the JARVIS widget request.":"Your launcher did not accept the widget request.",Toast.LENGTH_SHORT).show();}else{Toast.makeText(this,"Long-press your Home screen, choose Widgets, then add JARVIS Quick Access.",Toast.LENGTH_LONG).show();}}

    private String providerSummary(){String status=CortexProviderFactory.status(this).toLowerCase(Locale.ROOT);if(status.contains("openai-compatible")&&status.contains("configured"))return "Free/local AI configured";if(status.contains("anthropic"))return "Anthropic connected";if(status.contains("openai"))return "OpenAI provider connected";return "Deterministic local fallback";}
    private void showProviderConnections(){String[] choices={"Free Local AI (Ollama)","CONNECT / CHANGE API provider","DISCONNECT / deterministic local fallback"};new AlertDialog.Builder(this).setTitle("AI Providers").setMessage(providerSummary()+"\n\nFree Local AI uses a model running on a computer you control and has no mandatory per-message token bill.").setItems(choices,(dialog,which)->{if(which==0)showLocalAiSetup();else if(which==1)startActivity(new Intent(this,DeveloperSettingsActivity.class));else disconnectProvider();}).setNegativeButton("CANCEL",null).show();}
    private void showLocalAiSetup(){
        SharedPreferences cortex=getSharedPreferences("jarvis_cortex",MODE_PRIVATE);
        LinearLayout form=new LinearLayout(this);form.setOrientation(LinearLayout.VERTICAL);form.setPadding(dp(20),dp(4),dp(20),0);
        EditText endpoint=new EditText(this);endpoint.setHint("Local AI server");endpoint.setSingleLine(true);endpoint.setText(cortex.getString("endpoint",DEFAULT_LOCAL_AI_ENDPOINT));endpoint.setContentDescription("Local AI server");
        EditText model=new EditText(this);model.setHint("Model");model.setSingleLine(true);model.setText(cortex.getString("model",DEFAULT_LOCAL_AI_MODEL));model.setContentDescription("Local AI model");
        form.addView(endpoint);form.addView(model);
        AlertDialog dialog=new AlertDialog.Builder(this)
                .setTitle("Free Local AI (Ollama)")
                .setMessage("Run Ollama on a computer you control, keep the phone and computer on the same network, and use that computer's .local hostname. No OpenAI or Google provider credential is required.")
                .setView(form)
                .setPositiveButton("SAVE",null)
                .setNegativeButton("CANCEL",null)
                .create();
        dialog.setOnShowListener(ignored->dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{
            String endpointValue=endpoint.getText().toString().trim();
            String modelValue=model.getText().toString().trim();
            if(endpointValue.isEmpty())endpointValue=DEFAULT_LOCAL_AI_ENDPOINT;
            if(modelValue.isEmpty())modelValue=DEFAULT_LOCAL_AI_MODEL;
            if(!LocalAiEndpointPolicy.allows(endpointValue)){
                Toast.makeText(this,"Use a local .local hostname, localhost, or loopback address for Free Local AI.",Toast.LENGTH_LONG).show();
                return;
            }
            cortex.edit().putString("mode",CortexProviderFactory.MODE_OPENAI_COMPATIBLE).putString("endpoint",endpointValue).putString("model",modelValue).apply();
            new SecureSecretStore(this).remove("provider_api_key");
            Toast.makeText(this,"Free local AI selected. JARVIS will use it when that computer is reachable.",Toast.LENGTH_LONG).show();
            dialog.dismiss();
            render();
        }));
        dialog.show();
    }
    private void disconnectProvider(){getSharedPreferences("jarvis_cortex",MODE_PRIVATE).edit().putString("mode",CortexProviderFactory.MODE_LOCAL).apply();new SecureSecretStore(this).remove("provider_api_key");Toast.makeText(this,"AI provider disconnected. JARVIS is using its deterministic local fallback.",Toast.LENGTH_SHORT).show();render();}
    private boolean isAssistantRoleHeld(){RoleManager manager=getSystemService(RoleManager.class);return manager!=null&&manager.isRoleAvailable(RoleManager.ROLE_ASSISTANT)&&manager.isRoleHeld(RoleManager.ROLE_ASSISTANT);}
    private String assistantSummary(){return isAssistantRoleHeld()?"JARVIS is the default assistant":"Required for passive wake — tap to enable";}
    private String wakeSummary(){if(!preferences.getBoolean("wake_enabled",true))return "Disabled";return isAssistantRoleHeld()?"Listen locally for “Jarvis” or “Hey Jarvis”":"Requires JARVIS as your default assistant — tap to finish setup";}
    private String voiceModelSummary(){return preferences.getString("voice_model_label","System voice");}
    private String languageSummary(){String tag=preferences.getString("language","system");if("system".equalsIgnoreCase(tag))return "Follow Android system — "+getResources().getConfiguration().getLocales().get(0).getDisplayLanguage();Locale locale=Locale.forLanguageTag(tag);return locale.getDisplayName();}
    private String permissionSummary(){return "Microphone, contacts, calendar, notifications and screen control";}
    private String backupSummary(){if(!preferences.getBoolean("backup_sync_enabled",false))return "Local only";return new RemoteGoalStateStore(this).loadConnection()==null?"Local only — remote connection not configured":"Configured remote sync allowed";}
    private String widgetLockSummary(){return preferences.getBoolean("lock_screen_assistant_enabled",true)?"Lock-screen assistant enabled":"Lock-screen assistant disabled";}
    private void requestAssistant(){RoleManager manager=getSystemService(RoleManager.class);if(manager==null||!manager.isRoleAvailable(RoleManager.ROLE_ASSISTANT)){Toast.makeText(this,"Android did not expose the Assistant role on this device.",Toast.LENGTH_LONG).show();return;}if(manager.isRoleHeld(RoleManager.ROLE_ASSISTANT)){JarvisVoiceInteractionService.refreshPassiveWakePreference();Toast.makeText(this,"JARVIS is already your default assistant.",Toast.LENGTH_SHORT).show();return;}startActivity(manager.createRequestRoleIntent(RoleManager.ROLE_ASSISTANT));}
    private void launchAppDetails(){startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,Uri.parse("package:"+getPackageName())));}
    private void launch(String action){try{startActivity(new Intent(action));}catch(Exception ignored){Toast.makeText(this,"That Android settings page is not available on this device.",Toast.LENGTH_SHORT).show();}}
    private int dp(int value){return Math.round(value*getResources().getDisplayMetrics().density);}
}