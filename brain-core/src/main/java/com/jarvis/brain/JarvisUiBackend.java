package com.jarvis.brain;

import java.time.Instant;
import java.util.List;

/**
 * Single backend facade for the prototype's UI surfaces. Views read/write these stores;
 * they do not invent independent state. Platform adapters persist/execute where needed.
 */
public final class JarvisUiBackend {
    private final LongTermMemoryStore memory;
    private final ManualMemoryEditor memoryEditor;
    private final UiListStore lists;
    private final RoutineStore routines;
    private final ActivityLog activity;
    private final DeviceStateStore devices;
    private final MusicQueueStore music;
    private final SettingsStore settings;
    private final DefaultAppPreferenceStore defaultApps;
    private final ConnectionRegistry connections;
    private final ToolRegistry tools;
    private final PopupOverlayController popup;

    public JarvisUiBackend(LongTermMemoryStore memory, ToolRegistry tools, ConnectionRegistry connections) {
        this(memory, tools, connections, new SettingsStore());
    }

    public JarvisUiBackend(LongTermMemoryStore memory,
                           ToolRegistry tools,
                           ConnectionRegistry connections,
                           SettingsStore settings) {
        this(memory, tools, connections, settings, new DefaultAppPreferenceStore());
    }

    public JarvisUiBackend(LongTermMemoryStore memory,
                           ToolRegistry tools,
                           ConnectionRegistry connections,
                           SettingsStore settings,
                           DefaultAppPreferenceStore defaultApps) {
        this(memory, tools, connections, settings, defaultApps, new UiListStore());
    }

    public JarvisUiBackend(LongTermMemoryStore memory,
                           ToolRegistry tools,
                           ConnectionRegistry connections,
                           SettingsStore settings,
                           DefaultAppPreferenceStore defaultApps,
                           UiListStore lists) {
        this.memory = memory == null ? new LongTermMemoryStore() : memory;
        this.memoryEditor = new ManualMemoryEditor(this.memory);
        this.tools = tools == null ? ToolRegistry.standard() : tools;
        this.connections = connections == null ? new ConnectionRegistry() : connections;
        this.lists = lists == null ? new UiListStore() : lists;
        this.routines = new RoutineStore();
        this.activity = new ActivityLog();
        this.devices = new DeviceStateStore();
        this.music = new MusicQueueStore();
        this.settings = settings == null ? new SettingsStore() : settings;
        this.defaultApps = defaultApps == null ? new DefaultAppPreferenceStore() : defaultApps;
        this.popup = new PopupOverlayController();
    }

    public LongTermMemoryStore memory(){ return memory; }
    public ManualMemoryEditor memoryEditor(){ return memoryEditor; }
    public UiListStore lists(){ return lists; }
    public RoutineStore routines(){ return routines; }
    public ActivityLog activity(){ return activity; }
    public DeviceStateStore devices(){ return devices; }
    public MusicQueueStore music(){ return music; }
    public SettingsStore settings(){ return settings; }
    public DefaultAppPreferenceStore defaultApps(){ return defaultApps; }
    public ConnectionRegistry connections(){ return connections; }
    public ToolRegistry tools(){ return tools; }
    public PopupOverlayController popup(){ return popup; }

    public List<ToolSpec> skills(){ return tools.specs(); }
    public List<RichMemory> memories(){ return memory.snapshotAll(); }

    public void addManualMemory(String key, MemoryType type, String content, Instant when){
        memoryEditor.addOrReplace(key,type,content,when);
    }
}
