package com.jarvis.brain;

import java.nio.file.Files;
import java.nio.file.Path;

/** Pins UI music queue/playback state to app-private durable storage shared with Android UI. */
public final class AndroidMusicQueuePersistenceContractTest {
    public static void main(String[] args) throws Exception {
        Path adapterPath = Path.of("../android/app/src/main/java/com/jarvis/mobile/brain/AndroidMusicQueueStorePersistence.java");
        Path runtimePath = Path.of("../android/app/src/main/java/com/jarvis/mobile/brain/AndroidBrainRuntime.java");
        check(Files.exists(adapterPath), "Android must persist UI music queue/playback state");
        String adapter = Files.readString(adapterPath);
        String runtime = Files.readString(runtimePath);

        check(adapter.contains("implements MusicQueueStorePersistence"),
                "Android music adapter must implement the shared persistence port");
        check(adapter.contains("MODE_PRIVATE"), "music queue state must use app-private storage");
        check(adapter.contains("jarvis_music_queue"), "music queue needs a dedicated persistence namespace");
        check(adapter.contains("currentId"), "music serialization must preserve current track identity");
        check(adapter.contains("positionSeconds"), "music serialization must preserve playback position");
        check(adapter.contains("shuffle") && adapter.contains("repeat") && adapter.contains("volume"),
                "music serialization must preserve playback controls");
        check(runtime.contains("new AndroidMusicQueueStorePersistence(app)"),
                "Android runtime must bind durable music persistence");
        check(runtime.contains("MusicQueueStore music = new MusicQueueStore("),
                "Android runtime must own the durable music store");
        check(runtime.contains("new JarvisUiBackend(memory, tools, connections, settings, defaultApps, lists, routines, activity, devices, music)"),
                "frontend facade must receive the same durable music store alongside the shared durable memory store");

        System.out.println("AndroidMusicQueuePersistenceContractTest passed");
    }

    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
}
